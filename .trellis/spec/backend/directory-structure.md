# Directory Structure

## Overview

The backend is a modular monolith organized by business feature. It deliberately does not use DDD, hexagonal architecture, or global technical-layer packages.

Each small feature keeps its HTTP, application, and persistence types together so a change can be understood without navigating separate global `controller/`, `service/`, and `mapper/` trees.

## Directory Layout

```text
backend/src/main/
├── java/com/aiworkbench/
│   ├── project/       project Controller, Service, Mapper, DTO, Row
│   ├── record/        work-record Controller, Service, Mapper, DTO, Row
│   ├── task/          task Controller, Service, Mapper, DTO, Row, enums
│   ├── ai/            model gateway and adapter
│   ├── status/        runtime status endpoint and probes
│   ├── config/        cross-feature Spring/MyBatis configuration
│   └── web/           cross-feature HTTP exception translation
└── resources/
    ├── com/aiworkbench/<feature>/*Mapper.xml
    └── db/migration/V*__*.sql
```

## Module Organization

- Create one package per business capability, such as `project`, `record`, or `task`.
- Keep Controller, Service, Mapper interface, request/response records, persistence Row, and small feature enums in that package.
- Keep MyBatis XML under the matching resource namespace.
- Put only genuinely cross-feature infrastructure in `config` or `web`.
- Services own transactions and business validation. Controllers translate HTTP input; Mappers own SQL access.
- Do not add domain aggregates, repository abstractions, ports/adapters, or extra mapping layers unless a future task explicitly changes this architectural decision.
- Do not reorganize features into global `controller`, `service`, `mapper`, or `dto` packages.

## Naming Conventions

- `<Feature>Controller`, `<Feature>Service`, `<Feature>Mapper`
- `Create<Feature>Request`, `Update<Feature>Request`, `<Feature>Response`
- `<Feature>Row` for package-private persistence projections
- `<Feature>Mapper.xml` matching the Java mapper namespace
- `V<number>__<description>.sql` for immutable Flyway migrations

## Examples

- `com.aiworkbench.project` is the reference for a simple lifecycle module.
- `com.aiworkbench.task` is the reference for a feature with enums, dynamic filters, and optimistic locking.

Prefer extracting a small shared helper only after two or more feature packages require the same stable behavior. Co-location is intentional; duplicating business rules across packages is not.
