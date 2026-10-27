package com.caltaxy.labs.multitenancy;

import com.caltaxy.labs.multitenancy.security.TokenClaims;
import io.quarkus.security.Authenticated;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Sample tenant-scoped endpoint, protected by a bearer access token obtained via the OAuth2
 * client credentials grant. The caller must send the same tenant id both as the {@code
 * tenant_id} header (used to pick which tenant's OIDC issuer validates the token - see
 * {@link com.caltaxy.labs.multitenancy.tenant.DynamicTenantConfigResolver}) and as the {@code
 * tenant_id} claim inside that token (enforced by
 * {@link com.caltaxy.labs.multitenancy.security.TenantHeaderGuardFilter}).
 */
@Path("/api/tenant-info")
@Authenticated
public class TenantResource {

    @Inject
    SecurityIdentity securityIdentity;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public TenantInfo currentTenant() {
        String tenantId = TokenClaims.get(securityIdentity, "tenant_id");
        String issuer = TokenClaims.get(securityIdentity, "iss");
        return new TenantInfo(tenantId, issuer, securityIdentity.getPrincipal().getName());
    }

    public record TenantInfo(String tenantId, String issuer, String subject) {
    }
}
