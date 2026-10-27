# Local Security Verification

Application and container-image checks were rerun on 7 August 2026 against the
local Git worktree. This is a release-readiness record, not a claim that future
dependencies or images are free of new vulnerabilities.

## Checks

```powershell
# Full committed history and the staged release tree
docker run --rm -v "${PWD}:/repo" zricethezav/gitleaks:latest git /repo --redact
docker run --rm -v "${PWD}:/repo" zricethezav/gitleaks:latest git /repo --staged --redact

cd frontend
npm audit --omit=dev --audit-level=high

docker scout quickview local://spendify-backend:latest
docker scout quickview local://spendify-frontend:latest
docker scout quickview local://spendify-ocr:latest
```

## Results

- Gitleaks found no leaks in the complete history or staged release tree.
- Tracked-content checks found only synthetic `@spendify.local` users, seeded
  UUIDs, and fixture references. No personal email, phone number, bank account,
  API key, or private financial document is tracked.
- `npm audit` reported zero production dependency vulnerabilities.
- React Router was migrated to patched version 8.3.0 after a new
  RSC-specific advisory affected the previous 7.x line.
- The backend and frontend images reported no fixable critical or high
  vulnerabilities. The PostgreSQL JDBC driver was pinned to 42.7.12 after the
  scan identified CVE-2026-54291 in 42.7.11.
- All custom runtime containers run as non-root users.
- PostgreSQL and MinIO are reachable only on the internal data network. OCR is
  reachable only on the internal processing network.
- The final real-stack run retained two documents and exactly two encrypted
  MinIO objects after deletion. The sampled encrypted bytes did not contain a
  PNG signature.
- Private statement validation logged only provider/count/status summaries:
  seven Public Bank documents and one TNG document passed their declared-total
  or balance-continuity checks. No statement text or private CSV was tracked.

## Residual Image Findings

Docker Scout's complete layer view reports 3 critical and 3 high findings for
both the backend and frontend images, and 2 critical and 4 high findings for
the OCR image. These totals include inherited base-image and deleted-layer
records; they are not equivalent to exploitable packages in the final runtime
filesystem.

The backend and frontend policy checks report no fixable critical or high
vulnerabilities. For OCR, Scout attributes two fixable high findings to
`wheel` and `jaraco-context` in lower build layers. The final runtime filesystem
contains fixed `wheel` 0.46.3 and does not contain `jaraco-context`.

The OCR service is non-root, has no published port, receives only validated
uploads from the backend, and runs without internet access. Recheck the base
image and Debian fixes before any public deployment. Do not delete package
metadata or flatten image history merely to hide scanner results.

## Development Tooling Note

`npm audit --omit=dev` reports zero vulnerabilities. The full development audit
currently reports nine high-severity transitive advisories below
`vite-plugin-pwa` and `workbox-build`. These packages run only while generating
the static PWA bundle and are not copied into the final Nginx image.

npm suggests downgrading `vite-plugin-pwa` to 1.2.0, but that release does not
declare Vite 8 compatibility. The Vite-compatible 1.3.0 release is retained
until its Workbox dependency line publishes a compatible fix.

## Local Secret Boundary

An unfiltered directory scan is expected to detect generated values inside the
ignored `.env`. Release checks therefore scan Git history and staged/tracked
content, then separately verify that `.env`, `private-data/`, `personal-fixtures/`,
Docker volumes, build output, test output, OCR caches, and dependency directories
remain ignored.
