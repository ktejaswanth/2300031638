# Notification System Design Documentation

This document outlines the architectural blueprints, database performance optimizations, and algorithmic scaling strategies for the centralized student notification system.

---

## 1. STAGE 2 - Current System Architecture

The current architecture provides transactional notification management. It is designed around a traditional decoupled microservices tier with a centralized API gateway routing requests to stateless Spring Boot service layers.

### A. High-Level Architecture Flow

```text
  [ User Client ] 
        ↓
  [ API Gateway ] (Load balancing, rate limiting, and routing)
        ↓
  [ Spring Boot Microservice ] (Business logic, telemetry, and transactional boundaries)
        ↓
  [ MySQL Database ] (Persistent relational data store)
```

---

### B. Relational Database Schema Design

The persistence layer uses three tables representing users, categorized notification templates, and actual historical alert dispatches:

```sql
-- 1. Users Table
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. NotificationTypes Table
CREATE TABLE notification_types (
    id INT AUTO_INCREMENT PRIMARY KEY,
    type_name VARCHAR(50) NOT NULL UNIQUE, -- Placement, Result, Event
    priority_weight INT NOT NULL DEFAULT 1 -- Priority Weight: Placement=3, Result=2, Event=1
);

-- 3. Notifications Table
CREATE TABLE notifications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT NOT NULL,
    notification_type_id INT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    FOREIGN KEY (notification_type_id) REFERENCES notification_types(id)
);
```

---

### C. API Contract Specifications

All API endpoints exchange JSON payloads:

#### 1. Dispatch Notification
* **Endpoint**: `POST /api/notifications`
* **Request Body**:
```json
{
  "userId": 101,
  "notificationTypeId": 2,
  "title": "Exam Results Published",
  "message": "The final semester results have been uploaded."
}
```
* **Success Response (201 Created)**:
```json
{
  "id": 500001,
  "title": "Exam Results Published",
  "message": "The final semester results have been uploaded.",
  "isRead": false,
  "createdAt": "2026-05-30T11:45:00",
  "userId": 101,
  "notificationTypeId": 2
}
```

#### 2. Retrieve Student Notifications
* **Endpoint**: `GET /api/notifications?studentId=101`
* **Success Response (200 OK)**:
```json
[
  {
    "id": 500001,
    "title": "Exam Results Published",
    "message": "The final semester results have been uploaded.",
    "isRead": false,
    "createdAt": "2026-05-30T11:45:00",
    "notificationType": "Result"
  }
]
```

#### 3. Update Notification Status (Read/Unread)
* **Endpoint**: `PUT /api/notifications/{id}`
* **Request Body**:
```json
{
  "isRead": true
}
```
* **Success Response (200 OK)**:
```json
{
  "id": 500001,
  "isRead": true
}
```

#### 4. Delete/Archive Notification
* **Endpoint**: `DELETE /api/notifications/{id}`
* **Success Response (204 No Content)**

---

## 2. STAGE 3 - Scalability & Database Optimization

### A. The Scaling Challenge
When scaling to **50,000 students** and **500,000 notifications**, queries like:
```sql
SELECT * FROM notifications WHERE user_id = 10;
```
experience steep degradation, resulting in multi-second latency spikes.

### B. Root Cause Analysis: Full Table Scan
By default, relational columns (excluding the Primary Key) are unindexed. To resolve the query, MySQL is forced to execute a **Full Table Scan (All Rows Sweep)**:
1. It reads all 500,000 records from the disk.
2. It evaluates the `user_id = 10` filter condition against every single row.
3. This triggers massive **Disk I/O bottlenecks**, thrashing the system memory cache as millions of blocks are constantly paged in and out of RAM. The time complexity is linear: $O(N)$ where $N = 500,000$.

---

### C. Solution 1: B-Tree Indexing

Creating balanced B-Tree search indexes on high-cardinality query columns:

```sql
-- 1. Index for fast student-specific query lookups
CREATE INDEX idx_student ON notifications(user_id);

-- 2. Index for filtering notifications by categorical template types
CREATE INDEX idx_type ON notifications(notification_type_id);

-- 3. Index for isolating unread alerts
CREATE INDEX idx_read ON notifications(is_read);
```

#### B-Tree Index Mechanics
* **How It Works**: A B-Tree index maintains a sorted, self-balancing tree structure of key-pointer values. Instead of traversing 500,000 records sequentially, MySQL traverses tree nodes.
* **Algorithmic Benefit**: Query complexity drops from $O(N)$ linear scans to **$O(\log N)$ binary node traversals**. For 500,000 records:
  $$\log_2(500,000) \approx 19 \text{ comparisons}$$
  This is a **99.99% reduction** in row operations, resolving queries in milliseconds!
* **Trade-Offs**:
  * *Write Overhead*: `INSERT`, `UPDATE`, and `DELETE` queries slightly slow down as the database engine must recalculate and re-balance the B-Tree indexes.
  * *Storage Overhead*: Indexes consume additional disk/RAM space.

---

### D. Solution 2: Offset Pagination

Returning 500,000 notifications in a single payload is a critical failure point (JVM heap exhaust, network congestion, browser rendering freezes). We implement **Pagination**:

```sql
SELECT * 
FROM notifications 
WHERE user_id = 10 
ORDER BY created_at DESC 
LIMIT 20 OFFSET 0;
```

#### Benefits
1. **Low Memory Footprint**: Reduces JVM Heap and MySQL buffer pools by pulling only a page size (e.g. 20 rows) at a time.
2. **Network Efficiency**: Minimized JSON payload weights ensure ultra-fast packet transfers.
3. **Optimized Client DOM**: Browsers render small sets in microseconds, preventing freezes.

---

## 3. STAGE 6 - High-Performance Priority Heap Routing

### A. The Scaling Constraint
We must fetch the **Top 10 Notifications** for a student based on:
1. **Priority Weights**: `Placement = 3`, `Result = 2`, `Event = 1`
2. **Recency**: Newer notifications win (secondary key).

When streaming or receiving **1 million notifications**, the naive approach is to hold the records, sort them, and slice the top 10.

---

### B. Comparative Complexity Analysis

#### 1. Naive Sorting Solution ($O(N \log N)$)
* **Mechanism**: Collects 1,000,000 notifications in an array, runs standard Dual-Pivot Quicksort or Timsort, and slices the top 10.
* **Complexity**:
  $$O(N \log N) \implies 1,000,000 \times \log_2(1,000,000) \approx 2 \times 10^7 \text{ comparisons}$$
* **Why it fails**:
  - Wastes CPU cycles comparing elements that are irrelevant to the top 10.
  - Generates severe **RAM memory exhaustion** because all 1 million records must be held simultaneously in memory.

#### 2. PriorityQueue (Min-Heap) Solution ($O(N \log K)$)
* **Mechanism**:
  - We instantiate a **Min-Heap** (Java's `PriorityQueue`) configured with a custom comparator that ranks by weight and recency, maintaining a strict capacity limit of **$K = 10$**.
  - We stream/iterate through the 1 million notifications. For each notification:
    1. We add it to the Min-Heap ($O(\log K)$ complexity).
    2. If the heap size exceeds 10, we instantly poll and discard the root element ($O(\log K)$). The root is guaranteed to be the element of absolute lowest priority currently in our top 10 candidate set!
  - After processing all 1 million elements, the heap contains exactly the top 10 highest-priority notifications.
* **Complexity**:
  $$O(N \log K) \implies 1,000,000 \times \log_2(10) \approx 3.3 \times 10^6 \text{ comparisons}$$
* **Algorithmic Benefit**:
  * **84% Faster CPU execution** (fewer comparisons).
  * **$O(1)$ Constant Space Complexity**: Memory utilization is completely independent of $N$. It holds only 10 elements in memory at a time, allowing it to scale to billions of notifications without heap exhaustion!

---

### C. Java Priority Queue Implementation Details

Our production-grade microservice is engineered with two fully functioning implementations of this heap-based scheduling strategy:
1. **Dynamic REST Endpoint**: Located in [NotificationController.java](file:///c:/users/kteja/OneDrive/Desktop/afford%20medical/vehicle_maintenance_scheduler/src/main/java/com/affordmedical/vehiclescheduler/controller/NotificationController.java), serving sorted JSON payloads at `/api/notifications/priority`.
2. **Standalone Executable Application**: Located in [PriorityInboxApp.java](file:///c:/users/kteja/OneDrive/Desktop/afford%20medical/vehicle_maintenance_scheduler/src/main/java/com/affordmedical/vehiclescheduler/PriorityInboxApp.java), providing direct compiled execution displaying formatted terminal tables and full integration with the logging middleware.

#### 1. Core Priority Queue Algorithm
```java
// Comparator maintaining ascending lowest-priority order at heap root
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
        minHeap.poll(); // Evicts lowest weight and oldest timestamp elements dynamically
    }
}
```

#### 2. Efficiency Maintenance at Scale
To efficiently maintain the top 10 as new notifications keep streaming in:
* **Dynamic Heap Window**: Instead of storing millions of notifications in memory or running sorting updates, we maintain a persistent heap of size $K=10$.
* **Low Time Complexity**: Processing a new notification runs in $O(\log 10) \approx 3.32$ operations. This ensures that the system processes incoming streams in real-time, independent of historical notification volumes.
* **Bounded Space Complexity**: The spatial complexity remains $O(K)$, meaning RAM utilization is strictly bounded to holding only 10 objects, preventing memory leaks and out-of-memory crashes.

---

### D. Verification & Execution Screenshots

The standalone CLI application was compiled and executed via:
```powershell
.\mvnw.cmd exec:java '-Dexec.mainClass=com.affordmedical.vehiclescheduler.PriorityInboxApp' -DskipTests
```
The output generated correct sorted allocations, extensively utilizing the logging middleware for telemetry reporting before shutting down.

A high-fidelity screenshot of the compiled terminal execution output displaying the priority notifications is saved in the repository at:
* **Screenshots File**: [priority_inbox_terminal.png](file:///c:/users/kteja/OneDrive/Desktop/afford%20medical/vehicle_maintenance_scheduler/screenshots/priority_inbox_terminal.png)

![Console Priority Output](file:///c:/users/kteja/OneDrive/Desktop/afford%20medical/vehicle_maintenance_scheduler/screenshots/priority_inbox_terminal.png)
