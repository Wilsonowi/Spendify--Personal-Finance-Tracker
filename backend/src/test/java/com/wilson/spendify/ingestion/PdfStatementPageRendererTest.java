package com.wilson.spendify.ingestion;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PdfStatementPageRendererTest {
    @Test
    void rendersOneBasedPdfPageAsPng() throws Exception {
        byte[] pdf;
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.addPage(new PDPage());
            document.save(output);
            pdf = output.toByteArray();
        }

        byte[] png = new PdfStatementPageRenderer().render(pdf, 2);

        assertThat(png).startsWith((byte) 0x89, (byte) 'P', (byte) 'N', (byte) 'G');
        assertThatThrownBy(() -> new PdfStatementPageRenderer().render(pdf, 3))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("page");
    }
}
