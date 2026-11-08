package org.example.gateway;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

final class StubServer implements AutoCloseable {

    record RecordedRequest(String method, String path, String body) {
    }

    enum Mode {OK, SLOW}

    private final HttpServer server;
    private final List<RecordedRequest> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger requestCount = new AtomicInteger();
    private volatile Mode mode = Mode.OK;

    StubServer() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String path = exchange.getRequestURI().getPath();
            requests.add(new RecordedRequest(exchange.getRequestMethod(), path, body));
            requestCount.incrementAndGet();

            if (mode == Mode.SLOW || path.endsWith("/slow")) {
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }

            int status;
            String response;
            if (path.endsWith("/99")) {
                status = 404;
                response = "{\"detail\":\"not found\"}";
            } else if ("POST".equals(exchange.getRequestMethod())) {
                status = 201;
                response = "{\"id\":2}";
            } else {
                status = 200;
                response = "[{\"id\":1,\"name\":\"Alex\"}]";
            }
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            try {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (IOException ignored) {
                // клиент (шлюз) уже оборвал соединение по таймауту
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    void setMode(Mode mode) {
        this.mode = mode;
    }

    int requestCount() {
        return requestCount.get();
    }

    List<RecordedRequest> requests() {
        return requests;
    }

    @Override
    public void close() {
        server.stop(0);
    }

    static String deadUrl() {
        try (StubServer s = new StubServer()) {
            return s.url();     // сервер сразу закрывается, порт остаётся свободным
        }
    }
}
