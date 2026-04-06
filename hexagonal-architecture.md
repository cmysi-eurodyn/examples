# Hexagonal Architecture (Ports & Adapters): Enterprise Overview

*Tailored for: Senior Java Developer & Team Lead modernizing legacy JEE7 government eProcurement systems*

**Author:** Charalampos Mysirlidis (cmysi-eurodyn)  
**Date:** 2026-03-20  
**References:** Alistair Cockburn's original pattern (2005), DDD (Evans 2003), "Get Your Hands Dirty on Clean Architecture" (Hombergs 2019)  
**Focus:** Java EE (Jakarta EE) / Spring Boot — Public Procurement Domain

---

## Table of Contents

1. [What is Hexagonal Architecture?](#1-what-is-hexagonal-architecture)
2. [Core Concepts](#2-core-concepts)
   - [Domain (Application Core)](#21-domain-application-core)
   - [Ports](#22-ports)
   - [Adapters](#23-adapters)
3. [The Hexagon Diagram](#3-the-hexagon-diagram)
4. [Why It Matters for Procurement Systems](#4-why-it-matters-for-procurement-systems)
5. [Mapping to Your Stack](#5-mapping-to-your-stack)
6. [Project Structure](#6-project-structure)
7. [Implementation: Tender Submission Use Case](#7-implementation-tender-submission-use-case)
   - [Domain Model](#71-domain-model)
   - [Inbound Port (Use Case Interface)](#72-inbound-port-use-case-interface)
   - [Outbound Ports](#73-outbound-ports)
   - [Application Service (Use Case Implementation)](#74-application-service-use-case-implementation)
   - [Inbound Adapter: REST](#75-inbound-adapter-rest)
   - [Inbound Adapter: Apache Camel (Message-Driven)](#76-inbound-adapter-apache-camel-message-driven)
   - [Outbound Adapter: JPA/Hibernate Persistence](#77-outbound-adapter-jpahibernate-persistence)
   - [Outbound Adapter: External Registry Verification](#78-outbound-adapter-external-registry-verification)
8. [Transaction Boundaries in Hexagonal Design](#8-transaction-boundaries-in-hexagonal-design)
9. [Testing Strategy](#9-testing-strategy)
10. [Hexagonal vs. Layered Architecture (Your Current State)](#10-hexagonal-vs-layered-architecture-your-current-state)
11. [Migration Path from Legacy JEE7 Monolith](#11-migration-path-from-legacy-jee7-monolith)
12. [Trade-offs and Pitfalls](#12-trade-offs-and-pitfalls)
13. [Integration with Other Patterns](#13-integration-with-other-patterns)
14. [Quick PoC You Can Start This Week](#14-quick-poc-you-can-start-this-week)

---

## 1. What is Hexagonal Architecture?

Hexagonal Architecture — also called **Ports and Adapters** — was introduced by **Alistair Cockburn in 2005**. The central idea is deceptively simple:

> *"Allow an application to equally be driven by users, programs, automated test or batch scripts, and to be developed and tested in isolation from its eventual run-time devices and databases."*

The shape "hexagon" has no inherent meaning beyond visually conveying that an application has **many equivalent sides** — each side representing a way to interact with the application, none privileged over another. The real value is in the structural rule it encodes:

**The domain model never depends on infrastructure. Infrastructure depends on the domain.**

This is a hard inversion of the traditional layered architecture, where the domain layer calls down into a data access layer, tightly coupling business logic to persistence technology.

---

## 2. Core Concepts

### 2.1 Domain (Application Core)

The **domain** (or application core) contains:

- **Domain Entities** — objects with identity and lifecycle (e.g., `Tender`, `Offer`, `EconomicOperator`, `EvaluationCriteria`)
- **Value Objects** — immutable descriptors without identity (e.g., `CpvCode`, `TenderStatus`, `MonetaryAmount`)
- **Domain Services** — stateless operations that don't belong to a single entity (e.g., `EvaluationScoringService`, `TenderPublicationRuleValidator`)
- **Application Services (Use Cases)** — orchestrate domain objects to fulfil a single business use case; the entry points called by inbound adapters

The domain core has **zero imports from infrastructure frameworks** — no `javax.persistence`, no Spring `@Autowired`, no Jakarta EE `@Stateless`. It is pure Java.

### 2.2 Ports

A **port** is a Java interface that defines a contract for how the domain interacts with the outside world. There are two kinds:

| Kind | Driven by | Direction | Purpose |
|------|-----------|-----------|---------|
| **Inbound Port** (Driving) | External actors (HTTP, message queue, batch) | Outside → Domain | Expresses what the application *can do* — use case API |
| **Outbound Port** (Driven) | Domain | Domain → Outside | Expresses what the domain *needs* — persistence, notifications, external services |

Inbound ports are interfaces that **application services implement**.  
Outbound ports are interfaces that **adapters implement**.

### 2.3 Adapters

An **adapter** is a concrete class that translates between the outside world's representation and the domain's representation, connecting through a port.

| Kind | Examples | Responsibility |
|------|----------|---------------|
| **Inbound Adapter** | JAX-RS resource, Camel route, EJB `@MessageDriven`, Spring Batch step | Receives external input, maps it to domain commands/queries, calls inbound port |
| **Outbound Adapter** | JPA repository, REST client, Artemis producer, Jasper report renderer | Implements outbound port; translates domain calls into infrastructure calls |

---

## 3. The Hexagon Diagram

```
                    ┌──────────────────────────────────────────────────────────┐
   REST Client      │                                                          │
   ──────────────►  │   ┌────────────────────────────────────────────────┐    │
   [JAX-RS Adapter] │   │                                                │    │
                    │   │           APPLICATION CORE (DOMAIN)            │    │
   Camel Route      │   │                                                │    │
   ──────────────►  │   │   ┌──────────────────────────────────────┐    │    │
   [Camel Adapter]  │   │   │         Application Services          │    │    │
                    │   │   │  (Use Case Implementations)           │    │    │
   Batch Job        │   │   │                                       │    │    │
   ──────────────►  │   │   │  ┌─────────────────────────────┐     │    │    │
   [Batch Adapter]  │   │   │  │       Domain Entities       │     │    │    │
                    │   │   │  │    Value Objects            │     │    │    │
       INBOUND      │   │   │  │    Domain Services          │     │    │    │
       PORTS  ─────►│   │   │  └─────────────────────────────┘     │    │    │
                    │   │   └──────────────────────────────────────┘    │    │
                    │   │                   │ Outbound Ports             │    │
                    │   └───────────────────┼────────────────────────────┘    │
                    │                       │                                  │
                    │                       ▼                                  │
                    │   ┌───────────────────────────────────────────────┐     │
                    │   │               OUTBOUND ADAPTERS                │     │
                    │   │                                                │     │
                    │   │  [JPA Adapter]   [REST Client]   [MQ Adapter] │     │
                    │   │  PostgreSQL      External API    Artemis JMS  │     │
                    │   └───────────────────────────────────────────────┘     │
                    └──────────────────────────────────────────────────────────┘
```

---

## 4. Why It Matters for Procurement Systems

Government eProcurement systems have a structural problem: the business logic — tender validation rules, evaluation scoring, award criteria checks, GDPR-related data handling — has **accumulated over years** inside service beans that are also responsible for persistence queries, notification dispatch, and XML marshalling. When a framework upgrade (JEE7 → Jakarta EE 10), a database change, or a new integration requirement arrives, these tangled responsibilities multiply the cost of change.

Hexagonal Architecture solves this specifically:

| Procurement Challenge | Hexagonal Solution |
|-----------------------|--------------------|
| Business rules need to be testable without a database (tender evaluation logic is complex, policy-driven) | Domain + application services are plain Java — JUnit tests run in milliseconds with no container |
| Multiple entry points to the same use case (REST API for e-operators, batch job for nightly deadlines, Camel route for AS4 eDelivery messages) | One use case / application service, multiple inbound adapters |
| External registries (national business registry, tax authority, ESPD verification) may change or be unavailable | Outbound port insulates domain from external service implementations; stub adapter for tests |
| Compliance audit requirement: "what business rules applied when this tender was evaluated?" | Domain core is stable, independently versioned, and independently testable |
| Gradual migration from EJB3 monolith | Extract use cases one by one behind ports; EJB becomes an adapter, then is replaced |
| JasperReports, Knowage BI integration | Reporting driven through an outbound port; report engine is just another adapter |

---

## 5. Mapping to Your Stack

```
YOUR CURRENT STACK                 HEXAGONAL ROLE
──────────────────────────────────────────────────────────────────────
EJB3 @Stateless SessionBean   →   Application Service (or interim adapter during migration)
EJB3 @Entity (JPA)            →   Outbound JPA Adapter + Domain Entity (separate)
Struts 1 Action               →   Inbound Adapter (to be replaced by JAX-RS or REST controller)
WildFly JNDI / @EJB inject    →   Container wiring (replaced by CDI or Spring DI)
Apache Camel 4.8.1 route      →   Inbound Adapter (AS4 / eDelivery / MQ messages → use case)
Artemis JMS                   →   Outbound Adapter (publish domain events) OR Inbound Adapter
JPA/Hibernate                 →   Outbound Persistence Adapter
PostgreSQL                    →   Database behind outbound adapter — domain never mentions it
JasperReports 6.5.1           →   Outbound Reporting Adapter (implements ReportPort)
Knowage BI                    →   Outbound BI Adapter or external consumer of events/views
Spring Boot (Camel module)    →   Host for adapters; domain remains framework-free
```

---

## 6. Project Structure

A Maven multi-module layout that enforces the dependency rule at the build level:

```
procurement-hexagonal/
├── domain/                          # Pure Java — zero framework dependencies
│   └── src/main/java/
│       └── eu/procurement/domain/
│           ├── model/               # Entities, Value Objects, Aggregates
│           ├── port/
│           │   ├── in/              # Inbound ports (use case interfaces)
│           │   └── out/             # Outbound ports (repository/service interfaces)
│           └── service/             # Application services (use case implementations)
│
├── adapter-rest/                    # JAX-RS or Spring MVC controllers
│   └── pom.xml  (depends on: domain)
│
├── adapter-persistence/             # JPA/Hibernate implementations of outbound ports
│   └── pom.xml  (depends on: domain)
│
├── adapter-messaging/               # Apache Camel routes, Artemis producers/consumers
│   └── pom.xml  (depends on: domain)
│
├── adapter-reporting/               # JasperReports adapter
│   └── pom.xml  (depends on: domain)
│
├── adapter-external-registry/       # REST clients for national/EU registries
│   └── pom.xml  (depends on: domain)
│
└── bootstrap/                       # WildFly/Spring Boot app assembly, CDI/DI wiring
    └── pom.xml  (depends on: all adapters)
```

**Enforced rule:** `domain/pom.xml` must have **no compile-scope dependencies** on any framework module. A Maven Enforcer rule or ArchUnit test can verify this automatically.

---

## 7. Implementation: Tender Submission Use Case

The following example walks through the complete hexagonal stack for the "Submit Tender Offer" use case — one of the highest-stakes operations in an eProcurement system.

### 7.1 Domain Model

```java
// domain/src/main/java/eu/procurement/domain/model/Offer.java

package eu.procurement.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root: an Offer submitted by an EconomicOperator for a Tender.
 * No JPA annotations — persistence mapping is the adapter's responsibility.
 */
public class Offer {

    private final OfferId id;
    private final TenderId tenderId;
    private final EconomicOperatorId operatorId;
    private OfferStatus status;
    private final Instant submittedAt;
    private final List<OfferDocument> documents;

    // Private constructor — use factory method to enforce invariants
    private Offer(OfferId id, TenderId tenderId, EconomicOperatorId operatorId,
                  Instant submittedAt, List<OfferDocument> documents) {
        this.id = id;
        this.tenderId = tenderId;
        this.operatorId = operatorId;
        this.status = OfferStatus.SUBMITTED;
        this.submittedAt = submittedAt;
        this.documents = List.copyOf(documents); // immutable
    }

    public static Offer submit(TenderId tenderId, EconomicOperatorId operatorId,
                               List<OfferDocument> documents, Instant now) {
        if (documents == null || documents.isEmpty()) {
            throw new DomainException("An offer must contain at least one document.");
        }
        return new Offer(OfferId.generate(), tenderId, operatorId, now, documents);
    }

    public void withdraw() {
        if (this.status != OfferStatus.SUBMITTED) {
            throw new DomainException(
                "Only SUBMITTED offers can be withdrawn. Current status: " + this.status);
        }
        this.status = OfferStatus.WITHDRAWN;
    }

    // Getters — no setters to protect invariants
    public OfferId getId()                     { return id; }
    public TenderId getTenderId()              { return tenderId; }
    public EconomicOperatorId getOperatorId()  { return operatorId; }
    public OfferStatus getStatus()             { return status; }
    public Instant getSubmittedAt()            { return submittedAt; }
    public List<OfferDocument> getDocuments()  { return Collections.unmodifiableList(documents); }
}
```

```java
// domain/src/main/java/eu/procurement/domain/model/OfferId.java

package eu.procurement.domain.model;

import java.util.UUID;

/** Strongly-typed value object — prevents mixing OfferId with TenderId at compile time. */
public record OfferId(UUID value) {
    public static OfferId generate() { return new OfferId(UUID.randomUUID()); }
    public static OfferId of(UUID value) { return new OfferId(value); }
}
```

### 7.2 Inbound Port (Use Case Interface)

```java
// domain/src/main/java/eu/procurement/domain/port/in/SubmitOfferUseCase.java

package eu.procurement.domain.port.in;

import eu.procurement.domain.model.OfferId;
import eu.procurement.domain.model.TenderId;
import eu.procurement.domain.model.EconomicOperatorId;
import eu.procurement.domain.model.OfferDocument;

import java.util.List;

/**
 * Inbound port: defines what this use case accepts and returns.
 * Implemented by the application service. Called by inbound adapters.
 */
public interface SubmitOfferUseCase {

    /**
     * Command object — a self-validating value carrier.
     * Using a nested record keeps the use case interface self-documenting.
     */
    record SubmitOfferCommand(
        TenderId tenderId,
        EconomicOperatorId operatorId,
        List<OfferDocument> documents
    ) {
        public SubmitOfferCommand {
            if (tenderId == null) throw new IllegalArgumentException("tenderId is required");
            if (operatorId == null) throw new IllegalArgumentException("operatorId is required");
            if (documents == null || documents.isEmpty())
                throw new IllegalArgumentException("At least one document required");
        }
    }

    /**
     * @return the ID of the newly created offer
     * @throws TenderNotFoundException if the tender does not exist or is not open for submissions
     * @throws OperatorNotVerifiedException if the economic operator fails registry verification
     * @throws DuplicateSubmissionException if this operator already submitted for this tender
     */
    OfferId submit(SubmitOfferCommand command);
}
```

### 7.3 Outbound Ports

```java
// domain/src/main/java/eu/procurement/domain/port/out/OfferRepository.java

package eu.procurement.domain.port.out;

import eu.procurement.domain.model.Offer;
import eu.procurement.domain.model.OfferId;
import eu.procurement.domain.model.TenderId;
import eu.procurement.domain.model.EconomicOperatorId;

import java.util.Optional;

/** Outbound port: persistence contract for Offer aggregate. */
public interface OfferRepository {
    void save(Offer offer);
    Optional<Offer> findById(OfferId id);
    boolean existsByTenderAndOperator(TenderId tenderId, EconomicOperatorId operatorId);
}
```

```java
// domain/src/main/java/eu/procurement/domain/port/out/TenderRepository.java

package eu.procurement.domain.port.out;

import eu.procurement.domain.model.Tender;
import eu.procurement.domain.model.TenderId;

import java.util.Optional;

public interface TenderRepository {
    Optional<Tender> findById(TenderId id);
}
```

```java
// domain/src/main/java/eu/procurement/domain/port/out/OperatorVerificationPort.java

package eu.procurement.domain.port.out;

import eu.procurement.domain.model.EconomicOperatorId;

/**
 * Outbound port for external operator eligibility verification.
 * Implementation calls the national business registry / tax authority.
 * Stub implementation used in tests and when registry is unavailable.
 */
public interface OperatorVerificationPort {
    VerificationResult verify(EconomicOperatorId operatorId);

    enum VerificationResult { ELIGIBLE, INELIGIBLE, REGISTRY_UNAVAILABLE }
}
```

```java
// domain/src/main/java/eu/procurement/domain/port/out/DomainEventPublisher.java

package eu.procurement.domain.port.out;

import eu.procurement.domain.model.DomainEvent;

/** Outbound port: publish domain events to the message broker (Artemis). */
public interface DomainEventPublisher {
    void publish(DomainEvent event);
}
```

### 7.4 Application Service (Use Case Implementation)

```java
// domain/src/main/java/eu/procurement/domain/service/SubmitOfferService.java

package eu.procurement.domain.service;

import eu.procurement.domain.model.*;
import eu.procurement.domain.port.in.SubmitOfferUseCase;
import eu.procurement.domain.port.out.*;

import java.time.Instant;

/**
 * Application Service: implements the SubmitOfferUseCase inbound port.
 * Orchestrates domain objects and calls outbound ports.
 *
 * No framework annotations here — CDI/Spring wiring happens in the bootstrap module.
 * This class can be instantiated in a plain JUnit test with mock port implementations.
 */
public class SubmitOfferService implements SubmitOfferUseCase {

    private final TenderRepository tenderRepository;
    private final OfferRepository offerRepository;
    private final OperatorVerificationPort verificationPort;
    private final DomainEventPublisher eventPublisher;

    public SubmitOfferService(TenderRepository tenderRepository,
                              OfferRepository offerRepository,
                              OperatorVerificationPort verificationPort,
                              DomainEventPublisher eventPublisher) {
        this.tenderRepository = tenderRepository;
        this.offerRepository = offerRepository;
        this.verificationPort = verificationPort;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public OfferId submit(SubmitOfferCommand command) {

        // 1. Load and validate tender state
        Tender tender = tenderRepository.findById(command.tenderId())
            .orElseThrow(() -> new TenderNotFoundException(command.tenderId()));

        if (!tender.isOpenForSubmissions()) {
            throw new TenderNotOpenException(command.tenderId(), tender.getStatus());
        }

        // 2. Prevent duplicate submissions
        if (offerRepository.existsByTenderAndOperator(command.tenderId(), command.operatorId())) {
            throw new DuplicateSubmissionException(command.tenderId(), command.operatorId());
        }

        // 3. Verify operator eligibility via external registry
        OperatorVerificationPort.VerificationResult verification =
            verificationPort.verify(command.operatorId());

        if (verification == OperatorVerificationPort.VerificationResult.INELIGIBLE) {
            throw new OperatorNotVerifiedException(command.operatorId());
        }
        // REGISTRY_UNAVAILABLE: policy decision — allow submission with pending flag
        // (regulatory requirement: don't block operators due to registry outages)

        // 4. Create the offer via domain factory method
        Offer offer = Offer.submit(
            command.tenderId(),
            command.operatorId(),
            command.documents(),
            Instant.now()
        );

        // 5. Persist
        offerRepository.save(offer);

        // 6. Publish domain event (downstream: notifications, audit log, BI)
        eventPublisher.publish(new OfferSubmittedEvent(
            offer.getId(), offer.getTenderId(), offer.getOperatorId(), offer.getSubmittedAt()
        ));

        return offer.getId();
    }
}
```

### 7.5 Inbound Adapter: REST

```java
// adapter-rest/.../OfferSubmissionResource.java

package eu.procurement.adapter.rest;

import eu.procurement.domain.model.*;
import eu.procurement.domain.port.in.SubmitOfferUseCase;
import eu.procurement.domain.port.in.SubmitOfferUseCase.SubmitOfferCommand;

import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.UUID;

/**
 * Inbound adapter: translates JAX-RS HTTP request → domain command → HTTP response.
 * The adapter is responsible for:
 *   - HTTP contract (path, method, media type)
 *   - Input validation / deserialization (DTO → Command)
 *   - Exception mapping (domain exceptions → HTTP status codes)
 *   - Output serialization (OfferId → location header / response body)
 *
 * It knows NOTHING about persistence, verification, or event publishing.
 */
@Path("/api/v1/tenders/{tenderId}/offers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class OfferSubmissionResource {

    @Inject
    private SubmitOfferUseCase submitOfferUseCase;

    @POST
    public Response submitOffer(
            @PathParam("tenderId") UUID tenderIdValue,
            SubmitOfferRequestDto dto) {

        // Map DTO → domain command
        SubmitOfferCommand command = new SubmitOfferCommand(
            TenderId.of(tenderIdValue),
            EconomicOperatorId.of(dto.operatorId()),
            dto.documents().stream()
               .map(d -> new OfferDocument(d.filename(), d.contentType(), d.contentBase64()))
               .toList()
        );

        OfferId offerId = submitOfferUseCase.submit(command);

        return Response.status(Response.Status.CREATED)
                       .entity(new SubmitOfferResponseDto(offerId.value()))
                       .build();
    }

    // DTOs are adapter-local — domain model is never serialized directly
    public record SubmitOfferRequestDto(UUID operatorId, List<DocumentDto> documents) {}
    public record DocumentDto(String filename, String contentType, String contentBase64) {}
    public record SubmitOfferResponseDto(UUID offerId) {}
}
```

### 7.6 Inbound Adapter: Apache Camel (Message-Driven)

```java
// adapter-messaging/.../OfferSubmissionCamelRoute.java

package eu.procurement.adapter.messaging;

import eu.procurement.domain.model.*;
import eu.procurement.domain.port.in.SubmitOfferUseCase;
import eu.procurement.domain.port.in.SubmitOfferUseCase.SubmitOfferCommand;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Inbound adapter: consumes AS4/eDelivery messages from Artemis queue.
 * Maps the message payload to the same SubmitOfferUseCase used by the REST adapter.
 *
 * The use case implementation is identical — only the transport mechanism differs.
 * This is the core value of Ports & Adapters: one domain, multiple entry points.
 */
@Component
public class OfferSubmissionCamelRoute extends RouteBuilder {

    @Autowired
    private SubmitOfferUseCase submitOfferUseCase;

    @Override
    public void configure() {
        // Error handling: send to dead-letter queue for manual review (procurement compliance)
        errorHandler(deadLetterChannel("artemis:queue:offer.submission.dlq")
            .maximumRedeliveries(3)
            .redeliveryDelay(2000));

        from("artemis:queue:offer.submission.inbound")
            .routeId("offer-submission-from-edelivery")
            .log("Received AS4 offer submission for tender ${header.tenderId}")
            .unmarshal().json(As4OfferMessageDto.class)
            .process(exchange -> {
                As4OfferMessageDto dto = exchange.getIn().getBody(As4OfferMessageDto.class);

                SubmitOfferCommand command = new SubmitOfferCommand(
                    TenderId.of(dto.tenderId()),
                    EconomicOperatorId.of(dto.operatorId()),
                    mapDocuments(dto.attachments())
                );

                OfferId offerId = submitOfferUseCase.submit(command);

                exchange.getIn().setHeader("X-Offer-Id", offerId.value().toString());
            })
            .to("artemis:queue:offer.submission.acknowledgement");
    }

    private List<OfferDocument> mapDocuments(List<As4AttachmentDto> attachments) {
        return attachments.stream()
            .map(a -> new OfferDocument(a.filename(), a.mimeType(), a.base64Content()))
            .toList();
    }
}
```

### 7.7 Outbound Adapter: JPA/Hibernate Persistence

```java
// adapter-persistence/.../JpaOfferRepository.java

package eu.procurement.adapter.persistence;

import eu.procurement.domain.model.*;
import eu.procurement.domain.port.out.OfferRepository;

import jakarta.persistence.*;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound adapter: implements the OfferRepository port using JPA/Hibernate + PostgreSQL.
 *
 * Key design decision: the JPA @Entity (OfferJpaEntity) is SEPARATE from the domain Offer.
 * This protects the domain model from JPA's requirements (@Id, no-arg constructor,
 * nullable fields for lazy loading) and from schema evolution coupling.
 *
 * Mapping between domain Offer ↔ OfferJpaEntity is this adapter's sole responsibility.
 *
 * Annotation choice:
 *  - Spring Boot: use @Repository (org.springframework.stereotype.Repository)
 *  - Jakarta EE / CDI (WildFly): use @ApplicationScoped (jakarta.enterprise.context.ApplicationScoped)
 * The example below uses @Repository for Spring Boot. Replace with @ApplicationScoped
 * and inject EntityManager via @PersistenceContext for a pure Jakarta EE deployment.
 */
@Repository  // Spring Boot — swap for @ApplicationScoped on WildFly/CDI
public class JpaOfferRepository implements OfferRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Offer offer) {
        OfferJpaEntity entity = toJpaEntity(offer);
        if (em.find(OfferJpaEntity.class, offer.getId().value()) == null) {
            em.persist(entity);
        } else {
            em.merge(entity);
        }
    }

    @Override
    public Optional<Offer> findById(OfferId id) {
        return Optional.ofNullable(em.find(OfferJpaEntity.class, id.value()))
                       .map(this::toDomain);
    }

    @Override
    public boolean existsByTenderAndOperator(TenderId tenderId, EconomicOperatorId operatorId) {
        Long count = em.createQuery(
                "SELECT COUNT(o) FROM OfferJpaEntity o " +
                "WHERE o.tenderId = :tid AND o.operatorId = :oid", Long.class)
            .setParameter("tid", tenderId.value())
            .setParameter("oid", operatorId.value())
            .getSingleResult();
        return count > 0;
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private OfferJpaEntity toJpaEntity(Offer offer) {
        OfferJpaEntity e = new OfferJpaEntity();
        e.setId(offer.getId().value());
        e.setTenderId(offer.getTenderId().value());
        e.setOperatorId(offer.getOperatorId().value());
        e.setStatus(offer.getStatus().name());
        e.setSubmittedAt(offer.getSubmittedAt());
        return e;
    }

    private Offer toDomain(OfferJpaEntity e) {
        // Reconstruct via package-private factory or reflection —
        // keep the domain constructor accessible only within the domain package
        return OfferReconstitutionFactory.reconstitute(
            OfferId.of(e.getId()),
            TenderId.of(e.getTenderId()),
            EconomicOperatorId.of(e.getOperatorId()),
            OfferStatus.valueOf(e.getStatus()),
            e.getSubmittedAt()
        );
    }
}
```

### 7.8 Outbound Adapter: External Registry Verification

```java
// adapter-external-registry/.../NationalRegistryVerificationAdapter.java

package eu.procurement.adapter.externalregistry;

import eu.procurement.domain.model.EconomicOperatorId;
import eu.procurement.domain.port.out.OperatorVerificationPort;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Outbound adapter: calls the national business/tax registry REST API.
 *
 * Circuit breaker (Resilience4j) wraps the call to prevent cascading failures
 * when the registry is under maintenance (common in government integrations).
 *
 * The domain has no knowledge of REST, Resilience4j, or HTTP status codes.
 * It only sees the OperatorVerificationPort contract.
 */
@Component
public class NationalRegistryVerificationAdapter implements OperatorVerificationPort {

    private final RestTemplate registryClient;
    private final String registryBaseUrl;

    public NationalRegistryVerificationAdapter(RestTemplate registryClient,
                                               String registryBaseUrl) {
        this.registryClient = registryClient;
        this.registryBaseUrl = registryBaseUrl;
    }

    @Override
    // @CircuitBreaker(name = "national-registry", fallbackMethod = "fallbackVerify")
    public VerificationResult verify(EconomicOperatorId operatorId) {
        try {
            RegistryResponseDto response = registryClient.getForObject(
                registryBaseUrl + "/operators/{id}/eligibility",
                RegistryResponseDto.class,
                operatorId.value()
            );
            return (response != null && response.eligible())
                ? VerificationResult.ELIGIBLE
                : VerificationResult.INELIGIBLE;
        } catch (RestClientException e) {
            return VerificationResult.REGISTRY_UNAVAILABLE;
        }
    }

    // Fallback when circuit is open
    @SuppressWarnings("unused")
    private VerificationResult fallbackVerify(EconomicOperatorId operatorId, Exception ex) {
        return VerificationResult.REGISTRY_UNAVAILABLE;
    }

    private record RegistryResponseDto(boolean eligible) {}
}
```

---

## 8. Transaction Boundaries in Hexagonal Design

This is a **critical concern in JEE/Spring systems** and a common source of confusion when adopting hexagonal architecture.

**Rule:** Transaction demarcation belongs at the **application service boundary**, not inside the domain model or inside the outbound adapter.

```java
// Option A: Spring @Transactional on the application service method (Spring Boot)
@Transactional
public OfferId submit(SubmitOfferCommand command) { ... }

// Option B: CDI @Transactional (Jakarta EE / WildFly)
@jakarta.transaction.Transactional
public OfferId submit(SubmitOfferCommand command) { ... }

// Option C: Programmatic — keep domain annotation-free, handle in bootstrap
// Wrap in TransactionTemplate (Spring) or UserTransaction (JEE)
```

**Why not in the adapter?**

If the REST adapter opens the transaction, a second inbound adapter (Camel route) must duplicate the same transaction logic. The application service is the single correct place — it owns the use case, and the transaction is a use case-level concern.

**Flush boundary with Hibernate:**

Hibernate's `EntityManager` flush happens before the transaction commits. In the hexagonal model:

- `offerRepository.save(offer)` → calls `em.persist()` (not flushed yet)
- `eventPublisher.publish(event)` → appends to the Transactional Outbox table (same transaction)
- Transaction commits → Hibernate flushes → both rows land atomically in PostgreSQL
- Outbox relay picks up the event and publishes to Artemis

This is the **Transactional Outbox** pattern integrated naturally into hexagonal design.

---

## 9. Testing Strategy

The hexagonal structure enables a fast, confidence-building test pyramid:

### Unit Tests — Domain Core (No Container, <1ms each)

```java
// Tests for pure domain logic — zero Spring, zero JPA, zero network
class OfferTest {

    @Test
    void submit_requiresAtLeastOneDocument() {
        assertThrows(DomainException.class, () ->
            Offer.submit(
                TenderId.of(UUID.randomUUID()),
                EconomicOperatorId.of(UUID.randomUUID()),
                List.of(),    // empty documents — must fail
                Instant.now()
            )
        );
    }

    @Test
    void withdraw_onlyAllowedFromSubmittedStatus() {
        Offer offer = Offer.submit(
            TenderId.of(UUID.randomUUID()),
            EconomicOperatorId.of(UUID.randomUUID()),
            List.of(new OfferDocument("espd.xml", "application/xml", "base64data")),
            Instant.now()
        );
        offer.withdraw();
        assertThrows(DomainException.class, offer::withdraw); // already withdrawn
    }
}
```

### Integration Tests — Application Service with Stub Ports

```java
class SubmitOfferServiceTest {

    // Stub adapters — simple in-memory implementations of outbound ports
    private final InMemoryOfferRepository offerRepository = new InMemoryOfferRepository();
    private final InMemoryTenderRepository tenderRepository = new InMemoryTenderRepository();
    private final AlwaysEligibleVerificationAdapter verificationPort =
        new AlwaysEligibleVerificationAdapter();
    private final CapturingEventPublisher eventPublisher = new CapturingEventPublisher();

    private final SubmitOfferService service = new SubmitOfferService(
        tenderRepository, offerRepository, verificationPort, eventPublisher
    );

    @Test
    void submit_savesOfferAndPublishesEvent_whenTenderIsOpenAndOperatorEligible() {
        TenderId tenderId = TenderId.of(UUID.randomUUID());
        tenderRepository.save(Tender.openTender(tenderId, "Supply of medical equipment"));

        OfferId offerId = service.submit(new SubmitOfferUseCase.SubmitOfferCommand(
            tenderId,
            EconomicOperatorId.of(UUID.randomUUID()),
            List.of(new OfferDocument("offer.pdf", "application/pdf", "data"))
        ));

        assertNotNull(offerId);
        assertTrue(offerRepository.findById(offerId).isPresent());
        assertEquals(1, eventPublisher.capturedEvents().size());
        assertInstanceOf(OfferSubmittedEvent.class, eventPublisher.capturedEvents().get(0));
    }

    @Test
    void submit_rejects_whenOperatorAlreadySubmitted() {
        TenderId tenderId = TenderId.of(UUID.randomUUID());
        EconomicOperatorId operatorId = EconomicOperatorId.of(UUID.randomUUID());
        tenderRepository.save(Tender.openTender(tenderId, "IT equipment"));

        var command = new SubmitOfferUseCase.SubmitOfferCommand(
            tenderId, operatorId,
            List.of(new OfferDocument("offer.pdf", "application/pdf", "data"))
        );
        service.submit(command); // first submission

        assertThrows(DuplicateSubmissionException.class, () -> service.submit(command));
    }
}
```

### Adapter Tests — Slice Tests

```java
// Test only the REST adapter in isolation (no domain logic required)
@WebMvcTest(OfferSubmissionResource.class)
class OfferSubmissionResourceTest {

    @MockBean
    private SubmitOfferUseCase submitOfferUseCase;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void post_returnsCreated_withOfferId() throws Exception {
        UUID offerId = UUID.randomUUID();
        when(submitOfferUseCase.submit(any())).thenReturn(OfferId.of(offerId));

        mockMvc.perform(post("/api/v1/tenders/{tid}/offers", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"operatorId":"%s","documents":[{"filename":"offer.pdf",
                    "contentType":"application/pdf","contentBase64":"data"}]}
                    """.formatted(UUID.randomUUID())))
               .andExpect(status().isCreated())
               .andExpect(jsonPath("$.offerId").value(offerId.toString()));
    }
}
```

### Full Integration Tests — Spring Boot Test with TestContainers

```java
// Boots full application context with a real PostgreSQL (Testcontainers)
// Only run in CI, not on every local build
@SpringBootTest
@Testcontainers
class OfferSubmissionIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SubmitOfferUseCase submitOfferUseCase;

    // ... full round-trip tests
}
```

**Test count ratio in a well-structured hexagonal codebase:**
- ~70% Unit tests (domain core) — milliseconds each, no infrastructure
- ~20% Integration tests (application service + stub adapters) — milliseconds each
- ~10% Full integration / E2E tests — seconds each, run in CI only

---

## 10. Hexagonal vs. Layered Architecture (Your Current State)

| Dimension | Traditional Layered (JEE7 Monolith) | Hexagonal (Ports & Adapters) |
|-----------|-------------------------------------|------------------------------|
| **Dependency direction** | Presentation → Business → Data | All layers → Domain Core |
| **Domain testability** | Needs container / mocked EntityManager | Plain JUnit, no container |
| **Multiple entry points** | Duplicated transaction/validation logic | One use case, multiple adapters |
| **Technology coupling** | Business logic knows about JPA entities, ResultSets | Domain knows only Java interfaces |
| **DB migration cost** | Changes ripple through all layers | Only the JPA adapter changes |
| **Framework upgrade** | High risk (JEE7 → Jakarta EE 10) | Low risk — only adapter modules |
| **Audit/compliance tracing** | Business logic scattered across beans | Business rules isolated in domain core |
| **Onboarding** | "Read all the EJBs to understand the domain" | Domain module tells the business story |

---

## 11. Migration Path from Legacy JEE7 Monolith

A full big-bang rewrite is not required or recommended. Hexagonal structure can be adopted incrementally alongside the **Strangler Fig** pattern:

### Phase 1 — Extract Interfaces (2–4 weeks, zero behavioural change)

Define port interfaces for existing EJBs. The EJB itself becomes the initial adapter.

```java
// New interface in domain module — defines what the application "needs"
public interface OfferRepository {
    void save(Offer offer);
    Optional<Offer> findById(OfferId id);
}

// Existing EJB now implements the interface (it is now an adapter, not core logic)
@Stateless
public class OfferEjb implements OfferRepository {
    @PersistenceContext EntityManager em;

    @Override
    public void save(Offer offer) { em.persist(toEntity(offer)); }
    // ...
}
```

### Phase 2 — Extract Domain Core (per aggregate, 1–2 weeks each)

Move validation, state machine logic, and invariant checks out of EJBs into plain Java domain classes. EJBs become thin adapters calling into the domain.

### Phase 3 — Replace Adapters (per use case, ongoing)

Once domain and ports are stable, replace adapter implementations one by one:
- EJB → Spring Boot application service (or CDI bean)
- Struts 1 Action → JAX-RS resource
- Direct JNDI lookup → CDI/Spring injection through port interface

### Phase 4 — Split into Services (optional, long-term)

If microservices are a goal, the hexagonal module boundaries become natural service boundaries. The domain module becomes a shared library or is copied into the new service.

---

## 12. Trade-offs and Pitfalls

### Legitimate Costs

| Cost | Mitigation |
|------|-----------|
| More classes/interfaces (port + adapter + domain entity + JPA entity per aggregate) | Use code generation or Lombok; the structural clarity pays for itself in a team |
| Mapping overhead (domain ↔ JPA entity ↔ DTO) | MapStruct for compile-time mapping; profiling shows this is rarely a bottleneck |
| Learning curve for developers used to anemic CRUD models | Start with one bounded context; let the pattern prove itself |

### Common Pitfalls to Avoid

1. **Leaking JPA annotations into the domain model.** If your `Tender.java` has `@Entity`, you have broken the primary constraint. Use a separate `TenderJpaEntity`.

2. **Fat application services.** If `SubmitOfferService` is 400 lines, domain logic has leaked upward. Push behaviour into domain entities and domain services.

3. **Anemic domain model.** Hexagonal ≠ DDD, but they pair naturally. A domain where `Offer` only has getters/setters and all logic lives in services is not hexagonal — it is just re-labelled layers.

4. **Putting transactions in the adapter.** If your JAX-RS resource is `@Transactional`, the Camel route that calls the same use case must also be `@Transactional` — you have duplicated transaction logic. Move it to the application service.

5. **One port per use case, taken too literally.** For simple read operations, a shared `ReadOfferPort` is fine. Don't create `FindOfferByIdPort`, `FindOffersByTenderPort`, `CountOffersPort` as separate interfaces unless they have meaningful independent variability.

6. **Exposing domain objects across adapter boundaries.** If your JAX-RS response body serializes a domain `Offer` directly, you have coupled your API contract to domain internals. Use adapter-local DTOs.

---

## 13. Integration with Other Patterns

Hexagonal Architecture is an **enabling structure** — it makes other patterns easier to apply correctly:

| Pattern | How Hexagonal Enables It |
|---------|--------------------------|
| **Transactional Outbox** | Outbox write is just another call in the application service; no framework coupling |
| **CQRS** | Separate inbound ports for commands vs. queries; separate outbound adapters for write/read models |
| **Event Sourcing** | EventStore is an outbound port; domain aggregates emit events without knowing the store technology |
| **Strangler Fig** | The port interface is the "strangling point" — legacy and modern implementations are interchangeable adapters |
| **Anti-Corruption Layer** | ACL is the outbound adapter — it translates between the external model and domain model at the port boundary |
| **Circuit Breaker** | Applied inside the outbound adapter only; domain never sees Resilience4j |
| **Saga (Orchestration)** | Saga orchestrator is an application service; each step calls an outbound port |

---

## 14. Quick PoC You Can Start This Week

Extract the **Tender Search** use case from the existing EJB monolith as a hexagonal slice. This is read-only, low-risk, and immediately demonstrates the value.

**Goal:** REST endpoint `/api/v1/tenders?cpv=33190000&status=OPEN` backed by hexagonal domain + JPA adapter, without touching the existing EJB.

**Step 1:** Create `domain` Maven module with zero framework dependencies.

```xml
<!-- domain/pom.xml — enforce no framework dependencies -->
<dependencies>
    <!-- intentionally empty: pure Java -->
</dependencies>

<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-enforcer-plugin</artifactId>
            <executions>
                <execution>
                    <id>enforce-no-framework-in-domain</id>
                    <goals><goal>enforce</goal></goals>
                    <configuration>
                        <rules>
                            <bannedDependencies>
                                <excludes>
                                    <exclude>org.springframework:*</exclude>
                                    <exclude>jakarta.persistence:*</exclude>
                                    <exclude>javax.persistence:*</exclude>
                                    <exclude>jakarta.ejb:*</exclude>
                                </excludes>
                            </bannedDependencies>
                        </rules>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**Step 2:** Define `SearchTendersUseCase` inbound port and `TenderSearchRepository` outbound port.

**Step 3:** Write `SearchTendersService` application service. Write unit tests — zero container startup.

**Step 4:** Write `JpaTenderSearchAdapter` implementing `TenderSearchRepository` using Spring Data JPA or a plain `EntityManager` query.

**Step 5:** Write `TenderSearchResource` JAX-RS adapter injecting `SearchTendersUseCase`.

**Step 6:** Run existing system alongside — the hexagonal slice is an additive change, not a replacement.

Measure: unit tests run in < 500ms total for this slice. Behavioural parity with the EJB query is verifiable by running both in parallel and comparing results.

---

*For questions, collaboration, or review: [cmysi-eurodyn on GitHub](https://github.com/cmysi-eurodyn)*
