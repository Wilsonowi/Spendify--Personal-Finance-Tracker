from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "fixtures" / "statements"


def pdf_text(lines: list[str]) -> bytes:
    def escape(value: str) -> str:
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)")

    content = ["BT", "/F1 10 Tf", "50 780 Td"]
    for line in lines:
        content.extend([f"({escape(line)}) Tj", "0 -14 Td"])
    content.append("ET")
    stream = "\n".join(content).encode("ascii")
    objects = [
        b"<< /Type /Catalog /Pages 2 0 R >>",
        b"<< /Type /Pages /Kids [3 0 R] /Count 1 >>",
        b"<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>",
        b"<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>",
        b"<< /Length " + str(len(stream)).encode("ascii") + b" >>\nstream\n" + stream + b"\nendstream",
    ]
    output = bytearray(b"%PDF-1.4\n")
    offsets = [0]
    for index, body in enumerate(objects, start=1):
        offsets.append(len(output))
        output.extend(f"{index} 0 obj\n".encode("ascii"))
        output.extend(body)
        output.extend(b"\nendobj\n")
    xref = len(output)
    output.extend(f"xref\n0 {len(objects) + 1}\n".encode("ascii"))
    output.extend(b"0000000000 65535 f \n")
    for offset in offsets[1:]:
        output.extend(f"{offset:010d} 00000 n \n".encode("ascii"))
    output.extend(f"trailer\n<< /Size {len(objects) + 1} /Root 1 0 R >>\nstartxref\n{xref}\n%%EOF\n".encode("ascii"))
    return bytes(output)


def main() -> None:
    OUTPUT.mkdir(parents=True, exist_ok=True)
    bank = [
        "PUBLIC BANK",
        "STATEMENT DATE 20 Jan 2026",
        "Total Debits 100.00",
        "No. of Debits 1",
        "Total Credits 0.00",
        "No. of Credits 0",
        "DATE TRANSACTION DEBIT CREDIT BALANCE",
        "01/01 Balance From Last Statement 200.00",
        "02/01 PB DEBIT CARD DR TNG-EWALLET 100.00 100.00",
        "Closing Balance In This Statement 100.00",
    ]
    wallet = [
        "TNG WALLET TRANSACTION HISTORY",
        "Date Status Transaction Type Reference Description Details Amount Wallet Balance",
        "05/01/2026 Success Payment  SYN-SNACK  Synthetic Snack  demo RM1.00 RM86.50",
        "04/01/2026 Success Payment  SYN-PAY  X001 David Kitchen  lunch RM12.50 RM87.50",
        "02/01/2026 Success Balance Top Up  SYN-TOP  Quick Payment Card  topup RM100.00 RM100.00",
    ]
    (OUTPUT / "synthetic-public-bank-2026.pdf").write_bytes(pdf_text(bank))
    (OUTPUT / "synthetic-tng-2026.pdf").write_bytes(pdf_text(wallet))


if __name__ == "__main__":
    main()
