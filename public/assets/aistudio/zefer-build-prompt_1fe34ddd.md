# ZEFER — BUILD PROMPT v2 (final, for code generation)

Act as a Senior Native Android Engineer with AR Computer Vision experience. Build "Zefer", a native Android app for buying press-on nail extensions with a live AR virtual try-on. Stack: Kotlin 2.x, Jetpack Compose (BOM), Navigation Compose (type-safe routes), CameraX, MediaPipe Tasks Vision (Hand Landmarker), Hilt, MVVM, unidirectional state, single state class per screen.

## Decisions locked
- Light theme ONLY. App background #F2F2F2; fields/placeholders #D9D9D9; primary buttons/text #1A1A1A; hero radius 24dp, everything else 12dp. Inter if bundled, else system sans. All tokens in one theme file; no hardcoded colors/dimens/strings.
- Logo: provided PNG at res/drawable/zefer_logo.png, used as-is via painterResource. NEVER generate/draw/trace/vectorize/recreate/substitute a font. Missing asset → labeled placeholder + flag.
- Min SDK 26, targetSdk latest stable, portrait only. Pinned versions in one version-catalog block.
- Front camera DEFAULT (rear optional toggle). 1 hand; architecture ready for 2.
- Auth: Firebase Auth — passwordless email link + Google Sign-In via Credential Manager; session persisted (DataStore). Splash routes on session.
- DI: Hilt (committed). Debug toggle gated behind BuildConfig.DEBUG.
- v1 non-goals: real purchase (Buy Now = snackbar stub), backend (local JSON behind repository interface), 3D, multi-hand, iOS.

## Data model
Product { id, name, description, variants: [Variant { id, name, thumbImage, sprites: {thumb,index,middle,ring,pinky}, manifest: {aspectRatio, anchor:"base-center", tipDirection:"up"} }] } in assets/products.json.

## Screens (wireframe-exact)
1. Splash: system SplashScreen API, then in-app splash — logo centered slightly below vertical center, black progress bar on gray track; route on session.
2. Login: wordmark upper-middle; "Don't have an account? Sign up" (underlined link); Email field (no password — passwordless); full-width black Login button; "Or" divider; gray Continue with Google; dim legal footer with Terms/Privacy links.
3. Sign up: mirror — "Already have an account? Sign in"; black Create Account button; same Google + footer.
4. Home: small wordmark top-left; search field (magnifier, placeholder "Search for nails extension...") filtering a 2-column lazy grid of white rounded product cards; skeleton shimmer; tap → detail.
5. Product detail: back arrow + "Nails" title; 24dp-radius hero image; row of 5 square thumbnails = design variants (tap swaps hero); name + description; bottom bar with wide black Buy Now (bag icon, snackbar stub) + square black camera button → AR Try-On for THIS product + selected variant.
6. AR Try-On: full-screen CameraX preview + Compose Canvas overlay; front camera default, rear toggle; back button; debug toggle (top-right, BuildConfig-gated); bottom variant carousel (live sprite swap); capture button → Retake/Save-to-gallery; camera permission rationale + denied states.
7. Admin side
- Entry: long-press Home logo → local PIN → Admin. CRUD for products.
- NailSpriteExtractor (Kotlin port of the VALIDATED Module 0 algorithm): Lab background
  from borders → deltaE threshold → morph open → connected components → merge-split at
  narrowest column neck (never distance-peak watershed) → 2 rows by centroid y, sort by x
  → PCA axis + width-taper tip direction → rotate tip-up → erode 1px + feather alpha →
  tight crop → PNGs + manifest.json in internal storage.
- Preview/confirm screen with per-nail gates; renderer loads sprites from assets OR
  internal storage transparently via the repository.
- Sample bundle: wine_gold + wine_gold_floral (20 sprites, extracted and validated).

## Tracking & rendering pipeline
- CameraX ImageAnalysis (RGBA_8888 via setOutputImageFormat, KEEP_ONLY_LATEST, target 720p) → HandLandmarker LIVE_STREAM (GPU delegate, CPU fallback with graceful retry) on background executor → monotonic ImageProxy timestamps → StateFlow → Canvas (read state inside draw lambda; NO per-frame recomposition).
- Coordinate mapping MUST handle: ImageProxy.imageInfo.rotationDegrees (pass via ImageProcessingOptions.setRotationDegrees — no manual buffer rotation); PreviewView FILL_CENTER crop rect vs analysis frame size; **front-camera asymmetry: PreviewView auto-mirrors front preview, analysis frames do not** — landmarks must be transformed to match what the user sees.
- z polarity, cross-product hand normal, and MediaPipe handedness label convention (handedness is from the hand's own perspective) MUST be calibrated empirically on device; expose raw values in debug HUD; never hardcode sign assumptions. Hide nails when palm faces camera.
- Nail anchor: DIP→TIP vector sets position + orientation; sprite pivot at base-center, growing past the fingertip. theta = atan2(tipY-dipY, tipX-dipX) + PI/2 (image space y-down). Extension base anchor = DIP + small tunable fraction toward TIP (expose in debug).
- Foreshortening: scale from landmark z / worldLandmarks (width ~|cos(yaw)|, slight skew, fade out at steep angles). Per-finger size from finger geometry + depth, not constants. Thumb gets stronger smoothing + wider fade cone (thumb landmarks are noisy).
- Smoothing: One Euro Filter per quantity (x, y, scale, angle); angle via shortest-path: delta = atan2(sin(t-o), cos(t-o)).
- Tracking loss: hold last pose ~150ms → fade out → re-acquire without popping. Track loss count in HUD.
- Occlusion: state limitations; simple depth-based hide for folded fingers (tunable threshold, visible in debug).

## Debug mode
White skeleton (HAND_CONNECTIONS); highlighted DIP/TIP per finger with bone vectors; HUD: FPS, inference ms, handedness + score, palm/back state, tracking-lost count, per-finger raw vs smoothed markers + angle + scale; recorded-video replay mode (feed a saved clip through the identical landmarker path); all tunables (anchors, fade angles, filter params) editable in debug.

## Assets (Module 0)
Source: one photo of 10 press-on nails laid flat (2 rows of 5, thumb→pinky) on plain light background, top-down, even lighting, no shadows crossing nails. Offline Python/OpenCV script slices, cuts out with 1–2px feathered alpha, rotates tip-up, names designName_finger.png (thumb/index/middle/ring/pinky), emits manifest.json + a contact sheet for human review + automated checks (alpha coverage per sprite, min/max nail-pixel warnings). Mirror sprites horizontally for the other hand. Bundle one sample design. Product JSON references hero, 5 variant thumbs, sprite sets.

## Output & quality bar
- Complete compilable Kotlin files; version catalog with pinned versions; Hilt modules; one ViewModel per screen; StateFlow + sealed events.
- Handle: camera permission (rationale/denied/forever-denied), lifecycle, model-load failure (labeled error state, retry), offline, empty grid, empty search results, missing logo asset.
- @Preview for every screen with placeholder data.
- Verification per module: Roborazzi screenshot tests for screens; pure JVM unit tests for ALL coordinate-mapping/geometry math (synthetic landmark inputs); pipeline validated on a physical mid-range device (emulator is NOT valid for AR); target ≥30 FPS, <50ms camera-to-overlay.
- Release: R8/ProGuard rules for CameraX + MediaPipe; strings in strings.xml; debug UI stripped from release via BuildConfig.
