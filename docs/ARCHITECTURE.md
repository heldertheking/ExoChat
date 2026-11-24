# ExoChat System Architecture

## Architectural approach

Use a **modular monolith first**, with event-driven integration boundaries. Extract a module into an independently deployed service only when it has a distinct scaling profile, operational boundary or ownership need.

This preserves a path to distributed services without imposing microservice operations on a solo developer.

## Logical service diagram

```mermaid
flowchart TB
    subgraph Clients["Client applications"]
      WEB["Web client<br/>React + TypeScript"]
      DESK["Desktop client<br/>Tauri (later)"]
      MOBILE["Mobile clients<br/>Future"]
      EXT["Third-party clients"]
    end

    EDGE["Edge / reverse proxy<br/>TLS · rate limits · routing"]

    subgraph Realtime["Realtime edge"]
      WS["Realtime Gateway<br/>WebSocket · sessions · fan-out"]
    end

    subgraph Core["ExoChat Core · Java / Spring Boot"]
      API["API boundary<br/>gRPC / Protobuf"]
      ID["Identity & Sessions"]
      COMMUNITY["Communities & Membership"]
      AUTHZ["Authorization & Roles"]
      MSG["Messaging & Conversations"]
      PRESENCE["Presence"]
      ACTIVITY["Activities & Extensions"]
      NOTIFY["Notification orchestration"]
      JOURNAL["Activity journal"]
    end

    subgraph Data["State and delivery infrastructure"]
      PG[("MariaDB<br/>authoritative state")]
      OUTBOX[("Transactional outbox")]
      MQ[("RabbitMQ<br/>domain event transport")]
      CACHE[("Valkey<br/>ephemeral presence/cache")]
      OBJ[("S3-compatible storage<br/>attachments")]
    end

    subgraph Consumers["Independent consumers / future services"]
      RT["Realtime event consumer"]
      EMAIL["Email / push delivery"]
      AUDIT["Audit & activity projection"]
      SEARCH["Search indexer"]
      EXTDEL["Extension delivery service"]
      ANALYTICS["Analytics / moderation"]
    end

    WEB --> EDGE
    DESK --> EDGE
    MOBILE --> EDGE
    EXT --> EDGE
    EDGE --> API
    EDGE --> WS

    API --> ID
    API --> COMMUNITY
    API --> AUTHZ
    API --> MSG
    API --> ACTIVITY
    MSG --> PG
    COMMUNITY --> PG
    ID --> PG
    AUTHZ --> PG
    MSG --> OUTBOX
    OUTBOX --> MQ
    MQ --> RT
    MQ --> EMAIL
    MQ --> AUDIT
    MQ --> SEARCH
    MQ --> EXTDEL
    MQ --> ANALYTICS
    RT --> WS
    WS <--> CACHE
    MSG --> OBJ
    PRESENCE <--> CACHE
    NOTIFY --> MQ
    JOURNAL --> PG
```

## Request path: send message

```mermaid
sequenceDiagram
    participant C as Client
    participant API as Messaging API
    participant Z as Authorization
    participant DB as MariaDB
    participant O as Outbox publisher
    participant MQ as RabbitMQ
    participant RT as Realtime consumer
    participant G as Realtime gateway
    participant R as Recipient client

    C->>API: SendMessage(client_message_id, conversation_id)
    API->>Z: Check membership and send permission
    Z-->>API: Allowed
    API->>DB: Transaction: insert message + outbox event
    DB-->>API: Commit
    API-->>C: message_id + sequence + server timestamp
    O->>DB: Read unpublished outbox records
    O->>MQ: Publish MessageCreated
    MQ-->>O: Publisher confirm
    O->>DB: Mark outbox record published
    MQ->>RT: Deliver event
    RT->>G: Fan-out to active recipient sessions
    G-->>R: MessageCreated
    R-->>G: Delivery acknowledgement (optional protocol)
```

The client acknowledgement is tied to the database commit, not to email, journal, search or extension consumers.

## Module boundaries

| Module | Owns |
|---|---|
| Identity | Users, credentials, sessions, account lifecycle |
| Community | Communities, memberships, invitations |
| Authorization | Roles, policies and permission evaluation |
| Messaging | Conversations, messages, reactions, sequence allocation |
| Content | Attachment metadata and upload authorization |
| Presence | Ephemeral online state and typing indicators |
| Notifications | Preferences and delivery orchestration |
| Journal | Durable user-facing activity projections and audit records |
| Activities | Activity sessions and lifecycle |
| Extensions | Manifests, installs, grants, versions |
| Integrations | External provider credentials and adapters |

## Initial deployment topology

Start with:
- `exochat-api`: Spring Boot application
- `exochat-worker`: outbox publisher and event consumers
- MariaDB
- RabbitMQ
- Valkey
- S3-compatible object storage
- Reverse proxy / TLS termination

The realtime gateway can initially be part of the Java deployment. Extract it into Rust only after load tests show a meaningful benefit.

## Communication rules

- Synchronous commands/queries use typed application APIs.
- Domain events represent facts that have already committed.
- No consumer is in the critical path for message persistence.
- Modules do not query another module's tables directly.
- Event consumers are idempotent.
- No global ordering assumption across RabbitMQ consumers.
- Use per-conversation sequence numbers for message ordering.
