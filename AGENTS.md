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
        query
      repository
      entity
      constant
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
- `service.query`
- `repository`
- `entity`
- `constant`

Use `constant` for enums and stable domain constants.

Do not place business code directly in `com.meant.api` or directly under `com.meant.api.module`.

## Request Flow

Controllers receive HTTP requests and return HTTP responses.

Controller DTOs must live in:

- `controller.request`
- `controller.response`

Controllers must map request DTOs into command or query records before calling services.

Services must only accept command or query records. Do not pass controller request DTOs into services.

Commands and queries must be Java records and must live in:

- `service.command`
- `service.query`

Use commands for operations that create, update, delete, or otherwise change state.
Use queries for read-only operations.

## Validation

Validation of service inputs belongs at the service boundary.

Service methods that accept commands or queries must validate them using Jakarta Bean Validation. Put validation annotations on the command/query record components and annotate the service method parameter with `@Valid`.

Controller request DTOs may also use validation annotations for HTTP boundary feedback, but service-layer command/query validation is required because commands and queries are the only allowed service inputs.

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

## Tests

Keep tests aligned with the package being tested. Prefer focused service/controller tests for module behavior and a small application context test for bootstrapping.

## Git Commits

Use this commit message format:

```text
<type>/<commit description>
```

Allowed types:

- `f` for feature work
- `b` for bug fixes
- `c` for chores, documentation, configuration, and maintenance

Examples:

- `f/Add user preference search`
- `b/Fix merchant ranking filter`
- `c/Document Meant product specification`
