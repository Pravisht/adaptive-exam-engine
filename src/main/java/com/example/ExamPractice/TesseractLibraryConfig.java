package com.example.ExamPractice;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;

/**
 * Configuration to ensure Tesseract native library can be found.
 */
@Configuration
public class TesseractLibraryConfig {

    private static final Logger log = LoggerFactory.getLogger(TesseractLibraryConfig.class);

    @Value("${ocr.library.path:/opt/homebrew/lib}")
    private String libraryPath;

    @PostConstruct
    public void configureLibraryPath() {
        String currentLibraryPath = System.getProperty("java.library.path", "");
        String newLibraryPath = currentLibraryPath.isEmpty() 
            ? libraryPath 
            : currentLibraryPath + File.pathSeparator + libraryPath;
        
        System.setProperty("java.library.path", newLibraryPath);
        log.info("Configured java.library.path to include: {}", libraryPath);
    }
}

