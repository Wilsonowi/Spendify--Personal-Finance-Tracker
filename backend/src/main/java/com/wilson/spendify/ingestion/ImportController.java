package com.wilson.spendify.ingestion;

import com.wilson.spendify.finance.SourceDocument;
import com.wilson.spendify.finance.CoverageService;
import com.wilson.spendify.identity.CurrentUser;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/imports")
public class ImportController {
    private final ImportService imports;
    private final CoverageService coverage;

    public ImportController(ImportService imports, CoverageService coverage) {
        this.imports = imports;
        this.coverage = coverage;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public List<DocumentView> upload(@RequestPart("files") List<MultipartFile> files, Authentication auth) {
        if (files.isEmpty() || files.size() > 20) {
            throw new IllegalArgumentException("Upload between 1 and 20 documents per batch.");
        }
        UUID userId = CurrentUser.id(auth);
        return files.stream().map(file -> DocumentView.from(imports.upload(userId, file))).toList();
    }

    @PostMapping(value = "/statements", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public List<DocumentView> uploadStatements(@RequestPart("files") List<MultipartFile> files,
                                               @RequestParam LocalDate periodStart,
                                               @RequestParam LocalDate periodEnd,
                                               Authentication auth) {
        if (files.isEmpty() || files.size() > 20) {
            throw new IllegalArgumentException("Upload between 1 and 20 statement PDFs per batch.");
        }
        UUID userId = CurrentUser.id(auth);
        return files.stream().map(file -> DocumentView.from(
                imports.uploadStatement(userId, file, periodStart, periodEnd))).toList();
    }

    @GetMapping
    public List<DocumentView> list(Authentication auth) {
        return imports.list(CurrentUser.id(auth)).stream().map(DocumentView::from).toList();
    }

    @GetMapping("/coverage")
    public List<CoverageService.ProviderCoverage> coverage(@RequestParam int year,
                                                            Authentication auth) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("Coverage year must be between 2000 and 2100.");
        }
        return coverage.forYear(CurrentUser.id(auth), year, LocalDate.now());
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<byte[]> content(@PathVariable UUID id, Authentication auth) {
        SourceDocument document = imports.list(CurrentUser.id(auth)).stream()
                .filter(item -> item.getId().equals(id)).findFirst()
                .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Document not found."));
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"document\"")
                .contentType(MediaType.parseMediaType(document.getContentType()))
                .body(imports.read(CurrentUser.id(auth), id));
    }

    @GetMapping(value = "/{id}/pages/{page}", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> page(@PathVariable UUID id, @PathVariable int page, Authentication auth) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .body(imports.readPage(CurrentUser.id(auth), id, page));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id, Authentication auth) {
        imports.delete(CurrentUser.id(auth), id);
    }

    public record DocumentView(UUID id, String name, String contentType, long size, String provider,
                               String kind, LocalDate periodStart, LocalDate periodEnd, int rowCount,
                               int resolvedCount, int autoPostedCount, int transferCount, int duplicateCount,
                               int exceptionCount, String validationStatus, String validationMessage,
                               String status, String error, Instant createdAt) {
        static DocumentView from(SourceDocument document) {
            return new DocumentView(document.getId(), document.getOriginalName(), document.getContentType(),
                    document.getByteSize(), document.getProvider(), document.getDocumentKind().name(),
                    document.getPeriodStart(), document.getPeriodEnd(), document.getRowCount(),
                    document.getResolvedCount(), document.getAutoPostedCount(), document.getTransferCount(),
                    document.getDuplicateCount(), document.getExceptionCount(), document.getValidationStatus().name(),
                    document.getValidationMessage(), document.getStatus().name(), document.getErrorMessage(),
                    document.getCreatedAt());
        }
    }
}
