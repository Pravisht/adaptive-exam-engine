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
        cleaned = cleaned.replaceAll("(?i)Your Personal Exams Guide.*", "");

        // Remove common scoring artifacts from OCR like "(+3, -1)" or "( +3,-1 )"
        cleaned = cleaned.replaceAll("\\(\\s*[+\\-]?\\d+\\s*,\\s*[+\\-]?\\d+\\s*\\)", "");

        // Normalize multiple newlines and spaces
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        cleaned = cleaned.replaceAll("\\n{2,}", "\n\n");

        return cleaned.trim();
    }
}

