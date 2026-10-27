# Local Development

## Docker Path

```powershell
./scripts/init-local-env.ps1
docker compose up --build -d --wait
docker compose ps
```

The application is available at `http://localhost:5173`. PostgreSQL, MinIO, and
OCR are reachable only through internal Compose networks. PP-OCRv6 weights are
baked into the OCR image, so runtime inference does not need internet access.

To verify clean-database startup, remove only this project's Compose volumes and
start the stack again:

```powershell
docker compose down --volumes --remove-orphans
docker compose up --build -d --wait
```

## Component Checks Without Docker

Portable Java 21 and Maven may be placed under ignored `.tools/jdk21` and
`.tools/maven` directories. Then run:

```powershell
./scripts/test-local.ps1
```

For OCR API tests without installing the Paddle model:

```powershell
cd ocr-service
python -m venv .venv
./.venv/Scripts/python.exe -m pip install -r requirements-test.txt
./.venv/Scripts/python.exe -m pytest -q
```

The full Paddle dependencies are installed only inside the OCR Docker image.

## Complete Local Verification

With Docker Desktop running:

```powershell
./scripts/test-local.ps1
./scripts/test-ocr-golden.ps1

cd frontend
npm run test:e2e
npm run test:e2e:real
```

The real-stack tests create unique synthetic users. One verifies the original
three-slip OCR workflow. The other uploads synthetic multi-row PDF statements,
checks automatic posting with provisional categories and transfer
reconciliation, verifies RM13.50 of net expense and coverage gaps, and exports
a 2026 CSV.

Run the documented leak, dependency, and container checks before a release. The
verified commands and current residual image finding are recorded in
[Local Security Verification](security-verification.md).

## Troubleshooting

- If `docker compose` cannot connect, start Docker Desktop and wait for the
  engine status to report running.
- If required variables are missing, rerun `./scripts/init-local-env.ps1`; the
  generated `.env` is ignored by Git.
- If a service is not healthy, run `docker compose ps` followed by
  `docker compose logs --tail=200 <service>`.
- If OCR is slow on the first fixture, allow the model to warm in memory. It
  must not attempt an external model download at runtime.
- If statement upload returns HTTP 415, rebuild the backend; statement dates are
  ordinary multipart form fields and PDFs are the only accepted files.
- A scanned statement without embedded text is rejected clearly. Use a digital
  provider statement; whole-statement OCR is intentionally unsupported.
- If a statement is valid but some rows remain under **Exceptions**, only those
  rows need attention. Posted rows are already included in the selected month.
- Overview opens the latest closed imported month. Use the month selector for
  partial or older periods; the URL retains the selection.
- If port 5173 is occupied, stop the conflicting process before starting this
  verified configuration; browser-origin checks intentionally use
  `http://localhost:5173`.
