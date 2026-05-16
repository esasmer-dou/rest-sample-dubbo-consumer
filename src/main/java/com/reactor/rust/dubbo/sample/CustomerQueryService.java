package com.reactor.rust.dubbo.sample;

/**
 * Database-backed provider contract used by this consumer sample.
 *
 * <p>The provider owns JDBC/Hikari/ActiveJDBC. The REST consumer receives
 * ready JSON bytes and forwards them without building another DTO graph.</p>
 */
public interface CustomerQueryService {
    byte[] getDatabaseCustomersJson();
}
