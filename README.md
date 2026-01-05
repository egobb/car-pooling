# Car Pooling Service

A concurrency-focused car pooling backend built with DDD and a hexagonal (ports & adapters) style.
This repo is intentionally single-node and in-memory so atomicity is not “delegated to the database” — it is implemented explicitly at the service boundary.

## What this project showcases

- Concurrency correctness in memory
    - Atomic seat allocation and consistent state transitions under concurrent requests.
    - A deliberately simple baseline model (coarse-grained locking) with clear trade-offs.
- DDD-first domain model
    - Small, explicit domain objects (`Car`, `Journey`) and domain-specific errors.
- Hexagonal architecture
    - HTTP is an adapter.
    - Persistence is behind ports (in-memory repositories in this implementation).
- Deterministic assignment
    - Best-fit car selection (min available seats that can still fit the group).
    - Pending journeys preserved in insertion order; “first pending that fits” gets assigned.

## Why focus on concurrency here?

In many distributed systems, atomicity often lives in:
- database transactions / constraints,
- optimistic/pessimistic locking,
- or external coordination.

Here, the goal is the opposite: make concurrency explicit and testable by keeping the state in memory and protecting invariants at the domain service boundary.

## Core rules implemented

- Cars have 4, 5 or 6 seats.
- Groups request journeys with 1 to 6 people.
- A group must ride together in a single car.
- If no car can accommodate them, they wait in a pending queue.
- Once assigned, groups keep their car until `/dropoff`.
- Fairness rule: preserve arrival order when possible; a later group can be served first only if earlier groups cannot fit anywhere.

## Architecture

High level:

- Domain
    - Models: `Car`, `Journey`
    - Business rules and invariants
- Application / Domain Service
    - `CarPoolingService` orchestrates operations (`resetCars`, `newJourney`, `dropoff`, `locate`)
    - Concurrency policy is enforced here
- Ports
    - `CarRepository`, `JourneyRepository`
- Adapters
    - Inbound: REST controller
    - Outbound: in-memory repositories

Mermaid sketch:

```mermaid
flowchart LR
  Client -->|HTTP| Controller
  Controller --> Service[CarPoolingService]
  Service --> CarRepo[CarRepository (port)]
  Service --> JourneyRepo[JourneyRepository (port)]
  CarRepo --> InMemCars[(In-memory)]
  JourneyRepo --> InMemJourneys[(In-memory)]
```

## Concurrency model (baseline)

Because the HTTP layer is multi-threaded and state is in memory, operations must be atomic to avoid races (overbooking seats, duplicated IDs, inconsistent reads).

Baseline approach: a single coarse-grained lock at the service boundary:

- Each public operation is executed atomically with respect to the in-memory state.
- Easy to reason about; correctness first.
- Trade-off: throughput is bounded by the single lock.

This repo is designed so I can later evolve the model (per-car locks, CAS-style updates, concurrent collections) and compare complexity vs. performance.

## Quick Start

### Prerequisites

- A recent JDK
- Maven

### Run locally (dev)

```bash
mvn spring-boot:run
```

Health check:

```bash
curl -i http://localhost:8080/status
```

### Run with Docker

A Docker Compose setup is included under `deploy/`.

```bash
docker compose -f deploy/docker-compose.yml up -d
```

## Testing

Run unit tests:

```bash
mvn test
```

Test coverage includes:
- Domain behaviour (assignment, pending queue, dropoff, locate)
- Repository behaviour (in-memory persistence expectations)
- Concurrency-focused tests to validate invariants under contention

## API

Base URL: `http://localhost:8080`

### `GET /status`

Readiness endpoint.

- `200 OK` when ready.

### `PUT /cars`

Resets all state and loads a new fleet.

Body (`application/json`):

```json
[
  { "id": 1, "seats": 4 },
  { "id": 2, "seats": 6 }
]
```

- `200 OK` on success
- `400 Bad Request` on invalid input (seat count, duplicates, malformed payload)

### `POST /journey`

Registers a journey request.

Body (`application/json`):

```json
{ "id": 1, "people": 4 }
```

- `202 Accepted` when the journey is registered (assigned immediately or queued)
- `400 Bad Request` on invalid group size / duplicate journey id / malformed payload

### `POST /dropoff`

Drops off a group.

Body (`application/x-www-form-urlencoded`): `ID=X`

- `204 No Content` on success
- `404 Not Found` if the journey does not exist
- `400 Bad Request` on malformed input

### `POST /locate`

Locates a group.

Body (`application/x-www-form-urlencoded`): `ID=X`
Accept: `application/json`

- `200 OK` + car payload if assigned
- `204 No Content` if waiting (registered, not assigned)
- `404 Not Found` if not registered
- `400 Bad Request` on malformed input

## Roadmap (next evolutions)

If you want to evolve this repo beyond the baseline:

- Fine-grained locking (per-car locks, reduced contention)
- Concurrent repositories (`ConcurrentHashMap`, lock-free patterns where appropriate)
- Dedicated stress/perf harness to compare approaches
- Replace in-memory state with external persistence and compare transactional atomicity vs. in-memory atomicity

## License

MIT — see `LICENSE`.
