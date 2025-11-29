# Local Machine:

## Computational Helper Service:

**Purpose**: Helps in computational tasks such as operations on big files before sending optimized data to the main central service, it acts as an optional optimization gateway between the user and the central service for some key operations.

**Technologies**:
- REST API: Go
- POSSIBLE: Local Postgresql

---
---
---

# Central Server:

## Central Service:

**Purpose**: The main service in the automata ecosystem, handles the CRUD operations on all entities in the ecosystem, and acts as the routine publisher in the ecosystem, also handles the storage of all entity data plus routine results data.

**Technologies**:
- REST API: Java Spring Boot
- ORM: JPA
- Entities DB: Postgresql
- Fast Storage for high read endpoints needed for routines states and rate limits orchestration: REDIS
- Queue: RabbitMQ
- Routine Results DB: MongoDB

---
---
---

# Worker Server

## Supervisor

**Purpose**: Monitor its own machine performance, consume from queue when system resources allow and launch a seperate executer worker for handling the routine.

**Technologies**:
- Go or Python

## Job Executer Worker

**Purpose**: Execute routines and send or delegate the results back to the main central service.

**Technologies**: _DYNAMIC_