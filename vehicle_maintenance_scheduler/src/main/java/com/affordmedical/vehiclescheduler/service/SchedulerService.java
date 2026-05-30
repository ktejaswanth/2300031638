package com.affordmedical.vehiclescheduler.service;

import com.affordmedical.vehiclescheduler.client.ExternalDepotClient;
import com.affordmedical.vehiclescheduler.dto.Depot;
import com.affordmedical.vehiclescheduler.dto.ScheduleResponse;
import com.affordmedical.vehiclescheduler.dto.Vehicle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import logging_middleware.LoggerService;

@Service
public class SchedulerService {

    @Autowired
    private ExternalDepotClient externalDepotClient;

    @Autowired
    private LoggerService loggerService;

    public List<Depot> fetchDepots() {
        return externalDepotClient.fetchDepots();
    }

    public List<Vehicle> fetchVehicles() {
        return externalDepotClient.fetchVehicles();
    }

    /**
     * Executes the 0/1 Knapsack DP solver for all depots and generates the optimal allocations.
     */
    public List<ScheduleResponse> generateOptimalSchedules() {
        loggerService.log("backend", "info", "service", "SchedulerService: Initiating dynamic scheduling optimization");
        
        List<Depot> depots = fetchDepots();
        List<Vehicle> vehicles = fetchVehicles();
        List<ScheduleResponse> responses = new ArrayList<>();

        for (Depot depot : depots) {
            loggerService.log("backend", "info", "service", 
                    String.format("SchedulerService: Solving knapsack for Depot ID: %d with capacity: %d hours", 
                            depot.getId(), depot.getMechanicHours()));

            ScheduleResponse response = solveKnapsack(depot, vehicles);
            responses.add(response);

            loggerService.log("backend", "info", "service", 
                    String.format("SchedulerService: Optimal solution found for Depot ID: %d. Total Impact: %d. Total Hours: %d/%d", 
                            depot.getId(), response.getTotalImpact(), response.getTotalDurationUsed(), depot.getMechanicHours()));
        }

        loggerService.log("backend", "info", "service", "SchedulerService: Optimization completed for all active depots");
        return responses;
    }

    /**
     * Solves the 0/1 Knapsack dynamic programming table.
     */
    private ScheduleResponse solveKnapsack(Depot depot, List<Vehicle> vehicles) {
        int W = depot.getMechanicHours();
        int N = vehicles.size();

        if (W <= 0 || N == 0) {
            return ScheduleResponse.builder()
                    .depotId(depot.getId())
                    .mechanicHours(W)
                    .totalImpact(0)
                    .totalDurationUsed(0)
                    .selectedVehicles(Collections.emptyList())
                    .unselectedVehicles(vehicles)
                    .build();
        }

        // DP table matrix: dp[i][j] stores the max impact score
        int[][] dp = new int[N + 1][W + 1];

        for (int i = 1; i <= N; i++) {
            Vehicle v = vehicles.get(i - 1);
            int w = v.getDuration();
            int val = v.getImpact();
            for (int j = 0; j <= W; j++) {
                if (j >= w) {
                    dp[i][j] = Math.max(dp[i - 1][j], dp[i - 1][j - w] + val);
                } else {
                    dp[i][j] = dp[i - 1][j];
                }
            }
        }

        // Backtrack from matrix cells to partition selected and unselected sets
        List<Vehicle> selected = new ArrayList<>();
        List<Vehicle> unselected = new ArrayList<>();
        int j = W;
        int totalDurationUsed = 0;

        for (int i = N; i > 0; i--) {
            Vehicle v = vehicles.get(i - 1);
            if (dp[i][j] != dp[i - 1][j]) {
                selected.add(v);
                j -= v.getDuration();
                totalDurationUsed += v.getDuration();
            } else {
                unselected.add(v);
            }
        }

        // Reverse backtracked lists to maintain original list layout
        Collections.reverse(selected);
        Collections.reverse(unselected);

        return ScheduleResponse.builder()
                .depotId(depot.getId())
                .mechanicHours(depot.getMechanicHours())
                .totalImpact(dp[N][W])
                .totalDurationUsed(totalDurationUsed)
                .selectedVehicles(selected)
                .unselectedVehicles(unselected)
                .build();
    }
}
