package de.claudioaltamura.labs.quarkus.oidc.multitenancy;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.quarkus.test.security.jwt.Claim;
import io.quarkus.test.security.jwt.JwtSecurity;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;

/**
 * Exercises {@link TenantResource} without a real OIDC provider: {@code @TestSecurity} +
 * {@code @JwtSecurity} fabricate an authenticated request whose access token carries the given
 * claims, standing in for a token that quarkus-oidc would otherwise have validated against a
 * tenant's real issuer.
 */
@QuarkusTest
class TenantResourceTest {

    @Test
    @TestSecurity(user = "svc-acme", roles = {})
    @JwtSecurity(claims = {
            @Claim(key = "tenant_id", value = "acme")
    })
    void matchingTenantHeaderAndClaim_returns200() {
        given()
                .header("tenant_id", "acme")
                .when().get("/api/tenant-info")
                .then()
                .statusCode(200)
                .body("tenantId", Matchers.is("acme"));
    }

    @Test
    @TestSecurity(user = "svc-acme", roles = {})
    @JwtSecurity(claims = {
            @Claim(key = "tenant_id", value = "acme")
    })
    void missingTenantHeader_returns400() {
        given()
                .when().get("/api/tenant-info")
                .then()
                .statusCode(400);
    }

    @Test
    void noToken_returns401() {
        given()
                .header("tenant_id", "acme")
                .when().get("/api/tenant-info")
                .then()
                .statusCode(401);
    }

    @Test
    @TestSecurity(user = "svc-globex", roles = {})
    @JwtSecurity(claims = {
            @Claim(key = "tenant_id", value = "globex")
    })
    void tenantHeaderDoesNotMatchTokenClaim_returns403() {
        given()
                .header("tenant_id", "acme")
                .when().get("/api/tenant-info")
                .then()
                .statusCode(403);
    }

    @Test
    @TestSecurity(user = "svc-acme", roles = {})
    @JwtSecurity(claims = {
            @Claim(key = "tenant_id", value = "acme")
    })
    void unknownPath_returns404() {
        given()
                .header("tenant_id", "acme")
                .when().get("/api/does-not-exist")
                .then()
                .statusCode(404);
    }
}
