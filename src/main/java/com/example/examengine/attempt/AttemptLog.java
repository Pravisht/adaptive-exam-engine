package com.example.examengine.attempt;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * Log of a user's answer attempt (to be expanded).
 */
@Entity
@Table(name = "attempt_logs")
@Getter
@Setter
public class AttemptLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;

    private Long questionId;

    private Boolean correct;

    private Instant attemptedAt;
}
