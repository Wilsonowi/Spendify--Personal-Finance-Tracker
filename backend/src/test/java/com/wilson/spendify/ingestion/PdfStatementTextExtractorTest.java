package com.wilson.spendify.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfStatementTextExtractorTest {
    private final PdfStatementTextExtractor extractor = new PdfStatementTextExtractor();

    @Test
    void extractsEmbeddedTextPageByPage() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            addPage(document, "PUBLIC BANK STATEMENT");
            addPage(document, "02/01  SYNTHETIC TRANSACTION  12.50  87.50");
            document.save(output);
            pdf = output.toByteArray();
        }

        var pages = extractor.extract(pdf);

        assertThat(pages).hasSize(2);
        assertThat(pages.getFirst()).contains("PUBLIC BANK STATEMENT");
        assertThat(pages.get(1)).contains("SYNTHETIC TRANSACTION");
    }

    @Test
    void rejectsPdfWithoutEmbeddedText() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }

        assertThatThrownBy(() -> extractor.extract(pdf))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("embedded text");
    }

    private void addPage(PDDocument document, String text) throws Exception {
        PDPage page = new PDPage();
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 11);
            content.newLineAtOffset(72, 720);
            content.showText(text);
            content.endText();
        }
    }
}
