package com.example.examengine.attempt;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AttemptRepository extends JpaRepository<AttemptLog, Long> {
}
