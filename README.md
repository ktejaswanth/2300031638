# Notification System Design Document

## Project Overview

This project was developed as part of the AffordMed Backend Evaluation. The solution consists of two major backend modules:

1. Vehicle Maintenance Scheduler Microservice
2. Campus Notifications Microservice

The project is implemented using Spring Boot and follows a modular architecture. A reusable Logging Middleware is integrated across all components to provide centralized logging and monitoring.

---

# Logging Middleware

## Purpose

The Logging Middleware was developed as a reusable component to capture important application events and send them to the AffordMed Logging API.

Instead of using normal console logs, all major operations are logged through a common logging service. This helps in monitoring application behavior and debugging issues efficiently.

### Example Logs

* Vehicle scheduling started
* Vehicle data fetched successfully
* Notification created successfully
* Invalid request received
* Database operation failed

### Middleware Usage

The logging middleware is used throughout the application in:

* Controllers
* Service classes
* API client classes
* Exception handlers

This ensures consistent logging across the entire backend application.

---

# Vehicle Maintenance Scheduler Microservice

## Problem Statement

The company manages multiple vehicle maintenance requests every day. Each maintenance task requires a specific amount of mechanic hours and contributes a certain operational impact score.

Every depot has a limited number of mechanic hours available. The objective is to select the best combination of maintenance tasks that maximizes total impact while staying within the available mechanic hours.

---

## APIs Used

### Depots API

GET

http://localhost:8080/api/depots

This API returns depot information.

Example:

```json
{
  "ID": 1,
  "MechanicHours": 60
}
```

### Vehicles API

GET

http://localhost:8080/api/vehicles

This API returns vehicle maintenance tasks.

Example:

```json
{
  "TaskID": "264e638f",
  "Duration": 5,
  "Impact": 10
}
```

---

## Solution Approach

After analyzing the problem, it was identified as a classic 0/1 Knapsack Problem.

Mapping:

| Vehicle Scheduler | Knapsack Problem |
| ----------------- | ---------------- |
| Duration          | Weight           |
| Impact            | Value            |
| Mechanic Hours    | Capacity         |

The algorithm evaluates different combinations of tasks and selects the combination that provides the highest total impact without exceeding the available mechanic hours.

---

## Why Dynamic Programming?

A brute-force solution would require checking every possible combination of tasks, which becomes inefficient as the number of tasks increases.

Dynamic Programming allows us to solve the problem efficiently by storing intermediate results and avoiding repeated calculations.

### Time Complexity

O(N × H)

Where:

* N = Number of maintenance tasks
* H = Available mechanic hours

---

## Output

For each depot, the system calculates:

* Selected tasks
* Total duration used
* Total impact achieved
* Remaining mechanic hours

This provides an optimal maintenance schedule for each depot.

---

# Campus Notifications Microservice

## Objective

The Campus Notifications Microservice is designed to manage and deliver notifications to students.

The system supports different categories of notifications such as:

* Placement Notifications
* Result Notifications
* Event Notifications

Students can view notifications, filter them, and access important updates through a centralized notification platform.

---

## REST APIs

### Create Notification

POST

http://localhost:8082/api/notifications

Creates a new notification.

---

### Get All Notifications

GET

http://localhost:8082/api/notifications

Returns all available notifications.

---

### Get Notifications by Student

GET

http://localhost:8082/api/notifications?studentId={id}

Returns notifications belonging to a specific student.

---

### Delete Notification

DELETE

http://localhost:8082/api/notifications/{id}

Removes a notification from the system.

---

### Priority Notifications

GET

http://localhost:8082/api/notifications/priority

Returns the highest priority notifications for display in the priority inbox.

---

# Database Design

The notification system uses a relational database structure.

## Students Table

Stores student information.

Fields:

* id
* name
* email

---

## Notifications Table

Stores notification records.

Fields:

* id
* student_id
* type
* message
* created_at
* is_read

---

## Notification Types

Stores notification categories.

Examples:

* Placement
* Result
* Event

---

# Stage 3 - Database Scaling and Optimization

## Problem

As the system grows, the database must handle:

* 50,000 students
* 500,000 notifications

Without optimization, retrieving notifications becomes slower because the database must scan a large number of records.

---

## Indexing Strategy

Indexes are added to frequently searched columns.

```sql
CREATE INDEX idx_student
ON notifications(student_id);

CREATE INDEX idx_type
ON notifications(type);

CREATE INDEX idx_read
ON notifications(is_read);
```

### Benefits

* Faster searches
* Faster filtering
* Reduced query execution time
* Better overall performance

---

## Pagination

Returning thousands of notifications in a single response is inefficient.

To solve this problem, pagination is used.

Example:

```sql
SELECT *
FROM notifications
LIMIT 20 OFFSET 0;
```

### Benefits

* Lower memory consumption
* Faster API responses
* Better user experience
* Improved scalability

---

# Stage 6 - Priority Inbox

## Requirement

The product team requested a Priority Inbox that displays only the most important unread notifications.

Priority should be determined using:

1. Notification Type
2. Recency

### Priority Weights

| Type      | Weight |
| --------- | ------ |
| Placement | 3      |
| Result    | 2      |
| Event     | 1      |

If two notifications have the same weight, the newer notification receives higher priority.

---

## Solution Approach

A Priority Queue (Min Heap) was selected for this implementation.

The heap continuously maintains only the Top 10 highest-priority notifications.

When a new notification arrives:

1. The notification is inserted into the heap.
2. If the heap size exceeds 10, the lowest-priority notification is removed.
3. The heap always contains only the most important notifications.

---

## Why Priority Queue?

Sorting all notifications repeatedly becomes expensive when the dataset grows.

A Priority Queue provides a more efficient solution.

### Complexity Comparison

Full Sorting:

O(N log N)

Priority Queue:

O(N log K)

Where:

* N = Total notifications
* K = 10

Since K remains constant, the solution scales efficiently even for very large datasets.

---

# Project Architecture

```text
Client
   |
   v
Spring Boot Application
   |
   +---- Logging Middleware
   |
   +---- Vehicle Maintenance Scheduler
   |
   +---- Campus Notifications Service
   |
   v
Database / External APIs
```

The Logging Middleware is shared by all modules and records important application events throughout the system.

---

# Conclusion

This project demonstrates backend development concepts including API integration, reusable middleware design, dynamic programming, database optimization, and scalable notification processing.

The Vehicle Maintenance Scheduler uses Dynamic Programming to generate optimal maintenance schedules, while the Campus Notifications Microservice provides efficient notification management and priority-based notification retrieval.

The final solution is modular, maintainable, scalable, and capable of handling increasing amounts of data and user activity efficiently.
