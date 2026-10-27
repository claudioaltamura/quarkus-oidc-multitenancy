package com.caltaxy.labs.multitenancy.tenant;

import java.util.Optional;
import java.util.Set;

import io.quarkus.oidc.OidcTenantConfig;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Stand-in for a real tenant directory (database, config service, ...). Maps a tenant id to
 * the OIDC settings of that tenant's identity provider realm. Every known tenant here shares
 * the same Keycloak instance/client for lab purposes; in production each tenant could point at
 * a completely different issuer and client.
 */
@ApplicationScoped
public class TenantRegistry {

    private static final Set<String> KNOWN_TENANTS = Set.of("acme", "globex");

    @ConfigProperty(name = "tenant.oidc.base-url")
    String baseUrl;

    @ConfigProperty(name = "tenant.oidc.client-id")
    String clientId;

    @ConfigProperty(name = "tenant.oidc.client-secret")
    String clientSecret;

    public Optional<OidcTenantConfig> findConfig(String tenantId) {
        if (!KNOWN_TENANTS.contains(tenantId)) {
            return Optional.empty();
        }
        OidcTenantConfig config = OidcTenantConfig
                .authServerUrl(baseUrl + "/realms/" + tenantId)
                .tenantId(tenantId)
                .clientId(clientId)
                .credentials(clientSecret)
                .build();
        return Optional.of(config);
    }
}
