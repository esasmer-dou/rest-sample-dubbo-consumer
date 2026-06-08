package com.reactor.rust.dubbo.sample;

import com.reactor.rust.dubbo.sample.dto.CatalogInfo;
import com.reactor.rust.dubbo.sample.dto.CatalogItem;

import java.util.List;
import java.util.Map;

/**
 * Minimal Dubbo contract used by this consumer sample.
 *
 * <p>In production, put this interface in a shared API jar used by both the
 * provider and this REST consumer.</p>
 */
public interface NestedCatalogService {

    /**
     * Fast native path: no arguments, response is already JSON bytes.
     */
    byte[] getNestedCatalogJson();

    String getCatalogTitle();

    int countCatalogItems();

    CatalogInfo getCatalogInfo();

    List<CatalogItem> listFeaturedItems(int limit);

    Map<String, String> getCatalogAttributes();
}
