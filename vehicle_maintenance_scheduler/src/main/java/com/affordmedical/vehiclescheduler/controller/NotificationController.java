package com.affordmedical.vehiclescheduler.controller;

import com.affordmedical.vehiclescheduler.dto.Notification;
import com.affordmedical.vehiclescheduler.service.NotificationService;
import logging_middleware.LoggerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private LoggerService loggerService;

    @GetMapping("/priority")
    public List<Notification> getPriorityNotifications() {
        loggerService.log("backend", "info", "controller", "NotificationController: GET /api/notifications/priority called");
        
        List<Notification> all = notificationService.fetchAllNotifications();
        List<Notification> priorityTopTen = notificationService.filterPriorityNotifications(all);

        loggerService.log("backend", "info", "controller", "NotificationController: GET /priority succeeded, returning top 10");
        return priorityTopTen;
    }
}
