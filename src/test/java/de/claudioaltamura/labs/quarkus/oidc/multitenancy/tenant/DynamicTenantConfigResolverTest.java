package de.claudioaltamura.labs.quarkus.oidc.multitenancy.tenant;

import java.util.Optional;

import io.quarkus.oidc.OidcRequestContext;
import io.quarkus.oidc.OidcTenantConfig;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.ext.web.RoutingContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plain unit test driving {@link DynamicTenantConfigResolver#resolve} directly against mocked
 * Vert.x/Quarkus collaborators, isolated from the real {@link TenantRegistry} (mocked here) and
 * from any actual OIDC discovery/HTTP call.
 */
class DynamicTenantConfigResolverTest {

    private static final String TENANT_HEADER = DynamicTenantConfigResolver.TENANT_HEADER;

    private TenantRegistry tenantRegistry;
    private DynamicTenantConfigResolver resolver;

    @BeforeEach
    void setUp() {
        tenantRegistry = mock(TenantRegistry.class);
        resolver = new DynamicTenantConfigResolver();
        resolver.tenantRegistry = tenantRegistry;
    }

    private RoutingContext routingContextWithHeader(String headerValue) {
        HttpServerRequest request = mock(HttpServerRequest.class);
        when(request.getHeader(eq(TENANT_HEADER))).thenReturn(headerValue);
        RoutingContext context = mock(RoutingContext.class);
        when(context.request()).thenReturn(request);
        return context;
    }

    @Test
    void missingHeader_resolvesToNull() {
        RoutingContext context = routingContextWithHeader(null);

        OidcTenantConfig result = resolver.resolve(context, (OidcRequestContext<OidcTenantConfig>) null)
                .await().indefinitely();

        assertNull(result);
    }

    @Test
    void blankHeader_resolvesToNull() {
        RoutingContext context = routingContextWithHeader("   ");

        OidcTenantConfig result = resolver.resolve(context, (OidcRequestContext<OidcTenantConfig>) null)
                .await().indefinitely();

        assertNull(result);
    }

    @Test
    void knownTenantHeader_resolvesToRegistryConfig() {
        OidcTenantConfig acmeConfig = OidcTenantConfig
                .authServerUrl("http://localhost:8180/realms/acme")
                .tenantId("acme")
                .build();
        when(tenantRegistry.findConfig("acme")).thenReturn(Optional.of(acmeConfig));
        RoutingContext context = routingContextWithHeader("acme");

        OidcTenantConfig result = resolver.resolve(context, (OidcRequestContext<OidcTenantConfig>) null)
                .await().indefinitely();

        assertSame(acmeConfig, result);
    }

    @Test
    void unknownTenantHeader_resolvesToNull() {
        when(tenantRegistry.findConfig("unknown")).thenReturn(Optional.empty());
        RoutingContext context = routingContextWithHeader("unknown");

        OidcTenantConfig result = resolver.resolve(context, (OidcRequestContext<OidcTenantConfig>) null)
                .await().indefinitely();

        assertNull(result);
    }
}
