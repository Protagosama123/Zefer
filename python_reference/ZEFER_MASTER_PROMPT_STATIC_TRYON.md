# ZEFER — MASTER PROMPT: Replace real-time try-on with the validated static try-on (port from Python to Kotlin)

Attach with this prompt: (1) the current Android project zip (`app/`), (2) the Python reference files
`nail_algo.py`, `static_tryon.py`, `nail_slicer.py` (the `python_reference/` folder in the bundle).
**The Python files are the single source of truth.** Where this prompt and the Python differ, the Python wins; tell me about the difference.

---

## 0. Who I am / what this is
I am building **Zefer**, a native Android e-commerce app for press-on nails (Kotlin, Jetpack Compose, Material3, CameraX, MediaPipe Tasks Vision Hand Landmarker, Room, DataStore, Coil, Navigation-Compose, minSdk 26).
The real purpose is a **demo of how a nail try-on SDK/API plugs into an existing shopping flow**. The SDK/API itself is not a priority. The try-on must look right, and I must be able to *see and keep the result*.

Existing screens (do not redesign them): Splash → Login / Sign up → Home grid → Product detail (camera FAB at bottom-right) → Try-on. The logo is my provided PNG — **never generate or recreate it**. Background `#000000` where specified.

## 1. The problem and the goal
The current real-time pipeline (`cv/pipeline/*`, `ui/screens/tryon/*`) puts nails in the wrong place. I redesigned and validated the algorithm offline in Python on **static images** and it places nails correctly (right position, angle, size, facing, left/right hand).

**Goal (this task): replace the live camera-overlay try-on with a STATIC try-on flow that is a faithful Kotlin port of the Python algorithm.**
Flow: Product detail → tap camera button → choose/take a photo → run detection + the ported algorithm once → show the rendered result → **save it and show it** (gallery + in-app).
Real-time is explicitly **out of scope now** (roadmap V4/V5). Do not delete the live code; move it behind a disabled flag/package (`cv/live_legacy/`) so it can come back later, but nothing in the live path may run or be reachable from navigation.

## 2. Read first (before writing any code)
1. Read the whole existing app, especially: `cv/pipeline/*`, `ui/screens/tryon/*`, `ui/screens/detail/*`, `ui/navigation/*`, `data/model/Product.kt`, `assets/products.json`, `assets/sprites/*`.
2. Read the three Python files fully.
3. Then reply with a **short plan (max ~25 lines)**: files to add/change/move, risks, and any conflict you found between the app and the Python. Then proceed without waiting unless a decision is irreversible.

Known bugs in the old Android code (do not carry them over): `rotationDegrees` was not passed to MediaPipe; no FILL_CENTER coordinate mapping; the palm test used a 3D normal (unreliable); One-Euro beta units; GPU→CPU fallback leaked the old landmarker; fake inference-time numbers; per-frame allocations. The static flow avoids most of these by construction (one still image, IMAGE running mode, original-pixel coordinates).

## 3. Non-negotiable lessons from the Python work (these are why it works now)
- **Do NOT use MediaPipe world landmarks** for scale, twist or flexion. They were inconsistent on real photos (palm-width ratio 0.30 vs 0.74–0.86 in image). Use the **2D image landmarks in original-image pixels** (z scaled like x).
- **Size is relative to the hand, not metric.** `unit` = hand size in px; every nail dimension is a fraction of it. This is what makes kid and elder hands work.
- **Handedness label naming:** for a normal (non-mirrored) photo the label names the real hand as seen (left hand, back view → `"Left"`). `real_right = (label == "Right") != labelMirrored`. A selfie/mirrored image sets `labelMirrored = true`. Facing is verified only on back-of-hand left-hand photos so far; palm-facing and right-hand are **unverified** — keep the manual override (`facing = auto | back | palm`) in the UI.
- **Sprites are tip-UP, base-centre pivot**, almond shape: wide at the cuticle, narrow at the free edge. If a sprite arrives tip-down it must be rotated 180° (the slicer does this). **Check every sprite in `assets/sprites/*` for orientation**; wrong orientation looked like "nails upside down" before.
- Nail **length comes from the sprite art** (keep sprite aspect), then pitch foreshortening — not from a fixed length ratio.
- Detection **fallback rotations**: if no hand is found, retry on the image rotated by 90, 270, 180, 45, 315, 135, 225 deg, then 0 deg upscaled ×1.5; map landmarks back to the original frame exactly (inverse affine). This is how tilted/upside-down hands are handled.
- Do the maths in **original image pixels**, render onto a bitmap of the original image (capped at 1600 px long edge, EXIF-transposed). Only scale for display afterwards.

## 4. The algorithm to port (exact — mirror the Python function by function, same names where sensible)
Create package `com.example.cv.tryon` (pure Kotlin, **no Android/Compose imports** in the math files so it is unit-testable on the JVM):

### 4.1 Constants
```
FINGERS (mcp, pip, dip, tip): thumb (1,2,3,4) [CMC,MCP,IP,TIP], index (5,6,7,8), middle (9,10,11,12), ring (13,14,15,16), pinky (17,18,19,20)
NAIL_WIDTH_REL      = thumb 0.195, index 0.172, middle 0.172, ring 0.161, pinky 0.138
TYPICAL_DIPTIP_REL  = thumb 0.30, index 0.24, middle 0.26, ring 0.25, pinky 0.20
Params: nailBackRel 0.14, labelMirrored false, flexHideStartDeg 60, flexHideEndDeg 100,
        backShowStart 0.10, backShowEnd 0.35, fitScale 1.0, geoBlend 0.3,
        minWidthForeshorten 0.35, minLenForeshorten 0.45
```
### 4.2 `handGeometry(px)` → (unit, yawCos)
```
palm  = |p9-p0|; palm = max(palm, 0.93*|p5-p0|, 0.95*|p13-p0|)        (2D)
width = |p5-p17|
unit  = max(palm, width/0.80, 1e-3)
yawCos= min(1, width / (0.80*max(palm,1e-3)))
```
### 4.3 `backConfidence(px, label, labelMirrored)` → (backConf, realRight)
```
cross = (p5-p0).x*(p17-p0).y - (p5-p0).y*(p17-p0).x
realRight = (label=="Right") != labelMirrored
sign = (cross>0 ? +1 : -1) * (realRight ? +1 : -1)
backConf = sign * yawCos          // >0 => back of hand faces the camera
```
`facing` override: `back` → `abs(backConf)` if >0.3 else 0.9; `palm` → `-abs(backConf)` if >0.3 else -0.9.

### 4.3b `rawFingerPoses(px, backConf, unit, params, adj)` per finger
Copy the Python exactly (read `raw_finger_poses` in `nail_algo.py`). Summary:
- `d = tip - dip`; `L2 = |d.xy|`, `L3 = |d|`; `proj = L2/L3` (1 = in image plane, 0 = pointing at camera).
- `theta = atan2(d.y, d.x) + PI/2` (image coords, y down; 0 = finger up; positive = clockwise). If `proj < 0.25` or `L2 < 1e-3` reuse the previous theta of that finger (0 initially). `thetaAdj = theta + rotAdj`.
- direction to tip `u = (sin(thetaAdj), -cos(thetaAdj))`.
- width: `const = NAIL_WIDTH_REL*fitScale`; if `proj > 0.7`: `geo = const*(L2/unit)/TYPICAL_DIPTIP_REL`, `widthRel = clamp((1-geoBlend)*const + geoBlend*geo, 0.8*const, 1.25*const)` else `const`; `widthRel *= size`.
- base: `back = nailBackRel*unit`; `baseX = tip.x - u.x*back + u.x*offset*widthRel*unit` (same for y).
- `rawW = widthRel*unit*max(minWidthForeshorten, min(|backConf|,1))`.
- flexion = angle between (pip-mcp) and (tip-dip) in 3 px-space; `visFlex = 1 - smoothstep(60,100,flex)`.
- `visBack = smoothstep(backShowStart, backShowEnd, backConf)`; thumb uses `smoothstep(-0.30, 0, backConf)`.
- `targetAlpha = visFlex * visBack`.
- `adj` = {size, length, offset (fraction of nail width, + toward tip), rotDeg} — manual tuning sliders.

### 4.4 Rendering (`render` in `static_tryon.py`)
For each hand: pick sprite set A (real right appearance) or B (left; mirrored sprites) — fall back to the first set if missing. `lum` = median grey of the hand's landmark bbox; `gain = clamp(1 + 0.5*(lum/150 - 1), 0.75, 1.12)`.
For each finger with `targetAlpha >= 0.05`:
- `wPx = max(3, rawW)`; `hPx = wPx/spriteAspect * max(0.45, proj) * lengthMult`.
- Resize sprite (area interpolation) to `(wPx, hPx)`, multiply RGB by `gain`, **premultiply alpha**.
- Affine: rotate by `theta` about the **base-centre pivot** `(w/2, h)` and translate it to `(baseX, baseY)`: `dst = R(p - (w/2,h)) + (baseX,baseY)`, `R = [[c,-s],[s,c]]`. In Android use `Canvas` + `Matrix` (`postTranslate(-w/2,-h)`, `postRotate(deg)`, `postTranslate(baseX,baseY)`) with `Paint(FILTER_BITMAP|ANTI_ALIAS)` on an ARGB_8888 bitmap, `drawBitmap` with `alpha = targetAlpha`.
- Contact shadow: blur the nail alpha (sigma ≈ 0.07*wPx), offset (+0.05*wPx, +0.07*wPx), darken the photo underneath by `0.28*targetAlpha`. Use `RenderEffect`/`BlurMaskFilter` or a small `RenderScript`-free blur; it must be cheap and optional (`shadow` slider).
Draw order: shadow, then sprite. Nails are drawn on top of fingers (occlusion handling is out of scope).

### 4.5 Detection wrapper
`HandLandmarker` in **IMAGE** running mode, `numHands = 2`, min detection/presence/tracking confidence 0.3. Convert normalized landmarks → pixels (`x*W`, `y*H`, `z*W`). Implement `detectHands(bitmap, fallback = true)` with the rotation-fallback list above and exact inverse mapping. Create the landmarker once, close it in `onCleared`, GPU→CPU fallback must release the failed instance.

## 5. Android app changes
1. **Photo input screen** replaces the camera preview screen. Entry: the existing camera button on Product detail. Offer three actions: **Take photo** (CameraX `ImageCapture`, high-quality, honour EXIF rotation, back camera default, `labelMirrored=false` for the saved still), **Choose from gallery** (Photo Picker `PickVisualMedia`, no storage permission needed), and a **sample hands** option (ship 2–3 sample hand photos in assets so the demo works with no camera/permission).
2. **Try-on result screen:** shows the original ↔ result (tap/hold to compare, or a slider), the variant chooser from the existing product screen (switching variant re-renders instantly from the cached landmarks — **never re-run detection when only the design changes**), and these buttons: **Save to gallery**, **Share**, **Try another photo**, **Debug**.
3. **Save and show the result (required):**
   - Render at full resolution (≤1600 px long edge) to a Bitmap, JPEG quality 92.
   - Save with `MediaStore` to `Pictures/Zefer/ZEFER_<yyyyMMdd_HHmmss>.jpg` (API 29+: `RELATIVE_PATH` with `IS_PENDING`; API 26–28: `WRITE_EXTERNAL_STORAGE` request).
   - Also save a copy plus a small JSON (`ZEFER_<ts>.json`: product, variant, per-hand label/score/backConf/unit/yawCos/rotation-attempt, per-finger pose values, params used, app version) in app-private storage `filesDir/tryon_results/`.
   - After saving show a snackbar "Saved" with an **Open** action (ACTION_VIEW on the MediaStore URI), and add a **My Try-Ons** screen (grid from `filesDir/tryon_results`, tap → full-screen viewer with original/result toggle, share, delete). Reach it from the Home top bar or the result screen.
4. **Debug mode (keep the existing toggle):** draw the 21 landmarks + skeleton, the per-finger tip/base points, theta arrows, and a panel with: label, score, facing (back/palm), backConf, yawCos, unit px, rotation attempt used, per-finger proj / flex / alpha / width. A **Share debug** button exports result image + debug image + JSON as one zip (so I can upload it back to you for review).
5. **Tuning sheet (debug only):** sliders Size (0.7–1.3), Length (0.7–1.4), Offset (−0.3–0.3), Rotate (−20–20 deg), Shadow (0–0.5), Light match (0–1), `Facing: auto/back/palm`, `Label mirrored` toggle. Re-render from cached landmarks on change (debounce ~50 ms; run on `Dispatchers.Default`).
6. **Failure UX:** no hand found → clear message + hint (use a clear photo, hand fully visible, good light, back of hand toward camera); nails hidden (e.g. palm judged as facing the camera) → offer a "Nails missing? Try the other side" action that flips the `facing` override and re-renders. Never crash on a missing sprite: use the first available set and log.
7. **Sprites/assets:** keep the existing `products.json` + `assets/sprites/<variant>/` structure, but verify each sprite is tip-up, base-centre, with transparent background and `aspect = w/h` taken from the bitmap itself (not from the JSON constant 2.0). For left hands use the mirrored sprites (`*_m.png`) or flip horizontally at load time; pick the same behavior as `SpriteSet.get(set, finger, mirror)` in Python. List any sprite that looks wrong and tell me instead of silently fixing it.
8. **Performance/memory:** decode with `inSampleSize`/`ImageDecoder` to ≤1600 px, reuse bitmaps, close `MPImage`, run detection and rendering off the main thread, keep one `HandLandmarker`. Show a progress indicator during detection.
9. Navigation: add routes `photo_input/{productId}/{variantId}`, `tryon_result`, `my_tryons`. Pass the bitmap through a ViewModel/cache file, **not** through nav arguments.

## 6. Tests (must be written and run)
- JVM unit tests (no Android) for `handGeometry`, `backConfidence`, `rawFingerPoses`, rotation inverse mapping, `smoothstep`, angle convention (finger straight up → theta 0; finger pointing right → theta +90 deg clockwise).
- **Golden test:** take the synthetic hand construction idea from the Python tests (right hand back view, tilted 30 deg, twisted, palm view, upside-down) and assert the same pose numbers within tolerance. I will also send you `debug.json` files exported from the Python notebook for real photos — add a test that loads those landmarks and checks Kotlin output equals Python output (tolerance 0.5 px / 0.5 deg).
- Instrumented smoke test: sample hand photo → result bitmap not equal to input, 5 nails drawn on a back-of-hand sample.

## 7. Output / how to report back
- Work in small compilable steps; after each step the app must still build. Run `./gradlew assembleDebug` and the unit tests and paste the result summary.
- Do not print whole files in chat; write the code to the project.
- At the end give me: (a) list of changed/added/moved files, (b) what is unverified (I expect: palm-facing, right hands, strong 3D twist, occlusion), (c) exact steps to try it on my phone, (d) a short list of what I should test and send back (screenshots from Share debug).
- If something in this prompt conflicts with the Python, with the app, or with Android constraints, say so in one line and pick the Python behavior.

## 8. Explicitly out of scope now
Real-time/live tracking, temporal filters (One Euro, hold/reacquire — they exist in `nail_algo.py` for V4/V5, do not wire them), 3D nail meshes, per-finger roll/skew, occlusion, SDK/API packaging, redesign of non-try-on screens, any logo generation.

## 9. Known limits of the algorithm (do not "fix" silently; tell me if you see them)
Twist toward/away from camera uses one global `yawCos` for width (no per-finger roll/skew); nails always draw over fingers; palm-facing and right-hand facing are unverified on real photos; nail width constants were tuned on only two left-hand back-view photos.
