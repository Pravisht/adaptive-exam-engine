package com.example.examengine.question;

import lombok.Data;

import java.util.List;

/**
 * DTO representing a single extracted question.
 */
@Data
public class QuestionDto {

    /**
     * Optional identifier, can be populated when persisting.
     */
    private String id;

    /**
     * The question text.
     */
    private String text;

    /**
     * The answer options. The pipeline will normally expect exactly 4 options.
     */
    private List<String> options;

    /**
     * Index (0-based) of the correct option in the options list.
     */
    private Integer correctOptionIndex;

    /**
     * Subject of the question, e.g. "Maths", "GK", "English", "Aptitude".
     */
    private String subject;

    /**
     * Difficulty level of the question.
     */
    private DifficultyLevel difficulty;

    /**
     * Optional metadata – name of the source PDF file.
     */
    private String sourcePdfName;

    /**
     * Optional metadata – page number in the PDF where the question was found.
     */
    private Integer pageNumber;
}
