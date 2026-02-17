package com.example.ExamPractice;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Service that converts a PDF into per-page images using Apache PDFBox.
 */
@Service
public class PdfToImages {

    /**
     * Rendering resolution in DPI.
     */
    private final float dpi;

    public PdfToImages(@Value("${pdf.render.dpi:300}") float dpi) {
        this.dpi = dpi;
    }

    /**
     * Convert all pages of the given PDF input stream into a list of images.
     *
     * @param pdfInputStream input stream of the PDF; caller is responsible for closing it
     * @return list of page images in order
     * @throws IOException if the PDF cannot be read or rendered
     */
    public List<BufferedImage> convert(InputStream pdfInputStream) throws IOException {
        List<BufferedImage> images = new ArrayList<>();

        try (PDDocument document = PDDocument.load(pdfInputStream)) {
            PDFRenderer renderer = new PDFRenderer(document);
            int pageCount = document.getNumberOfPages();

            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, dpi, ImageType.GRAY);
                images.add(image);
            }
        }

        return images;
    }
}
