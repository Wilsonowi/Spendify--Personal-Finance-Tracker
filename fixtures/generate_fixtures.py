from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).parent

FIXTURES = {
    "public-bank-tng-topup.png": [
        "PUBLIC BANK",
        "Transaction Successful",
        "Description: TNG eWallet Top Up",
        "Transaction Amount: RM 100.00",
        "Transaction Date: 2026-07-10 14:30",
        "Reference No: PB-TNG-7812",
        "Recipient: Touch n Go eWallet",
    ],
    "tng-topup-credit.png": [
        "TNG eWallet",
        "Top Up Successful",
        "Top Up Amount: RM 100.00",
        "Date & Time: 2026-07-10 14:32",
        "Transaction ID: TNG-7812",
        "Description: Public Bank Reload",
    ],
    "tng-chicken-rice-payment.png": [
        "TNG eWallet",
        "Payment Successful",
        "Paid To: X001 DAVIDS CHICK",
        "Amount: RM 12.50",
        "Date & Time: 2026-07-10 18:45",
        "Transaction ID: TNG-9001",
    ],
}


def generate() -> None:
    font = ImageFont.load_default(size=24)
    for filename, lines in FIXTURES.items():
        image = Image.new("RGB", (900, 620), "#f7f8f3")
        draw = ImageDraw.Draw(image)
        draw.rectangle((35, 35, 865, 585), outline="#152d2a", width=3)
        y = 75
        for index, line in enumerate(lines):
            fill = "#112421" if index else "#0b6b58"
            draw.text((75, y), line, fill=fill, font=font)
            y += 64
        image.save(ROOT / filename, optimize=True)


if __name__ == "__main__":
    generate()

