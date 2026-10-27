from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1] / "frontend" / "public" / "icons"
ROOT.mkdir(parents=True, exist_ok=True)


def icon(size: int) -> None:
    image = Image.new("RGB", (size, size), "#132a25")
    draw = ImageDraw.Draw(image)
    pad = size // 7
    draw.rounded_rectangle((pad, pad, size - pad, size - pad), radius=size // 14, fill="#0c6b58")
    font = ImageFont.truetype("C:/Windows/Fonts/georgiab.ttf", size=size // 2)
    box = draw.textbbox((0, 0), "S", font=font)
    width, height = box[2] - box[0], box[3] - box[1]
    draw.text(((size - width) / 2, (size - height) / 2 - size * 0.05), "S", font=font, fill="#ffffff")
    image.save(ROOT / f"spendify-{size}.png", optimize=True)


for value in (192, 512):
    icon(value)
