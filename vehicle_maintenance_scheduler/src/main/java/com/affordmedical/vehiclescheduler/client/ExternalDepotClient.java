package com.affordmedical.vehiclescheduler.client;

import com.affordmedical.vehiclescheduler.dto.Depot;
import com.affordmedical.vehiclescheduler.dto.Vehicle;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.util.ArrayList;
import java.util.List;
import logging_middleware.LoggerService;

@Component
public class ExternalDepotClient {

    private static final String DEPOTS_URL = "http://4.224.186.213/evaluation-service/depots";
    private static final String VEHICLES_URL = "http://4.224.186.213/evaluation-service/vehicles";
    private static final String BEARER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJNYXBDbGFpbXMiOnsiYXVkIjoiaHR0cDovLzIwLjI0NC41Ni4xNDQvZXZhbHVhdGlvbi1zZXJ2aWNlIiwiZW1haWwiOiJrdGVqYXN3YW50aEBnbWFpbC5jb20iLCJleHAiOjE3ODAxMjcyODksImlhdCI6MTc4MDEyNjM4OSwiaXNzIjoiQWZmb3JkIE1lZGljYWwgVGVjaG5vbG9naWVzIFByaXZhdGUgTGltaXRlZCIsImp0aSI6IjY0OTU3MGQ2LTg0Y2YtNDNlZC1iNGJlLTNmY2QwZThlNTNhYiIsImxvY2FsZSI6ImVuLUlOIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwic3ViIjoiY2FjOTc4MzItYTk3MS00ZDYwLWI1M2EtYTJmZmFiNDcwYTY5In0sImVtYWlsIjoia3RlamFzd2FudGhAZ21haWwuY29tIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwicm9sbE5vIjoiMjMwMDAzMTYzOCIsImFjY2Vzc0NvZGUiOiJBdnJBQUsiLCJjbGllbnRJRCI6ImNhYzk3ODMyLWE5NzEtNGQ2MC1iNTNhLWEyZmZhYjQ3MGE2OSIsImNsaWVudFNlY3JldCI6InlwZHFuV0RIcnRqQUtOcHMifQ.imciygo2RVkMBB7uhymauVgiD0ZuyELYvmdCAmnbq8k";
    
    private static final String ACTUAL_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJNYXBDbGFpbXMiOnsiYXVkIjoiaHR0cDovLzIwLjI0NC41Ni4xNDQvZXZhbHVhdGlvbi1zZXJ2aWNlIiwiZW1haWwiOiJrdGVqYXN3YW50aEBnbWFpbC5jb20iLCJleHAiOjE3ODAxMjcyODksImlhdCI6MTc4MDEyNjM4OSwiaXNzIjoiQWZmb3JkIE1lZGljYWwgVGVjaG5vbG9naWVzIFByaXZhdGUgTGltaXRlZCIsImp0aSI6IjY0OTU3MGQ2LTg0Y2YtNDNlZC1iNGJlLTNmY2QwZThlNTNhYiIsImxvY2FsZSI6ImVuLUlOIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwic3ViIjoiY2FjOTc4MzItYTk3MS00ZDYwLWI1M2EtYTJmZmFiNDcwYTY5In0sImVtYWlsIjoia3RlamFzd2FudGhAZ21haWwuY29tIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwicm9sbE5vIjoiMjMwMDAzMTYzOCIsImFjY2Vzc0NvZGUiOiJBdnJBQUsiLCJjbGllbnRJRCI6ImNhYzk3ODMyLWE5NzEtNGQ2MC1iNTNhLWEyZmZhYjQ3MGE2OSIsImNsaWVudFNlY3JldCI6InlwZHFuV0RIcnRqQUtOcHMifQ.imciygo2RVkMBB7uhymauVgiD0ZuyELYvmdCAmnbq8k";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LoggerService loggerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Depot> fetchDepots() {
        loggerService.log("backend", "info", "service", "Depot fetch started from API client");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + ACTUAL_TOKEN);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    DEPOTS_URL,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode depotsNode = root.get("depots");
            List<Depot> depots = new ArrayList<>();

            if (depotsNode != null && depotsNode.isArray()) {
                for (JsonNode node : depotsNode) {
                    Depot depot = objectMapper.treeToValue(node, Depot.class);
                    depots.add(depot);
                }
            }

            loggerService.log("backend", "info", "service", "Depots query completed successfully. Count: " + depots.size());
            return depots;
        } catch (Exception e) {
            loggerService.log("backend", "error", "service", "Depot query failed: " + e.getMessage());
            throw new RuntimeException("Error fetching evaluation depots: " + e.getMessage(), e);
        }
    }

    public List<Vehicle> fetchVehicles() {
        loggerService.log("backend", "info", "service", "Vehicle fetch started from API client");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + ACTUAL_TOKEN);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    VEHICLES_URL,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode vehiclesNode = root.get("vehicles");
            List<Vehicle> vehicles = new ArrayList<>();

            if (vehiclesNode != null && vehiclesNode.isArray()) {
                for (JsonNode node : vehiclesNode) {
                    Vehicle vehicle = objectMapper.treeToValue(node, Vehicle.class);
                    vehicles.add(vehicle);
                }
            }

            loggerService.log("backend", "info", "service", "Vehicles query completed successfully. Count: " + vehicles.size());
            return vehicles;
        } catch (Exception e) {
            loggerService.log("backend", "error", "service", "Vehicle query failed: " + e.getMessage());
            throw new RuntimeException("Error fetching evaluation vehicles: " + e.getMessage(), e);
        }
    }
}
