package com.affordmedical.vehiclescheduler.controller;

import com.affordmedical.vehiclescheduler.dto.Depot;
import com.affordmedical.vehiclescheduler.dto.ScheduleResponse;
import com.affordmedical.vehiclescheduler.dto.Vehicle;
import com.affordmedical.vehiclescheduler.service.SchedulerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;
import logging_middleware.LoggerService;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ScheduleController {

    @Autowired
    private SchedulerService schedulerService;

    @Autowired
    private LoggerService loggerService;

    @GetMapping("/schedule")
    public ResponseEntity<List<ScheduleResponse>> getOptimalSchedule() {
        loggerService.log("backend", "info", "controller", "ScheduleController: GET /api/schedule endpoint called");
        List<ScheduleResponse> schedules = schedulerService.generateOptimalSchedules();
        loggerService.log("backend", "info", "controller", "ScheduleController: GET /api/schedule succeeded, returning results");
        return ResponseEntity.ok(schedules);
    }

    @GetMapping("/depots")
    public ResponseEntity<List<Depot>> getDepots() {
        loggerService.log("backend", "info", "controller", "ScheduleController: GET /api/depots endpoint called");
        List<Depot> depots = schedulerService.fetchDepots();
        loggerService.log("backend", "info", "controller", "ScheduleController: GET /api/depots succeeded");
        return ResponseEntity.ok(depots);
    }

    @GetMapping("/vehicles")
    public ResponseEntity<List<Vehicle>> getVehicles() {
        loggerService.log("backend", "info", "controller", "ScheduleController: GET /api/vehicles endpoint called");
        List<Vehicle> vehicles = schedulerService.fetchVehicles();
        loggerService.log("backend", "info", "controller", "ScheduleController: GET /api/vehicles succeeded");
        return ResponseEntity.ok(vehicles);
    }
}
