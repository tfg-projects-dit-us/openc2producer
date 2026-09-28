package us.dit.ueba.openc2producer;

import com.sun.net.httpserver.HttpServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import us.dit.ueba.openc2producer.profiles.th.ThreatHuntingCommands;
import org.oasis.openc2.lycan.OpenC2Message;
import org.oasis.openc2.lycan.OpenC2Response;
import org.oasis.openc2.lycan.profiles.th.args.HuntArgs;
import static org.junit.Assert.*;

public class OpenC2ProducerTest {
    private final ThreatHuntingCommands thCommands = new ThreatHuntingCommands();
    private OpenC2Message createCommand() {
        HuntArgs huntargs = new HuntArgs();
        huntargs.setStringArgs(List.of("ejemplo ñ 漢字"));
        return thCommands.createInvestigate("catalogo/ejemplo", huntargs);
    }
    private HttpServer startServer(int status, String body, AtomicReference<String> request) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/command", exchange -> {
            request.set(exchange.getRequestMethod() + "\n"
                    + exchange.getRequestHeaders().getFirst("Content-Type") + "\n"
                    + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            if (status == 204) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private ConsumerConnection connection(String ip, int port) {
        ConsumerConnection consumer = new ConsumerConnection();
        consumer.setIp(ip);
        consumer.setPort(port);
        return consumer;
    }

    private ConsumerConnection connection(String ip, int port, String path) {
        ConsumerConnection consumer = connection(ip, port);
        consumer.setCommandPath(path);
        return consumer;
    }

    private OpenC2Producer producer(HttpServer server) {
        String ip = "127.0.0.1";
        return new OpenC2Producer(connection(ip, server.getAddress().getPort(), "/command"));
    }

    @Test
    public void enviaJsonDeLycanYLeeResultadosTh() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        String body = "{\"status\":200,\"results\":{\"th\":{\"datasources\":[{\"ds_name\":\"edr\"}]}}}";
        HttpServer server = startServer(200, body, request);
        try {
            OpenC2Producer producer = producer(server);
            OpenC2Message command = createCommand();
            String json = producer.toJson(command);
            assertNull(request.get()); // Preparar el JSON no debe enviar nada.
            OpenC2Response response = producer.sendCommand(command);
            assertEquals(Integer.valueOf(200), response.getStatus());
            assertEquals("POST\napplication/openc2+json;version=1.0\n" + json, request.get());
            ObjectMapper mapper = new ObjectMapper();
            assertEquals(mapper.readTree(body).path("results"), mapper.valueToTree(response.getResults()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void informaDelErrorHttpConSuCuerpo() throws Exception {
        AtomicReference<String> request = new AtomicReference<>();
        HttpServer server = startServer(400, "{\"status\":400,\"status_text\":\"Comando rechazado\"}", request);
        try {
            IOException error = assertThrows(IOException.class,
                    () -> producer(server).sendCommand(createCommand()));
            assertTrue(error.getMessage().contains("HTTP 400"));
            assertTrue(error.getMessage().contains("Comando rechazado"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void admiteRespuestaSinCuerpo() throws Exception {
        HttpServer server = startServer(204, "", new AtomicReference<>());
        try {
            assertNull(producer(server).sendCommand(createCommand()));
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void rechazaRespuestaInvalida() throws Exception {
        for (String body : List.of("no es JSON", "{}", "null")) {
            HttpServer server = startServer(200, body, new AtomicReference<>());
            try {
                assertThrows(IOException.class, () -> producer(server).sendCommand(createCommand()));
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    public void distingueEstadoOpenc2DeEstadoHttp() throws Exception {
        HttpServer server = startServer(200, "{\"status\":501}", new AtomicReference<>());
        try {
            assertEquals(Integer.valueOf(501), producer(server).sendCommand(createCommand()).getStatus());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void rechazaDireccionOComandoIncompleto() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new OpenC2Producer(null));
        assertThrows(IllegalArgumentException.class, () -> new OpenC2Producer(new ConsumerConnection()));
        assertThrows(IllegalArgumentException.class, () -> new OpenC2Producer(connection("http://127.0.0.1", 8080)));
        assertThrows(IllegalArgumentException.class, () -> new OpenC2Producer(connection(null, 8080)));
        assertThrows(IllegalArgumentException.class, () -> new OpenC2Producer(connection("127.0.0.1", 0)));
        assertThrows(IllegalArgumentException.class, () -> new OpenC2Producer(connection("127.0.0.1", 65536)));
        assertThrows(IllegalArgumentException.class, () -> new OpenC2Producer(connection("127.0.0.1", 8080, "command")));
        OpenC2Producer producer = new OpenC2Producer(connection("127.0.0.1", 8080));
        assertThrows(IllegalArgumentException.class, () -> producer.toJson(null));
        assertThrows(IllegalArgumentException.class, () -> producer.toJson(new OpenC2Message()));
    }

    @Test
    public void enviaALaRaizConIpYPuerto() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            exchange.getRequestBody().readAllBytes();
            byte[] body = "{\"status\":200}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            OpenC2Producer producer = new OpenC2Producer(connection("127.0.0.1", server.getAddress().getPort()));
            assertEquals(Integer.valueOf(200), producer.sendCommand(createCommand()).getStatus());
            assertEquals("/", path.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void limitaLaEsperaDelConsumer() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            OpenC2Producer producer = new OpenC2Producer(connection("127.0.0.1", socket.getLocalPort()));
            assertThrows(HttpTimeoutException.class, () -> producer.sendCommand(createCommand()));
        }
    }

}
