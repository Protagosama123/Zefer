"""
Zefer nail-pose algorithm (reference implementation, pure Python).

Input per frame : 21 hand landmarks in IMAGE PIXELS (x, y, z) + 21 world landmarks (metres)
Output per frame: for each finger -> anchor, angle, width, height, alpha (all in pixels / radians)

Everything here is deliberately free of camera / UI code so it can be ported to Kotlin
function-by-function and verified against the recorded session.json.
"""
import math
from dataclasses import dataclass, asdict
import numpy as np

HAND_CONNECTIONS = [
    (0, 1), (1, 2), (2, 3), (3, 4),
    (0, 5), (5, 6), (6, 7), (7, 8),
    (5, 9), (9, 10), (10, 11), (11, 12),
    (9, 13), (13, 14), (14, 15), (15, 16),
    (13, 17), (17, 18), (18, 19), (19, 20), (0, 17),
]

# finger -> (mcp, pip, dip, tip). For the thumb these are (CMC, MCP, IP, TIP).
FINGERS = {
    "thumb": (1, 2, 3, 4),
    "index": (5, 6, 7, 8),
    "middle": (9, 10, 11, 12),
    "ring": (13, 14, 15, 16),
    "pinky": (17, 18, 19, 20),
}
# Nail width relative to the hand's own size (palm length, see hand_geometry). Relative units adapt to small
# (kid) and large hands without needing metric scale, which MediaPipe's world landmarks do not reliably give.
NAIL_WIDTH_REL = {"thumb": 0.195, "index": 0.172, "middle": 0.172, "ring": 0.161, "pinky": 0.138}   # tuned on 2 real photos (x1.15 of first guess)
# Typical DIP->TIP (thumb: IP->TIP) image length relative to the same unit; adapts width to long/short fingers.
TYPICAL_DIPTIP_REL = {"thumb": 0.30, "index": 0.24, "middle": 0.26, "ring": 0.25, "pinky": 0.20}


@dataclass
class Params:
    nail_back_rel: float = 0.14     # nail base sits this far behind the TIP landmark (x hand unit); covers the real nail bed
    nail_len_rel: float = 0.30      # visible length incl. extension, base -> free edge (x hand unit); live path only
    label_mirrored: bool = False    # True if the handedness label was computed on a mirrored image
    # One Euro filter settings. Units: position in px, angle in rad, size in px.
    pos_mincut: float = 1.5
    pos_beta: float = 0.02
    ang_mincut: float = 1.5
    ang_beta: float = 1.0
    size_mincut: float = 0.8
    size_beta: float = 0.0
    # visibility
    flex_hide_start_deg: float = 60.0   # nail starts fading when finger flexion exceeds this
    flex_hide_end_deg: float = 100.0    # fully hidden
    back_show_start: float = 0.10       # back-of-hand confidence where nails start to appear
    back_show_end: float = 0.35         # ... and are fully shown
    fit_scale: float = 1.0              # global nail size multiplier (Snug 0.9 / Regular 1.0 / Wide 1.1)
    geo_blend: float = 0.3              # 0 = fixed nail width, 1 = width fully from this finger's own length
    min_width_foreshorten: float = 0.35
    min_len_foreshorten: float = 0.45
    hold_ms: float = 150.0              # keep drawing last pose this long after tracking is lost
    reacquire_gap_ms: float = 300.0     # reset filters if the hand was gone longer than this


def smoothstep(e0, e1, x):
    if e1 == e0:
        return 1.0 if x >= e1 else 0.0
    t = min(1.0, max(0.0, (x - e0) / (e1 - e0)))
    return t * t * (3 - 2 * t)


def wrap_pi(a):
    """Shortest signed angle difference, in (-pi, pi]."""
    return math.atan2(math.sin(a), math.cos(a))


class OneEuro:
    def __init__(self, mincut, beta, dcut=1.0):
        self.mincut, self.beta, self.dcut = mincut, beta, dcut
        self.reset()

    def reset(self):
        self.t = None
        self.x_raw_prev = 0.0
        self.x_hat = 0.0
        self.dx_hat = 0.0

    @staticmethod
    def _alpha(cutoff, dt):
        tau = 1.0 / (2 * math.pi * cutoff)
        return 1.0 / (1.0 + tau / dt)

    def __call__(self, x, t_s):
        if self.t is None:
            self.t, self.x_raw_prev, self.x_hat, self.dx_hat = t_s, x, x, 0.0
            return x
        dt = max(t_s - self.t, 1e-3)
        self.t = t_s
        dx = (x - self.x_raw_prev) / dt
        self.x_raw_prev = x
        a_d = self._alpha(self.dcut, dt)
        self.dx_hat = a_d * dx + (1 - a_d) * self.dx_hat
        cutoff = self.mincut + self.beta * abs(self.dx_hat)
        a = self._alpha(cutoff, dt)
        self.x_hat = a * x + (1 - a) * self.x_hat
        return self.x_hat


class AngleFilter:
    """One Euro on an UNWRAPPED angle: each new sample is moved to the branch nearest the
    previous output, so crossing +-pi never causes a spin."""

    def __init__(self, mincut, beta):
        self.f = OneEuro(mincut, beta)
        self.prev = None
        self.out = 0.0

    def reset(self):
        self.f.reset()
        self.prev = None

    def __call__(self, theta, t_s):
        if self.prev is None:
            self.prev = theta
            self.out = self.f(theta, t_s)
            return self.out
        theta_u = self.prev + wrap_pi(theta - self.prev)
        self.prev = theta_u
        self.out = self.f(theta_u, t_s)
        return self.out


class FingerState:
    def __init__(self, p: Params):
        self.p = p
        self.reset()

    def reset(self):
        p = self.p
        self.fx = OneEuro(p.pos_mincut, p.pos_beta)
        self.fy = OneEuro(p.pos_mincut, p.pos_beta)
        self.fang = AngleFilter(p.ang_mincut, p.ang_beta)
        self.fw = OneEuro(p.size_mincut, p.size_beta)
        self.fh = OneEuro(p.size_mincut, p.size_beta)
        self.alpha = 0.0
        self.last_theta = 0.0
        self.t_alpha = None


def hand_geometry(px):
    """Image-space hand size and twist, from 2D landmarks only (the 3D world landmarks proved inconsistent).
    unit_px : hand size ~ palm length (wrist -> middle MCP). Uses the larger of the palm length and the MCP width
              (index MCP -> pinky MCP / 0.80), so tilting toward the camera (shortens length) or twisting
              (shortens width) does not shrink the unit by itself.
    yaw_cos : ~cos of the twist about the finger axis: 1 = back/palm facing the camera, 0 = edge-on.
    """
    palm = float(np.linalg.norm(px[9, :2] - px[0, :2]))
    palm = max(palm, 0.93 * float(np.linalg.norm(px[5, :2] - px[0, :2])), 0.95 * float(np.linalg.norm(px[13, :2] - px[0, :2])))
    width = float(np.linalg.norm(px[5, :2] - px[17, :2]))
    unit = max(palm, width / 0.80, 1e-3)
    yaw_cos = min(1.0, width / (0.80 * max(palm, 1e-3)))
    return unit, yaw_cos


def back_confidence_from_image(px, world, label, label_mirrored=False):
    """Back-of-hand-toward-camera confidence in [-1, 1] for a single frame, no calibration step.

    magnitude: yaw_cos from hand_geometry (1 = facing the camera, 0 = edge-on).
    sign     : image chirality (sign of the 2D cross product wrist->index MCP x wrist->pinky MCP, y down)
               combined with the handedness label. Measured on real photos (MediaPipe Tasks, IMAGE mode,
               normal non-mirrored photos): the label names the real hand as seen, i.e. a left hand
               photographed from the back is labelled "Left".
               Real RIGHT hand, back view, fingers up: cross > 0; palm view: cross < 0; a left hand is the mirror.
                   sign = sign(cross) * (+1 if real-right else -1)
               If the label comes from a mirrored image, pass label_mirrored=True.
    Returns (back_conf, real_right). back_conf > 0 => back of hand faces the camera.
    UNVERIFIED: palm-facing photos and right hands have not been tested with real data yet.
    """
    _, mag = hand_geometry(px)
    v1, v2 = px[5, :2] - px[0, :2], px[17, :2] - px[0, :2]
    cross = float(v1[0] * v2[1] - v1[1] * v2[0])
    real_right = (label == "Right") != bool(label_mirrored)
    sign = (1.0 if cross > 0 else -1.0) * (1.0 if real_right else -1.0)
    return sign * mag, real_right


def raw_finger_poses(px, world, back_conf, unit, p, adj=None, last_theta=None):
    """Per-finger pose for ONE frame, no temporal filtering. Shared by the static and live paths.

    px (21,3) image pixels (z scaled like x), back_conf from the facing test, unit = hand size in px.
    world is accepted for API stability but unused: world landmarks were inconsistent on real photos.
    adj: optional manual tweaks {size, length, offset (fraction of nail width, + toward tip), rot_deg}.
    Returns {finger: dict(raw_x, raw_y, raw_ang, raw_w, raw_h, flex_deg, proj, target_alpha, width_rel)}.
    """
    adj = adj or {}
    size, length = adj.get("size", 1.0), adj.get("length", 1.0)
    offset, rot = adj.get("offset", 0.0), math.radians(adj.get("rot_deg", 0.0))
    last_theta = last_theta if last_theta is not None else {}
    vis_back = smoothstep(p.back_show_start, p.back_show_end, back_conf)
    width_fs = max(p.min_width_foreshorten, min(abs(back_conf), 1.0))
    out = {}
    for name, (mcp, pip_, dip, tip) in FINGERS.items():
        d3 = px[tip] - px[dip]
        L2 = float(np.hypot(d3[0], d3[1]))
        L3 = float(np.linalg.norm(d3))
        proj = L2 / L3 if L3 > 1e-6 else 1.0          # 1 = in image plane, 0 = pointing at the camera

        # angle: theta = atan2(dy, dx) + pi/2 (image coords, y down; 0 = finger pointing up)
        if proj < 0.25 or L2 < 1e-3:
            theta = last_theta.get(name, 0.0)
        else:
            theta = math.atan2(d3[1], d3[0]) + math.pi / 2
        last_theta[name] = theta
        theta_adj = theta + rot
        ux, uy = math.sin(theta_adj), -math.cos(theta_adj)   # unit vector along the finger toward the tip (+ nudge)

        # nail width: fixed relative width blended with this finger's own image length, clamped
        const = NAIL_WIDTH_REL[name] * p.fit_scale
        if proj > 0.7:
            geo = const * (L2 / unit) / TYPICAL_DIPTIP_REL[name]
            width_rel = min(max((1 - p.geo_blend) * const + p.geo_blend * geo, 0.8 * const), 1.25 * const)
        else:
            width_rel = const
        width_rel *= size

        back_px = p.nail_back_rel * unit
        base_x = float(px[tip, 0] - ux * back_px + ux * offset * width_rel * unit)
        base_y = float(px[tip, 1] - uy * back_px + uy * offset * width_rel * unit)
        raw_w = width_rel * unit * width_fs
        raw_h = p.nail_len_rel * unit * max(p.min_len_foreshorten, proj) * length * size

        # flexion from image landmarks (x, y, z-in-px): angle between proximal and distal bone directions
        v1, v2 = px[pip_] - px[mcp], px[tip] - px[dip]
        den = float(np.linalg.norm(v1) * np.linalg.norm(v2))
        flex = math.degrees(math.acos(float(np.clip(np.dot(v1, v2) / den, -1, 1)))) if den > 1e-9 else 0.0
        vis_flex = 1.0 - smoothstep(p.flex_hide_start_deg, p.flex_hide_end_deg, flex)
        vb = smoothstep(-0.30, 0.0, back_conf) if name == "thumb" else vis_back   # thumb is seen from the side
        out[name] = dict(raw_x=base_x, raw_y=base_y, raw_ang=theta_adj, raw_w=raw_w, raw_h=raw_h,
                         flex_deg=flex, proj=proj, target_alpha=vis_flex * vb, width_rel=width_rel)
    return out


class HandTracker:
    def __init__(self, params: Params | None = None):
        self.p = params or Params()
        self.fingers = {k: FingerState(self.p) for k in FINGERS}
        self.fppm = OneEuro(0.8, 0.002)
        self.palm_sign = 1.0           # set by calibrate(); +1/-1
        self.calib = []
        self.last_det_t = None
        self.last_out = None
        self.lost_events = 0
        self._was_detected = False

    # ---------- palm / back-of-hand ----------
    @staticmethod
    def palm_normal_z(world, label):
        """Signed z of the unit palm normal, times a handedness sign.
        The sign convention (which side means 'back of hand') is calibrated, not assumed."""
        n = np.cross(world[5] - world[0], world[17] - world[0])
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            return 0.0
        hs = 1.0 if label == "Right" else -1.0
        return float(n[2] / nn * hs)

    def add_calibration_sample(self, world, label):
        self.calib.append(self.palm_normal_z(world, label))

    def finish_calibration(self):
        if len(self.calib) >= 5:
            self.palm_sign = 1.0 if np.median(self.calib) >= 0 else -1.0
        return self.palm_sign

    # ---------- main ----------
    def update(self, t_ms, px, world, label, score):
        """px, world: (21,3) numpy arrays or None when no hand was detected.
        Returns (poses, info). poses: dict finger -> dict."""
        t_s = t_ms / 1000.0
        p = self.p

        if px is None:
            if self._was_detected:
                self.lost_events += 1
            self._was_detected = False
            if self.last_out is not None and self.last_det_t is not None:
                el = t_ms - self.last_det_t
                if 0 < el <= p.hold_ms:
                    fade = 1.0 - el / p.hold_ms
                    faded = {k: dict(v, alpha=v["alpha"] * fade) for k, v in self.last_out.items()}
                    return faded, {"held": True, "back_conf": None, "ppm": None}
            return {}, {"held": False, "back_conf": None, "ppm": None}

        # re-acquisition after a long gap: reset filters so nails do not slide in from the old spot
        if self.last_det_t is not None and t_ms - self.last_det_t > p.reacquire_gap_ms:
            for fs in self.fingers.values():
                fs.reset()
            self.fppm.reset()
        self._was_detected = True
        self.last_det_t = t_ms

        unit_raw, yaw_cos = hand_geometry(px)
        unit = self.fppm(unit_raw, t_s)
        sgn = 1.0 if self.palm_normal_z(world, label) * self.palm_sign >= 0 else -1.0
        back_conf = sgn * yaw_cos            # sign: calibrated world normal; magnitude: image twist estimate
        ppm = unit
        last_theta = {k: fs.last_theta for k, fs in self.fingers.items()}
        raws = raw_finger_poses(px, world, back_conf, unit, p, last_theta=last_theta)

        out = {}
        for name, r in raws.items():
            fs = self.fingers[name]
            fs.last_theta = last_theta[name]
            target_alpha = r["target_alpha"]
            if fs.t_alpha is None:
                fs.alpha = target_alpha
            else:
                k = 1 - math.exp(-max(t_s - fs.t_alpha, 1e-3) / 0.06)
                fs.alpha += k * (target_alpha - fs.alpha)
            fs.t_alpha = t_s
            out[name] = dict(
                raw_x=r["raw_x"], raw_y=r["raw_y"],
                sm_x=fs.fx(r["raw_x"], t_s), sm_y=fs.fy(r["raw_y"], t_s),
                raw_ang=r["raw_ang"], sm_ang=fs.fang(r["raw_ang"], t_s),
                raw_w=r["raw_w"], sm_w=fs.fw(r["raw_w"], t_s),
                raw_h=r["raw_h"], sm_h=fs.fh(r["raw_h"], t_s),
                flex_deg=r["flex_deg"], proj=r["proj"], alpha=fs.alpha)
        self.last_out = out
        return out, {"held": False, "back_conf": back_conf, "ppm": ppm}
