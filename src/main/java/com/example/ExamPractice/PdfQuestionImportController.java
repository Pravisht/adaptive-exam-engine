package com.example.ExamPractice;

import org.springframework.beans.factory.annotation.Value;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Controller that runs the full pipeline: PDF -> images -> OCR -> cleaning -> QuestionDto list.
 */
@RestController
@RequestMapping("/api/questions")
public class PdfQuestionImportController {

    private final PdfToImages pdfToImages;
    private final TesseractOcrService ocrService;
    private final TextCleaningService cleaningService;
    private final QuestionExtractionClient extractionClient;
    private final int chunkPages;

    public PdfQuestionImportController(PdfToImages pdfToImages,
                                       TesseractOcrService ocrService,
                                       TextCleaningService cleaningService,
                                       QuestionExtractionClient extractionClient,
                                       @Value("${llm.chunkPages:8}") int chunkPages) {
        this.pdfToImages = pdfToImages;
        this.ocrService = ocrService;
        this.cleaningService = cleaningService;
        this.extractionClient = extractionClient;
        this.chunkPages = Math.max(1, chunkPages);
    }

    /**
     * Upload a PDF, extract questions, and return them as a list of QuestionDto objects.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<QuestionDto>> importQuestions(@RequestParam("file") MultipartFile file) throws IOException {
        String sourcePdfName = file.getOriginalFilename();
        List<String> cleanedPages = new ArrayList<>();

        try (InputStream inputStream = file.getInputStream()) {
            List<BufferedImage> images = pdfToImages.convert(inputStream);
            for (int i = 0; i < images.size(); i++) {
                int pageNumber = i + 1;
                String rawText = ocrService.extractText(images.get(i), pageNumber);
                String cleaned = cleaningService.cleanPageText(rawText);
                cleanedPages.add(cleaned);
            }
        }

        List<QuestionDto> merged = new ArrayList<>();

        // Chunked extraction to avoid oversized LLM requests.
        for (int start = 0; start < cleanedPages.size(); start += chunkPages) {
            int endExclusive = Math.min(start + chunkPages, cleanedPages.size());
            StringBuilder chunkText = new StringBuilder();
            int chunkStartPage = start + 1;

            for (int i = start; i < endExclusive; i++) {
                chunkText.append("\n\n=== Page ").append(i + 1).append(" ===\n");
                chunkText.append(cleanedPages.get(i));
            }

            List<QuestionDto> chunkQuestions = extractionClient.extractQuestions(chunkText.toString(), sourcePdfName, chunkStartPage);
            String fallbackChunkSubject = inferSubjectFromChunk(chunkText.toString());
            for (QuestionDto question : chunkQuestions) {
                if (question.getPageNumber() == null || question.getPageNumber() <= 0) {
                    System.out.println("page num fallback");
                    question.setPageNumber(chunkStartPage);
                }
                if (question.getSourcePdfName() == null || question.getSourcePdfName().isBlank()) {
                    System.out.println("pdf name fallback");
                    question.setSourcePdfName(sourcePdfName);
                }
                if (question.getSubject() == null || question.getSubject().isBlank()) {
                    System.out.println("subject fallback"+" "+question.toString());
                    question.setSubject(inferSubjectFromQuestion(question.getText(), fallbackChunkSubject));
                }
            }
            merged.addAll(chunkQuestions);
        }

        return ResponseEntity.ok(deduplicate(merged));
    }

    private List<QuestionDto> deduplicate(List<QuestionDto> questions) {
        Map<String, QuestionDto> unique = new LinkedHashMap<>();
        for (QuestionDto question : questions) {
            if (question == null || question.getText() == null || question.getText().isBlank()) {
                continue;
            }
            if (isLowQualityQuestion(question)) {
                continue;
            }
            String key = buildDedupKey(question);
            unique.putIfAbsent(key, question);
        }
        return new ArrayList<>(unique.values());
    }

    private String buildDedupKey(QuestionDto question) {
        String normalizedText = question.getText().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
        String normalizedOptions = question.getOptions() == null
                ? ""
                : question.getOptions().stream()
                .map(option -> option == null ? "" : option.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim())
                .reduce((left, right) -> left + "|" + right)
                .orElse("");
        return normalizedText + "::" + normalizedOptions;
    }

    private boolean isLowQualityQuestion(QuestionDto question) {
        String text = question.getText().trim();
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.startsWith("=== page")) {
            return true;
        }
        // Reject ultra-short fragments that are usually OCR split leftovers.
        if (text.length() < 12) {
            return true;
        }
        // At least two options should be reasonably long to qualify as a stable MCQ parse.
        if (question.getOptions() != null) {
            long meaningfulOptions = question.getOptions().stream()
                    .filter(o -> o != null && o.trim().length() >= 3)
                    .count();
            return meaningfulOptions < 2;
        }
        return false;
    }

    private String inferSubjectFromChunk(String chunkText) {
        String lower = chunkText == null ? "" : chunkText.toLowerCase(Locale.ROOT);
        if (lower.contains("maths and reasoning") || lower.contains("quantitative aptitude")) {
            return "Maths";
        }
        if (lower.contains("general knowledge") || lower.contains("general awareness") || lower.contains("current affairs")) {
            return "GK";
        }
        if (lower.contains("english language") || lower.contains("comprehension") || lower.contains("grammar")) {
            return "English";
        }
        if (lower.contains("aptitude")) {
            return "Aptitude";
        }
        return null;
    }

    private String inferSubjectFromQuestion(String questionText, String fallbackChunkSubject) {
        if (questionText == null) {
            return fallbackChunkSubject;
        }
        String lower = questionText.toLowerCase(Locale.ROOT);

        // Maths signals
        if (lower.matches(".*[=+\\-*/%^].*")
                || lower.contains("triangle")
                || lower.contains("probability")
                || lower.contains("average")
                || lower.contains("interest")
                || lower.contains("ratio")
                || lower.contains("volume")
                || lower.contains("equation")) {
            return "Maths";
        }

        // English signals
        if (lower.contains("synonym")
                || lower.contains("antonym")
                || lower.contains("passage")
                || lower.contains("grammar")
                || lower.contains("meaning")
                || lower.contains("vocabulary")) {
            return "English";
        }

        // GK signals
        if (lower.contains("india")
                || lower.contains("constitution")
                || lower.contains("minister")
                || lower.contains("project")
                || lower.contains("history")
                || lower.contains("which year")) {
            return "GK";
        }

        return fallbackChunkSubject;
    }
}

