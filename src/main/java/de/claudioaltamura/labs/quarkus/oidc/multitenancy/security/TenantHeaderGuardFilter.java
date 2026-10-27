package de.claudioaltamura.labs.quarkus.oidc.multitenancy.security;

import java.io.IOException;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;

/**
 * BOLA (Broken Object Level Authorization) guard: the {@code tenant_id} request header is what
 * {@link de.claudioaltamura.labs.quarkus.oidc.multitenancy.tenant.DynamicTenantConfigResolver} used to pick which
 * tenant's issuer to validate the token against, but a validly-signed token from tenant A's
 * issuer sent alongside tenant B's header would otherwise let a caller read/write tenant B's
 * data merely by changing a header. This filter closes that gap by requiring the header to
 * match the {@code tenant_id} claim actually present in the validated access token, rejecting
 * any mismatch with 403.
 *
 * <p>Runs right after authentication ({@code Priorities.AUTHENTICATION}), so the injected
 * {@link SecurityIdentity} reflects the validated token. Anonymous (unauthenticated) requests
 * are left alone here; endpoint-level security ({@code @Authenticated}/{@code @RolesAllowed})
 * is responsible for rejecting those with 401.
 */
@Provider
@Priority(Priorities.AUTHENTICATION + 10)
public class TenantHeaderGuardFilter implements ContainerRequestFilter {

    public static final String TENANT_HEADER = "tenant_id";
    public static final String TENANT_CLAIM = "tenant_id";

    @Inject
    SecurityIdentity securityIdentity;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (securityIdentity.isAnonymous()) {
            // No validated token on this request - nothing to compare the header against,
            // leave authorization of the request to the endpoint (e.g. @Authenticated -> 401).
            return;
        }

        String headerTenantId = requestContext.getHeaderString(TENANT_HEADER);
        if (headerTenantId == null || headerTenantId.isBlank()) {
            throw new BadRequestException("Missing required tenant_id header");
        }

        String tokenTenantId = TokenClaims.get(securityIdentity, TENANT_CLAIM);
        if (tokenTenantId == null || !headerTenantId.equals(tokenTenantId)) {
            throw new ForbiddenException(
                    "tenant_id header does not match the tenant_id claim of the access token");
        }
    }
}
