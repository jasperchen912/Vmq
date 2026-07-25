package com.vone.mq.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

public final class HttpRequest {

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequest.class);
    private static final String NO_RESPONSE = "服务器无响应";
    private static final Duration REQUEST_TIMEOUT =
            Duration.ofSeconds(readTimeout("vmq.http.request-timeout-seconds", 30L));
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(
                    readTimeout("vmq.http.connect-timeout-seconds", 5L)))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private HttpRequest() {
    }

    public static String sendGet(String url, String param) {
        try {
            URI target = appendQuery(url, param);
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(target)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "*/*")
                    .header("User-Agent", "Vmq/modernized")
                    .GET()
                    .build();
            return send("GET", target, request);
        } catch (RuntimeException | URISyntaxException exception) {
            LOGGER.warn("GET callback target is invalid ({})",
                    exception.getClass().getSimpleName());
            return NO_RESPONSE;
        }
    }

    public static String sendPost(String url, String param) {
        try {
            URI target = URI.create(url);
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder(target)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Accept", "*/*")
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("User-Agent", "Vmq/modernized")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(
                            param == null ? "" : param,
                            StandardCharsets.UTF_8))
                    .build();
            return send("POST", target, request);
        } catch (RuntimeException exception) {
            LOGGER.warn("POST callback target is invalid ({})",
                    exception.getClass().getSimpleName());
            return NO_RESPONSE;
        }
    }

    public static String formEncode(Map<String, ?> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey())
                        + "="
                        + encode(String.valueOf(entry.getValue())))
                .collect(Collectors.joining("&"));
    }

    private static String send(
            String method,
            URI target,
            java.net.http.HttpRequest request) {
        try {
            HttpResponse<String> response = CLIENT.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return response.body();
            }
            LOGGER.warn("{} callback to {} returned HTTP {}",
                    method, safeTarget(target), response.statusCode());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOGGER.warn("{} callback to {} was interrupted", method, safeTarget(target));
        } catch (Exception exception) {
            LOGGER.warn("{} callback to {} failed ({})",
                    method, safeTarget(target), exception.getClass().getSimpleName());
        }
        return NO_RESPONSE;
    }

    private static URI appendQuery(String url, String param) throws URISyntaxException {
        URI base = URI.create(url);
        String additionalQuery = param == null ? "" : param;
        String currentQuery = base.getRawQuery();
        String combinedQuery = currentQuery == null || currentQuery.isEmpty()
                ? additionalQuery
                : additionalQuery.isEmpty() ? currentQuery : currentQuery + "&" + additionalQuery;
        return new URI(
                base.getScheme(),
                base.getRawAuthority(),
                base.getRawPath(),
                combinedQuery,
                base.getRawFragment());
    }

    private static String safeTarget(URI target) {
        StringBuilder value = new StringBuilder();
        if (target.getScheme() != null) {
            value.append(target.getScheme()).append("://");
        }
        value.append(target.getHost() == null ? "<unknown-host>" : target.getHost());
        if (target.getPort() >= 0) {
            value.append(':').append(target.getPort());
        }
        String path = target.getPath();
        value.append(path == null || path.isEmpty() ? "/" : path);
        return value.toString();
    }

    private static long readTimeout(String name, long fallback) {
        long configured = Long.getLong(name, fallback);
        return configured > 0 ? configured : fallback;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
