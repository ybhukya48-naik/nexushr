package com.zidio.nexushr.service.email;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ResendEmailService {

    private final RestClient restClient;

    public ResendEmailService() {
        String apiKey = System.getenv("RESEND_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "RESEND_API_KEY environment variable is not configured"
            );
        }

        this.restClient = RestClient.builder()
                .baseUrl("https://api.resend.com")
                .defaultHeader(
                        "Authorization",
                        "Bearer " + apiKey
                )
                .defaultHeader(
                        "Content-Type",
                        MediaType.APPLICATION_JSON_VALUE
                )
                .build();
    }

    public String sendTextEmail(
            String to,
            String subject,
            String message) {

        String from = System.getenv(
                "CYOND_EMAIL_FROM"
        );

        if (from == null || from.isBlank()) {
            from = "Cyond HR <onboarding@resend.dev>";
        }

        Map<String, Object> request = Map.of(
                "from", from,
                "to", List.of(to),
                "subject", subject,
                "text", message
        );

        Map<?, ?> response = restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("id") == null) {
            throw new IllegalStateException(
                    "Resend did not return an email ID"
            );
        }

        return response.get("id").toString();
    }

    public String sendHtmlEmail(
            String to,
            String subject,
            String html) {

        String from = System.getenv(
                "CYOND_EMAIL_FROM"
        );

        if (from == null || from.isBlank()) {
            from = "Cyond HR <onboarding@resend.dev>";
        }

        Map<String, Object> request = Map.of(
                "from", from,
                "to", List.of(to),
                "subject", subject,
                "html", html
        );

        Map<?, ?> response = restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("id") == null) {
            throw new IllegalStateException(
                    "Resend did not return an email ID"
            );
        }

        return response.get("id").toString();
    }

    public String sendHtmlEmailWithPdf(
            String to,
            String subject,
            String html,
            byte[] pdfBytes,
            String filename) {

        String from = System.getenv(
                "CYOND_EMAIL_FROM"
        );

        if (from == null || from.isBlank()) {
            from = "Cyond HR <onboarding@resend.dev>";
        }

        String base64Pdf = Base64.getEncoder()
                .encodeToString(pdfBytes);

        Map<String, Object> attachment = Map.of(
                "filename", filename,
                "content", base64Pdf
        );

        Map<String, Object> request = Map.of(
                "from", from,
                "to", List.of(to),
                "subject", subject,
                "html", html,
                "attachments", List.of(attachment)
        );

        Map<?, ?> response = restClient.post()
                .uri("/emails")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(Map.class);

        if (response == null || response.get("id") == null) {
            throw new IllegalStateException(
                    "Resend did not return an email ID"
            );
        }

        return response.get("id").toString();
    }
}
