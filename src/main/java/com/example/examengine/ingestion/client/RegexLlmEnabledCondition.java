package com.example.examengine.ingestion.client;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * Active when Gemini LLM is not active: {@code llm.provider=regex}, unset, or {@code gemini} without an API key.
 */
public class RegexLlmEnabledCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return !GeminiLlmEnabledCondition.isGeminiEnabled(context.getEnvironment());
    }
}
