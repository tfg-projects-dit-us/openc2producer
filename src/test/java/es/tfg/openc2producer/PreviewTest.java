package es.tfg.openc2producer;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class PreviewTest {
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> post(ProducerServer server, String path, String type, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + path))
                .header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    public void previewCoincideExactamenteConEnvioSinContactarAntes() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AtomicReference<String> received = new AtomicReference<>();
        var consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        consumer.createContext("/openc2/command", exchange -> {
            calls.incrementAndGet();
            received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"status\":501}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(501, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        consumer.start();
        try (var server = new ProducerServer("localhost", 0)) {
            server.start();
            String url = "http://localhost:" + consumer.getAddress().getPort();
            for (String operation : new String[] {"capabilities", "huntflows", "datasources", "investigate"}) {
                String params = operation.equals("investigate") ? "&username=ana%3D%2B%26%C3%B1&vigilance_level=CRITICAL" : "";
                int before = calls.get();
                var preview = post(server, "/api/preview", "application/x-www-form-urlencoded", "operation=" + operation + params);
                assertEquals(200, preview.statusCode());
                assertEquals(before, calls.get());
                assertTrue(preview.headers().firstValue("X-Consumer-Status").isEmpty());
                var json = new ObjectMapper().readTree(preview.body());
                assertFalse(json.has("commandId"));
                assertFalse(json.has("actuator"));
                if (!operation.equals("investigate")) {
                    assertEquals(2, json.size());
                    assertFalse(json.has("args"));
                } else {
                    assertEquals(3, json.size());
                    assertEquals(1, json.path("args").size());
                    assertEquals("username=ana=+&ñ", json.at("/args/th/huntargs/string_args/0").asText());
                }
                String body = operation.equals("investigate")
                        ? "consumerUrl=" + URLEncoder.encode(url, StandardCharsets.UTF_8) + params : url;
                String type = operation.equals("investigate") ? "application/x-www-form-urlencoded" : "text/plain";
                var response = post(server, "/api/" + operation, type, body);
                assertEquals(200, response.statusCode());
                assertEquals(before + 1, calls.get());
                assertEquals(preview.body(), received.get());
                assertEquals(preview.body(), response.headers().firstValue("X-OpenC2-Command").orElseThrow());
            }
        } finally {
            consumer.stop(0);
        }
    }

    @Test
    public void previewRechazaOpcionesNoAdmitidas() throws Exception {
        try (var server = new ProducerServer("localhost", 0)) {
            server.start();
            for (String fields : new String[] {"", "operation=unknown", "operation=investigate",
                    "operation=investigate&username=ana&vigilance_level=BAD",
                    "operation=capabilities&username=ana", "operation=huntflows&operation=datasources",
                    "operation=capabilities&consumerUrl=http://localhost:8080", "operation=%XX"}) {
                assertEquals(fields, 400, post(server, "/api/preview", "application/x-www-form-urlencoded", fields).statusCode());
            }
            assertEquals(415, post(server, "/api/preview", "text/plain", "operation=capabilities").statusCode());
            assertEquals(413, post(server, "/api/preview", "application/x-www-form-urlencoded", "x".repeat(4097)).statusCode());
            var get = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/preview")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(405, get.statusCode());
        }
    }

    @Test
    public void sirvePaginaDelPerfil() throws Exception {
        try (var server = new ProducerServer("localhost", 0)) {
            server.start();
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/perfil")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            assertTrue(response.headers().firstValue("Content-Type").orElseThrow().startsWith("text/html"));
            assertTrue(response.body().contains("csd02"));
            assertTrue(response.body().contains("Actuator Profile desarrollado"));
            assertTrue(response.body().contains("Cómo se forma un comando"));
        }
    }
}
