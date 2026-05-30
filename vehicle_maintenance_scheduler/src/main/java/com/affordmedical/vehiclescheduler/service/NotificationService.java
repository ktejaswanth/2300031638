package com.affordmedical.vehiclescheduler.service;

import com.affordmedical.vehiclescheduler.dto.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import logging_middleware.LoggerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Service
public class NotificationService {

    private static final String NOTIFICATIONS_URL = "http://4.224.186.213/evaluation-service/notifications";
    private static final String BEARER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJNYXBDbGFpbXMiOnsiYXVkIjoiaHR0cDovLzIwLjI0NC41Ni4xNDQvZXZhbHVhdGlvbi1zZXJ2aWNlIiwiZW1haWwiOiJrdGVqYXN3YW50aEBnbWFpbC5jb20iLCJleHAiOjE3ODAxMjcyODksImlhdCI6MTc4MDEyNjM4OSwiaXNzIjoiQWZmb3JkIE1lZGljYWwgVGVjaG5vbG9naWVzIFByaXZhdGUgTGltaXRlZCIsImp0aSI6IjY0OTU3MGQ2LTg0Y2YtNDNlZC1iNGJlLTNmY2QwZThlNTNhYiIsImxvY2FsZSI6ImVuLUlOIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwic3ViIjoiY2FjOTc4MzItYTk3MS00ZDYwLWI1M2EtYTJmZmFiNDcwYTY5In0sImVtYWlsIjoia3RlamFzd2FudGhAZ21haWwuY29tIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwicm9sbE5vIjoiMjMwMDAzMTYzOCIsImFjY2Vzc0NvZGUiOiJBdnJBQUsiLCJjbGllbnRJRCI6ImNhYzk3ODMyLWE5NzEtNGQ2MC1iNTNhLWEyZmZhYjQ3MGE2OSIsImNsaWVudFNlY3JldCI6InlwZHFuV0RIcnRqQUtOcHMifQ.imciygo2RVkMBB7uhymauVgiD0ZuyELYvmdCAmnbq8k";

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LoggerService loggerService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<Notification> fetchAllNotifications() {
        loggerService.log("backend", "info", "service", "NotificationService: Fetching campus notifications");
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + BEARER_TOKEN);
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    NOTIFICATIONS_URL,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            JsonNode root = objectMapper.readTree(response.getBody());
            JsonNode notifNode = root.get("notifications");
            List<Notification> notifications = new ArrayList<>();

            if (notifNode != null && notifNode.isArray()) {
                for (JsonNode node : notifNode) {
                    Notification n = objectMapper.treeToValue(node, Notification.class);
                    notifications.add(n);
                }
            }

            loggerService.log("backend", "info", "service", "NotificationService: Query completed. Size: " + notifications.size());
            return notifications;
        } catch (Exception e) {
            loggerService.log("backend", "error", "service", "NotificationService: Query failed: " + e.getMessage());
            throw new RuntimeException("Error querying evaluation notifications: " + e.getMessage(), e);
        }
    }

    public List<Notification> filterPriorityNotifications(List<Notification> allNotifications) {
        loggerService.log("backend", "info", "service", "NotificationService: Initiating Min-Heap selection");

        // Min-Heap custom comparator: ranks lowest priority first (ascending)
        Comparator<Notification> minHeapComparator = (a, b) -> {
            if (a.getPriorityWeight() != b.getPriorityWeight()) {
                return Integer.compare(a.getPriorityWeight(), b.getPriorityWeight());
            }
            return a.parseTimestamp().compareTo(b.parseTimestamp());
        };

        PriorityQueue<Notification> minHeap = new PriorityQueue<>(11, minHeapComparator);

        for (Notification n : allNotifications) {
            minHeap.offer(n);
            if (minHeap.size() > 10) {
                Notification evicted = minHeap.poll();
                // Substring safe handling
                String logId = (evicted != null && evicted.getId() != null && evicted.getId().length() > 8) 
                        ? evicted.getId().substring(0, 8) 
                        : "unknown";
                loggerService.log("backend", "debug", "service", "Evicted lower priority: " + logId);
            }
        }

        List<Notification> topTen = new ArrayList<>(minHeap);
        // Sort topTen in descending order (highest priority first) for presentation
        topTen.sort((a, b) -> {
            if (a.getPriorityWeight() != b.getPriorityWeight()) {
                return Integer.compare(b.getPriorityWeight(), a.getPriorityWeight());
            }
            return b.parseTimestamp().compareTo(a.parseTimestamp());
        });

        loggerService.log("backend", "info", "service", "NotificationService: Heap sorting finished");
        return topTen;
    }
}
