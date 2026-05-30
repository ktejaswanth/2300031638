package logging_middleware;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;

public class LogClient implements LoggerService {

    private static final String EVALUATION_LOG_URL = "http://4.224.186.213/evaluation-service/logs";
    private static final String BEARER_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJNYXBDbGFpbXMiOnsiYXVkIjoiaHR0cDovLzIwLjI0NC41Ni4xNDQvZXZhbHVhdGlvbi1zZXJ2aWNlIiwiZW1haWwiOiJrdGVqYXN3YW50aEBnbWFpbC5jb20iLCJleHAiOjE3ODAxMjQ3MDAsImlhdCI6MTc4MDEyMzgwMCwiaXNzIjoiQWZmb3JkIE1lZGljYWwgVGVjaG5vbG9naWVzIFByaXZhdGUgTGltaXRlZCIsImp0aSI6ImZhZTUwMmE4LTcxM2MtNGNiNC04NmQ3LTI0ZjgyNDZkOGVlZiIsImxvY2FsZSI6ImVuLUlOIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwic3ViIjoiY2FjOTc4MzItYTk3MS00ZDYwLWI1M2EtYTJmZmFiNDcwYTY5In0sImVtYWlsIjoia3RlamFzd2FudGhAZ21haWwuY29tIiwibmFtZSI6ImtvbmRhdmV0aSB0ZWphc3dhbnRoIiwicm9sbE5vIjoiMjMwMDAzMTYzOCIsImFjY2Vzc0NvZGUiOiJBdnJBQUsiLCJjbGllbnRJRCI6ImNhYzk3ODMyLWE5NzEtNGQ2MC1iNTNhLWEyZmZhYjQ3MGE2OSIsImNsaWVudFNlY3JldCI6InlwZHFuV0RIcnRqQUtOcHMifQ.hTgpH9f8nyxWrOx3mMceIcScR0bBffkvQSKMtDAqUfc";

    private static final List<String> VALID_STACKS = Arrays.asList("backend", "frontend");
    private static final List<String> VALID_LEVELS = Arrays.asList("debug", "info", "warn", "error", "fatal");

    private static final List<String> VALID_BACKEND_PACKAGES = Arrays.asList("cache", "controller", "cron_job", "db", "domain", "handler", "repository", "route", "service");
    private static final List<String> VALID_FRONTEND_PACKAGES = Arrays.asList("api", "component", "hook", "page", "state", "style");
    private static final List<String> VALID_COMMON_PACKAGES = Arrays.asList("auth", "config", "middleware", "utils");

    private final HttpClient httpClient;

    public LogClient() {
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public void log(String stack, String level, String packageName, String message) {
        if (stack == null || !VALID_STACKS.contains(stack.toLowerCase())) {
            System.err.println("[LogClient ERROR] Invalid stack: " + stack);
            return;
        }

        if (level == null || !VALID_LEVELS.contains(level.toLowerCase())) {
            System.err.println("[LogClient ERROR] Invalid level: " + level);
            return;
        }

        String stackLower = stack.toLowerCase();
        String levelLower = level.toLowerCase();
        String pkgLower = packageName != null ? packageName.toLowerCase() : "";

        boolean isCommon = VALID_COMMON_PACKAGES.contains(pkgLower);
        boolean isBackendValid = "backend".equals(stackLower) && VALID_BACKEND_PACKAGES.contains(pkgLower);
        boolean isFrontendValid = "frontend".equals(stackLower) && VALID_FRONTEND_PACKAGES.contains(pkgLower);

        if (!isCommon && !isBackendValid && !isFrontendValid) {
            System.err.printf("[LogClient ERROR] Invalid package \"%s\" for stack \"%s\".%n", packageName, stack);
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        System.out.printf("[%s] [%s] [%s] [%s] - %s%n", 
                timestamp, 
                stackLower.toUpperCase(), 
                levelLower.toUpperCase(), 
                pkgLower, 
                message
        );

        try {
            String truncatedMessage = message;
            if (truncatedMessage != null && truncatedMessage.length() > 48) {
                truncatedMessage = truncatedMessage.substring(0, 45) + "...";
            }

            String jsonPayload = String.format(
                    "{\"stack\":\"%s\",\"level\":\"%s\",\"package\":\"%s\",\"message\":\"%s\"}",
                    escapeJson(stackLower),
                    escapeJson(levelLower),
                    escapeJson(pkgLower),
                    escapeJson(truncatedMessage)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(EVALUATION_LOG_URL))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + BEARER_TOKEN)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 300) {
                            System.err.printf("[LogClient WARNING] Log server returned status %d: %s%n", 
                                    response.statusCode(), response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        System.err.println("[LogClient ERROR] Remote post failed: " + ex.getMessage());
                        return null;
                    });

        } catch (Exception e) {
            System.err.println("[LogClient ERROR] Exception during log post: " + e.getMessage());
        }
    }

    private String escapeJson(String input) {
        if (input == null) return "";
        return input.replace("\\", "\\\\")
                    .replace("\"", "\\\"")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r")
                    .replace("\t", "\\t");
    }
}
