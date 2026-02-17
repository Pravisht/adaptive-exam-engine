package com.example.ExamPractice;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.awt.image.BufferedImage;

/**
 * Tesseract (tess4j) implementation of {@link TesseractOcrService}.
 */
@Service
public class TesseractOcrServiceImpl implements TesseractOcrService {

    private static final Logger log = LoggerFactory.getLogger(TesseractOcrServiceImpl.class);

    private final Tesseract tesseract;

    public TesseractOcrServiceImpl(
            @Value("${ocr.tessdata.path}") String tessdataPath,
            @Value("${ocr.language:eng}") String language) {

        this.tesseract = new Tesseract();
        this.tesseract.setDatapath(tessdataPath);
        this.tesseract.setLanguage(language);
    }

    @Override
    public String extractText(BufferedImage image, int pageNumber) {
        try {
            return tesseract.doOCR(image);
        } catch (TesseractException e) {
            log.error("OCR failed for page {}", pageNumber, e);
            throw new RuntimeException("OCR failed for page " + pageNumber, e);
        }
    }
}


