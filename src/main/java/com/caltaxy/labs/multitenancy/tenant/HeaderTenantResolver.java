package com.caltaxy.labs.multitenancy.tenant;

import io.quarkus.hibernate.orm.PersistenceUnitExtension;
import io.quarkus.hibernate.orm.runtime.tenant.TenantResolver;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;

/**
 * Resolves the active Hibernate ORM tenant (= Postgres schema) from the
 * {@code X-Tenant-Id} request header. Requests without the header fall back
 * to the "base" schema.
 */
@PersistenceUnitExtension
@RequestScoped
public class HeaderTenantResolver implements TenantResolver {

    public static final String TENANT_HEADER = "X-Tenant-Id";
    public static final String DEFAULT_TENANT = "base";

    @Inject
    RoutingContext context;

    @Override
    public String getDefaultTenantId() {
        return DEFAULT_TENANT;
    }

    @Override
    public String resolveTenantId() {
        if (context == null) {
            return getDefaultTenantId();
        }
        String tenantId = context.request().getHeader(TENANT_HEADER);
        return (tenantId == null || tenantId.isBlank()) ? getDefaultTenantId() : tenantId;
    }
}
