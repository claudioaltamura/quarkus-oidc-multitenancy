package de.claudioaltamura.labs.quarkus.oidc.multitenancy.security;

import io.quarkus.security.identity.SecurityIdentity;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ForbiddenException;
import jakarta.ws.rs.container.ContainerRequestContext;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Plain unit test driving {@link TenantHeaderGuardFilter#filter} directly against mocked
 * {@link SecurityIdentity}/{@link ContainerRequestContext} collaborators, exercising the same
 * four cases {@code TenantResourceTest} exercises end-to-end through the real JAX-RS stack.
 */
class TenantHeaderGuardFilterTest {

    private SecurityIdentity securityIdentity;
    private ContainerRequestContext requestContext;
    private TenantHeaderGuardFilter filter;

    @BeforeEach
    void setUp() {
        securityIdentity = mock(SecurityIdentity.class);
        requestContext = mock(ContainerRequestContext.class);
        filter = new TenantHeaderGuardFilter();
        filter.securityIdentity = securityIdentity;
    }

    private void givenTokenClaim(String tenantIdClaim) {
        JsonWebToken jwt = mock(JsonWebToken.class);
        when(jwt.getClaim(TenantHeaderGuardFilter.TENANT_CLAIM)).thenReturn(tenantIdClaim);
        when(securityIdentity.getPrincipal()).thenReturn(jwt);
    }

    @Test
    void anonymousRequest_isLeftAlone() throws Exception {
        when(securityIdentity.isAnonymous()).thenReturn(true);

        assertDoesNotThrow(() -> filter.filter(requestContext));
    }

    @Test
    void authenticatedWithoutTenantHeader_throwsBadRequest() {
        when(securityIdentity.isAnonymous()).thenReturn(false);
        when(requestContext.getHeaderString(TenantHeaderGuardFilter.TENANT_HEADER)).thenReturn(null);

        assertThrows(BadRequestException.class, () -> filter.filter(requestContext));
    }

    @Test
    void authenticatedWithBlankTenantHeader_throwsBadRequest() {
        when(securityIdentity.isAnonymous()).thenReturn(false);
        when(requestContext.getHeaderString(TenantHeaderGuardFilter.TENANT_HEADER)).thenReturn("   ");

        assertThrows(BadRequestException.class, () -> filter.filter(requestContext));
    }

    @Test
    void tenantHeaderMismatchesTokenClaim_throwsForbidden() {
        when(securityIdentity.isAnonymous()).thenReturn(false);
        when(requestContext.getHeaderString(TenantHeaderGuardFilter.TENANT_HEADER)).thenReturn("acme");
        givenTokenClaim("globex");

        assertThrows(ForbiddenException.class, () -> filter.filter(requestContext));
    }

    @Test
    void missingTokenClaim_throwsForbidden() {
        when(securityIdentity.isAnonymous()).thenReturn(false);
        when(requestContext.getHeaderString(TenantHeaderGuardFilter.TENANT_HEADER)).thenReturn("acme");
        givenTokenClaim(null);

        assertThrows(ForbiddenException.class, () -> filter.filter(requestContext));
    }

    @Test
    void tenantHeaderMatchesTokenClaim_passesThrough() {
        when(securityIdentity.isAnonymous()).thenReturn(false);
        when(requestContext.getHeaderString(TenantHeaderGuardFilter.TENANT_HEADER)).thenReturn("acme");
        givenTokenClaim("acme");

        assertDoesNotThrow(() -> filter.filter(requestContext));
    }
}
