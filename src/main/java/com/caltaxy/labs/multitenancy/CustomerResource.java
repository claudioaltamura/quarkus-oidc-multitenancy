package com.caltaxy.labs.multitenancy;

import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

/**
 * Tenant is selected per request via the {@code X-Tenant-Id} header; see
 * {@link com.caltaxy.labs.multitenancy.tenant.HeaderTenantResolver}. No tenant
 * filtering is done here — Hibernate ORM routes each request to the matching
 * Postgres schema automatically.
 */
@Path("/customers")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class CustomerResource {

    @GET
    public List<Customer> listAll() {
        return Customer.listAll();
    }

    @POST
    @Transactional
    public Customer create(Customer customer) {
        customer.id = null;
        customer.persist();
        return customer;
    }
}
