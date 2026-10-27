# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

A Quarkus lab demonstrating **OIDC multitenancy for a REST API secured via the OAuth2 client
credentials flow**: each request carries a `tenant_id` header that selects which tenant's
identity-provider realm validates the bearer token, and a guard filter closes the resulting BOLA
(Broken Object Level Authorization) gap by rejecting any request whose `tenant_id` header
doesn't match the `tenant_id` claim actually inside the validated token.

## Commands

All commands use the Maven wrapper (`./mvnw`), no local Maven install required.

- `./mvnw quarkus:dev` — run in dev mode with live reload. Dev UI at `http://localhost:8080/q/dev/`.
- `./mvnw test` — run unit tests (no external services required).
- `./mvnw test -Dtest=ClassName` — run a single test class.
- `./mvnw test -Dtest=ClassName#methodName` — run a single test method.
- `./mvnw package` — build `target/quarkus-app/quarkus-run.jar` (run with `java -jar target/quarkus-app/quarkus-run.jar`).
- `./mvnw package -Dquarkus.package.jar.type=uber-jar` — build an über-jar instead.
- `./mvnw package -Dnative` — build a native executable (requires GraalVM), or add
  `-Dquarkus.native.container-build=true` to build it in a container instead.

**A running OIDC provider is required for the end-to-end auth flow** (dev mode, manual testing,
or any test that actually sends a bearer token): `DynamicTenantConfigResolver` points each known
tenant at `http://localhost:8180/realms/<tenant_id>` by default (configurable via
`tenant.oidc.base-url`). `docker-compose.yml` provides exactly that:

```shell
docker compose up -d                      # starts Keycloak on :8180, auto-imports both realms
docker compose logs -f keycloak            # watch startup / realm import
docker compose down                        # stop it (add -v to also wipe its H2 dev data)
```

It runs `quay.io/keycloak/keycloak:latest` in dev mode (`start-dev --import-realm`) and mounts
`keycloak/realms/*.json` into `/opt/keycloak/data/import`, which auto-imports an `acme` and a
`globex` realm on startup. Each realm has one confidential client, `backend-service` / secret
`secret` (matching `tenant.oidc.client-id`/`tenant.oidc.client-secret`), with
`serviceAccountsEnabled: true` for the client credentials grant and a hardcoded-claim protocol
mapper that stamps every token issued by that realm with `tenant_id` = the realm's own name.
Admin console: `http://localhost:8180` (`admin`/`admin`, via `KC_BOOTSTRAP_ADMIN_USERNAME`/
`_PASSWORD`). Without Keycloak running, requests with a known `tenant_id` header fail with
`OIDCException: OIDC Server is not available` (500) during OIDC discovery — that's this missing
dependency, not an application bug.

`./mvnw test` does **not** need Keycloak: `TenantResourceTest` exercises every status code
(200/400/401/403/404) with `@TestSecurity`/`@JwtSecurity`-fabricated identities instead of a
real token (see the note on `TokenClaims` below for why that requires reading claims off
`SecurityIdentity` rather than injecting `JsonWebToken`). See **"Two layers of defense"** below
for why those same 400/403 cases behave differently against the real Keycloak than they do in
that test.

### Exercising the API manually (verified against the docker-compose Keycloak)

```shell
# Client credentials grant against the "acme" tenant realm
ACME_TOKEN=$(curl -s -d grant_type=client_credentials \
  -d client_id=backend-service -d client_secret=secret \
  http://localhost:8180/realms/acme/protocol/openid-connect/token | jq -r .access_token)
GLOBEX_TOKEN=$(curl -s -d grant_type=client_credentials \
  -d client_id=backend-service -d client_secret=secret \
  http://localhost:8180/realms/globex/protocol/openid-connect/token | jq -r .access_token)

# Matching header/claim -> 200
curl -H "tenant_id: acme" -H "Authorization: Bearer $ACME_TOKEN" localhost:8080/api/tenant-info
curl -H "tenant_id: globex" -H "Authorization: Bearer $GLOBEX_TOKEN" localhost:8080/api/tenant-info

# No token at all -> 401
curl -H "tenant_id: acme" localhost:8080/api/tenant-info

# Unknown path -> 404
curl -H "tenant_id: acme" -H "Authorization: Bearer $ACME_TOKEN" localhost:8080/api/does-not-exist

# Missing tenant_id header -> 401, NOT 400 (see "Two layers of defense")
curl -H "Authorization: Bearer $ACME_TOKEN" localhost:8080/api/tenant-info

# acme's token + globex's header (a real BOLA attempt) -> 401, NOT 403 (see below):
# OIDC itself rejects it at signature/issuer validation, since "globex"'s realm can't
# verify a token signed by "acme"'s realm key.
curl -H "tenant_id: globex" -H "Authorization: Bearer $ACME_TOKEN" localhost:8080/api/tenant-info
```

## Architecture

**Tenant-aware OIDC config resolution** (`tenant/DynamicTenantConfigResolver.java`): implements
`io.quarkus.oidc.TenantConfigResolver`. Reads the `tenant_id` HTTP header and builds an
`OidcTenantConfig` on the fly (issuer, client id/secret) for that tenant via `TenantRegistry`,
rather than relying on tenants statically declared in `application.yml`. The default static
tenant is disabled (`quarkus.oidc.tenant-enabled: false`), so authentication only succeeds for
requests whose `tenant_id` header resolves to a known tenant; unknown/missing headers fall back
to `Uni.createFrom().nullItem()`, leaving the request unauthenticated.

**Tenant directory** (`tenant/TenantRegistry.java`): a stand-in for a real per-tenant config
store (database, config service, ...). Holds the known tenant ids (`acme`, `globex`) and maps
each to its OIDC realm URL under `tenant.oidc.base-url`. In production each tenant would likely
have a distinct issuer/client rather than sharing one Keycloak instance as this lab does.

**BOLA guard** (`security/TenantHeaderGuardFilter.java`): resolving which issuer validates a
token (above) only decides *how* to check a token's signature — it does not by itself prove the
caller holding a validly-signed token for tenant A is entitled to act as tenant B just because
they changed the `tenant_id` header. This `@Provider ContainerRequestFilter`, prioritized to run
immediately after authentication (`Priorities.AUTHENTICATION + 10`), skips anonymous requests
(`SecurityIdentity.isAnonymous()`) entirely — leaving 401-vs-public-endpoint decisions to
endpoint-level annotations like `@Authenticated` — then rejects authenticated requests with
`BadRequestException` (400) if the `tenant_id` header is missing, or `ForbiddenException` (403)
if it doesn't match the token's `tenant_id` claim.

**Two layers of defense (and why 400/403 are test-only with the current per-realm setup)**:
with one Keycloak realm per tenant and the default static OIDC tenant disabled
(`quarkus.oidc.tenant-enabled: false`), *tenant resolution itself* ends up enforcing most of
what this filter also checks, verified by running the actual flow against the docker-compose
Keycloak:

- No `tenant_id` header → no tenant config to validate against → the request never
  authenticates at all → `@Authenticated` → **401**, before the filter's own
  missing-header → 400 branch is reached.
- A real cross-tenant attempt (tenant A's valid token + tenant B's header) → the resolver picks
  tenant B's issuer/JWKS to validate a token signed by tenant A's key → signature/issuer
  verification itself fails → **401**, before the filter's claim-mismatch → 403 branch is
  reached.

So against this real IdP, the filter's 400 and 403 branches are presently unreachable dead code
for genuine attacks - the *existing* OIDC authentication step already closes those cases, one
layer earlier. They stay as a deliberate second layer of defense (and are exercised directly in
`TenantResourceTest` via `@TestSecurity`, which bypasses tenant resolution entirely): they'd
become the actual enforcement point if tenants ever ended up sharing one issuer/realm, or if a
default/fallback OIDC tenant were ever enabled. Switching to one shared realm for all tenants
would make 400/403 reachable via real HTTP, at the cost of per-tenant issuer isolation; that
tradeoff was deliberately left as-is in favor of keeping per-tenant realms.

**Reading claims without `@Inject JsonWebToken`** (`security/TokenClaims.java`): both the guard
filter and `TenantResource` read claims via `TokenClaims.get(securityIdentity, claimName)`
(casts `SecurityIdentity.getPrincipal()` to `JsonWebToken`) instead of injecting
`org.eclipse.microprofile.jwt.JsonWebToken` as a CDI bean. quarkus-oidc's own `JsonWebToken`
producer only populates claims when the request went through its real bearer-token validation
(it specifically checks for an access-token credential on the identity); it silently returns an
empty token otherwise — including for the fabricated identities `@TestSecurity`/`@JwtSecurity`
create in `TenantResourceTest`, which caused every claim lookup to return `null` until this was
switched to read the principal directly.

**Sample protected endpoint** (`TenantResource.java`, `/api/tenant-info`): `@Authenticated`
resource that echoes the validated token's `tenant_id`/issuer/subject claims — exists to exercise
the resolver + guard filter end-to-end, not a real business endpoint. It is currently the only
JAX-RS resource in the project.

**Tests** (`TenantResourceTest.java`): covers all five status codes the guard/endpoint can
produce, using `@TestSecurity`/`@JwtSecurity` (from the `quarkus-test-security-jwt` test
dependency) to fabricate an authenticated identity with a given `tenant_id` claim — no real OIDC
provider needed. 200 (header matches claim), 400 (authenticated but header missing), 401 (no
`@TestSecurity` at all → anonymous), 403 (header present but doesn't match the claim), 404
(authenticated request to a path with no matching resource).

**Unit tests for the tenant/security classes** (`TenantHeaderGuardFilterTest.java`,
`DynamicTenantConfigResolverTest.java`, `TenantRegistryTest.java`): plain JUnit tests (no
`@QuarkusTest`, no container boot) that exercise each class in isolation, mocking its
collaborators directly via the `quarkus-junit5-mockito` test dependency rather than going
through a real HTTP request or OIDC flow:

- `TenantHeaderGuardFilterTest` mocks `SecurityIdentity`/`ContainerRequestContext` and calls
  `filter()` directly, covering the same branches `TenantResourceTest` covers end-to-end
  (anonymous pass-through, missing/blank header → 400, claim mismatch/missing claim → 403,
  matching header/claim → no exception) plus a blank-header case.
- `DynamicTenantConfigResolverTest` mocks `RoutingContext`/`HttpServerRequest` and a mocked
  `TenantRegistry`, calling `resolve()` directly and awaiting the returned `Uni`: missing/blank
  header → `null`, known tenant header → the `OidcTenantConfig` the registry returned, unknown
  tenant header → `null`.
- `TenantRegistryTest` sets the registry's package-private config fields directly (same
  package, no setters) and asserts `findConfig(...)` builds the expected `OidcTenantConfig`
  for known tenants (`acme`, `globex`) and returns empty for unknown/blank tenant ids.
