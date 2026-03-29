package com.example.examengine.ingestion.service;

import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * Basic cleaning utilities for OCR text before question extraction.
 */
@Service
public class TextCleaningService {
    private static final Pattern QUESTION_START = Pattern.compile("^\\s*\\d{1,3}[.)]\\s+.*");
    private static final Pattern OPTION_START = Pattern.compile("^\\s*[a-dA-D][.)]\\s+.*");
    private static final Pattern ANSWER_START = Pattern.compile("^\\s*\\d{1,3}[.)]?\\s*answer\\s*[:\\-]?\\s*[a-dA-D].*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE_MARKER = Pattern.compile("^\\s*=+\\s*Page\\s+\\d+\\s*=+\\s*$", Pattern.CASE_INSENSITIVE);

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

        return rebuildWrappedMcqLines(cleaned).trim();
    }

    /**
     * Rebuild OCR-wrapped lines so broken question text lines are stitched together.
     */
    private String rebuildWrappedMcqLines(String input) {
        String[] lines = input.split("\\R");
        StringBuilder out = new StringBuilder();
        StringBuilder current = new StringBuilder();

        for (String line : lines) {
            String trimmed = line == null ? "" : line.trim();
            if (trimmed.isEmpty()) {
                flushCurrent(out, current);
                continue;
            }

            boolean isBoundary = QUESTION_START.matcher(trimmed).matches()
                    || OPTION_START.matcher(trimmed).matches()
                    || ANSWER_START.matcher(trimmed).matches()
                    || PAGE_MARKER.matcher(trimmed).matches();

            if (isBoundary) {
                flushCurrent(out, current);
                current.append(trimmed);
            } else {
                if (current.length() == 0) {
                    current.append(trimmed);
                } else {
                    current.append(" ").append(trimmed);
                }
            }
        }

        flushCurrent(out, current);
        return out.toString();
    }

    private void flushCurrent(StringBuilder out, StringBuilder current) {
        if (current.length() == 0) {
            return;
        }
        if (out.length() > 0) {
            out.append('\n');
        }
        out.append(current);
        current.setLength(0);
    }
}
