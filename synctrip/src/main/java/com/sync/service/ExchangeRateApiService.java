package com.sync.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.sync.config.ExchangeRateApiProperties;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ExchangeRateApiService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateApiService.class);

    private final RestTemplate restTemplate;
    private final ExchangeRateApiProperties properties;

    public ExchangeRateApiService(RestTemplate restTemplate, ExchangeRateApiProperties properties) {
        this.restTemplate = restTemplate;
        this.properties = properties;
    }

    // baseCurrency 기준 모든 환율 조회 (1 baseCurrency = X other)
    public Map<String, BigDecimal> fetchRates(String baseCurrency) {
        String url = properties.baseUrl() + "/v6/" + properties.apiKey() + "/latest/" + baseCurrency;

        try {
            log.info("ExchangeRate-API 호출: base={}", baseCurrency);
            ResponseEntity<JsonNode> response = restTemplate.getForEntity(url, JsonNode.class);

            JsonNode body = response.getBody();
            if (body == null || !"success".equals(body.path("result").asText())) {
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "환율 API 응답이 올바르지 않습니다.");
            }

            Map<String, BigDecimal> rates = new HashMap<>();
            JsonNode conversionRates = body.path("conversion_rates");
            conversionRates.fields().forEachRemaining(entry ->
                    rates.put(entry.getKey(), new BigDecimal(entry.getValue().asText()))
            );
            return rates;

        } catch (HttpStatusCodeException ex) {
            log.error("ExchangeRate-API 오류. Status: {}, Body: {}", ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "환율 API 연동 실패: " + ex.getStatusCode(), ex);
        }
    }
}
