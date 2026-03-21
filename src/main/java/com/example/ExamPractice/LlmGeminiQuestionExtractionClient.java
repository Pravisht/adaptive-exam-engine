package com.example.ExamPractice;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini-backed extraction. It asks Gemini to return strict JSON matching {@link QuestionDto}.
 */
@Service
@ConditionalOnProperty(name = "llm.provider", havingValue = "gemini")
public class LlmGeminiQuestionExtractionClient implements QuestionExtractionClient {

    private static final Logger log = LoggerFactory.getLogger(LlmGeminiQuestionExtractionClient.class);

    private static final List<String> ALLOWED_SUBJECTS = List.of("Maths", "GK", "English", "Aptitude");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.model:gemini-1.5-flash}")
    private String model;

    @Value("${llm.maxChars:120000}")
    private int maxChars;

    @Value("${llm.temperature:0.2}")
    private double temperature;

    public LlmGeminiQuestionExtractionClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.restClient = RestClient.create();
    }

    @Override
    public List<QuestionDto> extractQuestions(String cleanedText, String sourcePdfName, int pageNumber) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Gemini API key is missing. Set GEMINI_API_KEY env var or gemini.api.key in application.properties.");
        }

        String documentText = cleanedText == null ? "" : cleanedText.trim();
        if (documentText.isBlank()) {
            return List.of();
        }

        if (documentText.length() > maxChars) {
            log.warn("OCR text too large ({} chars). Truncating to {} chars before sending to Gemini.",
                    documentText.length(), maxChars);
            documentText = documentText.substring(0, maxChars);
        }

        String prompt = """
You are an expert exam question parser.
Given OCR text from an exam PDF, extract all multiple-choice questions.

Important:
- The OCR text may contain noise. Use only what is present in the text.
- Return ONLY valid JSON. No markdown, no explanations.

JSON schema (return a JSON array of objects):
[
  {
    "text": string,                 // question statement only
    "options": [string,string,string,string], // 4 options, ordered a,b,c,d
    "correctOptionIndex": integer|null, // 0 for a, 1 for b, 2 for c, 3 for d, null if missing
    "subject": string|null,        // one of: Maths, GK, English, Aptitude (or null if not identifiable)
    "difficulty": "EASY"|"MEDIUM"|"HARD"|null, // infer if possible, else null
    "pageNumber": integer|null
  }
]

Rules:
1) Options must be exactly 4 and ordered as [a,b,c,d].
2) If the answer key at the end contains something like "(1. answer d)" or "1. answer d",
   map that to correctOptionIndex. If no answer for that question number is found, set correctOptionIndex=null.
3) `subject` MUST be exactly one of: Maths, GK, English, Aptitude.
   Try hard to classify using local context (section headers, nearby questions, vocabulary).
   Use null only when classification is genuinely impossible.
4) If difficulty cannot be inferred confidently, set difficulty to null.
5) Do NOT include option text inside "text".
6) Remove scoring artifacts/noise from text and options, e.g. "(+3, -1)", "(+2,-0.5)", app banners.
7) Use the nearest "=== Page N ===" marker to fill pageNumber where possible. If uncertain, set pageNumber to null.

Here is the OCR text (may include the question set and an answer key at the end):
%s
""".formatted(documentText);

        // Gemini REST request with JSON output
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + apiKey;

        var requestBody = java.util.Map.of(
                "contents", java.util.List.of(
                        java.util.Map.of(
                                "role", "user",
                                "parts", java.util.List.of(java.util.Map.of("text", prompt))
                        )
                ),
                "generationConfig", java.util.Map.of(
                        "responseMimeType", "application/json",
                        "temperature", temperature
                )
        );

        String responseBody = restClient.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(String.class);

        if (responseBody == null || responseBody.isBlank()) {
            return List.of();
        }

        return parseGeminiQuestions(responseBody);
    }

    private List<QuestionDto> parseGeminiQuestions(String geminiResponseBody) {
        try {
            JsonNode root = objectMapper.readTree(geminiResponseBody);
            JsonNode partsTextNode = root.at("/candidates/0/content/parts/0/text");
            String partsText = partsTextNode.isMissingNode() ? null : partsTextNode.asText(null);

            if (partsText == null || partsText.isBlank()) {
                log.warn("Gemini response did not contain parts[0].text. Root: {}", geminiResponseBody);
                return List.of();
            }

            String jsonText = stripCodeFences(partsText.trim());

            JsonNode parsed = objectMapper.readTree(jsonText);
            if (!parsed.isArray()) {
                log.warn("Gemini did not return a JSON array. Parsed node type: {}", parsed.getNodeType());
                return List.of();
            }

            List<QuestionDto> extracted = objectMapper.convertValue(parsed, new TypeReference<List<QuestionDto>>() {});

            // Post-validate minimal constraints
            List<QuestionDto> normalized = new ArrayList<>();
            for (QuestionDto q : extracted) {
                if (q == null || q.getOptions() == null || q.getOptions().size() != 4) {
                    continue;
                }

                // Enforce allowed subject set
                if (q.getSubject() != null && ALLOWED_SUBJECTS.stream().noneMatch(s -> s.equalsIgnoreCase(q.getSubject()))) {
                    q.setSubject(null);
                }

                // Normalize subject casing to our allowed values
                if (q.getSubject() != null) {
                    for (String allowed : ALLOWED_SUBJECTS) {
                        if (allowed.equalsIgnoreCase(q.getSubject())) {
                            q.setSubject(allowed);
                            break;
                        }
                    }
                }

                // Enforce difficulty set
                if (q.getDifficulty() != null) {
                    // jackson should already map enums; if it came as an unexpected value, parse would fail earlier
                    // so nothing to do here.
                }

                if (q.getCorrectOptionIndex() != null) {
                    int idx = q.getCorrectOptionIndex();
                    if (idx < 0 || idx > 3) {
                        q.setCorrectOptionIndex(null);
                    }
                }
                normalized.add(q);
            }

            return normalized;
        } catch (Exception e) {
            log.error("Failed to parse Gemini JSON response", e);
            throw new RuntimeException("Gemini response parsing failed", e);
        }
    }

    private static String stripCodeFences(String text) {
        // Common Gemini behavior: returns ```json ... ```
        String stripped = text;
        stripped = stripped.replaceAll("(?s)^```\\s*json\\s*", "");
        stripped = stripped.replaceAll("(?s)^```\\s*", "");
        stripped = stripped.replaceAll("(?s)```\\s*$", "");
        return stripped.trim();
    }
}

