# Vehicle Maintenance Scheduler & Central Telemetry Microservice (Backend Track)

This repository contains the complete, high-performance **Vehicle Maintenance Scheduler Microservice** alongside a unified **Centralized Telemetry Logging Middleware** connecting to the remote evaluation server.

---

## 1. Project Directory Structure

Conforming exactly to the evaluation specifications, the folder structure is designed as follows:

```text
afford medical/
│
├── logging_middleware/
│   ├── LoggerService.java         # Standalone Interface
│   ├── LogRequest.java            # Standalone DTO POJO
│   └── LogClient.java             # Standalone HttpClient Implementation
│
├── vehicle_maintenance_scheduler/ # Spring Boot 3 + Maven microservice
│   ├── pom.xml
│   ├── src/main/java/com/affordmedical/vehiclescheduler/
│   │   ├── controller/            # REST API endpoints (ScheduleController)
│   │   ├── service/               # SchedulerService & LoggerServiceImpl
│   │   ├── dto/                   # Depot, Vehicle, ScheduleResponse models
│   │   ├── client/                # External REST client (ExternalDepotClient)
│   │   ├── config/                # Spring configuration (AppConfig)
│   │   └── exception/             # Global handler (GlobalExceptionHandler)
│   └── screenshots/               # Directory for execution verification output screenshots
│
├── notification_system_design.md  # Detailed System Architecture and Database Scaling optimizations
└── README.md                      # Backend manual and algorithm walkthroughs
```

---

## 2. Core Features & Architecture

### A. Centralized Telemetry Logging Middleware (`logging_middleware/`)
* **Reusable Java SDK**: Fully decoupled from Spring, built inside the `logging_middleware` package using standard JDK `java.net.http.HttpClient` (Java 11+).
* **Asynchronous Telemetry**: Sends real-time telemetry HTTP POST messages directly to the remote evaluation logs server: `http://4.224.186.213/evaluation-service/logs`.
* **Input Validation**: Includes strict structural validation for logging level, stack, and stack-specific packages.
* **Extensive Backend Integration**: Our Spring Boot backend extends the `LogClient` into a standard `@Service` component (`LoggerServiceImpl`), replacing standard console log lines for all key operations.

### B. Spring Boot 3 Scheduler Microservice (`vehicle_maintenance_scheduler/`)
* **Stateless Dynamic Engine**: Dynamic data model. Fetches depot capacities and vehicle task profiles dynamically *on every request* instead of utilizing local databases or persistent files.
* **Dynamic Programming (0/1 Knapsack)**:
  * Optimizes the daily scheduling of service requests to maximize cumulative vehicle operational impact scores within each depot's strict mechanic hours budget.
  * Solves using classic DP table matrices ($O(N \times W)$ complexity, resolving in microseconds).
  * Backtracks to cleanly partition items into `selected` and `unselected` vehicle arrays.
* **Pre-Authorized Access**: Completely conforms to the revised constraints; all local endpoints are publicly accessible (no login or registration required).
* **Remote Protected Route Integration**: Queries the external protected endpoints by securely attaching the user's `Authorization: Bearer <evaluation_token>` header:
  * Depots: `http://4.224.186.213/evaluation-service/depots`
  * Vehicles: `http://4.224.186.213/evaluation-service/vehicles`

---

## 3. Optimization Algorithm Details

The microservice models vehicle scheduling as a **0/1 Knapsack Problem** for each depot. 
* **Knapsack Capacity ($W$)**: Depot `MechanicHours` capacity.
* **Item Weight ($w_i$)**: Vehicle `Duration` (hours required).
* **Item Value ($v_i$)**: Vehicle operational `Impact` score.

### Dynamic Programming Recurrence
Let $DP[i][j]$ be the maximum operational impact score achievable with the first $i$ vehicles using a maximum of $j$ mechanic hours:
$$DP[i][j] = \max\left(DP[i-1][j], \; DP[i-1][j - \text{Duration}_i] + \text{Impact}_i\right) \quad \text{if } j \ge \text{Duration}_i$$
$$DP[i][j] = DP[i-1][j] \quad \text{otherwise}$$

### Selected Set Backtracking
After building the matrix table, the algorithm backtracks from $DP[N][W]$:
* If $DP[i][j] \ne DP[i-1][j]$, then Vehicle $i$ is selected. We decrement remaining capacity $j \leftarrow j - \text{Duration}_i$, add Vehicle $i$ to the **selected** set, and proceed to vehicle $i-1$.
* Otherwise, Vehicle $i$ is not selected and is pushed to the **deferred** (unselected) set.

---

## 4. Run Manual (How to Build and Run)

### Prerequisites
* **Java**: JDK 21+
* **Maven**: (Utilizes the built-in Maven Wrapper `mvnw`)

### Step 1: Compile the Microservice
Open a terminal in the project directory and verify compilation:
```bash
cd vehicle_maintenance_scheduler
# Clean and compile code
./mvnw clean compile
```

### Step 2: Run the Spring Boot Application
```bash
# Launch Spring Boot REST API
./mvnw spring-boot:run
```
The server will boot up and bind to port `8080`.

### Step 3: Audit Optimized Output Endpoints
You can verify the solved Knapsack schedules by hitting the endpoints using a browser, Postman, or `curl`:

1. **Examine Optimized Schedules (Knapsack output per Depot)**:
   ```bash
   curl http://localhost:8080/api/schedule
   ```
2. **Examine Raw Fetched Depots Data**:
   ```bash
   curl http://localhost:8080/api/depots
   ```
3. **Examine Raw Fetched Vehicles/Tasks Data**:
   ```bash
   curl http://localhost:8080/api/vehicles
   ```

---

## 5. Exposed API Endpoints (CORS-Enabled)

| HTTP Method | Endpoint | Description |
| :--- | :--- | :--- |
| **GET** | `/api/schedule` | Solves and returns optimized vehicle allocations for all depots |
| **GET** | `/api/depots` | Fetches raw depot data from the remote evaluation service |
| **GET** | `/api/vehicles` | Fetches raw vehicle data from the remote evaluation service |
