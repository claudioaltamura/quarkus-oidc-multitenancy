package com.caltaxy.labs.multitenancy;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;

/**
 * Tenant-scoped entity. Each tenant's rows live in that tenant's own Postgres
 * schema (see {@link com.caltaxy.labs.multitenancy.tenant.HeaderTenantResolver}),
 * so no explicit tenant discriminator column is needed here.
 */
@Entity
public class Customer extends PanacheEntity {
    public String name;
}
