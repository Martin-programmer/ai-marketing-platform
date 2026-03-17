package com.amp.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.UUID;

@Component
public class ClaudeApiClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeApiClient.class);

    private final AiProperties aiProps;
    private final AiPromptLogRepository promptLogRepo;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public ClaudeApiClient(AiProperties aiProps, AiPromptLogRepository promptLogRepo) {
        this.aiProps = aiProps;
        this.promptLogRepo = promptLogRepo;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Send a text message to Claude and get a response.
     */
    public ClaudeResponse sendMessage(String systemPrompt, String userMessage,
                                       String module, UUID agencyId, UUID clientId) {
        return sendMessage(systemPrompt, userMessage, module, agencyId, clientId,
                          aiProps.getAnthropic().getDefaultModel(), aiProps.getAnthropic().getMaxTokens());
    }

    /**
     * Send a text message with specific model and max tokens.
     */
    public ClaudeResponse sendMessage(String systemPrompt, String userMessage,
                                       String module, UUID agencyId, UUID clientId,
                                       String model, int maxTokens) {
        long start = System.currentTimeMillis();
        AiPromptLog promptLog = new AiPromptLog();
        promptLog.setModule(module);
        promptLog.setModel(model);
        promptLog.setAgencyId(agencyId);
        promptLog.setClientId(clientId);
        promptLog.setCreatedAt(OffsetDateTime.now());

        try {
            String apiKey = aiProps.getAnthropic().getApiKey();
            if (apiKey == null || apiKey.isBlank() || "placeholder".equals(apiKey)) {
                throw new IllegalStateException("Anthropic API key is not configured");
            }

            // Build request body
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", model);
            body.put("max_tokens", maxTokens);

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                body.put("system", systemPrompt);
            }

            ArrayNode messages = body.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            // HTTP headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", aiProps.getAnthropic().getApiVersion());

            // Send request
            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            String url = aiProps.getAnthropic().getApiBaseUrl() + "/v1/messages";

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);
            JsonNode responseBody = response.getBody();

            // Extract response text
            String responseText = "";
            if (responseBody != null && responseBody.has("content") && responseBody.get("content").isArray()) {
                for (JsonNode block : responseBody.get("content")) {
                    if ("text".equals(block.get("type").asText())) {
                        responseText = block.get("text").asText();
                        break;
                    }
                }
            }

            // Extract usage
            int inputTokens = 0, outputTokens = 0;
            if (responseBody != null && responseBody.has("usage")) {
                JsonNode usage = responseBody.get("usage");
                inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0;
                outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0;
            }

            // Calculate cost
            BigDecimal cost = calculateCost(model, inputTokens, outputTokens);

            long durationMs = System.currentTimeMillis() - start;

            // Log
            promptLog.setPromptTokens(inputTokens);
            promptLog.setCompletionTokens(outputTokens);
            promptLog.setTotalTokens(inputTokens + outputTokens);
            promptLog.setCostUsd(cost);
            promptLog.setDurationMs((int) durationMs);
            promptLog.setSuccess(true);
            promptLog.setInputText(truncate((systemPrompt != null ? "[SYSTEM] " + systemPrompt + "\n" : "") + userMessage));
            promptLog.setOutputText(truncate(responseText));
            promptLogRepo.save(promptLog);

            log.info("Claude API call [{}]: model={}, tokens={}/{}, cost=${}, duration={}ms",
                    module, model, inputTokens, outputTokens, cost, durationMs);

            return new ClaudeResponse(responseText, inputTokens, outputTokens, cost, durationMs, null);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            promptLog.setDurationMs((int) durationMs);
            promptLog.setSuccess(false);
            promptLog.setErrorMessage(e.getMessage());
            promptLog.setInputText(truncate((systemPrompt != null ? "[SYSTEM] " + systemPrompt + "\n" : "") + userMessage));
            promptLogRepo.save(promptLog);

            log.error("Claude API call [{}] failed: {}", module, e.getMessage());
            return new ClaudeResponse(null, 0, 0, BigDecimal.ZERO, durationMs, e.getMessage());
        }
    }

    /**
     * Send a vision message (image + text) to Claude.
     */
    public ClaudeResponse sendVisionMessage(String systemPrompt, String textMessage,
                                             String imageUrl, String mediaType,
                                             String module, UUID agencyId, UUID clientId) {
        long start = System.currentTimeMillis();
        AiPromptLog promptLog = new AiPromptLog();
        promptLog.setModule(module);
        promptLog.setModel(aiProps.getAnthropic().getDefaultModel());
        promptLog.setAgencyId(agencyId);
        promptLog.setClientId(clientId);
        promptLog.setCreatedAt(OffsetDateTime.now());

        try {
            String apiKey = aiProps.getAnthropic().getApiKey();
            if (apiKey == null || apiKey.isBlank() || "placeholder".equals(apiKey)) {
                throw new IllegalStateException("Anthropic API key is not configured");
            }

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", aiProps.getAnthropic().getDefaultModel());
            body.put("max_tokens", aiProps.getAnthropic().getMaxTokens());

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                body.put("system", systemPrompt);
            }

            ArrayNode messages = body.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");

            ArrayNode content = userMsg.putArray("content");

            // Image block
            ObjectNode imageBlock = content.addObject();
            imageBlock.put("type", "image");
            ObjectNode source = imageBlock.putObject("source");
            source.put("type", "url");
            source.put("url", imageUrl);

            // Text block
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", textMessage);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", aiProps.getAnthropic().getApiVersion());

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            String url = aiProps.getAnthropic().getApiBaseUrl() + "/v1/messages";

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);
            JsonNode responseBody = response.getBody();

            String responseText = "";
            if (responseBody != null && responseBody.has("content") && responseBody.get("content").isArray()) {
                for (JsonNode block : responseBody.get("content")) {
                    if ("text".equals(block.get("type").asText())) {
                        responseText = block.get("text").asText();
                        break;
                    }
                }
            }

            int inputTokens = 0, outputTokens = 0;
            if (responseBody != null && responseBody.has("usage")) {
                JsonNode usage = responseBody.get("usage");
                inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0;
                outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0;
            }

            BigDecimal cost = calculateCost(aiProps.getAnthropic().getDefaultModel(), inputTokens, outputTokens);
            long durationMs = System.currentTimeMillis() - start;

            promptLog.setPromptTokens(inputTokens);
            promptLog.setCompletionTokens(outputTokens);
            promptLog.setTotalTokens(inputTokens + outputTokens);
            promptLog.setCostUsd(cost);
            promptLog.setDurationMs((int) durationMs);
            promptLog.setSuccess(true);
            promptLog.setInputText(truncate((systemPrompt != null ? "[SYSTEM] " + systemPrompt + "\n" : "") + "[IMAGE: " + imageUrl + "]\n" + textMessage));
            promptLog.setOutputText(truncate(responseText));
            promptLogRepo.save(promptLog);

            log.info("Claude Vision API call [{}]: tokens={}/{}, cost=${}, duration={}ms",
                    module, inputTokens, outputTokens, cost, durationMs);

            return new ClaudeResponse(responseText, inputTokens, outputTokens, cost, durationMs, null);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            promptLog.setDurationMs((int) durationMs);
            promptLog.setSuccess(false);
            promptLog.setErrorMessage(e.getMessage());
            promptLog.setInputText(truncate((systemPrompt != null ? "[SYSTEM] " + systemPrompt + "\n" : "") + "[IMAGE: " + imageUrl + "]\n" + textMessage));
            promptLogRepo.save(promptLog);

            log.error("Claude Vision API call [{}] failed: {}", module, e.getMessage());
            return new ClaudeResponse(null, 0, 0, BigDecimal.ZERO, durationMs, e.getMessage());
        }
    }

    /**
     * Send a video message to Claude for analysis.
     * Accepts raw video bytes, encodes as base64, sends to Claude.
     */
    public ClaudeResponse sendVideoMessage(String systemPrompt, String textMessage,
                                            byte[] videoBytes, String mediaType,
                                            String module, UUID agencyId, UUID clientId) {
        long start = System.currentTimeMillis();
        AiPromptLog promptLog = new AiPromptLog();
        promptLog.setModule(module);
        promptLog.setModel(aiProps.getAnthropic().getDefaultModel());
        promptLog.setAgencyId(agencyId);
        promptLog.setClientId(clientId);
        promptLog.setCreatedAt(OffsetDateTime.now());

        try {
            String apiKey = aiProps.getAnthropic().getApiKey();
            if (apiKey == null || apiKey.isBlank() || "placeholder".equals(apiKey)) {
                throw new IllegalStateException("Anthropic API key is not configured");
            }

            String base64Video = Base64.getEncoder().encodeToString(videoBytes);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", aiProps.getAnthropic().getDefaultModel());
            body.put("max_tokens", aiProps.getAnthropic().getMaxTokens());

            if (systemPrompt != null && !systemPrompt.isBlank()) {
                body.put("system", systemPrompt);
            }

            ArrayNode messages = body.putArray("messages");
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");

            ArrayNode content = userMsg.putArray("content");

            // Video block (base64-encoded)
            ObjectNode videoBlock = content.addObject();
            videoBlock.put("type", "video");
            ObjectNode source = videoBlock.putObject("source");
            source.put("type", "base64");
            source.put("media_type", mediaType != null ? mediaType : "video/mp4");
            source.put("data", base64Video);

            // Text block
            ObjectNode textBlock = content.addObject();
            textBlock.put("type", "text");
            textBlock.put("text", textMessage);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("x-api-key", apiKey);
            headers.set("anthropic-version", aiProps.getAnthropic().getApiVersion());

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(body), headers);
            String url = aiProps.getAnthropic().getApiBaseUrl() + "/v1/messages";

            log.info("Sending video to Claude [{}]: {}KB base64, mediaType={}",
                    module, base64Video.length() / 1024, mediaType);

            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.POST, entity, JsonNode.class);
            JsonNode responseBody = response.getBody();

            String responseText = "";
            if (responseBody != null && responseBody.has("content") && responseBody.get("content").isArray()) {
                for (JsonNode block : responseBody.get("content")) {
                    if ("text".equals(block.get("type").asText())) {
                        responseText = block.get("text").asText();
                        break;
                    }
                }
            }

            int inputTokens = 0, outputTokens = 0;
            if (responseBody != null && responseBody.has("usage")) {
                JsonNode usage = responseBody.get("usage");
                inputTokens = usage.has("input_tokens") ? usage.get("input_tokens").asInt() : 0;
                outputTokens = usage.has("output_tokens") ? usage.get("output_tokens").asInt() : 0;
            }

            BigDecimal cost = calculateCost(aiProps.getAnthropic().getDefaultModel(), inputTokens, outputTokens);
            long durationMs = System.currentTimeMillis() - start;

            promptLog.setPromptTokens(inputTokens);
            promptLog.setCompletionTokens(outputTokens);
            promptLog.setTotalTokens(inputTokens + outputTokens);
            promptLog.setCostUsd(cost);
            promptLog.setDurationMs((int) durationMs);
            promptLog.setSuccess(true);
            promptLog.setInputText(truncate((systemPrompt != null ? "[SYSTEM] " + systemPrompt + "\n" : "") + "[VIDEO: " + (mediaType != null ? mediaType : "video/mp4") + "]\n" + textMessage));
            promptLog.setOutputText(truncate(responseText));
            promptLogRepo.save(promptLog);

            log.info("Claude Video API call [{}]: tokens={}/{}, cost=${}, duration={}ms",
                    module, inputTokens, outputTokens, cost, durationMs);

            return new ClaudeResponse(responseText, inputTokens, outputTokens, cost, durationMs, null);

        } catch (Exception e) {
            long durationMs = System.currentTimeMillis() - start;
            promptLog.setDurationMs((int) durationMs);
            promptLog.setSuccess(false);
            promptLog.setErrorMessage(e.getMessage());
            promptLog.setInputText(truncate((systemPrompt != null ? "[SYSTEM] " + systemPrompt + "\n" : "") + "[VIDEO: " + (mediaType != null ? mediaType : "video/mp4") + "]\n" + textMessage));
            promptLogRepo.save(promptLog);

            log.error("Claude Video API call [{}] failed: {}", module, e.getMessage());
            return new ClaudeResponse(null, 0, 0, BigDecimal.ZERO, durationMs, e.getMessage());
        }
    }

    /**
     * Parse JSON from Claude response (strips markdown code fences if present).
     */
    public JsonNode parseJson(String text) {
        if (text == null) return null;
        // Strip markdown code fences
        String cleaned = text.trim();
        if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
        else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
        if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
        cleaned = cleaned.trim();

        try {
            return objectMapper.readTree(cleaned);
        } catch (Exception e) {
            log.warn("Failed to parse Claude response as JSON: {}", e.getMessage());
            return null;
        }
    }

    private static final int MAX_IO_LENGTH = 10_000;

    private static String truncate(String s) {
        return s == null ? null : (s.length() <= MAX_IO_LENGTH ? s : s.substring(0, MAX_IO_LENGTH));
    }

    private BigDecimal calculateCost(String model, int inputTokens, int outputTokens) {
        // Pricing per million tokens
        double inputPricePerM, outputPricePerM;
        if (model.contains("opus")) {
            inputPricePerM = 15.0;
            outputPricePerM = 75.0;
        } else {
            // Sonnet
            inputPricePerM = 3.0;
            outputPricePerM = 15.0;
        }
        double cost = (inputTokens * inputPricePerM / 1_000_000) + (outputTokens * outputPricePerM / 1_000_000);
        return BigDecimal.valueOf(cost).setScale(6, RoundingMode.HALF_UP);
    }

    /** Response record returned from all Claude API calls. */
    public record ClaudeResponse(
        String text,
        int inputTokens,
        int outputTokens,
        BigDecimal cost,
        long durationMs,
        String error
    ) {
        public boolean isSuccess() { return error == null && text != null; }
    }
}
