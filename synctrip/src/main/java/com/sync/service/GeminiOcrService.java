package com.sync.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sync.config.GeminiProperties;
import com.sync.dto.expense.OcrItemResult;
import com.sync.dto.expense.OcrReceiptResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
public class GeminiOcrService {

    private static final Logger log = LoggerFactory.getLogger(GeminiOcrService.class);

    private static final String GENERATE_CONTENT_PATH_TEMPLATE =
            "/v1beta/models/%s:generateContent";

    private static final String OCR_PROMPT =
            "Analyze this receipt image and extract the information in the following JSON format:\n" +
            "{\n" +
            "  \"storeName\": \"name of the store or null if not visible\",\n" +
            "  \"currency\": \"3-letter ISO currency code (KRW, USD, JPY, EUR, THB, etc.)\",\n" +
            "  \"total\": <total amount as a number>,\n" +
            "  \"items\": [\n" +
            "    {\n" +
            "      \"itemNameOriginal\": \"item name exactly as written on the receipt\",\n" +
            "      \"itemNameKo\": \"Korean translation of the item name (if already Korean, same as itemNameOriginal)\",\n" +
            "      \"amount\": <amount as a number>\n" +
            "    }\n" +
            "  ]\n" +
            "}\n" +
            "Rules:\n" +
            "- itemNameOriginal must preserve the original language and characters (Japanese, Chinese, English, etc.)\n" +
            "- itemNameKo must always be in Korean regardless of the original language\n" +
            "- If the receipt mixes multiple languages, handle each item independently\n" +
            "- Return ONLY the JSON with no explanation.";

    private final RestTemplate geminiRestTemplate;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiOcrService(@Qualifier("geminiRestTemplate") RestTemplate geminiRestTemplate,
                             GeminiProperties properties,
                             ObjectMapper objectMapper) {
        this.geminiRestTemplate = geminiRestTemplate;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public OcrReceiptResponse ocr(MultipartFile imageFile) {
        byte[] imageBytes;
        try {
            imageBytes = imageFile.getBytes();
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지 파일을 읽을 수 없습니다.", e);
        }

        String base64Image = Base64.getEncoder().encodeToString(imageBytes);
        String mimeType = imageFile.getContentType() != null ? imageFile.getContentType() : "image/jpeg";

        Map<String, Object> requestBody = buildRequestBody(base64Image, mimeType);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String path = String.format(GENERATE_CONTENT_PATH_TEMPLATE, properties.model());
        String url = properties.baseUrl() + path + "?key=" + properties.apiKey();

        try {
            log.info("Gemini OCR 호출: fileName={}, size={}bytes, mimeType={}",
                    imageFile.getOriginalFilename(), imageBytes.length, mimeType);

            ResponseEntity<JsonNode> response = geminiRestTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(requestBody, headers), JsonNode.class);

            JsonNode body = response.getBody();
            if (body == null) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini API 응답이 비어 있습니다.");
            }

            return parseGeminiResponse(body);

        } catch (HttpStatusCodeException ex) {
            log.error("Gemini API 오류. Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Gemini API 연동 실패: " + ex.getStatusCode(), ex);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> buildRequestBody(String base64Image, String mimeType) {
        Map<String, Object> textPart = Map.of("text", OCR_PROMPT);
        Map<String, Object> inlineData = Map.of("mimeType", mimeType, "data", base64Image);
        Map<String, Object> imagePart = Map.of("inlineData", inlineData);
        Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
        Map<String, Object> generationConfig = Map.of("responseMimeType", "application/json");
        return Map.of("contents", List.of(content), "generationConfig", generationConfig);
    }

    private OcrReceiptResponse parseGeminiResponse(JsonNode responseBody) {
        JsonNode textNode = responseBody
                .path("candidates").get(0)
                .path("content")
                .path("parts").get(0)
                .path("text");

        if (textNode == null || textNode.isMissingNode()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Gemini 응답에서 텍스트를 찾을 수 없습니다.");
        }

        String rawJson = textNode.asText();
        log.debug("Gemini OCR 원본 응답: {}", rawJson);

        try {
            JsonNode parsed = objectMapper.readTree(rawJson);

            String storeName = parsed.path("storeName").isNull() ? null : parsed.path("storeName").asText(null);
            String currency = parsed.path("currency").asText("KRW");
            BigDecimal total = new BigDecimal(parsed.path("total").asText("0"));

            List<OcrItemResult> items = new ArrayList<>();
            JsonNode itemsNode = parsed.path("items");
            if (itemsNode.isArray()) {
                for (JsonNode item : itemsNode) {
                    String itemNameOriginal = item.path("itemNameOriginal").asText();
                    String itemNameKo = item.path("itemNameKo").asText(itemNameOriginal);
                    BigDecimal amount = new BigDecimal(item.path("amount").asText("0"));
                    items.add(new OcrItemResult(itemNameOriginal, itemNameKo, amount));
                }
            }

            return new OcrReceiptResponse(storeName, currency, total, items, rawJson);

        } catch (JsonProcessingException | NumberFormatException e) {
            log.error("Gemini 응답 파싱 실패: {}", rawJson, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "OCR 결과 파싱에 실패했습니다.");
        }
    }
}
