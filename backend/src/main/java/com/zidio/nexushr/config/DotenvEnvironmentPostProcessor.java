package com.zidio.nexushr.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

public class DotenvEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String DOTENV_FILE = ".env";
    private static final String DOTENV_PROPERTY_SOURCE = "dotenvProperties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Path dotenvPath = Path.of(DOTENV_FILE);
        if (!Files.exists(dotenvPath)) {
            return;
        }

        Properties properties = loadDotenvProperties(dotenvPath);
        if (properties.isEmpty()) {
            return;
        }

        PropertiesPropertySource propertySource = new PropertiesPropertySource(DOTENV_PROPERTY_SOURCE, properties);
        if (environment.getPropertySources().contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addBefore(
                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                    propertySource
            );
            return;
        }

        environment.getPropertySources().addFirst(propertySource);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private Properties loadDotenvProperties(Path dotenvPath) {
        Properties properties = new Properties();

        try {
            for (String line : Files.readAllLines(dotenvPath, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                int separator = trimmed.indexOf('=');
                if (separator <= 0) {
                    continue;
                }

                String key = trimmed.substring(0, separator).trim();
                String value = trimmed.substring(separator + 1).trim();
                properties.setProperty(key, stripWrappingQuotes(value));
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load .env configuration from " + dotenvPath.toAbsolutePath(), exception);
        }

        return properties;
    }

    private String stripWrappingQuotes(String value) {
        if (value.length() < 2) {
            return value;
        }

        char first = value.charAt(0);
        char last = value.charAt(value.length() - 1);
        if ((first == '"' && last == '"') || (first == '\'' && last == '\'')) {
            return value.substring(1, value.length() - 1);
        }

        return value;
    }
}
