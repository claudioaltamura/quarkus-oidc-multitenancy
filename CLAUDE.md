# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A Quarkus lab demonstrating Hibernate ORM **schema-based multitenancy** on Postgres: each
tenant's rows live in their own Postgres schema, and Hibernate switches schema per-request
based on a resolver — no tenant-discriminator column or manual filtering in queries.

## Commands

All commands use the Maven wrapper (`./mvnw`), no local Maven install required.

- `./mvnw quarkus:dev` — run in dev mode with live reload. Dev UI at `http://localhost:8080/q/dev/`.
- `./mvnw test` — run unit tests.
- `./mvnw test -Dtest=CustomerResourceTest` — run a single test class.
- `./mvnw test -Dtest=CustomerResourceTest#methodName` — run a single test method.
- `./mvnw package` — build `target/quarkus-app/quarkus-run.jar` (run with `java -jar target/quarkus-app/quarkus-run.jar`).
- `./mvnw package -Dquarkus.package.jar.type=uber-jar` — build an über-jar instead.
- `./mvnw package -Dnative` — build a native executable (requires GraalVM), or add
  `-Dquarkus.native.container-build=true` to build it in a container instead.

**Docker must be running.** No datasource URL is configured in `application.yml`, so Quarkus
Dev Services provisions a Postgres container automatically for both `quarkus:dev` and `./mvnw
test`/`./mvnw verify`. If the Docker daemon isn't up, the build fails at the
`DevServicesDatasourceProcessor` step with "Please configure the datasource URL ... or ensure
the Docker daemon is up and running."

### Exercising multitenancy manually

```shell
curl -H "X-Tenant-Id: acme" -d '{"name":"Alice"}' -H 'Content-Type: application/json' localhost:8080/customers
curl -H "X-Tenant-Id: globex" localhost:8080/customers   # empty — separate schema from acme
curl localhost:8080/customers                             # no header -> "base" schema
```

## Architecture

**Tenant resolution** (`tenant/HeaderTenantResolver.java`): implements
`io.quarkus.hibernate.orm.runtime.tenant.TenantResolver`, annotated `@PersistenceUnitExtension`
and `@RequestScoped`. Reads the `X-Tenant-Id` HTTP header via the injected Vert.x
`RoutingContext`; falls back to the `base` schema when the header is absent. This is the single
place that decides which tenant a request belongs to — JAX-RS resources and Panache entities
stay tenant-agnostic.

**Schema per tenant, Flyway-owned** (`application.yml` + `db/migration/`): Hibernate's own
schema generation is disabled (`quarkus.hibernate-orm.schema-management.strategy: none`)
because schema creation is Flyway's job instead. `quarkus.flyway.schemas` lists every tenant
schema (`base,acme,globex`); Flyway applies every migration under
`src/main/resources/db/migration` to each of those schemas independently at startup
(`migrate-at-start: true`). Adding a tenant means adding its name to that list (and, in a real
deployment, provisioning the schema out-of-band) — it does not require a code change.
`quarkus.hibernate-orm.multitenant: SCHEMA` is what makes Hibernate actually route each
persistence operation to the schema named by `HeaderTenantResolver.resolveTenantId()`.

**Data access**: `Customer` (`Customer.java`) is a plain Panache active-record entity with no
tenant column — multitenancy is handled entirely at the schema/connection level, not in the
entity model. `CustomerResource` (`/customers`) does ordinary Panache calls
(`Customer.listAll()`, `persist()`); the active schema has already been chosen before the
query runs, by the resolver above.

**Unrelated scaffold code**: `GreetingResource` (`/hello`) and `GreetingConfig` are leftover
quarkus-maven-plugin boilerplate from project generation, not part of the multitenancy demo.
