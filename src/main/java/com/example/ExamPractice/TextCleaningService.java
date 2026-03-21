package com.example.ExamPractice;

import org.springframework.stereotype.Service;

/**
 * Basic cleaning utilities for OCR text before question extraction.
 */
@Service
public class TextCleaningService {

    /**
     * Remove obvious noise like app banners, download prompts, and collapse whitespace.
     */
    public String cleanPageText(String raw) {
        if (raw == null) {
            return "";
        }

        String cleaned = raw;

        // Drop recurring app/footer noise
        cleaned = cleaned.replaceAll("(?i)Prepp Download Prepp APP.*", "");
        cleaned = cleaned.replaceAll("(?i)GET ITON.*", "");
        cleaned = cleaned.replaceAll("(?i)Google Play.*", "");

        // Normalize multiple newlines and spaces
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        cleaned = cleaned.replaceAll("\\n{2,}", "\n\n");

        return cleaned.trim();
    }
}

