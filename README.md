# Patient Management

A Spring Boot microservice for managing patient records. It exposes a REST API to list,
create, and update patients, backed by JPA/Hibernate persistence. The project ships with an
in-memory H2 database (seeded with sample data) for local development, and is wired to use
PostgreSQL in other environments.

This repository is structured as a multi-service workspace. Today it contains a single
service, `patient-service`, with room to add more microservices alongside it.

## Tech Stack

- **Java 26**
- **Spring Boot 4.0.6** (`spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`)
- **Spring Data JPA / Hibernate** for persistence
- **H2** in-memory database (development), **PostgreSQL** driver (other environments)
- **Lombok** to reduce boilerplate (getters/setters/constructors/logging)
- **Maven** build tool (Maven Wrapper included)

## Project Structure

```
patient-management/
├── README.md                       # This file
├── patient-management.iml          # IntelliJ IDEA module file
├── api-requests/                   # Sample HTTP requests (IntelliJ / REST Client format)
│   └── patient-service/
│       ├── create-patient.http     # POST /patients example
│       ├── get-patients.http       # GET  /patients example
│       └── update-patient.http     # PUT  /patients/{id} example
└── patient-service/                # The patient microservice (Spring Boot app)
    ├── pom.xml                     # Maven build + dependency definitions
    ├── mvnw / mvnw.cmd             # Maven Wrapper scripts (Unix / Windows)
    ├── .mvn/wrapper/               # Maven Wrapper configuration
    └── src/
        ├── main/
        │   ├── java/com/ksnsolutions/com/patientservice/
        │   │   ├── PatientServiceApplication.java   # Spring Boot entry point
        │   │   ├── controller/
        │   │   │   └── PatientController.java        # REST endpoints (/patients)
        │   │   ├── service/
        │   │   │   └── PatientService.java           # Business logic
        │   │   ├── repository/
        │   │   │   └── PatientRepository.java        # Spring Data JPA repository
        │   │   ├── model/
        │   │   │   └── Patient.java                  # JPA entity (patient table)
        │   │   ├── dto/
        │   │   │   ├── PatientRequestDTO.java        # Incoming request payload + validation
        │   │   │   ├── PatientResponseDTO.java       # Outgoing response payload
        │   │   │   └── validators/
        │   │   │       └── CreatePatientValidationGroup.java  # Validation group marker
        │   │   ├── mapper/
        │   │   │   └── PatientMapper.java            # Entity <-> DTO conversion
        │   │   └── exception/
        │   │       ├── GlobalExceptionHandler.java   # Centralized REST error handling
        │   │       ├── EmailAlreadyExistsException.java
        │   │       └── PatientNotFoundException.java
        │   └── resources/
        │       ├── application.properties            # App / DB / server configuration
        │       └── data.sql                          # Seed data loaded at startup
        └── test/
            └── java/com/ksnsolutions/com/patientservice/
                └── PatientServiceApplicationTests.java  # Context-load smoke test
```

## What Each File Does

### Application bootstrap

- **`PatientServiceApplication.java`** — The Spring Boot entry point. Annotated with
  `@SpringBootApplication` and starts the embedded web server via `SpringApplication.run(...)`.

### Web layer (`controller/`)

- **`PatientController.java`** — A `@RestController` mapped to `/patients`. Exposes three
  endpoints and delegates all work to `PatientService`:
  - `GET /patients` — returns all patients.
  - `POST /patients` — creates a patient. Validates the request body against both the default
    constraints and the `CreatePatientValidationGroup` (so `registeredDate` is required on
    create).
  - `PUT /patients/{id}` — updates an existing patient. Validates only the default constraints
    (`registeredDate` is not required on update).

### Business layer (`service/`)

- **`PatientService.java`** — A `@Service` holding the core business logic:
  - `getPatients()` — fetches all patients and maps them to `PatientResponseDTO`.
  - `createPatient(...)` — rejects duplicate emails (`EmailAlreadyExistsException`), then
    persists a new patient.
  - `updatePatient(id, ...)` — loads the patient by id (throws `PatientNotFoundException` if
    missing), guards against using an email owned by a different patient, then updates and saves
    the fields.

### Persistence layer (`repository/` and `model/`)

- **`PatientRepository.java`** — A Spring Data `JpaRepository<Patient, UUID>`. Inherits standard
  CRUD operations and adds two derived queries:
  - `existsByEmail(email)` — used to prevent duplicate emails on create.
  - `existsByEmailAndIdNot(email, id)` — used to prevent email collisions on update (ignoring the
    patient being updated).
- **`Patient.java`** — The JPA `@Entity` mapped to the `patient` table. Fields: `id` (UUID,
  auto-generated), `name`, `email` (unique), `address`, `dateOfBirth`, and `registeredDate`. Uses
  Lombok `@Getter`/`@Setter`.

### Data transfer objects (`dto/`)

- **`PatientRequestDTO.java`** — The shape of incoming create/update requests. Carries Jakarta
  Bean Validation constraints: `name` (required, max 100 chars), `email` (required, valid email),
  `address` (required), `dateOfBirth` (required), and `registeredDate` (required only for the
  `CreatePatientValidationGroup`). Dates are accepted as strings and parsed to `LocalDate`.
- **`PatientResponseDTO.java`** — The shape returned to clients: `id`, `name`, `email`, `address`,
  and `dateOfBirth`. Note that `registeredDate` is intentionally not returned.
- **`validators/CreatePatientValidationGroup.java`** — An empty marker interface used as a Jakarta
  validation group. It lets the controller apply create-only validation rules (e.g. requiring
  `registeredDate`) without affecting updates.

### Mapping (`mapper/`)

- **`PatientMapper.java`** — Static helper methods that convert between the `Patient` entity and
  the DTOs:
  - `toDTO(Patient)` — entity → `PatientResponseDTO`.
  - `toModel(PatientRequestDTO)` — request DTO → new `Patient` entity (parsing date strings to
    `LocalDate`).

### Error handling (`exception/`)

- **`GlobalExceptionHandler.java`** — A `@ControllerAdvice` that centralizes REST error responses:
  - `MethodArgumentNotValidException` → `400` with a field → message map of validation errors.
  - `EmailAlreadyExistsException` → `400` with `{"message": "Email address already exists"}`.
  - `PatientNotFoundException` → `400` with `{"message": "Patient not found"}`.
- **`EmailAlreadyExistsException.java`** — A `RuntimeException` thrown when an email is already in
  use.
- **`PatientNotFoundException.java`** — A `RuntimeException` thrown when a patient id cannot be
  found.

### Configuration & seed data (`resources/`)

- **`application.properties`** — Configures the app and runtime:
  - Server runs on **port 4000**.
  - In-memory H2 datasource (`jdbc:h2:mem:testingMemDB`) with the H2 console enabled at
    `/h2-console`.
  - JPA `ddl-auto=update` and `spring.sql.init.mode=always` so `data.sql` runs on startup.
- **`data.sql`** — Creates the `patient` table if absent and seeds ~15 sample patients with
  well-known UUIDs (idempotent inserts guarded by `WHERE NOT EXISTS`).

### Tests (`test/`)

- **`PatientServiceApplicationTests.java`** — A `@SpringBootTest` smoke test (`contextLoads`) that
  verifies the Spring application context starts successfully.

### API request samples (`api-requests/`)

- **`*.http` files** — Ready-to-run HTTP requests (IntelliJ HTTP Client / VS Code REST Client
  format) demonstrating each endpoint against `http://localhost:4000`.

## Getting Started

### Prerequisites

- JDK 26
- No separate Maven install required — use the bundled Maven Wrapper.

### Run the service

From the `patient-service/` directory:

```bash
cd patient-service
./mvnw spring-boot:run
```

The service starts on `http://localhost:4000`.

### Run the tests

```bash
cd patient-service
./mvnw test
```

### Build a runnable JAR

```bash
cd patient-service
./mvnw clean package
java -jar target/patient-service-0.0.1-SNAPSHOT.jar
```

## API Reference

Base URL: `http://localhost:4000`

| Method | Path             | Description                  | Body                | Success |
|--------|------------------|------------------------------|---------------------|---------|
| GET    | `/patients`      | List all patients            | —                   | `200`   |
| POST   | `/patients`      | Create a new patient         | `PatientRequestDTO` | `200`   |
| PUT    | `/patients/{id}` | Update an existing patient   | `PatientRequestDTO` | `200`   |

### Create a patient

```http
POST http://localhost:4000/patients
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john.doe@example.com",
  "address": "123 Main Street",
  "dateOfBirth": "1995-09-09",
  "registeredDate": "1995-09-11"
}
```

### Update a patient

```http
PUT http://localhost:4000/patients/{id}
Content-Type: application/json

{
  "name": "John Doe",
  "email": "john.doe@example.com",
  "address": "123 Main Street",
  "dateOfBirth": "1995-09-09",
  "registeredDate": "1995-09-11"
}
```

`registeredDate` is required on create but optional on update. Dates use ISO format
(`yyyy-MM-dd`).

### Error responses

- **Validation failure** → `400` with a map of field names to messages.
- **Duplicate email** → `400` with `{"message": "Email address already exists"}`.
- **Patient not found** → `400` with `{"message": "Patient not found"}`.

## Database

By default the service uses an in-memory H2 database, so all data resets on restart (then
re-seeds from `data.sql`).

Browse the data via the H2 console at `http://localhost:4000/h2-console` using:

- **JDBC URL:** `jdbc:h2:mem:testingMemDB`
- **User:** `admin_viewer`
- **Password:** `password`

The PostgreSQL driver is included on the classpath; point the datasource properties at a
PostgreSQL instance to use it in other environments.

## Architecture Overview

Requests flow through clearly separated layers:

```
HTTP request
   ↓
PatientController        (validates input, maps HTTP <-> service)
   ↓
PatientService           (business rules: uniqueness, existence)
   ↓
PatientRepository        (Spring Data JPA)
   ↓
H2 / PostgreSQL          (patient table)
```

`PatientMapper` converts between the `Patient` entity and the request/response DTOs, and
`GlobalExceptionHandler` translates exceptions into consistent JSON error responses.
