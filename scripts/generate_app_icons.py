import sys
from pathlib import Path
from PIL import Image, ImageFilter
import numpy as np
from collections import deque

def create_icons(src_image_path: str):
    src_path = Path(src_image_path)
    if not src_path.exists():
        raise FileNotFoundError(f"Source image not found: {src_path}")

    img = Image.open(src_path).convert('RGBA')
    arr = np.array(img, dtype=np.uint8)
    h, w = arr.shape[:2]

    # Flood-fill transparency from image boundary for near-white background
    visited = np.zeros((h, w), dtype=bool)
    q = deque()

    # Seed all border pixels that are near-white
    for c in range(w):
        for r in (0, h - 1):
            if arr[r, c, 0] > 235 and arr[r, c, 1] > 235 and arr[r, c, 2] > 235:
                q.append((r, c))
                visited[r, c] = True
    for r in range(h):
        for c in (0, w - 1):
            if not visited[r, c] and arr[r, c, 0] > 235 and arr[r, c, 1] > 235 and arr[r, c, 2] > 235:
                q.append((r, c))
                visited[r, c] = True

    while q:
        r, c = q.popleft()
        arr[r, c, 3] = 0  # Set alpha to 0
        for dr, dc in [(-1, 0), (1, 0), (0, -1), (0, 1)]:
            nr, nc = r + dr, c + dc
            if 0 <= nr < h and 0 <= nc < w and not visited[nr, nc]:
                visited[nr, nc] = True
                if arr[nr, nc, 0] > 235 and arr[nr, nc, 1] > 235 and arr[nr, nc, 2] > 235:
                    q.append((nr, nc))

    clean_img = Image.fromarray(arr, mode='RGBA')

    # Find bounding box of non-transparent pixels
    bbox = clean_img.getbbox()
    if bbox:
        cropped = clean_img.crop(bbox)
    else:
        cropped = clean_img

    # Place in a 512x512 square canvas with ~6% padding for optimal icon appearance
    canvas_size = 512
    padding = int(canvas_size * 0.06)
    target_content_size = canvas_size - 2 * padding

    aspect = cropped.width / cropped.height
    if aspect > 1:
        new_w = target_content_size
        new_h = int(target_content_size / aspect)
    else:
        new_h = target_content_size
        new_w = int(target_content_size * aspect)

    resized_content = cropped.resize((new_w, new_h), Image.Resampling.LANCZOS)

    master_icon = Image.new('RGBA', (canvas_size, canvas_size), (0, 0, 0, 0))
    offset_x = (canvas_size - new_w) // 2
    offset_y = (canvas_size - new_h) // 2
    master_icon.paste(resized_content, (offset_x, offset_y), resized_content)

    # 1. Save Windows Assets
    win_assets = Path('windows/assets')
    win_assets.mkdir(parents=True, exist_ok=True)
    icon_png_path = win_assets / 'icon.png'
    master_icon.save(icon_png_path, format='PNG')
    print(f"Saved: {icon_png_path}")

    icon_ico_path = win_assets / 'icon.ico'
    ico_sizes = [(256, 256), (128, 128), (64, 64), (48, 48), (32, 32), (16, 16)]
    master_icon.save(icon_ico_path, format='ICO', sizes=ico_sizes)
    print(f"Saved: {icon_ico_path}")

    # 2. Save Android mipmap icons
    android_res = Path('android/app/src/main/res')
    android_sizes = {
        'mipmap-mdpi': 48,
        'mipmap-hdpi': 72,
        'mipmap-xhdpi': 96,
        'mipmap-xxhdpi': 144,
        'mipmap-xxxhdpi': 192
    }

    for folder_name, sz in android_sizes.items():
        folder = android_res / folder_name
        folder.mkdir(parents=True, exist_ok=True)

        scaled = master_icon.resize((sz, sz), Image.Resampling.LANCZOS)
        out_launcher = folder / 'ic_launcher.png'
        out_round = folder / 'ic_launcher_round.png'

        scaled.save(out_launcher, format='PNG')
        scaled.save(out_round, format='PNG')
        print(f"Saved: {out_launcher} & {out_round} ({sz}x{sz})")

    # Clean up test file if present
    test_path = Path('windows/assets/icon_test.png')
    if test_path.exists():
        test_path.unlink()

if __name__ == '__main__':
    src = r'C:\Users\cshar\.gemini\antigravity\brain\bf772fd2-ee01-4d63-81f4-d901ce4cbd56\.user_uploaded\media_1794767478915_1a89f06b.jpg'
    if len(sys.argv) > 1:
        src = sys.argv[1]
    create_icons(src)
