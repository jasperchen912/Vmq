package com.vone.mq.utils;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpRequestTest {

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/get", exchange ->
                respond(exchange, 200, exchange.getRequestURI().getRawQuery()));
        server.createContext("/post", exchange ->
                respond(exchange, 200, new String(
                        exchange.getRequestBody().readAllBytes(),
                        StandardCharsets.UTF_8)));
        server.createContext("/failure", exchange -> respond(exchange, 400, "rejected"));
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void reusesModernClientForGetAndPostRequests() {
        assertEquals("existing=1&name=%E5%BC%A0%E4%B8%89",
                HttpRequest.sendGet(
                        baseUrl + "/get?existing=1",
                        "name=张三"));
        assertEquals("name=张三",
                HttpRequest.sendPost(baseUrl + "/post", "name=张三"));
    }

    @Test
    void nonSuccessfulStatusHasStableFailureResult() {
        assertEquals("服务器无响应",
                HttpRequest.sendGet(baseUrl + "/failure", "secret=not-logged"));
    }

    private void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, response.length);
        exchange.getResponseBody().write(response);
        exchange.close();
    }
}
