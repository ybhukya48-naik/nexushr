package com.zidio.nexushr.service;

import com.zidio.nexushr.domain.Employee;
import com.zidio.nexushr.domain.PerformanceReview;
import com.zidio.nexushr.repository.PerformanceReviewRepository;
import com.zidio.nexushr.web.dto.AiInsightResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.ai.chat.prompt.UserPromptTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class AiInsightService {

    private final PerformanceReviewRepository performanceReviewRepository;
    private final ChatClient.Builder chatClientBuilder;
    private ChatClient chatClient;

    public AiInsightService(PerformanceReviewRepository performanceReviewRepository,
                            ChatClient.Builder chatClientBuilder) {
        this.performanceReviewRepository = performanceReviewRepository;
        this.chatClientBuilder = chatClientBuilder;
        this.chatClient = chatClientBuilder.build();
    }

    public AiInsightResponse estimateAttrition(Employee employee) {
        List<PerformanceReview> reviews = performanceReviewRepository.findAll().stream()
                .filter(r -> r.getEmployee().getId().equals(employee.getId()))
                .sorted(Comparator.comparing(PerformanceReview::getReviewDate).reversed())
                .toList();

        int latestScore = reviews.isEmpty() ? 65 : reviews.get(0).getScore();
        double baseRisk = 0.25;

        if (latestScore < 60) {
            baseRisk += 0.35;
        } else if (latestScore < 75) {
            baseRisk += 0.2;
        } else {
            baseRisk -= 0.1;
        }

        if (!employee.isActive()) {
            baseRisk = 1.0;
        }

        double normalized = Math.max(0.05, Math.min(0.95, baseRisk));
        String band = normalized > 0.7 ? "HIGH" : normalized > 0.45 ? "MEDIUM" : "LOW";

        // Try to get AI-generated recommendation if possible, otherwise fall back
        String recommendation;
        try {
            String prompt = String.format("""
                    You are an HR analytics expert. Based on the following employee data, provide a concise (max 2 sentences) retention recommendation:
                    - Employee Name: %s
                    - Role: %s
                    - Department: %s
                    - Latest Performance Score: %d/100
                    - Attrition Risk Band: %s
                    """, employee.getFullName(), employee.getRoleType(), employee.getDepartment(), latestScore, band);
            
            recommendation = chatClient.prompt().user(prompt).call().content();
            if (recommendation == null || recommendation.isBlank()) {
                throw new Exception("Empty AI response");
            }
        } catch (Exception e) {
            recommendation = switch (band) {
                case "HIGH" -> "Schedule manager 1:1, compensation calibration, and targeted retention plan.";
                case "MEDIUM" -> "Review growth path and assign upskilling roadmap.";
                default -> "Maintain engagement with recognition and progression checkpoints.";
            };
        }

        return new AiInsightResponse(employee.getId(), normalized, band, recommendation);
    }
}
