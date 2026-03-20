# Top 10 Architecture Patterns for Modernizing Public Procurement Java EE Systems

*Tailored for: Senior Java Developer & Team Lead migrating legacy JEE7 government eProcurement systems*

**Author:** Charalampos Mysirlidis (cmysi-eurodyn)  
**Date:** 2026-03-01 13:27:07  
**Source:** Curated from [Microsoft Azure Architecture Center](https://learn.microsoft.com/en-us/azure/architecture/browse/)  
**Focus:** Java EE (Jakarta EE) / Spring — Public Procurement Domain

---

## Table of Contents

1. [Context: The Public Procurement Domain](#context-the-public-procurement-domain)
2. [Pattern 1: Strangler Fig](#1-strangler-fig-pattern)
3. [Pattern 2: Anti-Corruption Layer (ACL)](#2-anti-corruption-layer-acl)
4. [Pattern 3: Transactional Outbox](#3-transactional-outbox-pattern)
5. [Pattern 4: Saga (Orchestration)](#4-saga-pattern-orchestration-first-for-procurement)
6. [Pattern 5: CQRS](#5-cqrs-command-query-responsibility-segregation)
7. [Pattern 6: Event Sourcing](#6-event-sourcing-selective--for-audit-critical-aggregates)
8. [Pattern 7: Circuit Breaker + Retry + Bulkhead](#7-circuit-breaker--retry--bulkhead-resilience-patterns)
9. [Pattern 8: Cache-Aside](#8-cache-aside-and-distributed-caching)
10. [Pattern 9: API Gateway / Backend for Frontend](#9-api-gateway--backend-for-frontend)
11. [Pattern 10: Domain Events + Idempotent Consumers](#10-domain-events--idempotent-consumers-reliability-foundation)
12. [Pattern Interaction Map](#pattern-interaction-map)
13. [Recommended Learning & Implementation Order](#recommended-learning--implementation-order)
14. [Quick PoC You Can Start This Week](#quick-poc-you-can-start-this-week)

---

## Context: The Public Procurement Domain

Public procurement platforms (think ESPD, eTendering, eNotices, eInvoicing, eAwarding, contract management) share distinctive characteristics:

- **Long-lived, stateful business processes** (tender lifecycle: publication → submission → evaluation → award → contract → execution → audit)
- **Strict audit & compliance** (EU directives, national legislation, GDPR, eIDAS)
- **Heavy document management** (ESPD XML/UBL, PDF tenders, structured/unstructured attachments)
- **Complex authorization** (contracting authorities, economic operators, evaluators, auditors — each with different visibility rules per tender phase)
- **Integration with national/EU registries** (TED, national business registries, tax/social security verification, eDelivery/AS4)
- **Reporting obligations** (statistical, audit trail, transparency portals)

These constraints make certain architecture patterns far more relevant than others. Below are the 10 most impactful, ordered by migration priority.

---

## 1. Strangler Fig Pattern

**What it is:** Incrementally replace parts of a monolithic system by intercepting calls and routing them to new implementations, while the legacy system continues to run.

**Why it matters for procurement:**
Your procurement monolith (EJB3 + Struts 1 + WildFly) has years of battle-tested business logic — tender validation rules, evaluation formulas, notification workflows. A big-bang rewrite is politically and technically unacceptable in a government context (service continuity obligations, certification requirements).

### Implementation in JEE/Jakarta/Spring

```java
/**
 * Phase 1: Deploy a reverse proxy or a thin Jakarta REST resource
 * that decides per-endpoint whether to delegate to the legacy EJB
 * or to a new Spring Boot service.
 *
 * Example: Tender search is migrated first (read-only, low risk).
 * Tender submission remains in legacy until fully validated.
 */
@Path("/api/tenders")
@ApplicationScoped
public class TenderResource {

    @Inject
    private LegacyTenderServiceProxy legacyProxy; // calls EJB via JNDI/remote

    @Inject
    private ModernTenderSearchService modernSearch; // new Spring Boot service

    @GET
    @Path("/search")
    public Response searchTenders(@QueryParam("cpv") String cpvCode,
                                  @QueryParam("status") String status) {
        // NEW path — routed to modern service
        return Response.ok(modernSearch.search(cpvCode, status)).build();
    }

    @POST
    @Path("/{tenderId}/submissions")
    public Response submitOffer(@PathParam("tenderId") Long tenderId,
                                OfferDTO offer) {
        // LEGACY path — still handled by EJB until migrated
        return Response.ok(legacyProxy.submitOffer(tenderId, offer)).build();
    }
}
```

### Migration Sequencing for Procurement

| Phase | What to Migrate First | Why |
|-------|----------------------|-----|
| 1 | Read-only search/browse (tender lists, CPV search) | Zero write-side risk, immediate UX improvement |
| 2 | Notification/alerting (new tender alerts) | Decoupled, event-driven, easy to validate |
| 3 | Document management (upload/download/preview) | Stateless, can use modern object storage |
| 4 | Evaluation workflows | Complex but self-contained per tender |
| 5 | Submission/award (write-heavy, transactional) | Last — highest risk, most compliance constraints |

### Trade-offs

- ✅ Zero downtime migration, feature-by-feature validation, rollback per feature
- ❌ Two stacks running simultaneously, shared DB creates coupling, session/auth must work across both
- **Critical:** Shared PostgreSQL DB becomes the bottleneck — plan for database decomposition early

### Production Recommendation

Use a feature flag system (e.g., Unleash, LaunchDarkly, or a simple DB-backed config table) to control routing per contracting authority or per tender type. This lets you do gradual rollouts in a government context where different agencies have different risk tolerance.

---

## 2. Anti-Corruption Layer (ACL)

**What it is:** A translation boundary that prevents legacy domain concepts, data structures, and transactional semantics from leaking into new components.

**Why it matters for procurement:**
Legacy procurement systems accumulate years of implicit business rules encoded in entity relationships, DB triggers, stored procedures, and EJB interceptors. When you build new services, you must not replicate those implicit behaviors — you need explicit, clean domain models.

### Implementation — Procurement-Specific Example

```java
/**
 * ACL between legacy procurement DB schema and new Tender domain model.
 *
 * Legacy schema: PROC_NOTICE table with 87 columns, nullable everything,
 * status encoded as VARCHAR(2) codes ('PU','CL','EV','AW','CA'),
 * mixed concerns (notice + tender + lot in same table).
 *
 * New domain: Clean Tender aggregate with explicit value objects.
 */
@ApplicationScoped
public class TenderAntiCorruptionLayer {

    @PersistenceContext(unitName = "legacyPU")
    private EntityManager legacyEm;

    /**
     * Translates legacy PROC_NOTICE row into a clean Tender domain object.
     * All implicit rules are made EXPLICIT here.
     */
    public Tender translateFromLegacy(Long procNoticeId) {
        ProcNotice legacy = legacyEm.find(ProcNotice.class, procNoticeId);
        if (legacy == null) {
            throw new TenderNotFoundException(procNoticeId);
        }

        // Explicit translation of encoded status
        TenderStatus status = mapLegacyStatus(legacy.getStatus());

        // Decompose the monolithic row into proper value objects
        ContractingAuthority ca = new ContractingAuthority(
            legacy.getCaName(),
            legacy.getCaNationalId(),
            NutsCode.of(legacy.getCaNutsCode())
        );

        // Legacy stores monetary values as BigDecimal without currency —
        // the currency was "implied" by the contracting authority's country.
        // ACL makes this EXPLICIT.
        Currency currency = resolveCurrency(ca.getNutsCode());
        EstimatedValue value = new EstimatedValue(
            legacy.getEstimatedValueNet(), currency
        );

        // Lots were stored as semicolon-delimited in a TEXT column (!!)
        List<LotSummary> lots = parseLegacyLots(legacy.getLotData());

        return Tender.builder()
            .id(TenderId.of(procNoticeId))
            .status(status)
            .contractingAuthority(ca)
            .estimatedValue(value)
            .cpvCodes(parseCpvCodes(legacy.getCpvMain(), legacy.getCpvSuppl()))
            .lots(lots)
            .submissionDeadline(legacy.getDtSubmission())
            .build();
    }

    private TenderStatus mapLegacyStatus(String code) {
        return switch (code) {
            case "PU" -> TenderStatus.PUBLISHED;
            case "CL" -> TenderStatus.CLOSED_FOR_SUBMISSION;
            case "EV" -> TenderStatus.UNDER_EVALUATION;
            case "AW" -> TenderStatus.AWARDED;
            case "CA" -> TenderStatus.CANCELLED;
            default -> throw new UnknownLegacyStatusException(code);
        };
    }
}
```

### Key Architectural Decision

- Place the ACL in a **dedicated module/JAR** — not scattered across services
- The ACL is the **only code** that has a dependency on legacy entity classes
- New services depend only on the clean domain model

### Trade-offs

- ✅ Clean domain isolation, testable translation logic, documented implicit rules
- ❌ Mapping code that must be maintained; potential performance overhead from translation
- **Critical for procurement:** The ACL is where you discover and document all the undocumented business rules ("why is this column always null for framework agreements?")

---

## 3. Transactional Outbox Pattern

**What it is:** Write domain events to an outbox table within the same DB transaction as the business operation, then publish them asynchronously to a message broker.

**Why it matters for procurement:**
When a tender status changes (Published → Closed → Awarded), multiple things must happen: notifications, audit log entries, statistical updates, TED publication, transparency portal sync. You need **guaranteed event delivery** without distributed transactions (XA/2PC across PostgreSQL + Artemis is fragile and slow).

### Outbox Entity

```java
@Entity
@Table(name = "domain_event_outbox",
    indexes = {
        @Index(name = "idx_outbox_status_created",
               columnList = "status, created_at")
    })
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;  // e.g., "Tender", "Submission", "Evaluation"

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;      // e.g., "TenderPublished", "AwardDecisionMade"

    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status = OutboxStatus.PENDING;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Version
    private int version;
}
```

### Domain Service (Atomic Write)

```java
@Stateless
public class TenderService {

    @PersistenceContext
    private EntityManager em;

    public void publishTender(Long tenderId) {
        Tender tender = em.find(Tender.class, tenderId);
        tender.publish();

        // Same transaction — atomic with the status change
        OutboxEvent event = new OutboxEvent();
        event.setAggregateType("Tender");
        event.setAggregateId(tenderId.toString());
        event.setEventType("TenderPublished");
        event.setPayload(buildPayload(tender));
        event.setCreatedAt(Instant.now());
        event.setStatus(OutboxStatus.PENDING);
        em.persist(event);
    }
}
```

### Outbox Publisher (Polling)

```java
@Singleton
@Startup
public class OutboxPublisher {

    @PersistenceContext
    private EntityManager em;

    @Inject
    private JMSContext jmsContext;

    @Resource(lookup = "java:/jms/topic/DomainEvents")
    private Topic domainEventsTopic;

    @Schedule(hour = "*", minute = "*", second = "*/5", persistent = false)
    public void publishPendingEvents() {
        List<OutboxEvent> pending = em.createQuery(
                "SELECT e FROM OutboxEvent e " +
                "WHERE e.status = :status AND e.retryCount < :maxRetries " +
                "ORDER BY e.createdAt ASC", OutboxEvent.class)
            .setParameter("status", OutboxStatus.PENDING)
            .setParameter("maxRetries", 5)
            .setMaxResults(100)
            .getResultList();

        for (OutboxEvent event : pending) {
            try {
                jmsContext.createProducer()
                    .setProperty("eventType", event.getEventType())
                    .setProperty("aggregateType", event.getAggregateType())
                    .setProperty("aggregateId", event.getAggregateId())
                    .send(domainEventsTopic, event.getPayload());

                event.setStatus(OutboxStatus.PUBLISHED);
                event.setPublishedAt(Instant.now());
            } catch (Exception ex) {
                event.setRetryCount(event.getRetryCount() + 1);
                if (event.getRetryCount() >= 5) {
                    event.setStatus(OutboxStatus.FAILED);
                }
            }
        }
    }
}
```

### Alternative: Debezium CDC

Instead of polling, use Debezium to capture changes on the `domain_event_outbox` table via PostgreSQL's WAL (logical replication). Debezium publishes changes to Kafka. From Kafka you can bridge to Artemis or consume directly. This eliminates polling overhead but adds Kafka + Debezium to your infrastructure.

### Trade-offs

- ✅ Guaranteed consistency between DB and messaging without XA; simple to implement with pure JPA
- ❌ Polling has latency (seconds); Debezium adds infrastructure; outbox table grows (needs cleanup/archival)
- **Critical for procurement:** Audit events (tender published, award decision, contract signed) must **never be lost**. The outbox guarantees this.

---

## 4. Saga Pattern (Orchestration-first for Procurement)

**What it is:** Coordinate a multi-step business process across services/modules using a sequence of local transactions with explicit compensating actions for rollback.

**Why it matters for procurement:**
Tender evaluation is a multi-step, multi-actor process: validate submissions → check exclusion criteria → evaluate technical criteria → evaluate financial criteria → produce ranking → notify winner → standstill period → award. Each step may involve different services, external registry checks (tax clearance, social security), and human approvals.

### Orchestration Implementation

```java
/**
 * Saga orchestrator for tender evaluation process.
 * Orchestration > Choreography for procurement because:
 * 1. Regulatory requirement to know exact process state at any time
 * 2. Auditors need a single place to inspect the full flow
 * 3. Complex conditional logic (e.g., skip financial eval if single bidder)
 */
@Stateless
public class EvaluationSagaOrchestrator {

    @Inject private SubmissionValidationService validationService;
    @Inject private ExclusionCriteriaService exclusionService;
    @Inject private TechnicalEvaluationService technicalService;
    @Inject private FinancialEvaluationService financialService;
    @Inject private RankingService rankingService;
    @Inject private SagaStateRepository sagaRepo;

    public EvaluationResult executeEvaluation(Long tenderId) {
        SagaState saga = sagaRepo.create(tenderId, "EVALUATION");

        try {
            // Step 1: Validate all submissions
            saga.markStepStarted("SUBMISSION_VALIDATION");
            ValidationResult validation = validationService.validateAll(tenderId);
            saga.markStepCompleted("SUBMISSION_VALIDATION", validation);

            // Step 2: Check exclusion grounds (external registry calls)
            saga.markStepStarted("EXCLUSION_CHECK");
            ExclusionResult exclusion = exclusionService.checkAll(
                tenderId, validation.getValidSubmissionIds()
            );
            saga.markStepCompleted("EXCLUSION_CHECK", exclusion);

            // Step 3: Technical evaluation
            saga.markStepStarted("TECHNICAL_EVALUATION");
            TechnicalResult technical = technicalService.evaluate(
                tenderId, exclusion.getEligibleSubmissionIds()
            );
            saga.markStepCompleted("TECHNICAL_EVALUATION", technical);

            // Step 4: Financial evaluation
            saga.markStepStarted("FINANCIAL_EVALUATION");
            FinancialResult financial = financialService.evaluate(
                tenderId, technical.getQualifiedSubmissionIds()
            );
            saga.markStepCompleted("FINANCIAL_EVALUATION", financial);

            // Step 5: Produce final ranking
            saga.markStepStarted("RANKING");
            EvaluationResult result = rankingService.rank(
                tenderId, technical, financial
            );
            saga.markStepCompleted("RANKING", result);
            saga.markSagaCompleted();

            return result;

        } catch (SagaStepException ex) {
            compensate(saga, ex);
            throw new EvaluationFailedException(tenderId, ex);
        }
    }

    private void compensate(SagaState saga, SagaStepException cause) {
        List<CompletedStep> completed = saga.getCompletedStepsReversed();
        for (CompletedStep step : completed) {
            try {
                switch (step.getName()) {
                    case "RANKING" -> rankingService.compensate(saga.getTenderId());
                    case "FINANCIAL_EVALUATION" ->
                        financialService.compensate(saga.getTenderId());
                    case "TECHNICAL_EVALUATION" ->
                        technicalService.compensate(saga.getTenderId());
                    case "EXCLUSION_CHECK" ->
                        exclusionService.compensate(saga.getTenderId());
                    case "SUBMISSION_VALIDATION" ->
                        validationService.compensate(saga.getTenderId());
                }
                saga.markStepCompensated(step.getName());
            } catch (Exception compEx) {
                saga.markCompensationFailed(step.getName(), compEx);
            }
        }
        saga.markSagaFailed(cause);
    }
}
```

### Saga State Persistence (Critical for Audit)

```sql
CREATE TABLE saga_state (
    id              BIGSERIAL PRIMARY KEY,
    tender_id       BIGINT NOT NULL,
    saga_type       VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    initiated_by    VARCHAR(100) NOT NULL
);

CREATE TABLE saga_step (
    id              BIGSERIAL PRIMARY KEY,
    saga_id         BIGINT NOT NULL REFERENCES saga_state(id),
    step_name       VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    started_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at    TIMESTAMPTZ,
    result_payload  JSONB,
    error_message   TEXT,
    compensated_at  TIMESTAMPTZ
);
```

### Trade-offs

- ✅ Full audit trail, clear process visibility, easier debugging and regulatory compliance
- ❌ Orchestrator becomes a coordination bottleneck; compensating actions must be carefully designed
- **Critical:** Some procurement steps are **one-way** (opening of tenders is witnessed and cannot be undone). Model these as non-compensable steps.

---

## 5. CQRS (Command Query Responsibility Segregation)

**What it is:** Separate the write model (commands) from the read model (queries) to optimize for different concerns.

**Why it matters for procurement:**

- **Write side:** Complex validation rules, authorization checks, state machine transitions, audit logging
- **Read side:** Full-text search across tenders, CPV-based filtering, statistical dashboards, transparency portal exports, JasperReports data sources
- These have **completely different performance and consistency requirements**

### Write Side

```java
@Stateless
public class TenderCommandService {

    @PersistenceContext(unitName = "procurementPU")
    private EntityManager em;

    @Inject
    private OutboxEventPublisher outbox;

    @RolesAllowed("CONTRACTING_AUTHORITY")
    public void createTender(CreateTenderCommand cmd) {
        Tender tender = TenderFactory.create(cmd);
        em.persist(tender);

        outbox.publish("Tender", tender.getId(), "TenderCreated",
            TenderCreatedPayload.from(tender));
    }
}
```

### Read Side — Denormalized View

```java
@Entity
@Table(name = "tender_search_view")
public class TenderSearchView {

    @Id
    private Long tenderId;
    private String title;
    private String contractingAuthorityName;
    private String nutsCode;

    @Column(columnDefinition = "text[]")
    private String[] cpvCodes;

    private BigDecimal estimatedValueEur;
    private String procedureType;
    private String status;
    private LocalDate submissionDeadline;
    private LocalDate publicationDate;

    @Column(name = "search_vector", columnDefinition = "tsvector",
            insertable = false, updatable = false)
    private String searchVector;

    private int lotCount;
    private Instant lastProjectedAt;
}
```

### PostgreSQL Full-Text Search DDL

```sql
CREATE TABLE tender_search_view (
    tender_id                BIGINT PRIMARY KEY,
    title                    TEXT NOT NULL,
    contracting_authority    TEXT NOT NULL,
    nuts_code                VARCHAR(10),
    cpv_codes                TEXT[] NOT NULL,
    estimated_value_eur      NUMERIC(15,2),
    procedure_type           VARCHAR(50),
    status                   VARCHAR(30),
    submission_deadline      DATE,
    publication_date         DATE,
    lot_count                INT DEFAULT 0,
    search_vector            TSVECTOR,
    last_projected_at        TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_search_vector ON tender_search_view
    USING GIN (search_vector);
CREATE INDEX idx_cpv_codes ON tender_search_view USING GIN (cpv_codes);
CREATE INDEX idx_status_deadline ON tender_search_view
    (status, submission_deadline);
CREATE INDEX idx_nuts ON tender_search_view (nuts_code);

CREATE OR REPLACE FUNCTION update_search_vector() RETURNS trigger AS $$_
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('simple', coalesce(NEW.title, '')), 'A') ||
        setweight(to_tsvector('simple',
            coalesce(NEW.contracting_authority, ''), '')), 'B') ||
        setweight(to_tsvector('simple',
            coalesce(array_to_string(NEW.cpv_codes, ' '), '')), 'C');
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_search_vector
    BEFORE INSERT OR UPDATE ON tender_search_view
    FOR EACH ROW EXECUTE FUNCTION update_search_vector();
```

### Event Projector (MDB)

```java
@MessageDriven(activationConfig = {
    @ActivationConfigProperty(
        propertyName = "destinationType",
        propertyValue = "javax.jms.Topic"),
    @ActivationConfigProperty(
        propertyName = "destination",
        propertyValue = "java:/jms/topic/DomainEvents"),
    @ActivationConfigProperty(
        propertyName = "messageSelector",
        propertyValue = "aggregateType = 'Tender'"
    )
})
public class TenderSearchProjector implements MessageListener {

    @PersistenceContext(unitName = "readModelPU")
    private EntityManager readEm;

    @Override
    public void onMessage(Message message) {
        try {
            String eventType = message.getStringProperty("eventType");
            String payload = message.getBody(String.class);

            switch (eventType) {
                case "TenderCreated" -> projectTenderCreated(payload);
                case "TenderPublished" -> projectTenderPublished(payload);
                case "TenderStatusChanged" -> projectStatusChange(payload);
                case "TenderCancelled" -> projectCancellation(payload);
            }
        } catch (JMSException ex) {
            throw new RuntimeException("Projection failed", ex);
        }
    }
}
```

### Trade-offs

- ✅ Dramatic query performance improvement; JasperReports/Knowage can query a clean, indexed, denormalized schema
- ✅ Read model can be rebuilt from events if schema needs to change
- ❌ Eventual consistency: read model may lag seconds behind write model
- **Critical for procurement:** Submission deadlines must use the write-side clock, not the read model.

---

## 6. Event Sourcing (Selective — for Audit-Critical Aggregates)

**What it is:** Instead of storing only current state, persist every state-changing event as an immutable record.

**Why it matters for procurement:**
EU procurement directives require complete traceability. For critical aggregates (tender lifecycle, evaluation decisions, contract modifications), event sourcing provides **built-in, tamper-evident audit trails**.

### Event Store Entity

```java
@Entity
@Table(name = "tender_event_store")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "event_type")
public abstract class TenderEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long sequenceNumber;

    @Column(name = "tender_id", nullable = false)
    private Long tenderId;

    @Column(name = "event_type", insertable = false, updatable = false)
    private String eventType;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "caused_by_user", nullable = false)
    private String causedByUser;

    @Column(name = "caused_by_role", nullable = false)
    private String causedByRole;

    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;  // IP address, session ID, digital signature ref

    public abstract void applyTo(TenderState state);
}
```

### Tender Aggregate (Rebuilt from Events)

```java
public class TenderAggregate {

    private Long tenderId;
    private TenderState currentState;
    private List<TenderEvent> uncommittedEvents = new ArrayList<>();

    public static TenderAggregate reconstitute(Long tenderId,
                                                List<TenderEvent> history) {
        TenderAggregate aggregate = new TenderAggregate();
        aggregate.tenderId = tenderId;
        aggregate.currentState = new TenderState();
        for (TenderEvent event : history) {
            event.applyTo(aggregate.currentState);
        }
        return aggregate;
    }

    public void publish(String userId, String role) {
        if (currentState.getStatus() != TenderStatus.DRAFT) {
            throw new InvalidStateTransitionException(
                currentState.getStatus(), TenderStatus.PUBLISHED);
        }
        if (currentState.getSubmissionDeadline() == null) {
            throw new BusinessRuleViolation("Submission deadline required");
        }
        if (currentState.getSubmissionDeadline()
                .isBefore(LocalDate.now().plusDays(30))) {
            throw new BusinessRuleViolation(
                "Minimum 30 days for open procedure (EU directive)");
        }

        TenderPublishedEvent event = new TenderPublishedEvent();
        event.setTenderId(tenderId);
        event.setOccurredAt(Instant.now());
        event.setCausedByUser(userId);
        event.setCausedByRole(role);
        event.setPayload(toJson(Map.of(
            "publicationDate", LocalDate.now().toString(),
            "submissionDeadline",
                currentState.getSubmissionDeadline().toString()
        )));

        event.applyTo(currentState);
        uncommittedEvents.add(event);
    }

    public List<TenderEvent> getUncommittedEvents() {
        return Collections.unmodifiableList(uncommittedEvents);
    }
}
```

### When to Use Event Sourcing in Procurement

| Aggregate | Event Sourcing? | Rationale |
|-----------|----------------|-----------|
| Tender lifecycle | **Yes** | Full audit trail required by law |
| Evaluation decisions | **Yes** | Must prove every score, who entered it, when |
| Contract modifications | **Yes** | Every amendment must be traceable |
| User profiles | No | Simple CRUD, no audit requirement |
| CPV/NUTS reference data | No | Static lookup data |
| Notifications | No | Fire-and-forget, logged separately |

### Trade-offs

- ✅ Perfect audit trail, temporal queries, replay for debugging
- ❌ More complex persistence, snapshot management needed for long-lived aggregates
- **Critical:** Use **snapshots** for long-lived tenders (thousands of events over years).

---

## 7. Circuit Breaker + Retry + Bulkhead (Resilience Patterns)

**What it is:** Protect your system from cascading failures when calling external services.

**Why it matters for procurement:**
Procurement systems integrate with multiple external registries: TED, national business registries, tax authority verification, social security clearance, eDelivery AS4 gateways. **Any of these can be slow or down.**

### Implementation with Resilience4j + Jakarta CDI

```java
@ApplicationScoped
public class ExternalRegistryService {

    private final CircuitBreaker taxRegistryCB;
    private final Retry taxRegistryRetry;
    private final Bulkhead taxRegistryBulkhead;

    @Inject private TaxRegistryClient taxClient;
    @Inject private AuditLogger auditLogger;

    public ExternalRegistryService() {
        this.taxRegistryCB = CircuitBreaker.of("tax-registry",
            CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(10)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordExceptions(IOException.class, TimeoutException.class)
                .ignoreExceptions(TaxIdNotFoundException.class)
                .build());

        this.taxRegistryRetry = Retry.of("tax-registry",
            RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(500))
                .intervalFunction(
                    IntervalFunction.ofExponentialBackoff(500, 2.0))
                .retryOnException(e -> e instanceof IOException)
                .build());

        this.taxRegistryBulkhead = Bulkhead.of("tax-registry",
            BulkheadConfig.custom()
                .maxConcurrentCalls(10)
                .maxWaitDuration(Duration.ofSeconds(5))
                .build());
    }

    public TaxClearanceResult checkTaxClearance(String nationalTaxId,
                                                 String countryCode) {
        Supplier<TaxClearanceResult> decorated = Decorators
            .ofSupplier(() -> taxClient.verify(nationalTaxId, countryCode))
            .withBulkhead(taxRegistryBulkhead)
            .withCircuitBreaker(taxRegistryCB)
            .withRetry(taxRegistryRetry)
            .withFallback(List.of(
                BulkheadFullException.class,
                CallNotPermittedException.class
            ), ex -> {
                auditLogger.warn("Tax registry unavailable, marking PENDING",
                    nationalTaxId, countryCode, ex);
                return TaxClearanceResult.pending(
                    "Registry unavailable — manual verification required");
            })
            .decorate();

        return decorated.get();
    }
}
```

### Thread Pool / Bulkhead Design for Procurement WildFly

| Pool | Max Threads | Purpose |
|------|-------------|---------|
| `default` (EJB) | 64 | Normal OLTP operations |
| `external-registry` | 10 | Tax/Social security registry calls |
| `ted-publication` | 5 | TED eNotices API calls |
| `document-processing` | 8 | PDF generation, signature validation |
| `report-generation` | 4 | JasperReports (CPU-heavy) |

### Trade-offs

- ✅ Prevents one failing registry from bringing down the entire evaluation workflow
- ❌ Fallback logic must be carefully designed — "PENDING" statuses need follow-up workflows
- **Critical:** When a registry is down during evaluation, you **cannot silently skip** the check.

---

## 8. Cache-Aside (and Distributed Caching)

**What it is:** Application loads data into cache on demand; invalidation is application-controlled.

**Why it matters for procurement:**

- **Reference data** (CPV: ~9,500 codes, NUTS: ~2,000 codes) changes rarely but is queried constantly
- **Published tenders** are immutable for the submission period
- **Heavy SQL aggregations** for dashboards are expensive

### Implementation with JCache / Infinispan

```java
@ApplicationScoped
public class ReferenceDataCache {

    @Inject
    @CacheResult(cacheName = "cpv-codes")
    private Cache<String, CpvCode> cpvCache;

    @PersistenceContext
    private EntityManager em;

    public CpvCode getCpvCode(String code) {
        CpvCode cached = cpvCache.get(code);
        if (cached != null) {
            return cached;
        }

        CpvCode fromDb = em.createQuery(
                "SELECT c FROM CpvCode c WHERE c.code = :code",
                CpvCode.class)
            .setParameter("code", code)
            .getSingleResult();

        cpvCache.put(code, fromDb);
        return fromDb;
    }

    /**
     * Preload entire CPV taxonomy at startup.
     * ~9500 codes × ~200 bytes ≈ ~2MB — fits easily in memory.
     */
    @PostConstruct
    public void preloadCpvCodes() {
        List<CpvCode> all = em.createQuery(
            "SELECT c FROM CpvCode c", CpvCode.class).getResultList();
        for (CpvCode cpv : all) {
            cpvCache.put(cpv.getCode(), cpv);
        }
    }

    public void invalidateAll() {
        cpvCache.clear();
        preloadCpvCodes();
    }
}
```

### What to Cache in Procurement

| Data | Cache Strategy | TTL | Invalidation |
|------|---------------|-----|-------------|
| CPV codes | Preload + replicated | 24h | On reference data deploy |
| NUTS regions | Preload + replicated | 24h | On reference data deploy |
| Published tender summaries | Cache-aside | 5 min | On status change event |
| Dashboard statistics | Computed + cached | 5 min | Time-based expiry |
| User session/permissions | Cache-aside | 15 min | On logout or role change |

### What NOT to Cache

- Submission deadlines (must always be real-time)
- Evaluation scores during active evaluation
- Anything during the standstill period (legal challenge window)

### Trade-offs

- ✅ Dramatic reduction in DB load
- ❌ Stale data risk; cluster-wide invalidation requires replicated mode
- **Critical:** Never cache data that has legal timing implications.

---

## 9. API Gateway / Backend for Frontend

**What it is:** A single entry point handling cross-cutting concerns and potentially aggregating responses from multiple backends.

**Why it matters for procurement:**
A procurement platform serves fundamentally different clients:

| Client | BFF | Optimizations |
|--------|-----|---------------|
| Contracting authority portal | `/api/ca/*` | Rich DTOs, full validation feedback |
| Economic operator portal | `/api/eo/*` | Tender search optimized, submission-focused |
| Transparency portal | `/api/public/*` | Read-only, heavily cached, no auth |
| TED/eDelivery | `/api/system/*` | XML/UBL format, certificate auth |
| Admin/audit | `/api/admin/*` | Full entity access, event history |

### Gateway Auth Filter

```java
@Provider
@Priority(Priorities.AUTHENTICATION)
public class GatewayAuthFilter implements ContainerRequestFilter {

    @Inject private JwtValidator jwtValidator;
    @Inject private EidasCertificateValidator eidasValidator;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String authHeader = ctx.getHeaderString(HttpHeaders.AUTHORIZATION);
        String clientCert = ctx.getHeaderString("X-Client-Certificate");

        if (clientCert != null) {
            EidasIdentity identity = eidasValidator.validate(clientCert);
            ctx.setSecurityContext(new SystemSecurityContext(identity));
        } else if (authHeader != null
                   && authHeader.startsWith("Bearer ")) {
            JwtClaims claims = jwtValidator.validate(
                authHeader.substring(7));
            ctx.setSecurityContext(new UserSecurityContext(claims));
        } else {
            ctx.setSecurityContext(new AnonymousSecurityContext());
        }
    }
}
```

### Submission Rate Limiter

```java
@Provider
@Priority(Priorities.USER)
@SubmissionEndpoint
public class SubmissionRateLimiter implements ContainerRequestFilter {

    @Inject private RateLimitService rateLimitService;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        String tenderId = ctx.getUriInfo()
            .getPathParameters().getFirst("tenderId");
        String operatorId = ctx.getSecurityContext()
            .getUserPrincipal().getName();

        RateLimitResult result = rateLimitService.checkLimit(
            "submission:" + tenderId, operatorId,
            50, Duration.ofMinutes(1)
        );

        if (!result.isAllowed()) {
            ctx.abortWith(Response.status(429)
                .header("Retry-After", result.getRetryAfterSeconds())
                .entity(Map.of(
                    "error", "Too many concurrent submissions",
                    "retryAfter", result.getRetryAfterSeconds(),
                    "message", "Your submission will be accepted. " +
                        "Please retry in " +
                        result.getRetryAfterSeconds() + " seconds."
                ))
                .build());
        }
    }
}
```

### Trade-offs

- ✅ Centralized security, per-client optimization, traffic management near deadlines
- ❌ Gateway is a single point of failure (needs HA)
- **Critical:** Deadline-sensitive rate limiting must be fair — FIFO queueing with confirmation timestamps. Legal requirement: every submission received before the deadline must be accepted.

---

## 10. Domain Events + Idempotent Consumers (Reliability Foundation)

**What it is:** A foundational pattern combining domain event publishing with guaranteed exactly-once processing semantics through idempotency tracking.

**Why it matters for procurement:**
Every downstream consumer — notification service, TED publication, transparency portal sync — must process each event **exactly once**.

### Idempotent Consumer Base Class

```java
@MappedSuperclass
public abstract class IdempotentEventConsumer implements MessageListener {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void onMessage(Message message) {
        try {
            String eventId = message.getStringProperty("eventId");
            String consumerGroup = getConsumerGroup();

            boolean alreadyProcessed = isProcessed(eventId, consumerGroup);
            if (alreadyProcessed) {
                return;
            }

            String payload = message.getBody(String.class);
            String eventType = message.getStringProperty("eventType");
            processEvent(eventType, payload);

            markProcessed(eventId, consumerGroup);

        } catch (JMSException ex) {
            throw new RuntimeException("Event consumption failed", ex);
        }
    }

    private boolean isProcessed(String eventId, String consumerGroup) {
        Long count = em.createQuery(
                "SELECT COUNT(p) FROM ProcessedEvent p " +
                "WHERE p.eventId = :eventId " +
                "AND p.consumerGroup = :group", Long.class)
            .setParameter("eventId", eventId)
            .setParameter("group", consumerGroup)
            .getSingleResult();
        return count > 0;
    }

    private void markProcessed(String eventId, String consumerGroup) {
        ProcessedEvent record = new ProcessedEvent();
        record.setEventId(eventId);
        record.setConsumerGroup(consumerGroup);
        record.setProcessedAt(Instant.now());
        em.persist(record);
    }

    protected abstract String getConsumerGroup();
    protected abstract void processEvent(String eventType, String payload);
}
```

### TED Publication Consumer

```java
@MessageDriven(activationConfig = {
    @ActivationConfigProperty(
        propertyName = "destination",
        propertyValue = "java:/jms/topic/DomainEvents"),
    @ActivationConfigProperty(
        propertyName = "destinationType",
        propertyValue = "javax.jms.Topic"),
    @ActivationConfigProperty(
        propertyName = "messageSelector",
        propertyValue = "eventType IN ('TenderPublished'," +
            "'TenderCancelled','AwardDecisionPublished')"
    )
})
public class TedPublicationConsumer extends IdempotentEventConsumer {

    @Inject private TedSubmissionService tedService;
    @Inject private UblDocumentGenerator ublGenerator;

    @Override
    protected String getConsumerGroup() {
        return "ted-publication";
    }

    @Override
    protected void processEvent(String eventType, String payload) {
        switch (eventType) {
            case "TenderPublished" -> {
                TenderPublishedPayload event = deserialize(payload);
                String eFormsXml = ublGenerator
                    .generateContractNotice(event.getTenderId());
                tedService.submitNotice(eFormsXml, event.getTenderId());
            }
            case "AwardDecisionPublished" -> {
                AwardPayload event = deserialize(payload);
                String eFormsXml = ublGenerator
                    .generateAwardNotice(event.getTenderId());
                tedService.submitNotice(eFormsXml, event.getTenderId());
            }
        }
    }
}
```

### Processed Events Entity

```java
@Entity
@Table(name = "processed_events",
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"event_id", "consumer_group"}))
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "consumer_group", nullable = false, length = 50)
    private String consumerGroup;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}
```

### Trade-offs

- ✅ Guaranteed exactly-once semantics without distributed locking
- ❌ Extra DB writes per event per consumer; table grows (needs cleanup)
- **Critical for procurement:** A duplicate TED publication is a legal/reputational disaster. This pattern prevents it.

---

## Pattern Interaction Map

```
┌──────────────────────────────────────────────────────────────┐
│                     API GATEWAY (#9)                          │
│      Auth · Rate Limiting · BFF routing · Correlation IDs     │
└───────┬──────────────┬──────────────────┬────────────────────┘
        │              │                  │
 ┌──────▼─────┐ ┌──────▼─────┐  ┌────────▼───────┐
 │ CA Portal  │ │ EO Portal  │  │ Transparency   │
 │ (writes)   │ │ (submit)   │  │ Portal (reads) │
 └──────┬─────┘ └──────┬─────┘  └────────┬───────┘
        │              │                  │
 ┌──────▼──────────────▼─────┐   ┌───────▼───────┐
 │   WRITE SIDE (CQRS #5)   │   │ READ SIDE (#5)│
 │ ┌───────────────────────┐ │   │ Denormalized  │
 │ │ Tender Aggregate      │ │   │ search views  │
 │ │ (Event Sourced #6)    │ │   │ JasperReports │
 │ └───────────┬───────────┘ │   │ Knowage BI    │
 │             │             │   └───────▲───────┘
 │ ┌───────────▼───────────┐ │          │
 │ │ Transactional         │ │   ┌──────┴──────┐
 │ │ Outbox (#3)           │─┼──►│ Projectors  │
 │ └───────────────────────┘ │   │ (Idempotent │
 │                           │   │  #10)       │
 │ ┌───────────────────────┐ │   └─────────────┘
 │ │ Saga Orchestrator     │ │          │
 │ │ (#4) — evaluation     │ │   ┌──────▼──────┐
 │ └───────────┬───────────┘ │   │ TED Pub     │
 │             │             │   │ Notifications│
 │ ┌───────────▼───────────┐ │   │ Audit Log   │
 │ │ External Registries   │ │   │ Stats       │
 │ │ (Circuit Breaker #7)  │ │   │ Vector DB   │
 │ └───────────────────────┘ │   └─────────────┘
 │                           │
 │ ┌───────────────────────┐ │
 │ │ Cache-Aside (#8)      │ │
 │ │ CPV/NUTS/ref data     │ │
 │ └───────────────────────┘ │
 │                           │
 │ ┌───────────────────────┐ │
 │ │ ACL (#2) — legacy     │ │
 │ │ schema translation    │ │
 │ └───────────────────────┘ │
 │                           │
 │ Strangler Fig (#1):       │
 │ Routes per endpoint       │
 │ legacy ↔ modern           │
 └───────────────────────────┘
```

---

## Recommended Learning & Implementation Order

| Phase | Pattern | Effort | Risk | First PoC Target |
|-------|---------|--------|------|-------------------|
| **1** | Strangler Fig + ACL | Medium | Low | Route tender search to new service |
| **2** | Transactional Outbox | Low | Low | Reliable notifications on tender status change |
| **3** | Idempotent Consumers | Low | Low | Add to existing MDB consumers |
| **4** | CQRS read models | Medium | Low | Denormalized search view for public portal |
| **5** | Saga | High | Medium | Evaluation workflow orchestration |
| **6** | Circuit Breaker/Resilience | Medium | Low | External registry calls during evaluation |
| **7** | Cache-Aside | Low | Low | CPV/NUTS reference data |
| **8** | API Gateway / BFF | Medium | Medium | Separate public portal endpoint |
| **9** | Event Sourcing (selective) | High | Medium | Tender lifecycle aggregate |
| **10** | RAG/AI integration via events | Medium | Low | Semantic search over published tenders |

---

## Quick PoC You Can Start This Week

**Goal:** Add a transactional outbox + idempotent consumer to an existing tender status-change flow, without touching the legacy write path.

1. **Add `domain_event_outbox` and `processed_events` tables** to your existing PostgreSQL schema
2. **Inject outbox writes** into one existing `@Stateless` EJB that changes tender status (same JPA transaction)
3. **Deploy a `@Singleton @Startup` publisher** that polls outbox and sends to existing Artemis
4. **Wrap one existing MDB** with the `IdempotentEventConsumer` base class
5. **Observe:** You now have reliable, exactly-once event processing without changing any existing business logic

**Total code:** ~6 files, ~300 lines. Deployable on your existing WildFly 20. Zero new infrastructure.

---

*Document generated: 2026-03-01 13:27:07*  
*Source patterns: [Microsoft Azure Architecture Center](https://learn.microsoft.com/en-us/azure/architecture/browse/)*