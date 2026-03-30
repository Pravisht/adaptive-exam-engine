package com.example.examengine.ingestion.service;

import com.example.examengine.question.QuestionDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;

/**
 * Basic cleaning utilities for OCR text before question extraction.
 */
@Service
public class TextCleaningService {
    private static final Pattern QUESTION_START = Pattern.compile("^\\s*\\d{1,3}[.)]\\s+.*");
    private static final Pattern OPTION_START = Pattern.compile("^\\s*[a-dA-D][.)]\\s+.*");
    private static final Pattern ANSWER_START = Pattern.compile("^\\s*\\d{1,3}[.)]?\\s*answer\\s*[:\\-]?\\s*[a-dA-D].*", Pattern.CASE_INSENSITIVE);
    private static final Pattern PAGE_MARKER = Pattern.compile("^\\s*=+\\s*Page\\s+\\d+\\s*=+\\s*$", Pattern.CASE_INSENSITIVE);

    /** OCR sometimes glues the next question after an option (e.g. "544 ll. Vessel ..."). */
    private static final Pattern MERGED_NEXT_Q_LEAK = Pattern.compile("(?i)\\s+ll\\.\\s+[A-Za-z].*$");

    private static final String[] FOOTER_FROM_PHRASE_TO_EOL = {
            "(?i)Prepp Download Prepp APP.*",
            "(?i)GET IT ON.*",
            "(?i)GET ITON.*",
            "(?i)Rect ON.*",
            "(?i)Google Play.*",
            "(?i)Your Personal Exams Guide.*",
            "(?i)prepp\\.in.*",
    };

    /**
     * Remove obvious noise like app banners, download prompts, and collapse whitespace.
     */
    public String cleanPageText(String raw) {
        if (raw == null) {
            return "";
        }

        String cleaned = raw;

        // Drop recurring app/footer noise (line-based and inline)
        cleaned = stripFooterNoise(cleaned);
        cleaned = cleaned.replaceAll("(?m)(?i)^.*Prepp Download Prepp APP.*$", "");
        cleaned = cleaned.replaceAll("(?m)(?i)^.*GET IT ON.*$", "");
        cleaned = cleaned.replaceAll("(?m)(?i)^.*Google Play.*$", "");

        // Remove common scoring artifacts from OCR like "(+3, -1)" or "( +3,-1 )"
        cleaned = cleaned.replaceAll("\\(\\s*[+\\-]?\\d+\\s*,\\s*[+\\-]?\\d+\\s*\\)", "");

        // Normalize multiple newlines and spaces
        cleaned = cleaned.replaceAll("[ \\t]+", " ");
        cleaned = cleaned.replaceAll("\\n{2,}", "\n\n");

        return rebuildWrappedMcqLines(cleaned).trim();
    }

    /**
     * Strip Prepp / store-banner noise from extracted question text and options (LLM may still echo OCR junk).
     */
    public void sanitizeQuestionDto(QuestionDto q) {
        if (q == null) {
            return;
        }
        q.setText(stripFooterNoiseAndLeaks(q.getText()));
        if (q.getOptions() != null) {
            List<String> out = new ArrayList<>(q.getOptions().size());
            for (String o : q.getOptions()) {
                out.add(stripFooterNoiseAndLeaks(o));
            }
            q.setOptions(out);
        }
    }

    /**
     * Removes app banners and trailing OCR junk from a single line or field (processes line-by-line).
     */
    public String stripFooterNoise(String s) {
        if (s == null || s.isBlank()) {
            return s == null ? "" : s.trim();
        }
        return stream(s.split("\\R", -1)).map(this::stripFooterNoiseLine).collect(joining("\n"));
    }

    private String stripFooterNoiseLine(String line) {
        if (line == null) {
            return "";
        }
        String out = line;
        for (String p : FOOTER_FROM_PHRASE_TO_EOL) {
            out = out.replaceAll(p, "");
        }
        out = out.replaceAll("(?i)\\s*\\|+\\s*(?:wa|>\\s*)$", "");
        out = out.replaceAll("[|»\"'“”*_©]{1,}\\s*$", "").trim();
        out = out.replaceAll("[ \\t]+", " ").trim();
        return out;
    }

    private String stripFooterNoiseAndLeaks(String s) {
        String out = stripFooterNoise(s);
        if (out == null || out.isEmpty()) {
            return out;
        }
        var m = MERGED_NEXT_Q_LEAK.matcher(out);
        if (m.find() && out.length() > 30) {
            out = out.substring(0, m.start()).trim();
        }
        return out;
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
