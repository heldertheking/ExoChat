# API and Event Contracts

## API categories

1. Client-facing commands and queries.
2. Internal module/service APIs.
3. Domain events for asynchronous consumers.

Use Protocol Buffers as the canonical schema format. Use native gRPC for service-to-service and supported native clients. For browsers, choose a compatible gRPC-Web or Connect transport where useful, and use WebSocket for persistent realtime delivery.

## Example messaging contract

```protobuf
syntax = "proto3";
package exochat.messaging.v1;

service MessagingService {
  rpc SendMessage(SendMessageRequest) returns (SendMessageResponse);
  rpc GetHistory(GetHistoryRequest) returns (GetHistoryResponse);
}

message SendMessageRequest {
  string conversation_id = 1;
  string client_message_id = 2;
  string body = 3;
}

message SendMessageResponse {
  string message_id = 1;
  int64 sequence = 2;
  int64 created_at_unix_ms = 3;
}

message GetHistoryRequest {
  string conversation_id = 1;
  int64 after_sequence = 2;
  uint32 page_size = 3;
}
```

The `client_message_id` makes retries idempotent. A retry after a timeout should return the original message result rather than insert a duplicate.

## Event envelope

```protobuf
message DomainEvent {
  string event_id = 1;
  string event_type = 2;
  string event_version = 3;
  string aggregate_type = 4;
  string aggregate_id = 5;
  string community_id = 6;
  string actor_id = 7;
  int64 occurred_at_unix_ms = 8;
  string correlation_id = 9;
  string causation_id = 10;
  bytes payload = 11;
}
```

Prefer typed payload messages and a versioned contract catalog over opaque payloads with undocumented semantics.

## Initial event catalogue

- `identity.user.created`
- `community.created`
- `community.member.joined`
- `community.member.left`
- `community.role.updated`
- `messaging.message.created`
- `messaging.message.edited`
- `messaging.message.deleted`
- `messaging.reaction.added`
- `presence.user.online`
- `presence.user.offline`
- `activity.session.started`
- `activity.session.ended`
- `extension.installed`
- `extension.uninstalled`

## RabbitMQ conventions

Suggested exchanges:
- `exochat.domain` — internal domain events
- `exochat.integration` — external integration events
- `exochat.extension` — managed extension delivery

Each consumer owns its queue and processing lifecycle. Use routing keys based on event type and scope where appropriate.

## Delivery guarantees

- MariaDB transaction writes the domain state and outbox record atomically.
- Outbox publisher uses publisher confirms.
- Consumers acknowledge only after safely completing work.
- Assume at-least-once delivery.
- Every consumer must be idempotent using `event_id` or a domain-specific deduplication key.
- Configure bounded retries and dead-letter handling.
- Do not claim exactly-once delivery.
- Use per-conversation sequence numbers for message ordering; do not assume global event ordering.

## Event journal versus transport

RabbitMQ is transport, not the permanent event archive. Store durable audit/history records in MariaDB or a purpose-built append-only store. Build user-facing activity feeds as projections with their own retention and privacy rules.
