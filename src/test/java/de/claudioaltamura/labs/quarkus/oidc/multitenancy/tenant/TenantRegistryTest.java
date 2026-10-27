package de.claudioaltamura.labs.quarkus.oidc.multitenancy.tenant;

import java.util.Optional;

import io.quarkus.oidc.OidcTenantConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Plain unit test: {@link TenantRegistry} has no CDI-specific behaviour beyond holding
 * injected config properties, so the fields (package-private, no setters) are set directly
 * instead of going through a {@code @QuarkusTest}/CDI container.
 */
class TenantRegistryTest {

    private TenantRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new TenantRegistry();
        registry.baseUrl = "http://localhost:8180";
        registry.clientId = "backend-service";
        registry.clientSecret = "secret";
    }

    @ParameterizedTest
    @ValueSource(strings = {"acme", "globex"})
    void knownTenant_returnsConfigBuiltFromProperties(String tenantId) {
        Optional<OidcTenantConfig> result = registry.findConfig(tenantId);

        assertTrue(result.isPresent());
        OidcTenantConfig config = result.get();
        assertEquals(Optional.of("http://localhost:8180/realms/" + tenantId), config.getAuthServerUrl());
        assertEquals(Optional.of(tenantId), config.getTenantId());
        assertEquals(Optional.of("backend-service"), config.getClientId());
        assertEquals(Optional.of("secret"), config.getCredentials().getSecret());
    }

    @ParameterizedTest
    @ValueSource(strings = {"unknown", "ACME", ""})
    void unknownTenant_returnsEmpty(String tenantId) {
        assertFalse(registry.findConfig(tenantId).isPresent());
    }
}
