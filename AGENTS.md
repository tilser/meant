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
  plugin
    spi
    support
    signing
    catalog
      search
        dto
      lookup
        dto
      getproduct
        dto
      common
        dto
        support
        exception
      extension
        shopify
          dto
    cart
    checkout
    order
    payment
    transport
      client
      dto
      profile
      registry
  provider
    shopify
      auth
      capability
      catalog
        dto
      order
      review
  module
    catalog
      service
        dto
        port
        support
      properties
    checkout
      service
        command
        dto
      repository
      entity
      exception
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

Use top-level `common` for application-wide cross-cutting code such as Swagger/OpenAPI configuration, security, shared web configuration, shared exceptions, and other infrastructure that is not owned by a business module or plugin.

Use top-level `plugin` only for UCP capability contracts, capability-specific wire DTOs/parsers/builders/extensions, and generic UCP runtime, transport, signing, and protocol support. `plugin.spi` is contract-only and owns UCP plugin contracts such as `UcpCapability`, `CapabilityId`, `NegotiatedCapabilities`, `CapabilityAdvertisement`, and `UcpToolResponse`. Keep `plugin.spi` and `plugin.support` flat while they remain small and contract/support-only.

`plugin.catalog` is split by catalog capability and shared protocol role. Capability implementations live in `catalog.search`, `catalog.lookup`, and `catalog.getproduct`; their capability-specific request/argument/response records live in each capability's `dto` subpackage. Shared UCP catalog wire metadata and wire response records live in `catalog.common.dto`, catalog JSON helpers in `catalog.common.support`, and protocol response exceptions in `catalog.common.exception`. Provider-specific UCP capability extensions live under `catalog.extension.<provider>` and contribute serialized extension arguments through the generic catalog extension registry. Generic catalog capabilities must not import or branch on a provider extension.

`plugin.transport` is split by generic UCP transport role: MCP clients and client exceptions in `transport.client`, transport wire records in `transport.dto`, generated agent profile serving and hashing in `transport.profile`, and capability lookup/registration in `transport.registry`. It must not contain Shopify-, Etsy-, or other commerce-provider authentication, clients, DTOs, profiles, or readiness adapters.

Plugins must not own business controllers, services, entities, repositories, state machines, or persistence. Plugin source must have zero dependencies on `com.meant.api.module` and `com.meant.api.provider`. `plugin.payment.shoppay` remains a plugin because `com.shopify.shop_pay` is an actual negotiated payment-handler capability, not a Shopify platform adapter.

Use top-level `provider` for concrete external commerce-platform integrations. For example, `provider.shopify` owns Shopify authentication and authorized transport, Global Catalog clients and wire contracts, normalization and identity policy, discovery/rehydration/data-use adapters, capability readiness, order webhooks, and proven Shopify-specific review adapters. Providers may depend on plugin contracts and module-owned public services, commands, queries, service DTOs, and ports. A provider must not directly access a module repository or entity.

Use `module` for Meant business domains, use cases, and state. `module.catalog` owns provider-neutral canonical catalog models, federation, exact grouping, ranking, diversity, retention, freshness, and rehydration orchestration. `module.checkout` owns checkout state, idempotency, consent, canary, totals, and workflow persistence. Merchant routing/onboarding, cart, orders, users, and reviews remain in their owning modules. A module must not import a concrete provider.

`module.catalog.service.port` is the intentional provider-inversion boundary for catalog discovery sources, data-use policies, rehydration providers, and offer identity strategies. `module.review.service.port` is the equivalent narrow boundary for concrete platform ID normalization. Keep ports limited to cases where external adapters materially require inversion; do not create port packages as ceremony.

Top-level `common` remains application-wide cross-cutting infrastructure only. Do not move provider or business-domain code there to bypass dependency rules.

The dependency direction is enforced:

- `plugin` imports neither `module` nor `provider`.
- `module` does not import `provider`.
- `provider` may implement module-owned ports and use plugin capabilities, but does not import module repositories or entities.
- No JPA entity or Spring Data repository belongs under `plugin`.
- No package `plugin.catalog.shopify` is allowed; actual Shopify UCP extensions belong under `plugin.catalog.extension.shopify` and platform adapters belong under `provider.shopify`.
- Generic catalog capabilities serialize extension contributions through the extension registry and never import provider-specific extension classes.

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

Public controller request and response DTOs must include Swagger/OpenAPI `@Schema` annotations on the record and meaningful record components so frontend clients can generate useful request and response types. Every public controller request/response field must set `requiredMode = Schema.RequiredMode.REQUIRED` or `requiredMode = Schema.RequiredMode.NOT_REQUIRED` explicitly.

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

Do not use `Object`, raw maps, or wildcard JSON bags in controller request/response DTOs, service commands, service queries, or service DTOs. Model payloads with concrete records/classes and typed fields. Use `Object` only at unavoidable generic transport or JSON adapter boundaries, and keep those conversions isolated from module request flow.

Avoid generic runtime exceptions in module code. Create module-specific exceptions in the module `exception` package and extend `RuntimeException` when a custom exception is needed.

## Tests

Keep tests aligned with the package being tested. Prefer focused service/controller tests for module behavior and a small application context test for bootstrapping.

Keep test names aligned with actual behavior. If code stores raw payloads, tests should say and assert raw payloads, not parsed payloads.

## Git Commits

Use this commit message format:

```text
<type>[optional scope]: <description>
```

If the work is associated with a Linear ticket, the ticket key (including the project prefix, e.g., MEA-123) must always be included before the standard commit message:

```text
<type>[TICKET_NUMBER]: <description>
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
- `MEA-123 - feat(user): add preference commands`
