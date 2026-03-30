package com.example.examengine.ingestion.client;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * True when {@code llm.provider=gemini} and {@code gemini.api.key} is non-blank
 * (including resolution from {@code GEMINI_API_KEY}).
 */
public class GeminiLlmEnabledCondition implements Condition {

    static boolean isGeminiEnabled(Environment env) {
        String provider = env.getProperty("llm.provider", "");
        if (!"gemini".equalsIgnoreCase(provider.trim())) {
            return false;
        }
        String key = env.getProperty("gemini.api.key");
        return key != null && !key.isBlank();
    }

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return isGeminiEnabled(context.getEnvironment());
    }
}
