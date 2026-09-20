package es.tfg.openc2producer;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class ProducerServerTest {
    private final HttpClient client = HttpClient.newHttpClient();

    private HttpResponse<String> post(ProducerServer server, String url) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/capabilities"))
                .header("Content-Type", "text/plain; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(url)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    @Test
    public void sirveRecursosYRechazaRutasYMetodosIncorrectos() throws Exception {
        try (var server = new ProducerServer("localhost", 0)) {
            server.start();
            for (String path : new String[] {"/", "/style.css", "/app.js"}) {
                var response = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + server.port() + path)).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertFalse(response.body().isBlank());
                assertTrue(response.headers().firstValue("Content-Type").orElseThrow().contains("UTF-8"));
                if ("/".equals(path)) {
                    assertTrue(response.body().contains("Consultar capacidades"));
                    assertTrue(response.body().contains("http://localhost:8080"));
                }
            }
            for (String path : new String[] {"/api/capabilities", "/no-existe", "/api/capabilities/extra", "/../pom.xml"}) {
                var response = client.send(HttpRequest.newBuilder(
                        URI.create("http://localhost:" + server.port() + path)).build(),
                        HttpResponse.BodyHandlers.ofString());
                assertEquals("/api/capabilities".equals(path) ? 405 : 404, response.statusCode());
            }
        }
    }

    @Test
    public void consultaConsumerConservandoCuerpoYEstadoSeparado() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        HttpServer consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        var status = new java.util.concurrent.atomic.AtomicInteger(200);
        // El cuerpo contiene HTML para comprobar que se transporta como texto sin alterarlo.
        String body = "<script>alert('á & 漢字')</script>";
        consumer.createContext("/openc2/command", exchange -> {
            received.set(exchange.getRequestMethod() + "\n"
                    + exchange.getRequestHeaders().getFirst("Content-Type") + "\n"
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        consumer.start();
        try (var server = new ProducerServer("localhost", 0)) {
            server.start();
            for (int code : new int[] {200, 400, 501}) {
                status.set(code);
                var response = post(server, "http://localhost:" + consumer.getAddress().getPort());
                assertEquals(200, response.statusCode());
                assertEquals(Integer.toString(code), response.headers().firstValue("X-Consumer-Status").orElseThrow());
                assertEquals("text/plain; charset=UTF-8", response.headers().firstValue("Content-Type").orElseThrow());
                assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElseThrow());
                assertEquals(body, response.body());
                assertEquals(OpenC2Producer.CAPACIDADES, response.headers().firstValue("X-OpenC2-Command").orElseThrow());
                assertEquals("POST\napplication/openc2+json;version=1.0\n" + OpenC2Producer.CAPACIDADES, received.get());
            }
        } finally {
            consumer.stop(0);
        }
    }

    @Test
    public void erroresLocalesNoInventanUnEstadoDelConsumer() throws Exception {
        try (var server = new ProducerServer("localhost", 0)) {
            server.start();
            var invalid = post(server, "file:///tmp");
            assertEquals(400, invalid.statusCode());
            assertTrue(invalid.headers().firstValue("X-Consumer-Status").isEmpty());
            int port;
            try (var socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
            var refused = post(server, "http://localhost:" + port);
            assertEquals(502, refused.statusCode());
            assertTrue(refused.headers().firstValue("X-Consumer-Status").isEmpty());
            assertTrue(refused.body().contains("conexión"));
            try (var socket = new ServerSocket(0)) {
                var timeout = post(server, "http://localhost:" + socket.getLocalPort());
                assertEquals(504, timeout.statusCode());
                assertTrue(timeout.headers().firstValue("X-Consumer-Status").isEmpty());
                assertTrue(timeout.body().contains("tiempo de espera"));
            }
        }
    }

    @Test
    public void validaFormatoYLongitudDeEntrada() throws Exception {
        try (var server = new ProducerServer("localhost", 0)) {
            server.start();
            assertEquals(413, post(server, "x".repeat(4097)).statusCode());
            var response = client.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + server.port() + "/api/capabilities"))
                    .POST(HttpRequest.BodyPublishers.ofString("{}")).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(415, response.statusCode());
        }
    }

    @Test
    public void consultaInvestigacionesSinFiltrosNiEjecucion() throws Exception {
        String command = "{\"action\":\"query\",\"target\":{\"th\":{\"huntflows\":{}}}}";
        String body = "{\"status\":200,\"results\":{\"th\":{\"huntflow_info\":[{\"path\":\"userlogon\",\"version\":\"1.0\",\"args_required\":{\"username\":\"string\"}}]}}}";
        comprobarConsultaCatalogo("/api/huntflows", command, body);
    }

    @Test
    public void consultaFuentesConCadenaVaciaSinFiltros() throws Exception {
        String command = "{\"action\":\"query\",\"target\":{\"th\":{\"datasources\":\"\"}}}";
        String body = "{\"status\":200,\"results\":{\"th\":{\"datasources\":[{\"ds_name\":\"endpoint_logs\",\"ds_tags\":[\"authentication\",\"windows\",\"linux\"]}]}}}";
        comprobarConsultaCatalogo("/api/datasources", command, body);
    }

    // Las dos consultas deben conservar el comando exacto y los errores del Consumer.
    private void comprobarConsultaCatalogo(String path, String command, String body) throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        var code = new java.util.concurrent.atomic.AtomicInteger(200);
        HttpServer consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        consumer.createContext("/openc2/command", exchange -> {
            received.set(exchange.getRequestMethod() + "\n"
                    + exchange.getRequestHeaders().getFirst("Content-Type") + "\n"
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = (code.get() == 200 ? body : "{\"status\":" + code.get() + "}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code.get(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        consumer.start();
        try (var server = new ProducerServer("localhost", 0)) {
            server.start();
            URI endpoint = URI.create("http://localhost:" + server.port() + path);
            for (int status : new int[] {200, 400, 501}) {
                code.set(status);
                var response = client.send(HttpRequest.newBuilder(endpoint)
                        .header("Content-Type", "text/plain; charset=UTF-8")
                        .POST(HttpRequest.BodyPublishers.ofString("http://localhost:" + consumer.getAddress().getPort()))
                        .build(), HttpResponse.BodyHandlers.ofString());
                assertEquals(200, response.statusCode());
                assertEquals(Integer.toString(status), response.headers().firstValue("X-Consumer-Status").orElseThrow());
                assertEquals(status == 200 ? body : "{\"status\":" + status + "}", response.body());
                assertEquals(command, response.headers().firstValue("X-OpenC2-Command").orElseThrow());
                assertEquals("POST\napplication/openc2+json;version=1.0\n" + command, received.get());
            }
            var get = client.send(HttpRequest.newBuilder(endpoint).build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(405, get.statusCode());
        } finally {
            consumer.stop(0);
        }
    }
}
