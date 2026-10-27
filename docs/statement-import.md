# Private Statement Import

Spendify processes digital Public Bank/Public Islamic Bank and TNG statements
entirely on the local Docker stack. Original PDFs are encrypted before MinIO
storage. Header names, addresses, account numbers, and wallet identifiers are
not copied into transaction records.

## Import 2026 Records

1. Start Spendify and create your private account through the UI.
2. Open **Imports**, choose **Statement**, and keep the range at
   `2026-01-01` to `2026-12-31`.
3. Upload the seven 2026 bank PDFs and the TNG transaction-history PDF directly
   from their private folder. Do not copy them into this repository.
4. Wait for complete-document validation. Structurally sound rows post
   automatically, including provisional `Uncategorized` rows; a validation
   failure posts nothing.
5. Spendify opens the recommended closed imported month. Check net spending,
   categories, merchants, refunds, provider coverage, and internal transfers.
6. Open **Exceptions** only when the banner reports held rows. Correct or
   exclude suspicious, potentially duplicated, low-confidence, or contradictory
   rows; source-page preview appears only for the selected row.
7. A TNG reload matches a Public Bank posting on the same day or up to two
   calendar days later; earlier bank postings are not eligible. Spendify
   prefers the nearest settlement date and automatically confirms only a
   mutually unique pair. Review equal nearest matches only.
8. In **Transactions**, set export dates to `2026-01-01` and `2026-12-31`, then
   download the CSV.

Exceptions do not affect analytics until resolved. Coverage warnings mean the
provider period is unavailable; they must never be interpreted as zero
activity. A partial current month remains selectable but is not preferred over
the latest closed imported month.

## Private Output

After all candidates and transfer proposals are resolved, move the downloaded
file to:

```text
<private-finance-folder>\spendify-output\spendify-2026.csv
```

The private CSV, original statements, screenshots, logs, and database dumps
must remain outside Git.
