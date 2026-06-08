package com.reactor.rust.dubbo.sample;

import com.reactor.rust.dubbo.sample.dto.CustomerStats;
import com.reactor.rust.dubbo.sample.dto.CustomerSummary;

import java.util.List;

/**
 * Database-backed provider contract used by this consumer sample.
 *
 * <p>The provider owns JDBC/Hikari/ActiveJDBC. The REST consumer receives
 * ready JSON bytes and forwards them without building another DTO graph.</p>
 */
public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();

    CustomerSummary getCustomer(long customerId);

    List<CustomerSummary> findCustomersBySegment(String segment, int limit);

    CustomerStats getCustomerStats();

    boolean customerExists(long customerId);

    String getCustomerDisplayName(long customerId);
}
