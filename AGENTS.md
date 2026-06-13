# Meant Agent Rules

These rules apply to the whole repository.

## Backend Architecture

The backend is a Spring Boot application under `backend/src/main/java/com/meant/api`.

Backend code must be organized by domain:

```text
com.meant.api
  common
    controller
    service
    repository
    entity
    constant
  module
    user
      controller
        request
        response
      service
        command
        dto
        query
        task
      repository
      entity
      constant
      exception
      properties
```

Use `common` for shared cross-cutting code such as Swagger/OpenAPI configuration, security, shared web configuration, shared exceptions, and other infrastructure that is not owned by a business module.

Use `module` for business logic modules. Each module, such as `user`, owns its own packages and should not leak internal entities or repositories into other modules.

## Domain Package Layers

Each domain module should use these package names when the layer exists:

- `controller`
- `controller.request`
- `controller.response`
- `service`
- `service.command`
- `service.dto`
- `service.query`
- `service.task`
- `repository`
- `entity`
- `constant`
- `exception`
- `properties`

Use `constant` for enums and stable domain constants.

Do not place business code directly in `com.meant.api` or directly under `com.meant.api.module`.

Keep each layer clean. Do not put service DTO records next to service classes; put them under `service.dto`. Scheduled jobs belong under `service.task`. Module-specific exceptions belong under `exception`. Module configuration properties belong under `properties`.

## Request Flow

Controllers receive HTTP requests and return HTTP responses.

Controller DTOs must live in:

- `controller.request`
- `controller.response`

Controllers must map request DTOs into command or query records before calling services.

Services must only accept command or query records when caller-supplied input is required. Do not pass controller request DTOs into services.

Commands and queries must be Java records and must live in:

- `service.command`
- `service.query`

Use commands for operations that create, update, delete, or otherwise change state.
Use queries for read-only operations.

Do not create empty command or query records just to satisfy ceremony. If an internal or scheduled service operation has no input, use a no-argument service method.

Do not create result records that are not used by real callers. If a scheduled or internal operation has no meaningful return value, make the service method `void` and verify behavior through persisted state or side effects in tests.

## Validation

Validation of service inputs belongs at the service boundary.

Service methods that accept commands or queries must validate them using Jakarta Bean Validation. Put validation annotations on the command/query record components and annotate the service method parameter with `@Valid`.

Controller request DTOs may also use validation annotations for HTTP boundary feedback, but service-layer command/query validation is required when commands and queries are used as service inputs.

## Mapping Rules

Mapping direction should be explicit:

- `controller.request` to `service.command` or `service.query` in the controller layer.
- Service results to `controller.response` in the controller layer, unless a dedicated mapper exists inside the same module.

Do not expose JPA entities directly from controllers.

## Persistence

JPA entities live in `entity`.
Spring Data repositories live in `repository`.

Database schema changes must use Liquibase changelogs under:

```text
backend/src/main/resources/db/changelog
```

Do not add Flyway migrations or Flyway dependencies.

Liquibase changelogs must be XML files. Do not add YAML changelogs.

Use `text` for string columns in database migrations. Do not use `varchar(255)`.

Do not add redundant JPA column metadata. If the database column name is the same as the field name after normal naming strategy conversion, omit `@Column(name = ...)`. Do not add `columnDefinition = "text"` just to mirror the migration.

Use `Instant` for persisted timestamps unless the domain specifically needs to retain an offset.

Keep transaction boundaries tight. Do not wrap slow remote I/O in a database transaction. Fetch remote data first, then perform the database replace/update inside a short transaction.

## Code Style

Use Lombok to remove boilerplate in classes. Prefer `@RequiredArgsConstructor` for constructor injection and Lombok getters/builders/constructors for JPA entities when appropriate. Do not manually write constructors, getters, or setters that Lombok can clearly provide.

Spring `@ConfigurationProperties` classes should be Java records, live in the module `properties` package when module-specific, use `@Validated`, and read all values from `application.yml`. Do not hardcode property defaults in the properties record.

Prefer simple, direct conversions. For example, if an API numeric value is represented as `Double` and the database field is `Integer`, null-check it and use `value.intValue()` when that matches the API shape.

Do not parse data only to serialize it back into the same storage format. If a dataset field is stored as raw text and the application does not use it structurally, keep it as the original string instead of creating DTOs, parsing it, and writing it back to JSON.

Avoid generic runtime exceptions in module code. Create module-specific exceptions in the module `exception` package and extend `RuntimeException` when a custom exception is needed.

## Tests

Keep tests aligned with the package being tested. Prefer focused service/controller tests for module behavior and a small application context test for bootstrapping.

Keep test names aligned with actual behavior. If code stores raw payloads, tests should say and assert raw payloads, not parsed payloads.

## Git Commits

Use this commit message format:

```text
<type>[optional scope]: <description>
```

Allowed types:

- `feat` for feature work
- `fix` for bug fixes
- `chore` for chores, configuration, and maintenance
- `docs` for documentation-only changes
- `refactor` for code restructuring without behavior changes
- `test` for adding or updating tests
- `build` for build system or dependency changes
- `ci` for CI/CD configuration
- `perf` for performance improvements
- `style` for formatting-only changes

Examples:

- `feat: add user preference search`
- `fix: handle missing merchant checkout URL`
- `docs: document Meant product specification`
- `feat(user): add preference commands`
