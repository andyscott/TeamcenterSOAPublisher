package com.googlecode.bushel.repo;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.url.URLRepository;
import org.apache.ivy.plugins.repository.url.URLResource;

public class RelativeURLRepository extends URLRepository {

    private final URL baseUrl;

    public RelativeURLRepository() {
        super();
        baseUrl = null;
    }

    public RelativeURLRepository(URL baseUrl) {
        super();
        this.baseUrl = baseUrl;
    }

    private Map<String, Resource> resourcesCache = new HashMap<String, Resource>();

    @Override
    public Resource getResource(String source) throws IOException {
        source = encode(source);
        Resource res = resourcesCache.get(source);
        if (res == null) {
            if (baseUrl == null) {
                res = new URLResource(new URL(source));
            } else {
                res = new URLResource(new URL(baseUrl + source));                
            }
            resourcesCache.put(source, res);
        }
        return res;
    }

    private static String encode(String source) {
        // TODO: add some more URL encodings here
        return source.trim().replaceAll(" ", "%20");
    }

}
