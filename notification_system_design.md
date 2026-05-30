# Notification System Architecture & Database Scaling Blueprint

This document details the architectural blueprints, database optimizations, and algorithmic scaling strategies engineered for our centralized student notification microservice. It serves as a comprehensive system design manual for high-throughput alert dispatching, relational persistence, and real-time priority sorting.

---

## 1. Relational System Architecture & API Specifications

To guarantee reliable transactional message delivery and decoupled operations, the notification microservice utilizes a clean, stateless architecture. An API Gateway manages routing, load balancing, and rate limiting, forwarding request transactions to a Spring Boot service layer that manages validation, telemetry logging, and relational persistence in MySQL.

### A. High-Level Architecture Flow

```text
  [ User Client ] 
        ↓
  [ API Gateway ] (Load balancing, rate limiting, and routing)
        ↓
  [ Spring Boot Service ] (Validation, telemetry integration, and transactional boundaries)
        ↓
  [ Relational MySQL ] (Persistent indexing and relational storage)
```

---

### B. Relational Database Schema Design

The persistence tier relies on three tables representing users, categorical notification templates, and historical alert dispatches:

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
    type_name VARCHAR(50) NOT NULL UNIQUE, -- Categorized: Placement, Result, Event
    priority_weight INT NOT NULL DEFAULT 1 -- Ranks: Placement=3, Result=2, Event=1
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

### C. Core API Contract Specifications

All service endpoints process inputs and exchange responses using standard JSON payloads:

#### 1. Dispatch New Notification
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

#### 3. Update Notification Read State
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
* **Success Response**: `204 No Content`

---

## 2. Scalability Engineering & Database Optimizations

### A. The Scaling Challenge
As user activity increases to **50,000 active students** and **500,000 notifications**, unoptimized query filters such as:
```sql
SELECT * FROM notifications WHERE user_id = 10;
```
experience extreme performance degradation. Under load, query execution time increases linearly, causing severe CPU spikes and timeout bottlenecks.

### B. Root Cause: Full Table Scans
By default, columns (except for the Primary Key) are not indexed in relational engines. To evaluate this query, the database is forced to perform a **Full Table Scan (Sequential Row Sweep)**:
1. The database reads all 500,000 rows sequentially from disk.
2. It compares the `user_id` on every single record against the filter value (`10`).
3. This creates severe **Disk I/O bottlenecks**, thrashing the memory buffer pools as blocks are continuously paged in and out of RAM. The time complexity is linear: $O(N)$ where $N = 500,000$.

---

### C. Solution 1: B-Tree Indexing

To resolve sequential sweep overhead, we establish balanced B-Tree search indexes on our high-cardinality query filter columns:

```sql
-- Fast index for student-specific queries
CREATE INDEX idx_student ON notifications(user_id);

-- Fast index for categorical templates queries
CREATE INDEX idx_type ON notifications(notification_type_id);

-- Fast index for filtering unread alert subsets
CREATE INDEX idx_read ON notifications(is_read);
```

#### B-Tree Index Mechanics
* **How It Works**: A B-Tree index maintains a sorted, self-balancing tree structure of key-value pointers. Instead of scanning 500,000 rows, the search engine traverses tree nodes.
* **Algorithmic Benefit**: Query complexity drops from $O(N)$ linear scans to **$O(\log N)$ binary tree node traversals**. For 500,000 records:
  $$\log_2(500,000) \approx 19 \text{ comparisons}$$
  This represents a **99.99% reduction** in comparative row operations, dropping search execution time from seconds down to milliseconds.
* **Trade-Offs**:
  * *Write Overhead*: Data mutation operations (`INSERT`, `UPDATE`, `DELETE`) slightly slow down because the database engine must recalculate and rebalance the B-Tree index.
  * *Storage Overhead*: Indexes consume additional memory and disk space.

---

### D. Solution 2: Offset Pagination

Returning 500,000 notifications in a single payload causes severe memory exhaustion (JVM Heap pressure, network bandwidth saturation, and client-side DOM freeze). We solve this by implementing **Pagination**:

```sql
SELECT * 
FROM notifications 
WHERE user_id = 10 
ORDER BY created_at DESC 
LIMIT 20 OFFSET 0;
```

#### Key Architectural Benefits:
1. **Low Memory Footprint**: Keeps JVM memory heap usage extremely low by fetching only small, predictable subsets (e.g., 20 rows).
2. **Network Efficiency**: Small JSON payloads ensure fast network transfers and minimal latency.
3. **Improved Client UI**: Frontend clients render small page sets instantly, avoiding browser freezes.

---

## 3. High-Performance Priority Routing & Algorithm Selection

### A. The Priority Constraint
The system must retrieve the **Top 10 Notifications** for a student sorted dynamically by:
1. **Priority Weight**: `Placement = 3`, `Result = 2`, `Event = 1`
2. **Recency**: Newer alerts take precedence when priority weights are equal (secondary key).

When streaming or receiving **1 million active notifications** from multiple sources, sorting the entire dataset in memory is highly inefficient.

---

### B. Algorithmic Complexity Comparison

#### 1. Naive Sorting Solution ($O(N \log N)$)
* **Mechanism**: Collects all 1,000,000 notifications in a dynamically growing array, runs standard Dual-Pivot Quicksort or Timsort, and slices the top 10.
* **Complexity**:
  $$O(N \log N) \implies 1,000,000 \times \log_2(1,000,000) \approx 2 \times 10^7 \text{ operations}$$
* **Critical Drawbacks**:
  - Wastes massive CPU cycles comparing low-priority historical notifications that will never make it to the top 10.
  - Triggers severe **JVM Heap Out-Of-Memory Risks** because the entire 1,000,000 dataset must reside concurrently in RAM.

#### 2. PriorityQueue (Min-Heap) Solution ($O(N \log K)$)
* **Mechanism**:
  - We initialize a **Min-Heap** (Java's `PriorityQueue`) configured with a custom comparator that ranks items by weight and recency, maintaining a strict capacity limit of **$K = 10$**.
  - As we stream/iterate through the 1,000,000 notifications:
    1. We add the notification to the Min-Heap ($O(\log K)$ complexity).
    2. If the heap size exceeds 10, we instantly remove the root element ($O(\log K)$). The root is guaranteed to be the element of absolute lowest priority currently in our top-10 candidate window.
  - After processing the stream, the heap contains exactly the top 10 highest-priority notifications.
* **Complexity**:
  $$O(N \log K) \implies 1,000,000 \times \log_2(10) \approx 3.3 \times 10^6 \text{ operations}$$

#### Algorithmic Advantages:
* **84% Faster CPU execution** (significantly fewer operations).
* **$O(1)$ Bounded Space Complexity**: Memory utilization is completely independent of $N$. It holds only 10 objects in memory at a time, completely avoiding garbage collection overhead and out-of-memory crashes!

---

### C. Comparative Analysis Summary

| Metric | Naive Array Sorting | Min-Heap Priority Queue |
| :--- | :--- | :--- |
| **Time Complexity** | $O(N \log N)$ | $O(N \log K)$ where $K = 10$ |
| **Space Complexity** | $O(N)$ (Scales with dataset) | $O(K)$ (Constant $O(1)$ memory footprint) |
| **CPU Operations (1M elements)** | $\approx 2 \times 10^7$ comparisons | $\approx 3.3 \times 10^6$ comparisons |
| **RAM Footprint (1M elements)** | Heavy (Severe OOM Risk) | Negligible (Constant 10 objects) |
| **Garbage Collection Pressure** | Severe (Triggers full sweeps) | Zero (Extremely stable) |

---

### D. Production-Grade Java Implementation

Our Spring Boot microservice implements this strategy in two components:
1. **Dynamic REST Controller**: Exposed in `NotificationController.java` serving live JSON payloads at `/api/notifications/priority`.
2. **Standalone Executable Application**: Provided in `PriorityInboxApp.java` displaying formatted terminal tables and sending telemetry logs to the logging server.

#### Min-Heap Selection Logic:
```java
// Comparator maintaining ascending lowest-priority order at the root
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
        minHeap.poll(); // Evicts the lowest-weight and oldest element dynamically
    }
}
```

---

### E. Verification & Execution Outputs

The standalone sorting engine is executed using:
```powershell
.\mvnw.cmd exec:java '-Dexec.mainClass=com.affordmedical.vehiclescheduler.PriorityInboxApp' -DskipTests
```
This processes notifications in real-time, logs operations via our telemetry middleware, and outputs a formatted text table of sorted notifications. A visual output verifying the execution is preserved in your repository at:
* 📂 **Output Path**: [**`vehicle_maintenance_scheduler/screenshots/priority_inbox_terminal.png`**](file:///c:/users/kteja/OneDrive/Desktop/afford%20medical/vehicle_maintenance_scheduler/screenshots/priority_inbox_terminal.png)
