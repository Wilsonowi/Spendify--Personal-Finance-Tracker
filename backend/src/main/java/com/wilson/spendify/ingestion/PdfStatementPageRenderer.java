package com.wilson.spendify.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Component
public class PdfStatementPageRenderer {
    public byte[] render(byte[] pdf, int page) {
        try (PDDocument document = Loader.loadPDF(pdf); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (page < 1 || page > document.getNumberOfPages()) {
                throw new IllegalArgumentException("The requested statement page is unavailable.");
            }
            var image = new PDFRenderer(document).renderImageWithDPI(page - 1, 120, ImageType.RGB);
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalArgumentException("The statement page could not be rendered.", ex);
        }
    }
}
