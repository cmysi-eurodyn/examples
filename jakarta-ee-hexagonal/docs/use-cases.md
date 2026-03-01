# Use-Case Diagrams

Mermaid diagrams describing the two use cases implemented in this example.
Render them in any Markdown viewer that supports [Mermaid](https://mermaid.js.org/)
(GitHub renders Mermaid natively in Markdown files).

---

## Use Case 1 – Create a Task

```mermaid
sequenceDiagram
    actor Client
    participant REST as TaskResource<br/>(Inbound REST Adapter)
    participant Port as CreateTaskUseCase<br/>(Inbound Port)
    participant Service as TaskService<br/>(Application Service)
    participant Repo as TaskRepository<br/>(Outbound Port)
    participant DB as InMemoryTaskRepository<br/>(Outbound Adapter)

    Client->>REST: POST /tasks<br/>{"title": "...", "description": "..."}
    REST->>REST: Validate request (Bean Validation)
    REST->>Port: createTask(CreateTaskCommand)
    Port->>Service: (routed via CDI)
    Service->>Service: Task.create(title, description)
    Service->>Repo: save(task)
    Repo->>DB: store.put(id, task)
    DB-->>Repo: task
    Repo-->>Service: task
    Service-->>Port: task
    Port-->>REST: task
    REST-->>Client: 201 Created<br/>{"id": "...", "title": "...", "status": "OPEN", ...}
```

---

## Use Case 2 – List All Tasks

```mermaid
sequenceDiagram
    actor Client
    participant REST as TaskResource<br/>(Inbound REST Adapter)
    participant Port as ListTasksUseCase<br/>(Inbound Port)
    participant Service as TaskService<br/>(Application Service)
    participant Repo as TaskRepository<br/>(Outbound Port)
    participant DB as InMemoryTaskRepository<br/>(Outbound Adapter)

    Client->>REST: GET /tasks
    REST->>Port: listAllTasks()
    Port->>Service: (routed via CDI)
    Service->>Repo: findAll()
    Repo->>DB: store.values()
    DB-->>Repo: List<Task>
    Repo-->>Service: unmodifiableList
    Service-->>Port: List<Task>
    Port-->>REST: List<Task>
    REST-->>Client: 200 OK<br/>[{"id": "...", "title": "...", ...}, ...]
```

---

## Architecture Overview – Hexagon

```mermaid
graph TD
    subgraph Adapters["Adapters (outside the hexagon)"]
        REST["🌐 TaskResource\n(Inbound REST Adapter)"]
        REPO["🗄️ InMemoryTaskRepository\n(Outbound Adapter)"]
    end

    subgraph Hexagon["Application Core (the hexagon)"]
        direction TB
        IP1["📥 CreateTaskUseCase\n(Inbound Port)"]
        IP2["📥 ListTasksUseCase\n(Inbound Port)"]
        SVC["⚙️ TaskService\n(Application Service)"]
        OP["📤 TaskRepository\n(Outbound Port)"]
        DOM["🔷 Task / TaskStatus\n(Domain)"]
    end

    REST -->|calls| IP1
    REST -->|calls| IP2
    IP1 -->|implemented by| SVC
    IP2 -->|implemented by| SVC
    SVC -->|uses| OP
    SVC -->|creates/reads| DOM
    OP -->|implemented by| REPO
```

> **Key insight:** arrows always point *inward* into the hexagon for inbound
> ports and *outward* from the hexagon for outbound ports. The application core
> never references adapters.
