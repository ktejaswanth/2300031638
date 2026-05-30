package com.affordmedical.vehiclescheduler.service;

import logging_middleware.LogClient;
import logging_middleware.LoggerService;
import org.springframework.stereotype.Service;

@Service
public class LoggerServiceImpl extends LogClient implements LoggerService {
    // Inherits all JDK HttpClient asynchronous log posting capabilities from the standalone LogClient
}
