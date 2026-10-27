package de.claudioaltamura.labs.quarkus.oidc.multitenancy.tenant;

import io.quarkus.oidc.OidcRequestContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.quarkus.oidc.TenantConfigResolver;
import io.smallrye.mutiny.Uni;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Picks which tenant's OIDC configuration (issuer, client, ...) to validate the incoming
 * bearer token against, based on the {@code tenant_id} request header. The default static
 * tenant is disabled ({@code quarkus.oidc.tenant-enabled=false}), so every request must carry
 * a known {@code tenant_id} header or authentication fails.
 *
 * <p>Resolving the config this way only decides which issuer/JWKS to validate against; it does
 * not by itself prove the caller is entitled to that tenant's data. See
 * {@link de.claudioaltamura.labs.quarkus.oidc.multitenancy.security.TenantHeaderGuardFilter} for the BOLA check
 * that compares this header against the validated token's {@code tenant_id} claim.
 */
@ApplicationScoped
public class DynamicTenantConfigResolver implements TenantConfigResolver {

    public static final String TENANT_HEADER = "tenant_id";

    @Inject
    TenantRegistry tenantRegistry;

    @Override
    public Uni<OidcTenantConfig> resolve(RoutingContext context, OidcRequestContext<OidcTenantConfig> requestContext) {
        String tenantId = context.request().getHeader(TENANT_HEADER);
        if (tenantId == null || tenantId.isBlank()) {
            return Uni.createFrom().nullItem();
        }
        return tenantRegistry.findConfig(tenantId)
                .map(config -> Uni.createFrom().item(config))
                .orElseGet(() -> Uni.createFrom().nullItem());
    }
}
