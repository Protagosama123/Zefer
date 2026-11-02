"""
Nail sprite library: turn an uploaded product photo of press-on nails into per-finger transparent sprites.

Everything for one design lives in ONE folder:   sprites/<design>/
    manifest.json            sets -> fingers -> file / size / aspect / anchor
    <design>_<set>_<finger>.png
    source.<ext>             the photo you uploaded
    preview.png              what was extracted (check this!)
    debug_blobs.png          detected blobs with their numbers (useful when extraction fails)

Supported photos
    * flat lay, 2 rows x 5 nails  -> sets A and B (one per hand)
    * flat lay, 1 row  x 5 nails  -> set A (used for both hands)
    * a PNG with a real transparent background (alpha) works too
Row order is thumb -> pinky (auto-reversed if the widths say otherwise). Tip direction is auto-detected.

    python nail_slicer.py photo.png sprites my_design
"""
import io, json, os, re, shutil, sys, zipfile
import numpy as np
import cv2

FINGERS = ["thumb", "index", "middle", "ring", "pinky"]
MAX_EDGE = 3000


class ExtractionError(Exception):
    pass


# ------------------------------------------------------------------ helpers
def safe_name(name):
    stem = os.path.splitext(os.path.basename(name))[0]
    return re.sub(r"[^A-Za-z0-9_-]+", "_", stem).strip("_") or "design"


def read_image(src):
    """path or bytes -> uint8 array, BGR or BGRA, long edge capped."""
    if isinstance(src, (bytes, bytearray)):
        im = cv2.imdecode(np.frombuffer(src, np.uint8), cv2.IMREAD_UNCHANGED)
    else:
        im = cv2.imread(src, cv2.IMREAD_UNCHANGED)
    if im is None:
        raise ExtractionError("could not read the image (is it a PNG/JPG?)")
    if im.dtype != np.uint8:
        im = cv2.convertScaleAbs(im, alpha=255.0 / np.iinfo(im.dtype).max)
    if im.ndim == 2:
        im = cv2.cvtColor(im, cv2.COLOR_GRAY2BGR)
    s = MAX_EDGE / max(im.shape[:2])
    if s < 1:
        im = cv2.resize(im, None, fx=s, fy=s, interpolation=cv2.INTER_AREA)
    return im


def _components(mask, min_area_frac):
    h, w = mask.shape[:2]
    n, lbl, stats, cent = cv2.connectedComponentsWithStats(mask)
    comps = [(i, stats[i], cent[i]) for i in range(1, n) if stats[i, cv2.CC_STAT_AREA] > min_area_frac * h * w]
    return lbl, comps


def _plausible(comps, counts=(5, 10)):
    """right count and similar sizes (rejects a nail that was split in two, or two that were merged)."""
    if len(comps) not in counts:
        return False
    areas = np.array([c[1][cv2.CC_STAT_AREA] for c in comps], np.float32)
    return areas.max() / max(areas.min(), 1) < 3.0


def find_blobs(img, expected=None):
    """Returns (label_image, comps, bgr, mode). comps: [(label_id, stats, centroid)]."""
    bgr = img[..., :3]
    if img.shape[2] == 4 and (img[..., 3] < 250).mean() > 0.05:        # real transparency: trust the alpha channel
        m = (img[..., 3] > 16).astype(np.uint8) * 255
        k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
        m = cv2.morphologyEx(m, cv2.MORPH_OPEN, k)
        lbl, comps = _components(m, 0.002)
        return lbl, comps, bgr, "alpha"

    h, w = bgr.shape[:2]
    lab = cv2.cvtColor(bgr, cv2.COLOR_BGR2LAB).astype(np.float32)
    b = max(6, min(h, w) // 60)
    border = np.concatenate([lab[:b].reshape(-1, 3), lab[-b:].reshape(-1, 3),
                             lab[:, :b].reshape(-1, 3), lab[:, -b:].reshape(-1, 3)])
    bg = np.median(border, axis=0)
    dist = np.linalg.norm(lab - bg, axis=2)
    k = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (5, 5))
    counts = (expected,) if expected else (10, 5)
    best = None
    # soft shadows glue neighbouring nails together at a low threshold, so raise it until the blobs separate
    for t in np.arange(14, 46, 2.0):
        m = (dist > t).astype(np.uint8) * 255
        m = cv2.morphologyEx(m, cv2.MORPH_CLOSE, k, iterations=2)
        m = cv2.morphologyEx(m, cv2.MORPH_OPEN, k, iterations=1)
        lbl, comps = _components(m, 0.004)
        if _plausible(comps, counts):
            if best is None or len(comps) > len(best[1]):
                best = (lbl, comps)
            if len(comps) == counts[0]:
                break
    if best is None:
        lbl, comps = _components(m, 0.004)
        raise ExtractionError(f"found {len(comps)} nail shapes, expected 5 or 10. Use a plain, light background with nails not touching.")
    return best[0], best[1], bgr, "background"


def refine_edge(img_bgr, hull_mask):
    """Remove soft backdrop shadow from the hull mask with GrabCut, keep the largest blob, shave 1 px."""
    ys, xs = np.where(hull_mask > 0)
    pad = 12
    y0, y1 = max(ys.min() - pad, 0), min(ys.max() + pad, hull_mask.shape[0])
    x0, x1 = max(xs.min() - pad, 0), min(xs.max() + pad, hull_mask.shape[1])
    img = np.ascontiguousarray(img_bgr[y0:y1, x0:x1])
    hm = hull_mask[y0:y1, x0:x1]
    sure_fg = cv2.erode(hm, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (15, 15)))
    gc = np.full(hm.shape, cv2.GC_BGD, np.uint8)
    gc[hm > 0] = cv2.GC_PR_FGD
    gc[sure_fg > 0] = cv2.GC_FGD
    try:
        bgm, fgm = np.zeros((1, 65)), np.zeros((1, 65))
        cv2.grabCut(img, gc, None, bgm, fgm, 4, cv2.GC_INIT_WITH_MASK)
        out = np.where((gc == cv2.GC_FGD) | (gc == cv2.GC_PR_FGD), 255, 0).astype(np.uint8)
    except cv2.error:
        out = hm.copy()
    out = cv2.morphologyEx(out, cv2.MORPH_CLOSE, cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (7, 7)))
    n, lbl, st, _ = cv2.connectedComponentsWithStats(out)
    if n > 1:
        out = (lbl == 1 + int(np.argmax(st[1:, cv2.CC_STAT_AREA]))).astype(np.uint8) * 255
    cnts, _ = cv2.findContours(out, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
    filled = np.zeros_like(out)
    cv2.drawContours(filled, cnts, -1, 255, -1)
    filled = cv2.erode(filled, np.ones((3, 3), np.uint8))
    res = np.zeros_like(hull_mask)
    res[y0:y1, x0:x1] = filled
    return res


def normalise_tip_up(spr):
    """Trim to the alpha bounding box and make the NARROW end (free edge / tip) point up.

    A real nail is widest at the cuticle end and narrowest at the free edge. Product photos are often shot with
    the tips pointing DOWN, so the orientation is detected from the width profile and the sprite is rotated
    180 degrees (not mirrored) when the wide end is at the top."""
    ys, xs = np.where(spr[..., 3] > 8)
    spr = spr[ys.min():ys.max() + 1, xs.min():xs.max() + 1]
    widths = (spr[..., 3] > 128).sum(1).astype(np.float32)
    q = max(1, len(widths) // 4)
    if widths[:q].mean() > widths[-q:].mean():
        spr = cv2.rotate(spr, cv2.ROTATE_180)
    return spr


def _cut(bgr, lbl, comp, mode, feather_px=1.5):
    i, st, _ = comp
    x, y, bw, bh, _a = st
    h, w = bgr.shape[:2]
    m = (lbl == i).astype(np.uint8) * 255
    if mode == "background":
        cnts, _ = cv2.findContours(m, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        hull = cv2.convexHull(max(cnts, key=cv2.contourArea))          # nails are convex; fills pale gel areas
        hm = np.zeros_like(m)
        cv2.fillConvexPoly(hm, hull, 255)
        m = refine_edge(bgr, hm)
    pad = 6
    x0, y0, x1, y1 = max(x - pad, 0), max(y - pad, 0), min(x + bw + pad, w), min(y + bh + pad, h)
    a = cv2.GaussianBlur(m[y0:y1, x0:x1], (0, 0), feather_px)
    return np.dstack([bgr[y0:y1, x0:x1], a])


def _group_rows(comps):
    """Group blobs into rows by y position. Returns list of rows (each sorted left->right)."""
    comps = sorted(comps, key=lambda c: c[2][1])
    hs = np.array([c[1][cv2.CC_STAT_HEIGHT] for c in comps], np.float32)
    gap = 0.6 * float(np.median(hs))
    rows, cur = [], [comps[0]]
    for c in comps[1:]:
        if c[2][1] - cur[-1][2][1] > gap:
            rows.append(cur); cur = [c]
        else:
            cur.append(c)
    rows.append(cur)
    return [sorted(r, key=lambda c: c[2][0]) for r in rows]


def _thumb_first(row_sprites):
    """thumb is the widest nail, pinky the narrowest: reverse the row if the widths say it runs pinky -> thumb."""
    w = [s.shape[1] for s in row_sprites]
    return row_sprites[::-1] if np.mean(w[:2]) < np.mean(w[-2:]) else row_sprites


def _annotate(bgr, comps, rows):
    img = bgr.copy()
    t = max(2, bgr.shape[1] // 500)
    for ri, row in enumerate(rows):
        for ci, (i, st, cen) in enumerate(row):
            x, y, bw, bh, _ = st
            cv2.rectangle(img, (x, y), (x + bw, y + bh), (0, 200, 0), t)
            cv2.putText(img, f"{ri}.{ci}", (x, max(y - 6, 14)), cv2.FONT_HERSHEY_SIMPLEX, 0.4 * t, (0, 0, 255), t)
    return img


def _preview(sets, height=260):
    tiles = []
    for sname, spr in sets.items():
        row = []
        for f in FINGERS:
            im = spr[f]
            sc = height / im.shape[0]
            im = cv2.resize(im, None, fx=sc, fy=sc, interpolation=cv2.INTER_AREA)
            yy, xx = np.mgrid[0:height, 0:im.shape[1] + 20]
            bg = np.where(((yy // 12 + xx // 12) % 2)[..., None] == 0, 235, 205).astype(np.uint8).repeat(3, 2)
            al = im[..., 3:4] / 255.0
            bg[:, 10:10 + im.shape[1]] = (im[..., :3] * al + bg[:, 10:10 + im.shape[1]] * (1 - al)).astype(np.uint8)
            row.append(bg)
        tiles.append(np.hstack(row))
    w = max(t.shape[1] for t in tiles)
    return np.vstack([np.pad(t, ((0, 0), (0, w - t.shape[1]), (0, 0)), constant_values=255) for t in tiles])


# ------------------------------------------------------------------ main entry points
def extract_design(src, name, root="sprites", expected=None, source_bytes=None, source_ext=".png"):
    """Photo -> sprites/<name>/ (sprites, manifest.json, source, preview, debug). Returns the manifest dict.
    Raises ExtractionError (after saving debug_blobs.png) when the nails cannot be separated reliably."""
    name = safe_name(name)
    out_dir = os.path.join(root, name)
    os.makedirs(out_dir, exist_ok=True)
    if source_bytes is None and isinstance(src, (bytes, bytearray)):
        source_bytes = bytes(src)
    img = read_image(src)
    if source_bytes is not None:
        open(os.path.join(out_dir, "source" + source_ext), "wb").write(source_bytes)
    elif isinstance(src, str) and os.path.isfile(src):
        shutil.copy(src, os.path.join(out_dir, "source" + os.path.splitext(src)[1]))

    lbl, comps, bgr, mode = find_blobs(img, expected)
    rows = _group_rows(comps)
    cv2.imwrite(os.path.join(out_dir, "debug_blobs.png"), _annotate(bgr, comps, rows))
    if any(len(r) != 5 for r in rows) or len(rows) not in (1, 2):
        raise ExtractionError(f"nails found in rows of {[len(r) for r in rows]}; expected 1 or 2 rows of 5 "
                              f"(thumb -> pinky). See {out_dir}/debug_blobs.png")

    sets = {}
    for ri, row in enumerate(rows):
        sprites = [normalise_tip_up(_cut(bgr, lbl, c, mode)) for c in row]
        sets["AB"[ri]] = dict(zip(FINGERS, _thumb_first(sprites)))

    man = {"design": name, "layout": f"{len(rows)}x5", "extraction": mode, "source": "source" + source_ext, "sets": {}}
    for sname, spr in sets.items():
        man["sets"][sname] = {}
        for f in FINGERS:
            fn = f"{name}_{sname}_{f}.png"
            cv2.imwrite(os.path.join(out_dir, fn), spr[f])
            hh, ww = spr[f].shape[:2]
            man["sets"][sname][f] = {"file": fn, "w": ww, "h": hh, "aspect": ww / hh, "anchor": "base-center", "tip": "up"}
    json.dump(man, open(os.path.join(out_dir, "manifest.json"), "w"), indent=2)
    cv2.imwrite(os.path.join(out_dir, "preview.png"), _preview(sets))
    return man


def store_manual_files(files, design, root="sprites"):
    """files: {filename: bytes} uploaded by hand (PNGs + manifest.json, or one .zip).
    EVERYTHING is written inside sprites/<design>/ (nothing is left next to the notebook)."""
    design = safe_name(design)
    out_dir = os.path.join(root, design)
    os.makedirs(out_dir, exist_ok=True)
    for fn, data in files.items():
        if fn.lower().endswith(".zip"):
            with zipfile.ZipFile(io.BytesIO(data)) as z:
                for m in z.namelist():
                    if m.endswith("/") or m.startswith("__MACOSX"):
                        continue
                    open(os.path.join(out_dir, os.path.basename(m)), "wb").write(z.read(m))
        else:
            open(os.path.join(out_dir, os.path.basename(fn)), "wb").write(data)
    mpath = os.path.join(out_dir, "manifest.json")
    if not os.path.isfile(mpath):
        raise ExtractionError(f"manifest.json is missing from the uploaded files (saved in {out_dir}).")
    man = json.load(open(mpath))
    missing = [e["file"] for s in man["sets"].values() for e in s.values() if not os.path.isfile(os.path.join(out_dir, e["file"]))]
    if missing:
        raise ExtractionError(f"files listed in manifest.json but not uploaded: {missing}")
    return man


def list_designs(root="sprites"):
    if not os.path.isdir(root):
        return []
    return sorted(d for d in os.listdir(root) if os.path.isfile(os.path.join(root, d, "manifest.json")))


if __name__ == "__main__":
    m = extract_design(sys.argv[1], sys.argv[3] if len(sys.argv) > 3 else "design", sys.argv[2] if len(sys.argv) > 2 else "sprites")
    print("extracted", m["design"], m["layout"], list(m["sets"]))
