package es.tfg.openc2producer;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public class OpenC2ProducerTest {
    @Test
    public void conservaEstadoYCuerpoYEnviaElComandoExacto() throws Exception {
        for (int status : new int[] {200, 400, 501}) {
            AtomicReference<String> recibido = new AtomicReference<>();
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            String cuerpo = "{\"status\":" + status + ",\"status_text\":\"<script>á</script>\"}";
            server.createContext("/openc2/command", exchange -> {
                recibido.set(exchange.getRequestMethod() + "\n"
                        + exchange.getRequestHeaders().getFirst("Content-Type") + "\n"
                        + new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(status, bytes.length);
                try (var out = exchange.getResponseBody()) { out.write(bytes); }
            });
            server.start();
            try {
                var resultado = new OpenC2Producer("http://localhost:" + server.getAddress().getPort())
                        .sendCommand(OpenC2Producer.CAPACIDADES);
                assertEquals(status, resultado.estadoHttp());
                assertEquals(cuerpo, resultado.cuerpo());
                assertEquals("POST\napplication/openc2+json;version=1.0\n"
                        + OpenC2Producer.CAPACIDADES, recibido.get());
            } finally {
                server.stop(0);
            }
        }
    }

    @Test
    public void rechazaDireccionesInadecuadas() {
        for (String url : new String[] {"file:///tmp", "http://localhost:8080/ruta",
                "http://usuario:clave@localhost", "http://localhost?x=1", "texto", "", "http://localhost:99999", "http://localhost:0", "http://localhost#fragmento"}) {
            assertThrows(IllegalArgumentException.class, () -> new OpenC2Producer(url));
        }
    }

    @Test
    public void informaDeConexionRechazada() throws Exception {
        int puerto;
        try (ServerSocket socket = new ServerSocket(0)) { puerto = socket.getLocalPort(); }
        var producer = new OpenC2Producer("http://localhost:" + puerto);
        assertThrows(java.io.IOException.class, () -> producer.sendCommand(OpenC2Producer.CAPACIDADES));
    }

    @Test
    public void limitaLaEsperaDeRespuesta() throws Exception {
        // El servidor acepta la conexión pero no envía ninguna respuesta HTTP.
        try (ServerSocket socket = new ServerSocket(0)) {
            var producer = new OpenC2Producer("http://localhost:" + socket.getLocalPort());
            assertThrows(HttpTimeoutException.class, () -> producer.sendCommand(OpenC2Producer.CAPACIDADES));
        }
    }
}
