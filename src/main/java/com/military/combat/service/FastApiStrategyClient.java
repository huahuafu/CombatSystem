package com.military.combat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.military.combat.simulation.commander.CommanderGenerateRequest;
import com.military.combat.simulation.commander.CommanderGenerateResponse;
import com.military.combat.simulation.commander.CommanderStrategyValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class FastApiStrategyClient {
    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${combat.ai.fastapi.enabled:false}")
    private boolean enabled;

    @Value("${combat.ai.fastapi.base-url:http://localhost:8001}")
    private String baseUrl;

    public CommanderGenerateResponse tryGenerate(CommanderGenerateRequest req) {
        if (!enabled) {
            return null;
        }
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("scenarioId", req.getScenarioId());
            body.put("goal", req.getGoal());
            body.put("commanderSide", req.getCommanderSide());
            body.put("commanderRole", req.getCommanderRole());
            body.put("scorePerspective", req.getScorePerspective());
            body.put("seed", req.getSeed());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + "/strategies/generate", entity, String.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                return null;
            }
            CommanderGenerateResponse out = mapper.readValue(response.getBody(), CommanderGenerateResponse.class);
            if (out == null || out.getStrategies() == null || out.getStrategies().size() != 4) {
                return null;
            }
            out.getStrategies().forEach(CommanderStrategyValidator::validate);
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public boolean health() {
        if (!enabled) {
            return false;
        }
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/healthz", String.class);
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
