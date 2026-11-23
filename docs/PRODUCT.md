# ExoChat Product Specification

## Vision

ExoChat is an open, programmable communication platform with hosted infrastructure. It provides the reliable communication primitives communities need while allowing developers and community administrators to shape the experience through extensions, activities and integrations.

**Positioning:** a communication platform that communities can extend, customize and build upon.

## Initial audience

Start with technical and gaming communities. They are likely to value developer APIs, custom workflows, realtime interaction and community-specific activities. Expand only after the core product has strong retention.

## Product pillars

### 1. Communication
- Text spaces
- Direct and group conversations
- Message replies/threads, edits, deletes and reactions
- Attachments
- Presence and typing indicators
- In-app notifications
- Reliable offline recovery

### 2. Communities
- Community profiles and branding
- Membership and invitations
- Roles and permission policies
- Space organization
- Moderation and audit history

### 3. Activities
A first-class interactive experience hosted within a community or conversation. Examples: polls, tournament brackets, shared planning, watch parties and learning activities.

### 4. Extensions
Developers can add commands, widgets, event consumers and external integrations through stable APIs and scoped capabilities.

### 5. Freedom and portability
- Publicly documented API contracts
- Data export
- Clear extension permissions
- Hosted service first; self-hosting and federation are future decisions, not MVP commitments

## MVP acceptance criteria

A release candidate is not ready until:
- A user can register, authenticate and revoke a session.
- A user can create a community and invite another user.
- Members can create and access a text space according to permissions.
- Two users can exchange messages in realtime.
- Messages survive service restarts.
- Retrying a send request does not create duplicate messages.
- Reconnecting clients can recover missed messages from a cursor.
- Unauthorized users cannot read or write private spaces.
- Basic moderation and abuse controls exist.
- Backups and restore procedures have been tested.

## Differentiation

1. Programmable communities rather than static server templates.
2. Native activities, not just links or embedded webpages.
3. Stable event and API contracts for third-party developers.
4. Community-level customization built from controlled UI components.
5. Data portability and transparent extension permissions.

## Non-goals for MVP

- End-to-end encrypted messaging
- Federation
- Full video conferencing
- Native mobile applications
- Arbitrary third-party code execution
- Public extension marketplace
- AI features as a core selling point

## Product success measures

Track:
- Weekly active communities
- Weekly active users per community
- Messages delivered successfully
- Message send-to-recipient p50/p95/p99 latency
- Reconnection recovery success
- 4-week community retention
- Number of communities using first-party extensions
- Developer time to build and install a sample extension
- Abuse reports and moderation response time
