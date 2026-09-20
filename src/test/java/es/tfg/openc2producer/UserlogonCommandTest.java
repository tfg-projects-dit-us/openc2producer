package es.tfg.openc2producer;

import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class UserlogonCommandTest {
    private static final String PREFIX = "{\"action\":\"investigate\",\"target\":{\"th\":{\"hunt\":\"userlogon\"}},\"args\":{\"th\":{\"huntargs\":{\"string_args\":[";

    @Test
    public void generaNivelesYOmitido() {
        assertEquals(PREFIX + "\"username=ana\"]}}}}", UserlogonCommand.build("ana", null));
        for (String level : new String[] {"STANDARD", "SUSPICIOUS", "CRITICAL"}) {
            assertEquals(PREFIX + "\"username=ana\",\"vigilance_level=" + level + "\"]}}}}",
                    UserlogonCommand.build("ana", level));
        }
    }

    @Test
    public void conservaValoresYEscapaJson() {
        assertEquals(PREFIX + "\"username= a=+&\\\"\\\\\\n\\u00F1\\uD83D\\uDE00 \"]}}}}",
                UserlogonCommand.build(" a=+&\"\\\nñ ", null));
        assertThrows(IllegalArgumentException.class, () -> UserlogonCommand.build(" ", null));
        assertThrows(IllegalArgumentException.class, () -> UserlogonCommand.build(null, null));
        assertThrows(IllegalArgumentException.class, () -> UserlogonCommand.build("ana", "INVALID"));
        assertThrows(IllegalArgumentException.class, () -> UserlogonCommand.build("ana", ""));
    }

    @Test
    public void formularioEnviaComandoYConserva501Y400() throws Exception {
        AtomicReference<String> received = new AtomicReference<>();
        var code = new java.util.concurrent.atomic.AtomicInteger(501);
        var consumer = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        consumer.createContext("/openc2/command", exchange -> {
            received.set(exchange.getRequestMethod() + "|" + exchange.getRequestHeaders().getFirst("Content-Type")
                    + "|" + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = ("{\"status\":" + code.get() + ",\"status_text\":\"<b>Sin ejecución</b>\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(code.get(), bytes.length);
            try (var out = exchange.getResponseBody()) { out.write(bytes); }
        });
        consumer.start();
        try (var server = new ProducerServer("localhost", 0); var client = HttpClient.newHttpClient()) {
            server.start();
            URI endpoint = URI.create("http://localhost:" + server.port() + "/api/investigate");
            String base = "consumerUrl=" + URLEncoder.encode("http://localhost:" + consumer.getAddress().getPort(), StandardCharsets.UTF_8);
            for (int status : new int[] {501, 400}) {
                code.set(status);
                String name = "ana=+&\"ñ";
                String fields = base + "&username=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&vigilance_level=SUSPICIOUS";
                var response = post(client, endpoint, fields);
                assertEquals(200, response.statusCode());
                assertEquals(Integer.toString(status), response.headers().firstValue("X-Consumer-Status").orElseThrow());
                assertEquals("{\"status\":" + status + ",\"status_text\":\"<b>Sin ejecución</b>\"}", response.body());
                String command = PREFIX + "\"username=ana=+&\\\"\\u00F1\",\"vigilance_level=SUSPICIOUS\"]}}}}";
                assertEquals(command, response.headers().firstValue("X-OpenC2-Command").orElseThrow());
                assertEquals("POST|application/openc2+json;version=1.0|" + command, received.get());
            }
            post(client, endpoint, base + "&username=ana");
            assertTrue(received.get().endsWith(PREFIX + "\"username=ana\"]}}}}"));
            for (String invalid : new String[] {"", "&username= ", "&username=ana&vigilance_level=BAD",
                    "&username=ana&username=b", "&username=ana&extra=x", "&username=%XX"}) {
                received.set(null);
                var response = post(client, endpoint, base + invalid);
                assertEquals(400, response.statusCode());
                assertTrue(response.headers().firstValue("X-Consumer-Status").isEmpty());
                assertNull(received.get());
            }
            assertEquals(405, client.send(HttpRequest.newBuilder(endpoint).build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        } finally {
            consumer.stop(0);
        }
    }

    private HttpResponse<String> post(HttpClient client, URI endpoint, String fields) throws Exception {
        return client.send(HttpRequest.newBuilder(endpoint)
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .POST(HttpRequest.BodyPublishers.ofString(fields)).build(), HttpResponse.BodyHandlers.ofString());
    }
}
