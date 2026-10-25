# Zefer — App Design Specification (v2 — Light Theme)

> Nail extensions e-commerce app with in-app AR virtual try-on.
> Final decisions: **light theme** (app background light gray, matching wireframes);
> **logo is black `#000000` on the light background**.

---

## 1. Logo (strict rules)

| Rule | Value |
|---|---|
| **Logo asset** | Provided PNG (`zefer_logo.png`) — black hand-drawn brushstroke "Z" |
| **Logo color** | Black `#000000`, placed directly on the light screen background |
| **Background** | **Never** put the logo on a black background in-app; it is a black mark for light surfaces |
| **Wordmark** | "Zefer" in a brush-script typeface matching the logo style |
| **Strict rule** | **Never generate, draw, trace, vectorize, recreate in code, or substitute a font for the logo.** Use `painterResource(R.drawable.zefer_logo)` as-is. If the asset is missing, show a clearly labeled placeholder box and flag it |

### Usage
- **Splash:** logo + wordmark centered, black progress bar beneath.
- **Login / Sign up:** wordmark centered in the upper-middle.
- **Home:** small wordmark top-left.

---

## 2. Design Tokens (`ui/theme/Theme.kt` — single source, no hardcoded colors)

| Token | Value | Usage |
|---|---|---|
| `--color-bg` | `#F2F2F2` | Screen background (all screens except full-screen camera) |
| `--color-surface` | `#D9D9D9` | Input fields, image placeholders, skeleton cards |
| `--color-card` | `#FFFFFF` | Product cards, hero/thumbnail surfaces on bg |
| `--color-primary` | `#1A1A1A` | Primary buttons (Login, Create Account, Buy Now), primary text |
| `--color-text` | `#1A1A1A` | Titles, body |
| `--color-text-dim` | `#8E8E93` | Captions, legal footer, placeholders |
| `--color-white` | `#FFFFFF` | Text/icons on primary buttons |
| `--radius-sm` | `12dp` | Buttons, fields, product cards, thumbnails |
| `--radius-lg` | `24dp` | Hero image (product detail) |

### Typography
| Style | Font | Size | Weight |
|---|---|---|---|
| Screen title | Inter (bundled) or system sans | 20sp | Bold |
| Body | Inter / system | 14sp | Regular |
| Caption / legal | Inter / system | 11sp | Regular, `#8E8E93` |
| Logo wordmark | Brush script (match logo) | 32–48sp | — |

---

## 3. Screens (follow the wireframes)

### 3.1 Splash
- Background `#F2F2F2`; logo + wordmark centered slightly below vertical center.
- Thin horizontal progress bar under wordmark: black `#1A1A1A` fill on `#D9D9D9` track, animating left → right.
- Android system SplashScreen API first, then this in-app splash while session + product JSON load. Route: signed in → Home, else → Login.

### 3.2 Login
- Wordmark centered, upper-middle.
- "Don't have an account ? **Sign up**" — underlined tappable link → Sign up.
- Label "Email" + email field (`#D9D9D9`, 12dp radius, magnifier-free; single email field — passwordless auth).
- Full-width black **Login** button (white bold text).
- Hairline divider with "Or".
- Gray **Continue with Google** button (`#D9D9D9` fill, `#1A1A1A` text, Google glyph).
- Footer: "By clicking continue, you agree to our **Terms of Service** and **Privacy Policy**." — 11sp dim, links tappable.

### 3.3 Sign up
- Mirror of Login: "Already have an account ? **Sign in**" → Login.
- Black **Create Account** button; same Google button and footer.

### 3.4 Home
- Small wordmark top-left.
- Search field full width: `#D9D9D9`, magnifier icon, placeholder "Search for nails extension..." — filters the grid live.
- 2-column `LazyVerticalGrid` of product cards: white cards, 12dp radius, image fill, skeleton shimmer while loading. Tap → Product Detail.

### 3.5 Product Detail
- Top bar: back arrow **‹ Nails**.
- Large rounded hero image (`24dp` radius).
- Row of 5 square thumbnails (12dp radius) — **these are the product's design variants**; tap swaps the hero image.
- Product name (large, bold) + description.
- Bottom bar: wide black **Buy Now** (bag icon, white text; v1 = snackbar stub) + square black **camera/aperture icon button** at bottom-right → AR Try-On for **this product and selected variant**.

### 3.6 AR Try-On
- Full-screen CameraX preview; front camera **default** (rear toggle in top bar).
- Back button top-left; debug toggle (bug icon, `BuildConfig`-gated) top-right.
- Bottom carousel of the product's design variants — selecting swaps the active sprite set live.
- Capture button (white ring + dot, bottom-center) → preview → **Retake / Save to gallery**.
- Camera permission rationale + denied/forever-denied states.
- Debug mode: white landmark skeleton + highlighted DIP/TIP per finger; HUD with FPS, inference ms, handedness + score, palm/back state, tracking-lost count, per-finger raw vs smoothed markers, angle and scale; **recorded-video replay** mode feeding a saved clip through the same pipeline.

## 3.7 Admin (product management)
Hidden entry from Home (long-press logo) → PIN gate → Admin. Same visual language.
- Product CRUD: name, description, hero image, flat-lay photo (10 nails, 2 rows of 5,
  thumb→pinky, plain light bg) from gallery or camera.
- In-app sprite conversion: Kotlin port of the validated Module 0 extractor runs on save;
  admin sees a 10-sprite preview grid with per-nail pass/fail (alpha coverage 0.2–0.95,
  AR 1.4–3.0, core-vs-bg deltaE &gt; 20, exactly 10 components). Confirm or re-upload.
- Merged-blob splitting: cut at narrowest column neck (deterministic) — NOT watershed
  (misfires on decorated nails; validated on the sample photo).
- Each photo row = one design variant (5 sprites + mirrored _m set for other hand).
- Room DB for admin products; images/sprites in internal storage; repository merges
  bundled seed JSON + admin data through one interface.
- v1 admin gate = local PIN, not remote auth (out of scope while data is local).

---

## 4. Data Model (product JSON, local for v1)

```
Product {
  id, name, description,
  variants: [
    Variant {
      id, name,
      thumbImage,            // used in detail thumbnail row + AR carousel
      sprites: { thumb, index, middle, ring, pinky },  // transparent PNGs
      manifest: { aspectRatio, anchor: "base-center", tipDirection: "up" }
    }
  ]
}
```
`products.json` lives in assets; repository interface so a backend can replace it later.

---

## 5. Navigation

```
Splash → (session?) → Home ──→ Product Detail ⇄ AR Try-On
     └→ Login ⇄ Sign up ──→ Home
```
Type-safe Navigation Compose routes.

---

## 6. Asset Manifest

| Asset | File | Notes |
|---|---|---|
| Logo | `res/drawable/zefer_logo.png` (provided) | **Only ever this file. Never regenerate.** Black on light background |
| Product images + sprites | from Module 0 script | one sample design bundled for testing |
| Hand landmark model | `assets/hand_landmarker.task` | MediaPipe Tasks Vision |
| Product data | `assets/products.json` | schema in §4 |
