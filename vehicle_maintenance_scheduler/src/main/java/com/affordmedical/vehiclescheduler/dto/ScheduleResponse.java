package com.affordmedical.vehiclescheduler.dto;

import lombok.*;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScheduleResponse {
    private int depotId;
    private int mechanicHours;
    private int totalImpact;
    private int totalDurationUsed;
    private List<Vehicle> selectedVehicles;
    private List<Vehicle> unselectedVehicles;
}
