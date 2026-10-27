package com.wilson.spendify.ingestion;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class PdfStatementTextExtractor {
    public List<String> extract(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> pages = new ArrayList<>();
            int readableCharacters = 0;
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).strip();
                pages.add(text);
                readableCharacters += text.replaceAll("\\s+", "").length();
            }
            if (readableCharacters < 20) {
                throw new IllegalArgumentException(
                        "This statement has no usable embedded text. Scanned statements are not supported.");
            }
            return List.copyOf(pages);
        } catch (IOException ex) {
            throw new IllegalArgumentException("The PDF statement could not be read.", ex);
        }
    }
}
