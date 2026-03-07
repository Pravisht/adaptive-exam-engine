package com.example.ExamPractice;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple controller to test PDF -> images -> OCR pipeline.
 */
@RestController
@RequestMapping("/api/pdf")
public class PdfOcrController {

    private final PdfToImages pdfToImages;
    private final TesseractOcrService ocrService;

    public PdfOcrController(PdfToImages pdfToImages, TesseractOcrService ocrService) {
        this.pdfToImages = pdfToImages;
        this.ocrService = ocrService;
    }

    /**
     * Upload a PDF, convert it to images, run OCR on each page, and return raw text per page.
     */
    @PostMapping(value = "/ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<String>> ocrPdf(@RequestParam("file") MultipartFile file) throws IOException {
        List<String> pageTexts = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream()) {
            List<BufferedImage> images = pdfToImages.convert(inputStream);

            for (int i = 0; i < images.size(); i++) {
                int pageNumber = i + 1;
                String text = ocrService.extractText(images.get(i), pageNumber);
                pageTexts.add(text);
            }
        }
        // to do: to store and give the response as page wise or DTO wise
        return ResponseEntity.ok(pageTexts);
    }
}


