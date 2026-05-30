# Centralized Notification Backend Service (`notification_app_be`)

This is a high-performance Node.js & Express.js microservice designed to implement transactional and priority student notifications. It implements the database schemas, indexing optimizations, and priority sorting algorithms outlined in `notification_system_design.md`.

---

## 1. Project Directory Structure

```text
notification_app_be/
│
├── controllers/
│   └── notificationController.js  # Controller implementing DB fetches & Min-Heap algorithms
│
├── routes/
│   └── notificationRoutes.js      # REST endpoint routes mapping
│
├── services/
│   └── loggerService.js           # Decoupled JavaScript telemetry LoggerService client
│
├── db.js                          # SQLite schema creations, indexes & initial seeder
├── server.js                      # Express App server entry point
├── package.json                   # Project packages configuration
└── README.md                      # Service setup and verification manual
```

---

## 2. Dynamic Performance Engineering

1. **Stage 3 B-Tree Indexing**: On startup, three B-Tree indexes (`idx_student`, `idx_type`, `idx_read`) are automatically generated on the local relational store. This reduces the query retrieval time from linear $O(N)$ full table sweeps down to $O(\log N)$ tree traversal lookups.
2. **Stage 3 Offset Pagination**: The `GET /api/notifications` API strictly supports `limit` and `offset` query parameters. This keeps the network footprint minimal and bounds memory utilization.
3. **Stage 6 High-Performance Priority Heap Selection**: Under `/api/notifications/priority`, a custom binary Min-Heap data structure maintains a size-capped rolling window ($K = 10$). It processes incoming streams in $O(N \log K)$ time and constant $O(K)$ space.

---

## 3. How to Install and Run

### Prerequisites
* **Node.js**: v18.0.0+ (Tested on v22.12.0)
* **npm**: v9.0.0+

### Step 1: Install Dependencies
Open a terminal in the `notification_app_be` directory and run:
```bash
npm install
```

### Step 2: Launch the Microservice
```bash
npm start
```
The server will initialize the schema, seed the databases with sample data, generate performance search indexes, and listen on port `8082`.

---

## 4. Exposed REST API Manual

| Method | Endpoint | Description | Query Parameters |
| :--- | :--- | :--- | :--- |
| **GET** | `/health` | Check backend service health state | None |
| **POST** | `/api/notifications` | Dispatch notification to database | None |
| **GET** | `/api/notifications` | Retrieve paginated notifications | `studentId` (required), `limit` (optional), `offset` (optional) |
| **PUT** | `/api/notifications/:id` | Update read state of a notification | None |
| **DELETE** | `/api/notifications/:id` | Permanent archive/delete of notification | None |
| **GET** | `/api/notifications/priority` | Retrieve top-10 priority items using Min-Heap | None |

---

## 5. Audit & Manual Verification Queries

Verify correctness of each REST API endpoint using the following `curl` calls:

### 1. Health Audit Check
```bash
curl http://localhost:8082/health
```

### 2. Retrieve Student Notifications (Offset Paginated)
```bash
curl "http://localhost:8082/api/notifications?studentId=101&limit=3&offset=0"
```

### 3. Dispatch Notification
```bash
curl -X POST http://localhost:8082/api/notifications \
  -H "Content-Type: application/json" \
  -d '{"userId": 101, "notificationTypeId": 3, "title": "Goldman Sachs Interview", "message": "Congrats! You have been shortlisted."}'
```

### 4. Toggle Notification Read State (Mark as Read)
```bash
curl -X PUT http://localhost:8082/api/notifications/500001 \
  -H "Content-Type: application/json" \
  -d '{"isRead": true}'
```

### 5. Fetch Priority Queue Notifications (Top-10 Heap-sorted)
```bash
curl http://localhost:8082/api/notifications/priority
```

### 6. Delete/Archive Notification
```bash
curl -X DELETE http://localhost:8082/api/notifications/500001
```
