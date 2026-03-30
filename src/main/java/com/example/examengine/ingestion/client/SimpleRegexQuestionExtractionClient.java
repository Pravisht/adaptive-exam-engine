package com.example.examengine.ingestion.client;

import com.example.examengine.question.QuestionDto;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Simple regex-based extractor to turn MCQ-style text into QuestionDto objects.
 * This is a placeholder until an LLM-backed implementation is wired in.
 */
@Service
@Conditional(RegexLlmEnabledCondition.class)
public class SimpleRegexQuestionExtractionClient implements QuestionExtractionClient {

    // Matches start of a question line like "1. " or "23. "
    private static final Pattern QUESTION_SPLIT_PATTERN = Pattern.compile("(?m)(?=\\n?\\d+\\.)");

    private static final Pattern OPTION_PATTERN = Pattern.compile("(?m)^[a-dA-D]\\.\\s*(.+)$");

    @Override
    public List<QuestionDto> extractQuestions(String cleanedText, String sourcePdfName, int pageNumber) {
        List<QuestionDto> result = new ArrayList<>();
        if (cleanedText == null || cleanedText.isBlank()) {
            return result;
        }

        String[] blocks = QUESTION_SPLIT_PATTERN.split(cleanedText);
        for (String block : blocks) {
            String trimmedBlock = block.trim();
            if (trimmedBlock.isEmpty()) {
                continue;
            }

            // First line is question text (strip leading number + dot)
            String[] lines = trimmedBlock.split("\\R");
            String firstLine = lines[0].replaceFirst("^\\d+\\.\\s*", "").trim();
            if (firstLine.isEmpty()) {
                continue;
            }

            List<String> options = new ArrayList<>();
            Matcher optionMatcher = OPTION_PATTERN.matcher(trimmedBlock);
            while (optionMatcher.find()) {
                options.add(optionMatcher.group(1).trim());
            }

            // Expect exactly 4 options for now
            if (options.size() != 4) {
                continue;
            }

            QuestionDto dto = new QuestionDto();
            dto.setText(firstLine);
            dto.setOptions(options);
            // Correct answer is not reliably derivable from regex alone.
            dto.setCorrectOptionIndex(null);
            dto.setSubject(null);
            dto.setDifficulty(null);
            dto.setSourcePdfName(sourcePdfName);
            dto.setPageNumber(pageNumber);

            result.add(dto);
        }

        return result;
    }
}
