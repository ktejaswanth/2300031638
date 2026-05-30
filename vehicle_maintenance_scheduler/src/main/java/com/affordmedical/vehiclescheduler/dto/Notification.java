package com.affordmedical.vehiclescheduler.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @JsonProperty("ID")
    private String id;

    @JsonProperty("Type")
    private String type;

    @JsonProperty("Message")
    private String message;

    @JsonProperty("Timestamp")
    private String timestamp;

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
