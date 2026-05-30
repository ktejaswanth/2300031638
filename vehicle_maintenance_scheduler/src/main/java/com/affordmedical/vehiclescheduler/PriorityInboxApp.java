package com.affordmedical.vehiclescheduler;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import logging_middleware.LoggerService;
import logging_middleware.LogClient;

public class PriorityInboxApp {

    private static final String NOTIFICATIONS_URL = "http://4.224.186.213/evaluation-service/notifications";
    private static final String BEARER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJNYXBDbGFpbXMiOnsiYXVkIjoiaHR0cDovLzIwLjI0NC41Ni4xNDQvZXZhbHVhdGlvbi1zZXJ2aWNlIiwiZW1haWwiOiJrdGVqYXN3YW50aEBnbWFpbC5jb20iLCJleHAiOjE3ODAxMjcyODksImlhdCI6MTc4MDEyNjM4OSwiaXNzIjoiQWZmb3JkIE1lZGljYWwgVGVjaG5vbG9naWVzIFByaXZhdGUgTGltaXRlZCIsImp0aSI6IjY0OTU3MGQ2LTg0Y2YtNDNlZC1iNGJlLTNmY2QwZThlNTNhYiIsImxvY2FsZSI6ImVuLUlOIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwic3ViIjoiY2FjOTc4MzItYTk3MS00ZDYwLWI1M2EtYTJmZmFiNDcwYTY5In0sImVtYWlsIjoia3RlamFzd2FudGhAZ21haWwuY29tIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwicm9sbE5vIjoiMjMwMDAzMTYzOCIsImFjY2Vzc0NvZGUiOiJBdnJBQUsiLCJjbGllbnRJRCI6ImNhYzk3ODMyLWE5NzEtNGQ2MC1iNTNhLWEyZmZhYjQ3MGE2OSIsImNsaWVudFNlY3JldCI6InlwZHFuV0RIcnRqQUtOcHMifQ.imciygo2RVkMBB7uhymauVgiD0ZuyELYvmdCAmnbq8k";

    public static class Notification {
        @JsonProperty("ID")
        public String id;
        @JsonProperty("Type")
        public String type;
        @JsonProperty("Message")
        public String message;
        @JsonProperty("Timestamp")
        public String timestamp;

        public int getPriorityWeight() {
            if (type == null) return 0;
            switch (type) {
                case "Placement": return 3;
                case "Result": return 2;
                case "Event": return 1;
                default: return 0;
            }
        }

        public LocalDateTime parseTimestamp() {
            if (timestamp == null) return LocalDateTime.MIN;
            try {
                return LocalDateTime.parse(timestamp, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                return LocalDateTime.MIN;
            }
        }
    }

    public static void main(String[] args) {
        LoggerService logger = new LogClient();
        logger.log("backend", "info", "utils", "PriorityInboxApp: Starting standalone notification scheduler");

        try {
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(NOTIFICATIONS_URL))
                    .header("Authorization", "Bearer " + BEARER_TOKEN)
                    .GET()
                    .build();

            logger.log("backend", "info", "utils", "PriorityInboxApp: Fetching remote notifications");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                logger.log("backend", "error", "utils", "PriorityInboxApp: Failed to fetch notifications. Status: " + response.statusCode());
                System.exit(1);
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.body());
            JsonNode arrayNode = root.get("notifications");

            List<Notification> notifications = new ArrayList<>();
            if (arrayNode != null && arrayNode.isArray()) {
                for (JsonNode n : arrayNode) {
                    notifications.add(mapper.treeToValue(n, Notification.class));
                }
            }

            logger.log("backend", "info", "utils", "PriorityInboxApp: Successfully parsed " + notifications.size() + " notifications");
            logger.log("backend", "info", "utils", "PriorityInboxApp: Filtering top 10 using O(N log 10) heap sorting");

            // Min-Heap ranking lowest priority first
            Comparator<Notification> minHeapComparator = (a, b) -> {
                if (a.getPriorityWeight() != b.getPriorityWeight()) {
                    return Integer.compare(a.getPriorityWeight(), b.getPriorityWeight());
                }
                return a.parseTimestamp().compareTo(b.parseTimestamp());
            };

            PriorityQueue<Notification> minHeap = new PriorityQueue<>(11, minHeapComparator);

            for (Notification n : notifications) {
                minHeap.offer(n);
                if (minHeap.size() > 10) {
                    minHeap.poll();
                }
            }

            List<Notification> topTen = new ArrayList<>(minHeap);
            // Sort topTen descending (highest priority first)
            topTen.sort((a, b) -> {
                if (a.getPriorityWeight() != b.getPriorityWeight()) {
                    return Integer.compare(b.getPriorityWeight(), a.getPriorityWeight());
                }
                return b.parseTimestamp().compareTo(a.parseTimestamp());
            });

            logger.log("backend", "info", "utils", "PriorityInboxApp: Selection completed successfully");

            System.out.println("\n==================== TOP 10 CAMPUS NOTIFICATIONS ====================");
            System.out.printf("%-38s | %-15s | %-30s | %-20s\n", "Notification ID", "Type (Weight)", "Message", "Timestamp");
            System.out.println("-----------------------------------------------------------------------------------------------------------------");
            for (Notification n : topTen) {
                System.out.printf("%-38s | %-15s | %-30s | %-20s\n", 
                        n.id, 
                        n.type + " (" + n.getPriorityWeight() + ")", 
                        n.message, 
                        n.timestamp
                );
            }
            System.out.println("=================================================================================================================\n");

            // Wait for asynchronous telemetry post requests to terminate before JVM shutdown
            Thread.sleep(2500);

        } catch (Exception e) {
            logger.log("backend", "error", "utils", "PriorityInboxApp: Error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
