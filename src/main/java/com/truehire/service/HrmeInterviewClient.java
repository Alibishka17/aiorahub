package com.truehire.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class HrmeInterviewClient {

    private final URI endpoint;
    private final String serviceToken;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public HrmeInterviewClient(@Value("${app.hrme.base-url}") String baseUrl,
                               @Value("${app.hrme.service-token}") String serviceToken,
                               ObjectMapper objectMapper) {
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/api/integrations/aiorahub/interviews");
        this.serviceToken = serviceToken;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    }

    public InterviewSession createInterview(Long applicationId, Long vacancyId,
                                             String candidateEmail, String templateId) {
        if (serviceToken == null || serviceToken.isBlank()) {
            throw new IllegalStateException("HRme integration is not configured");
        }
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "application_id", applicationId,
                    "vacancy_id", vacancyId,
                    "candidate_email", candidateEmail,
                    "template_id", templateId));
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + serviceToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("HRme returned status " + response.statusCode());
            }
            JsonNode data = objectMapper.readTree(response.body()).path("data");
            if (!data.path("interview_id").canConvertToLong() || data.path("interview_url").asText().isBlank()) {
                throw new IllegalStateException("HRme returned an invalid response");
            }
            return new InterviewSession(
                    data.path("interview_id").asLong(),
                    data.path("access_token").asText(),
                    data.path("interview_url").asText());
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read HRme response", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("HRme request was interrupted", ex);
        }
    }

    public record InterviewSession(Long interviewId, String accessToken, String interviewUrl) {}
}
