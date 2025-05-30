package com.demo.crawlerproject.url;

import java.net.URI;
import java.net.URL;

public class UrlCanonicalizer {
    public static String normalizeUrl(String href, String context) {
        try {
            URL baseUrl = context == null || context.isEmpty() ? null : new URL(context);
            URL url = baseUrl == null ? new URL(href) : new URL(baseUrl, href);

            String protocol = url.getProtocol().toLowerCase();
            String host = url.getHost().toLowerCase();

            int port = url.getPort();
            if ((protocol.equals("http") && port == 80) || (protocol.equals("https") && port == 443)) {
                port = -1;
            }

            String path = new URI(url.getPath()).normalize().toString();
            if (path.isEmpty()) {
                path = "/";
            }

            String query = url.getQuery();

            URI normalizedUri = new URI(
                    protocol,
                    null,
                    host,
                    port,
                    path,
                    query,
                    null
            );

            return normalizedUri.toASCIIString();

        } catch (Exception e) {
            return null;
        }
    }

}
