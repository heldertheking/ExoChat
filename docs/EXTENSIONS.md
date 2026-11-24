# Extension Platform

## Strategy

Use a hybrid model, introduced in stages.

### WASM extensions
Suitable for bounded, sandboxed modules:
- Lightweight event handlers
- Commands
- Custom widgets
- Activity logic
- Controlled extension storage

### External integrations
Suitable for independently hosted services:
- Complex backend workloads
- External API adapters
- Large or long-running activities
- Custom infrastructure and storage

## Extension manifest sketch

```yaml
id: dev.example.polls
name: Community Polls
version: 1.0.0
apiVersion: v1

permissions:
  - community.read
  - members.read
  - messages.create

subscriptions:
  - community.member.joined

capabilities:
  - commands.register
  - activity.launch
  - activity.storage
```

Define a formal JSON Schema or protobuf manifest before implementation. The YAML above is illustrative only.

## Lifecycle

1. Developer packages extension and manifest.
2. Platform validates schema, API compatibility and package.
3. Publisher identity and package integrity are verified.
4. Community administrator selects a version.
5. Administrator reviews requested capabilities.
6. Platform creates scoped grants.
7. Runtime starts and receives authorized events/capabilities.
8. Extension can be monitored, updated, disabled or uninstalled.

## Security model

- Treat third-party code as untrusted.
- Default deny; grant only declared and approved capabilities.
- Scope grants to a community, installation and, where appropriate, user.
- Do not expose MariaDB credentials, RabbitMQ credentials or internal service tokens.
- Apply CPU, memory, execution-time and outbound-network limits.
- Sign or otherwise verify published packages.
- Support immediate revocation.
- Audit install, permission changes and uninstall actions.
- Prevent private conversation data from being delivered to community-scoped extensions without explicit authorization.

## SDK principles

The SDK should hide internal infrastructure. Developers subscribe to documented events and call authorized platform capabilities. Do not make direct RabbitMQ access part of the public extension contract.

## First-party extension examples

1. Polls
2. Community calendar
3. Tournament manager
4. Welcome/onboarding flow
5. Moderation dashboard

Launch with a few polished examples rather than an empty marketplace.
