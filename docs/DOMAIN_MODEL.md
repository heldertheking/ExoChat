# Domain Model and Data Ownership

## Core entities

| Entity | Key fields / purpose |
|---|---|
| User | Global account identity |
| Profile | Display name, avatar, profile preferences |
| Session | Device/session lifecycle, refresh token family |
| Community | Name, owner, visibility, settings |
| Membership | User-community relationship and state |
| Role | Community-defined permission bundle |
| MembershipRole | Role assignments |
| Space | Community-scoped communication or activity container |
| Conversation | Direct or group conversation |
| ConversationMember | Membership and read cursor |
| Message | Author, content, server sequence, timestamps |
| Reaction | User reaction to a message |
| Attachment | Object-storage key, metadata, ownership |
| OutboxEvent | Durable event awaiting publication |
| InstalledExtension | Extension version installed in a community |
| ExtensionGrant | Approved capabilities and scope |
| ActivitySession | Running activity and participants |
| AuditRecord | Security- and admin-relevant durable history |

## Ownership rules

- Identity owns global users and sessions.
- Community owns communities, memberships, roles and spaces.
- Messaging owns conversations, messages, reactions and per-conversation sequences.
- Content owns attachment metadata; binary data lives in object storage.
- Extensions own installation metadata and grants.
- Each module exposes application interfaces rather than allowing cross-module table access.

## Important database practices

- Use UUIDv7 or another time-sortable unique ID strategy.
- Use server-assigned sequence numbers for ordering within a conversation.
- Use cursor pagination; avoid large OFFSET scans for message history.
- Track each member's last-read sequence rather than scanning messages to calculate unread counts.
- Add indexes for common access paths: `(conversation_id, sequence)`, `(community_id, user_id)`, membership state and permission lookups.
- Enforce community scoping in service authorization and database constraints where practical.
- Use Flyway for schema migrations and jOOQ code generation.
- Keep attachment binaries outside PostgreSQL.

## Suggested message table fields

```sql
CREATE TABLE communication.message (
    id UUID PRIMARY KEY,
    conversation_id UUID NOT NULL,
    sequence BIGINT NOT NULL,
    author_user_id UUID NOT NULL,
    client_message_id UUID NOT NULL,
    body TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    edited_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    UNIQUE (conversation_id, sequence),
    UNIQUE (author_user_id, client_message_id)
);

CREATE INDEX message_history_idx
    ON communication.message (conversation_id, sequence DESC);
```

This is a starting sketch, not a complete production migration. The idempotency uniqueness scope should be finalized alongside the send API contract.
