# Jakarta EE – Ports and Adapters (Hexagonal Architecture) Example

A self-contained Jakarta EE application that teaches the
**Ports and Adapters** (Hexagonal) architecture pattern through a minimal,
runnable Task-management API.

---

## What is Ports and Adapters?

Ports and Adapters (coined by Alistair Cockburn) organises an application into
three rings:

| Ring | Contents | This project |
|------|----------|--------------|
| **Domain** | Pure business objects with no framework dependency | `Task`, `TaskStatus` |
| **Application (the hexagon)** | Use-case interfaces (**inbound ports**), outbound port interfaces, and application services | `CreateTaskUseCase`, `ListTasksUseCase`, `TaskRepository`, `TaskService` |
| **Adapters (outside the hexagon)** | Concrete drivers (REST, CLI …) and driven implementations (DB, messaging …) | `TaskResource`, `InMemoryTaskRepository` |

> Dependency arrows always point **inward**: adapters depend on ports;
> the core never imports an adapter.

---

## Project Layout

```
jakarta-ee-hexagonal/
├── docs/
│   └── use-cases.md               ← Mermaid sequence & architecture diagrams
├── src/
│   ├── main/
│   │   ├── java/com/example/hexagonal/
│   │   │   ├── domain/
│   │   │   │   ├── Task.java                       ← Domain entity
│   │   │   │   └── TaskStatus.java                 ← Enum (OPEN / COMPLETED)
│   │   │   ├── application/
│   │   │   │   ├── port/
│   │   │   │   │   ├── in/
│   │   │   │   │   │   ├── CreateTaskUseCase.java   ← Inbound port + command
│   │   │   │   │   │   └── ListTasksUseCase.java    ← Inbound port
│   │   │   │   │   └── out/
│   │   │   │   │       └── TaskRepository.java      ← Outbound port
│   │   │   │   └── service/
│   │   │   │       └── TaskService.java             ← Application service
│   │   │   └── adapter/
│   │   │       ├── in/rest/
│   │   │       │   ├── TaskResource.java            ← JAX-RS endpoint
│   │   │       │   └── dto/
│   │   │       │       ├── CreateTaskRequest.java   ← Request DTO
│   │   │       │       └── TaskResponse.java        ← Response DTO
│   │   │       └── out/persistence/
│   │   │           └── InMemoryTaskRepository.java  ← In-memory adapter
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       └── java/com/example/hexagonal/
│           └── application/service/
│               └── TaskServiceTest.java             ← Unit tests (no server)
└── pom.xml
```

---

## Use Cases

### Use Case 1 – Create a Task

**Endpoint:** `POST /tasks`

An HTTP client sends a JSON body with a `title` (required) and optional
`description`. The REST adapter validates the input using Jakarta Bean
Validation, wraps it in a `CreateTaskCommand`, and calls the
`CreateTaskUseCase` inbound port. The `TaskService` creates a `Task` domain
object and saves it via the `TaskRepository` outbound port.

**Flow:** `TaskResource` → `CreateTaskUseCase` → `TaskService` → `TaskRepository` → `InMemoryTaskRepository`

---

### Use Case 2 – List All Tasks

**Endpoint:** `GET /tasks`

The REST adapter calls the `ListTasksUseCase` inbound port. The `TaskService`
delegates to `TaskRepository.findAll()`, which returns all stored tasks. Each
domain `Task` is mapped to a `TaskResponse` DTO before being serialised.

**Flow:** `TaskResource` → `ListTasksUseCase` → `TaskService` → `TaskRepository` → `InMemoryTaskRepository`

---

## Diagrams

Mermaid sequence diagrams and an architecture overview are in
[`docs/use-cases.md`](docs/use-cases.md).

GitHub renders Mermaid natively. If your viewer does not, install the
[Mermaid CLI](https://github.com/mermaid-js/mermaid-cli):

```bash
npm install -g @mermaid-js/mermaid-cli
mmdc -i docs/use-cases.md -o docs/use-cases.svg
```

---

## Prerequisites

| Tool | Minimum version |
|------|----------------|
| JDK  | 17             |
| Maven | 3.8+          |

---

## How to Build and Run

### Run in development mode (hot-reload)

```bash
cd jakarta-ee-hexagonal
mvn quarkus:dev
```

The application starts on `http://localhost:8080`.

### Build an executable JAR

```bash
mvn package
java -jar target/quarkus-app/quarkus-run.jar
```

### Run unit tests only

```bash
mvn test
```

---

## Example API Calls

### Create a task

```bash
curl -s -X POST http://localhost:8080/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title": "Buy groceries", "description": "Milk, eggs, bread"}' | jq
```

**Response (201 Created):**

```json
{
  "id": "a1b2c3d4-...",
  "title": "Buy groceries",
  "description": "Milk, eggs, bread",
  "status": "OPEN",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

### Validation error (blank title)

```bash
curl -s -X POST http://localhost:8080/tasks \
  -H 'Content-Type: application/json' \
  -d '{"title": ""}' | jq
```

**Response (400 Bad Request)**

### List all tasks

```bash
curl -s http://localhost:8080/tasks | jq
```

**Response (200 OK):**

```json
[
  {
    "id": "a1b2c3d4-...",
    "title": "Buy groceries",
    "description": "Milk, eggs, bread",
    "status": "OPEN",
    "createdAt": "2024-01-15T10:30:00Z"
  }
]
```

---

## Architectural Mapping

| Component | Layer | Role |
|-----------|-------|------|
| `Task` | Domain | Entity – pure business object |
| `TaskStatus` | Domain | Enum – lifecycle states |
| `CreateTaskUseCase` | Application (inbound port) | Interface for Use Case 1 |
| `ListTasksUseCase` | Application (inbound port) | Interface for Use Case 2 |
| `TaskRepository` | Application (outbound port) | Persistence contract |
| `TaskService` | Application (service) | Implements both inbound ports; uses outbound port |
| `TaskResource` | Adapter (inbound/REST) | JAX-RS endpoint; translates HTTP ↔ use-case commands |
| `CreateTaskRequest` | Adapter (inbound/REST) | Request DTO with Bean Validation |
| `TaskResponse` | Adapter (inbound/REST) | Response DTO – decouples domain from wire format |
| `InMemoryTaskRepository` | Adapter (outbound/persistence) | In-memory `TaskRepository` implementation |
