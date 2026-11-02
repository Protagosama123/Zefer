import cv2, os, numpy as np

os.makedirs("app/src/main/assets/sample_hands", exist_ok=True)

def make_hand_photo(filename, tilt_deg=0, skin_tone=(180, 195, 225)):
    # Create 900x1200 realistic hand background photo
    H, W = 1200, 900
    img = np.full((H, W, 3), (242, 242, 242), dtype=np.uint8) # light background

    # Vignette
    y, x = np.ogrid[:H, :W]
    dist = np.sqrt((x - W/2)**2 + (y - H/2)**2) / 600.0
    vignette = np.clip(1.0 - 0.1 * dist, 0.85, 1.0)
    for c in range(3):
        img[..., c] = (img[..., c] * vignette).astype(np.uint8)

    # Hand canvas
    hand_mask = np.zeros((H, W), dtype=np.uint8)
    
    # Palm center around (450, 750)
    cv2.ellipse(hand_mask, (450, 750), (160, 180), 0, 0, 360, 255, -1)
    # Wrist
    cv2.rectangle(hand_mask, (330, 800), (570, 1200), 255, -1)
    
    # Fingers: thumb, index, middle, ring, pinky
    # Thumb
    cv2.ellipse(hand_mask, (280, 680), (45, 120), -35, 0, 360, 255, -1)
    # Index
    cv2.ellipse(hand_mask, (380, 480), (38, 170), -6, 0, 360, 255, -1)
    # Middle
    cv2.ellipse(hand_mask, (455, 430), (40, 190), 0, 0, 360, 255, -1)
    # Ring
    cv2.ellipse(hand_mask, (525, 470), (38, 170), 5, 0, 360, 255, -1)
    # Pinky
    cv2.ellipse(hand_mask, (595, 540), (34, 140), 12, 0, 360, 255, -1)

    # Soften hand mask
    hand_mask = cv2.GaussianBlur(hand_mask, (11, 11), 3)

    # Apply skin tone + subtle 3D lighting shading
    b, g, r = skin_tone
    hand_bgr = np.zeros_like(img)
    hand_bgr[..., 0] = b
    hand_bgr[..., 1] = g
    hand_bgr[..., 2] = r

    # Subtle shading
    shading = np.clip(1.0 - (x - 450) / 900.0 * 0.25 - (y - 500) / 1200.0 * 0.15, 0.8, 1.15)
    for c in range(3):
        hand_bgr[..., c] = np.clip(hand_bgr[..., c] * shading, 0, 255).astype(np.uint8)

    # Alpha composite
    alpha = (hand_mask.astype(np.float32) / 255.0)[..., None]
    img = (hand_bgr * alpha + img * (1.0 - alpha)).astype(np.uint8)

    # Optional rotation for tilt
    if tilt_deg != 0:
        M = cv2.getRotationMatrix2D((W/2, H/2), tilt_deg, 1.0)
        img = cv2.warpAffine(img, M, (W, H), borderValue=(242, 242, 242))

    cv2.imwrite(f"app/src/main/assets/sample_hands/{filename}", img, [cv2.IMWRITE_JPEG_QUALITY, 95])
    print("Wrote", filename)

make_hand_photo("sample_left_back.jpg", tilt_deg=-8, skin_tone=(175, 190, 220))
make_hand_photo("sample_right_back.jpg", tilt_deg=6, skin_tone=(165, 180, 215))
make_hand_photo("sample_tilted_hand.jpg", tilt_deg=25, skin_tone=(180, 195, 230))
