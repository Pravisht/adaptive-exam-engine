package com.example.examengine.ingestion.client;

import com.example.examengine.question.QuestionDto;

import java.util.List;

/**
 * Abstraction for turning cleaned OCR text into structured QuestionDto objects.
 * In production this can be backed by an LLM (OpenAI / Gemini).
 */
public interface QuestionExtractionClient {

    /**
     * Extract questions from a chunk of cleaned text.
     *
     * @param cleanedText   text after OCR and cleaning
     * @param sourcePdfName name of the source PDF, may be used as metadata
     * @param pageNumber    page where this text came from (if known), otherwise -1
     * @return list of extracted questions (possibly empty)
     */
    List<QuestionDto> extractQuestions(String cleanedText, String sourcePdfName, int pageNumber);
}
