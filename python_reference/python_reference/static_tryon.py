"""
Static-image nail try-on (Version 2 reference implementation).

  load_image        EXIF-correct photo -> BGR, long edge capped
  make_detector     MediaPipe HandLandmarker in IMAGE mode (2 hands, low thresholds)
  detect_hands      detection with rotation / upscale fallbacks; landmarks always returned in ORIGINAL image pixels
  compute_hand      shared pose maths (nail_algo.raw_finger_poses) for one detected hand
  render            sprite warp + contact shadow + mild lighting match
  draw_debug        skeleton, DIP/TIP, bone vectors, per-finger pose text
"""
import io, json, math, os
import numpy as np
import cv2
from nail_algo import (HAND_CONNECTIONS, FINGERS, Params, raw_finger_poses,
                       back_confidence_from_image, hand_geometry)

MAX_EDGE = 1600


# ------------------------------------------------------------------ image io
def load_image(src, max_edge=MAX_EDGE):
    """src: path or bytes. Applies EXIF orientation (phone photos are often stored sideways)."""
    from PIL import Image, ImageOps
    im = Image.open(io.BytesIO(src) if isinstance(src, (bytes, bytearray)) else src)
    im = ImageOps.exif_transpose(im).convert("RGB")
    w, h = im.size
    s = max_edge / max(w, h)
    if s < 1:
        im = im.resize((round(w * s), round(h * s)), Image.LANCZOS)
    return cv2.cvtColor(np.array(im), cv2.COLOR_RGB2BGR)


# ------------------------------------------------------------------ detection
def make_detector(model_path, num_hands=2, conf=0.3):
    import mediapipe as mp
    from mediapipe.tasks import python as mpp
    from mediapipe.tasks.python import vision
    opts = vision.HandLandmarkerOptions(
        base_options=mpp.BaseOptions(model_asset_path=model_path),
        running_mode=vision.RunningMode.IMAGE, num_hands=num_hands,
        min_hand_detection_confidence=conf, min_hand_presence_confidence=conf, min_tracking_confidence=conf)
    lm = vision.HandLandmarker.create_from_options(opts)

    def run(bgr):
        rgb = np.ascontiguousarray(cv2.cvtColor(bgr, cv2.COLOR_BGR2RGB))
        return lm.detect(mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb))
    return run


def _unrotate_px(pts, k, W, H):
    """pts: (N,3) pixel coords in an image that was np.rot90(orig, k) (orig is W x H). Back to original."""
    x, y, z = pts[:, 0].copy(), pts[:, 1].copy(), pts[:, 2]
    if k == 1:   ox, oy = W - y, x
    elif k == 2: ox, oy = W - x, H - y
    elif k == 3: ox, oy = y, H - x
    else:        ox, oy = x, y
    return np.stack([ox, oy, z], 1)


def _unrotate_world(w, k):
    x, y, z = w[:, 0].copy(), w[:, 1].copy(), w[:, 2]
    if k == 1:   ox, oy = -y, x
    elif k == 2: ox, oy = -x, -y
    elif k == 3: ox, oy = y, -x
    else:        ox, oy = x, y
    return np.stack([ox, oy, z], 1)


# The most recent forward transform (original px -> processed px) used by detect_hands; tests read it.
_LAST_FORWARD = None

FALLBACK_ANGLES = (0, 90, 270, 180, 45, 315, 135, 225)     # image rotations tried until a hand is found (degrees, CCW)


def rotate_bound(bgr, angle_deg, border=(128, 128, 128)):
    """Rotate counter-clockwise by angle_deg, enlarging the canvas so nothing is cut off.
    Returns (image, M) where M is the 2x3 forward affine (original px -> rotated px)."""
    h, w = bgr.shape[:2]
    c = (w / 2.0, h / 2.0)
    M = cv2.getRotationMatrix2D(c, angle_deg, 1.0)
    cos, sin = abs(M[0, 0]), abs(M[0, 1])
    nw, nh = int(round(h * sin + w * cos)), int(round(h * cos + w * sin))
    M[0, 2] += nw / 2.0 - c[0]
    M[1, 2] += nh / 2.0 - c[1]
    out = cv2.warpAffine(bgr, M, (nw, nh), flags=cv2.INTER_LINEAR, borderMode=cv2.BORDER_CONSTANT, borderValue=border)
    return out, M


def _forward(bgr, angle, up):
    """Image for one detection attempt plus its forward affine (original px -> attempt px)."""
    H, W = bgr.shape[:2]
    if angle % 360 == 0:
        img, M = np.ascontiguousarray(bgr), np.array([[1, 0, 0], [0, 1, 0]], np.float64)
    elif angle % 90 == 0:                                       # exact, no interpolation
        k = (angle // 90) % 4
        img = np.ascontiguousarray(np.rot90(bgr, k))
        M = np.array({1: [[0, 1, 0], [-1, 0, W]], 2: [[-1, 0, W], [0, -1, H]], 3: [[0, -1, H], [1, 0, 0]]}[k], np.float64)
    else:
        img, M = rotate_bound(bgr, angle)
        M = M.astype(np.float64)
    if up != 1.0:
        img = cv2.resize(img, None, fx=up, fy=up, interpolation=cv2.INTER_CUBIC)
        M = M * up
    return img, M


def detect_hands(run, bgr, fallback=True, angles=FALLBACK_ANGLES):
    """Returns (hands, attempt). hands = [dict(px, world, label, score)] with px (21,3) in ORIGINAL image pixels
    (z scaled like x) and world (21,3) rotated back into the original orientation.
    attempt = dict(angle=<deg CCW the image was rotated>, up=<upscale>) or None.
    Without fallback only the unrotated image is tried. With fallback the image is rotated (90-degree steps are exact,
    45-degree steps use interpolation) and finally enlarged, until a hand is found."""
    global _LAST_FORWARD
    attempts = [dict(angle=0, up=1.0)]
    if fallback:
        attempts = [dict(angle=a, up=1.0) for a in angles] + [dict(angle=0, up=1.5)]
    for at in attempts:
        img, M = _forward(bgr, at["angle"], at["up"])
        _LAST_FORWARD = (M.copy(), at["up"])
        res = run(img)
        if not res.hand_landmarks:
            continue
        h_r, w_r = img.shape[:2]
        A = M[:, :2] / at["up"]                                  # rotation part (scale removed)
        Ainv = np.linalg.inv(M[:, :2])
        hands = []
        for lms, wl, hd in zip(res.hand_landmarks, res.hand_world_landmarks, res.handedness):
            pr = np.array([[l.x * w_r, l.y * h_r, l.z * w_r] for l in lms], dtype=np.float64)
            xy = (Ainv @ (pr[:, :2] - M[:, 2]).T).T
            px = np.column_stack([xy, pr[:, 2] / at["up"]])
            wd = np.array([[l.x, l.y, l.z] for l in wl], dtype=np.float64)
            wxy = (np.linalg.inv(A) @ wd[:, :2].T).T
            world = np.column_stack([wxy, wd[:, 2]])
            hands.append(dict(px=px, world=world, label=hd[0].category_name, score=float(hd[0].score)))
        return hands, at
    return [], None


# ------------------------------------------------------------------ sprites
class SpriteSet:
    """Loads the sliced nail PNGs listed in manifest.json.

    Finds each file even if the zip was extracted into a nested folder, and fails with the exact
    missing path instead of a None image deep inside rendering."""

    def __init__(self, folder):
        self.folder = folder
        mpath = self._find("manifest.json")
        if mpath is None:
            raise FileNotFoundError(f"manifest.json not found under '{folder}'. Re-upload zefer_sprites.zip and run the sprites cell again.")
        self.root = os.path.dirname(mpath)
        self.manifest = json.load(open(mpath))
        self.cache = {}
        self.check()

    def _find(self, name):
        direct = os.path.join(self.folder, name)
        if os.path.isfile(direct):
            return direct
        for r, _, fs in os.walk(self.folder):
            if name in fs:
                return os.path.join(r, name)
        return None

    def check(self):
        """Verify every sprite in the manifest exists and decodes. Raises with the full list of problems."""
        problems = []
        for sname, fingers in self.manifest["sets"].items():
            for finger, e in fingers.items():
                p = self._find(e["file"])
                if p is None:
                    problems.append(f"missing: {sname}/{finger} -> {e['file']}")
                elif cv2.imread(p, cv2.IMREAD_UNCHANGED) is None:
                    problems.append(f"unreadable: {p}")
        if problems:
            raise FileNotFoundError("Sprite check failed:\n  " + "\n  ".join(problems))
        return True

    def sets(self):
        return list(self.manifest["sets"].keys())

    def get(self, set_name, finger, mirror=False):
        key = (set_name, finger, mirror)
        if key not in self.cache:
            e = self.manifest["sets"][set_name][finger]
            im = cv2.imread(self._find(e["file"]), cv2.IMREAD_UNCHANGED)
            if im is None:
                raise FileNotFoundError(f"could not read sprite {set_name}/{finger}: {e['file']}")
            if mirror:
                im = im[:, ::-1].copy()
            self.cache[key] = im
        return self.cache[key]


# ------------------------------------------------------------------ pose for one hand
def compute_hand(hand, params, facing="auto", adj=None):
    """facing: 'auto' | 'back' | 'palm' (manual override when the handedness label is wrong)."""
    px, world, label = hand["px"], hand["world"], hand["label"]
    back_conf, appears_right = back_confidence_from_image(px, world, label, params.label_mirrored)
    if facing == "back":
        back_conf = abs(back_conf) if abs(back_conf) > 0.3 else 0.9
    elif facing == "palm":
        back_conf = -abs(back_conf) if abs(back_conf) > 0.3 else -0.9
    unit, yaw_cos = hand_geometry(px)
    poses = raw_finger_poses(px, world, back_conf, unit, params, adj=adj)
    return dict(poses=poses, back_conf=back_conf, appears_right=appears_right, ppm=unit, yaw_cos=yaw_cos,
                label=label, score=hand["score"])


# ------------------------------------------------------------------ rendering
def _patch_warp(src_pm, w, h, theta, ax, ay, W, H, pad=4):
    """Warp a premultiplied-alpha float32 BGRA sprite (w x h, base-centre pivot) into an image-sized frame.
    Returns (patch, x0, y0) or None if completely outside."""
    c, s = math.cos(theta), math.sin(theta)
    # dst = R (p - (w/2, h)) + (ax, ay),  R = [[c,-s],[s,c]]   (positive theta turns "up" clockwise on screen)
    tx = ax - (c * (w / 2) - s * h)
    ty = ay - (s * (w / 2) + c * h)
    corners = np.array([[0, 0], [w, 0], [w, h], [0, h]], np.float64)
    dst = np.stack([c * corners[:, 0] - s * corners[:, 1] + tx, s * corners[:, 0] + c * corners[:, 1] + ty], 1)
    x0, y0 = int(math.floor(dst[:, 0].min())) - pad, int(math.floor(dst[:, 1].min())) - pad
    x1, y1 = int(math.ceil(dst[:, 0].max())) + pad, int(math.ceil(dst[:, 1].max())) + pad
    if x1 <= 0 or y1 <= 0 or x0 >= W or y0 >= H:
        return None
    M = np.array([[c, -s, tx - x0], [s, c, ty - y0]], np.float64)
    patch = cv2.warpAffine(src_pm, M, (x1 - x0, y1 - y0), flags=cv2.INTER_LINEAR,
                           borderMode=cv2.BORDER_CONSTANT, borderValue=(0, 0, 0, 0))
    return patch, x0, y0


def _blend(out, patch, x0, y0, opacity=1.0, shadow=None):
    H, W = out.shape[:2]
    ph, pw = patch.shape[:2]
    X0, Y0, X1, Y1 = max(x0, 0), max(y0, 0), min(x0 + pw, W), min(y0 + ph, H)
    if X1 <= X0 or Y1 <= Y0:
        return
    sub = patch[Y0 - y0:Y1 - y0, X0 - x0:X1 - x0]
    roi = out[Y0:Y1, X0:X1]
    if shadow is not None:
        sh = shadow[Y0 - y0:Y1 - y0, X0 - x0:X1 - x0, None]
        roi *= (1.0 - sh)
    a = sub[..., 3:4] * opacity
    roi *= (1.0 - a)
    roi += sub[..., :3] * opacity            # sprite is premultiplied


def local_luminance(bgr, px):
    x0, y0 = np.floor(px[:, :2].min(0)).astype(int)
    x1, y1 = np.ceil(px[:, :2].max(0)).astype(int)
    H, W = bgr.shape[:2]
    roi = bgr[max(y0, 0):min(y1, H), max(x0, 0):min(x1, W)]
    if roi.size == 0:
        return 150.0
    return float(np.median(cv2.cvtColor(roi, cv2.COLOR_BGR2GRAY)))


def render(bgr, hands_info, sprites, opts):
    """hands_info: list of (hand, computed) ; opts: dict(right_set, left_set, mirror_left, shadow, light_strength,
    min_alpha, length_from_sprite). Returns (result_bgr, per-hand rendered finger lists)."""
    H, W = bgr.shape[:2]
    out = bgr.astype(np.float32) / 255.0
    rendered = []
    for hand, comp in hands_info:
        set_name = opts["right_set"] if comp["appears_right"] else opts["left_set"]
        if set_name not in sprites.manifest["sets"]:              # one-row designs only have set A
            set_name = sprites.sets()[0]
        lum = local_luminance(bgr, hand["px"])
        gain = float(np.clip(1.0 + opts.get("light_strength", 0.5) * (lum / 150.0 - 1.0), 0.75, 1.12))
        done = []
        for name, ps in comp["poses"].items():
            alpha = ps["target_alpha"]
            if alpha < opts.get("min_alpha", 0.05):
                continue
            spr = sprites.get(set_name, name)
            sh_, sw_ = spr.shape[:2]
            aspect = sw_ / sh_
            w_px = max(3.0, ps["raw_w"])
            # keep the sprite's own aspect (nail length comes from the art), then apply pitch foreshortening
            h_px = w_px / aspect * max(0.45, ps["proj"]) * opts.get("length_mult", 1.0)
            if opts.get("length_from_sprite", True) is False:
                h_px = max(3.0, ps["raw_h"])
            tw, th = max(2, int(round(w_px))), max(2, int(round(h_px)))
            small = cv2.resize(spr, (tw, th), interpolation=cv2.INTER_AREA).astype(np.float32) / 255.0
            small[..., :3] = np.clip(small[..., :3] * gain, 0, 1)
            small[..., :3] *= small[..., 3:4]                                   # premultiply
            r = _patch_warp(small, tw, th, ps["raw_ang"], ps["raw_x"], ps["raw_y"], W, H)
            if r is None:
                continue
            patch, x0, y0 = r
            shadow = None
            if opts.get("shadow", 0.28) > 0:
                sig = max(1.0, 0.07 * tw)
                sh = cv2.GaussianBlur(patch[..., 3], (0, 0), sig)
                M = np.float32([[1, 0, 0.05 * tw], [0, 1, 0.07 * tw]])
                sh = cv2.warpAffine(sh, M, (sh.shape[1], sh.shape[0]))
                shadow = np.clip(sh * opts.get("shadow", 0.28) * alpha, 0, 1)
            _blend(out, patch, x0, y0, opacity=alpha, shadow=shadow)
            done.append(name)
        rendered.append(done)
    return (np.clip(out, 0, 1) * 255).astype(np.uint8), rendered


# ------------------------------------------------------------------ debug view
def draw_debug(bgr, hands_info, rendered=None):
    img = bgr.copy()
    H, W = img.shape[:2]
    lw = max(1, W // 600)
    for hi, (hand, comp) in enumerate(hands_info):
        px = hand["px"]
        for a, b in HAND_CONNECTIONS:
            cv2.line(img, tuple(int(v) for v in px[a, :2]), tuple(int(v) for v in px[b, :2]), (255, 255, 255), lw + 1, cv2.LINE_AA)
        for i in range(21):
            cv2.circle(img, tuple(int(v) for v in px[i, :2]), lw + 2, (255, 255, 255), -1, cv2.LINE_AA)
        for name, (mcp, pip_, dip, tip) in FINGERS.items():
            d, t = tuple(int(v) for v in px[dip, :2]), tuple(int(v) for v in px[tip, :2])
            cv2.line(img, d, t, (82, 82, 255), lw + 2, cv2.LINE_AA)
            cv2.circle(img, d, lw + 5, (255, 229, 0), -1, cv2.LINE_AA)
            cv2.circle(img, t, lw + 5, (0, 214, 255), -1, cv2.LINE_AA)
            ps = comp["poses"][name]
            cv2.circle(img, (int(ps["raw_x"]), int(ps["raw_y"])), lw + 4, (0, 220, 0), -1, cv2.LINE_AA)   # nail base
            shown = rendered is not None and name in rendered[hi]
            txt = f"{math.degrees(ps['raw_ang']):.0f}d w{ps['raw_w']:.0f} f{ps['flex_deg']:.0f} a{ps['target_alpha']:.1f}"
            cv2.putText(img, txt, (t[0] + 8, t[1] - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.45 * max(1, W / 900),
                        (0, 0, 0) if shown else (0, 0, 255), lw + 2, cv2.LINE_AA)
            cv2.putText(img, txt, (t[0] + 8, t[1] - 8), cv2.FONT_HERSHEY_SIMPLEX, 0.45 * max(1, W / 900),
                        (255, 255, 255), lw, cv2.LINE_AA)
        wx, wy = int(px[0, 0]), int(px[0, 1])
        face = "BACK" if comp["back_conf"] > 0.1 else ("PALM" if comp["back_conf"] < -0.1 else "EDGE")
        hud = f"hand{hi} label={comp['label']} {comp['score']:.2f} facing={face} ({comp['back_conf']:+.2f}) unit={comp['ppm']:.0f}px"
        cv2.putText(img, hud, (max(wx - 120, 4), min(wy + 28, H - 6)), cv2.FONT_HERSHEY_SIMPLEX, 0.55 * max(1, W / 900), (0, 0, 0), lw + 2, cv2.LINE_AA)
        cv2.putText(img, hud, (max(wx - 120, 4), min(wy + 28, H - 6)), cv2.FONT_HERSHEY_SIMPLEX, 0.55 * max(1, W / 900), (0, 255, 255), lw, cv2.LINE_AA)
    return img


# ------------------------------------------------------------------ one-call pipeline
DEFAULT_OPTS = dict(right_set="A", left_set="B", mirror_left=False, shadow=0.28, light_strength=0.5,
                    min_alpha=0.05, length_mult=1.0, length_from_sprite=True)


def tryon(bgr, hands, sprites, params=None, opts=None, facing="auto", adj=None):
    params = params or Params()
    o = dict(DEFAULT_OPTS, **(opts or {}))
    infos = [(h, compute_hand(h, params, facing, adj)) for h in hands]
    result, rendered = render(bgr, infos, sprites, o)
    debug = draw_debug(bgr, infos, rendered)
    return result, debug, infos, rendered


def debug_json(infos, rendered, attempt, params):
    import dataclasses
    return dict(params=dataclasses.asdict(params), attempt=attempt, hands=[dict(
        label=h["label"], score=h["score"], appears_right=c["appears_right"], back_conf=c["back_conf"], unit_px=c["ppm"], yaw_cos=c["yaw_cos"],
        rendered_fingers=r, px=np.round(h["px"], 1).tolist(), world=np.round(h["world"], 4).tolist(),
        poses=c["poses"]) for (h, c), r in zip(infos, rendered)])


def hint_for(infos, rendered, attempt):
    if not infos:
        return "No hand found. Try better light, show the whole hand, back of hand toward the camera."
    if all(len(r) == 0 for r in rendered):
        return "Hand found but no nails visible: show the BACK of your hand with fingers mostly straight."
    return "ok" + (f" (found after rotating {attempt['angle']} deg / {attempt['up']}x)" if attempt and (attempt['angle'] or attempt['up'] != 1.0) else "")


# ------------------------------------------------------------------ batch helpers (used by the notebook)
def process_photo(path_or_bytes, run, sprites, params=None, opts=None, facing="auto", fallback=True, adj=None):
    """Detect + render one photo. Returns a dict that the notebook caches (so sliders do not re-run the model)."""
    bgr = load_image(path_or_bytes)
    hands, attempt = detect_hands(run, bgr, fallback=fallback)
    out = dict(bgr=bgr, hands=hands, attempt=attempt)
    rerender(out, sprites, params, opts, facing, adj)
    return out


def rerender(item, sprites, params=None, opts=None, facing="auto", adj=None):
    params = params or Params()
    if item["hands"]:
        result, debug, infos, rend = tryon(item["bgr"], item["hands"], sprites, params, opts, facing, adj)
    else:
        result, debug, infos, rend = item["bgr"].copy(), item["bgr"].copy(), [], []
    item.update(result=result, debug=debug, infos=infos, rendered=rend, params=params,
                hint=hint_for(infos, rend, item["attempt"]))
    return item


def save_outputs(results, out_dir, tuned=None):
    """results: {name: item}. Writes result/debug JPGs, per-photo debug JSON, report.csv, tuned_settings.json."""
    import csv
    os.makedirs(out_dir, exist_ok=True)
    rows = []
    for name, it in results.items():
        stem = os.path.splitext(name)[0]
        cv2.imwrite(f"{out_dir}/{stem}_result.jpg", it["result"], [cv2.IMWRITE_JPEG_QUALITY, 92])
        cv2.imwrite(f"{out_dir}/{stem}_debug.jpg", it["debug"], [cv2.IMWRITE_JPEG_QUALITY, 88])
        json.dump(debug_json(it["infos"], it["rendered"], it["attempt"], it["params"]),
                  open(f"{out_dir}/{stem}_debug.json", "w"))
        h0 = it["infos"][0][1] if it["infos"] else None
        rows.append(dict(
            photo=name, hands_found=len(it["hands"]),
            rotation_deg=it["attempt"]["angle"] if it["attempt"] else "", upscaled=(it["attempt"]["up"] != 1.0) if it["attempt"] else "",
            label=h0["label"] if h0 else "", score=round(h0["score"], 2) if h0 else "",
            back_conf=round(h0["back_conf"], 2) if h0 else "",
            nails_rendered=sum(len(r) for r in it["rendered"]), message=it["hint"], manual_ok=""))
    with open(f"{out_dir}/report.csv", "w", newline="") as f:
        w = csv.DictWriter(f, fieldnames=list(rows[0].keys()))
        w.writeheader(); w.writerows(rows)
    if tuned:
        json.dump(tuned, open(f"{out_dir}/tuned_settings.json", "w"), indent=2)
    return rows
