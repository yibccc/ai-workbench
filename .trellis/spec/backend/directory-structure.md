# Directory Structure

## Decision

The backend uses conventional three-layer architecture: controllers, service interfaces and implementations, and MyBatis mappers.

## Layout

```text
backend/src/main/java/com/aiworkbench/
├── controller/         HTTP entry points
├── service/            Service interfaces
│   └── impl/           Spring implementations and transaction boundaries
├── mapper/             MyBatis interfaces
├── dto/<feature>/      Request, response and service command records
├── entity/<feature>/   Persistence row projections
├── enums/              Status, priority and source-role values
├── exception/          Typed exceptions and HTTP advice
├── common/             PageResponse and PageQueries
├── ai/                 Model interfaces and SDK adapters
├── events/             WebSocket infrastructure
├── config/             Spring, recovery, profile guards and MyBatis configuration
└── e2e/                Explicit-profile deterministic gateways

backend/src/main/resources/
├── mapper/*Mapper.xml
└── db/migration/V*__*.sql
```

## Dependencies and transactions

- Controllers inject service interfaces. Implementations reside in `service.impl`, contain validation and use Mapper interfaces for SQL access.
- Input and Report orchestration/persistence remain separate Spring beans: first commit the request, call the model outside a transaction, then commit the validated result in a short transaction.
- Do not replace separate-bean transactions with self-invocation during refactoring.
- Mapper XML namespaces reference `com.aiworkbench.mapper.*`; result mappings reference `entity.*`. Configure `mybatis.mapper-locations=classpath*:mapper/*.xml`.
- Projection records crossing packages must be accessible. Do not duplicate rows merely to bypass package visibility.
- Synchronize Java imports, XML mappings, test packages/mocks, constructors and Spring scanning when moving classes.
- Retain immutable Flyway migrations during package-only refactors.

## Naming and examples

- `ProjectController -> ProjectService -> ProjectServiceImpl -> ProjectMapper` is the reference dependency chain.
- `CreateProjectRequest` and `ProjectResponse` live under `dto.project`; `ProjectRow` lives under `entity.project`.
- `common.PageQueries` scopes a single PageHelper query and clears thread-local state before DTO conversion. See [Pagination](pagination.md).
- Existing arrays and zero-based paged APIs remain compatible; introducing PageHelper does not authorize changing report date semantics or full source selection.

## Verification

Run real PostgreSQL integration tests after structural moves. Specifically retain transaction rollback, optimistic-lock, input fencing, report snapshot, mapper UUID and after-commit notification assertions. Compilation alone does not validate Spring transaction behavior.
