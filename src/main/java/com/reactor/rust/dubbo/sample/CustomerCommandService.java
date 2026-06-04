package com.reactor.rust.dubbo.sample;

/**
 * Write-side provider contract used by the REST consumer sample.
 *
 * <p>The REST process forwards compact JSON command bytes to the provider.
 * The provider owns DB mutation, validation, and JSON response generation.</p>
 */
public interface CustomerCommandService {

    byte[] createCustomer(byte[] commandJson);

    byte[] patchCustomerSegment(long customerId, byte[] commandJson);

    byte[] patchCustomerStatus(long customerId, byte[] commandJson);

    byte[] deleteCustomer(long customerId, byte[] commandJson);
}
