package com.example.ExamPractice;

import java.awt.image.BufferedImage;

/**
 * Service abstraction for running OCR on page images.
 */
public interface TesseractOcrService {

    /**
     * Run OCR on a single page image.
     *
     * @param image      page image
     * @param pageNumber 1-based page number (for logging/debugging)
     * @return extracted raw text
     */
    String extractText(BufferedImage image, int pageNumber);
}


