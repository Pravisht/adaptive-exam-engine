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

            for (int i = start; i < endExclusive; i++) {
                chunkText.append("\n\n=== Page ").append(i + 1).append(" ===\n");
                chunkText.append(cleanedPages.get(i));
            }

            merged.addAll(extractionClient.extractQuestions(chunkText.toString(), sourcePdfName, -1));
        }

        return ResponseEntity.ok(deduplicate(merged));
    }

    private List<QuestionDto> deduplicate(List<QuestionDto> questions) {
        Map<String, QuestionDto> unique = new LinkedHashMap<>();
        for (QuestionDto question : questions) {
            if (question == null || question.getText() == null || question.getText().isBlank()) {
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
}

