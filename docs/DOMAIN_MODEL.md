# Domain Model and Data Ownership

## Core entities

| Entity | Key fields / purpose |
|---|---|
| User | Root login identity (`users/{user}`); owns several Accounts |
| Account | Public persona (`users/{user}/accounts/{account}`): username, display name, avatar, status |
| Relationship | Friend/pending/blocked link between two Accounts |
| Rule | Named action (`SEND_MESSAGES`, ...) that platform, server roles and channel overwrites allow or deny in layers (ADR-013) |
| Session | One per logged in device (`users/{user}/sessions/{session}`): refresh token family, active account, device label (ADR-012) |
| Server | Community (`servers/{server}`): name, icon, owner account |
| Member | Account-server relationship (`servers/{server}/members/{member}`), nickname, role assignments |
| Role | Server-defined set of rule decisions, ranked by position |
| Invite | Shareable join code for a server |
| Channel | Unified conversation container, with a scope (DIRECT, GROUP, SERVER) and a type (TEXT, VOICE, FORUM, STAGE, ANNOUNCEMENT). Canonical name `channels/{channel}`, server channels also addressable as `servers/{server}/channels/{channel}` |
| ChannelParticipant | Participant and read cursor of a direct or group channel |
| ChannelOverwrite | Per-channel rule decisions for a Role or an Account |
| Message | Author, content, optional reply, timestamps (`channels/{channel}/messages/{message}`) |
| Reaction | User reaction to a message |
| Attachment | Object-storage key, metadata, ownership |
| OutboxEvent | Durable event awaiting publication |
| InstalledExtension | Extension version installed in a community |
| ExtensionGrant | Approved capabilities and scope |
| ActivitySession | Running activity and participants |
| AuditRecord | Security- and admin-relevant durable history |

## Ownership rules

- Identity owns users, accounts, relationships and sessions.
- Community owns servers, members, roles and invites.
- Messaging owns channels (including server channels, which reference their server), participants, overwrites, messages, reactions and per-channel sequences.

Proto packages, `libs/api` packages and these module names are the same: `identity`, `community`, `messaging`.
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
