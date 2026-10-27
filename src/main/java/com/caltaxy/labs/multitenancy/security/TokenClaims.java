package com.caltaxy.labs.multitenancy.security;

import io.quarkus.security.identity.SecurityIdentity;
import org.eclipse.microprofile.jwt.JsonWebToken;

/**
 * Reads a claim straight from {@link SecurityIdentity#getPrincipal()} instead of injecting
 * {@code org.eclipse.microprofile.jwt.JsonWebToken} as a CDI bean. quarkus-oidc's own
 * {@code JsonWebToken} producer only looks at a request that went through its real bearer-token
 * flow (it checks for an associated access-token credential) and otherwise silently hands back
 * an empty token - including for identities fabricated by {@code @TestSecurity}/
 * {@code @JwtSecurity} in tests, which don't carry that credential. Reading the principal
 * directly works uniformly for both a real validated token and a test-fabricated identity.
 */
public final class TokenClaims {

    private TokenClaims() {
    }

    public static String get(SecurityIdentity identity, String claimName) {
        if (identity.getPrincipal() instanceof JsonWebToken jwt) {
            return jwt.getClaim(claimName);
        }
        return null;
    }
}
