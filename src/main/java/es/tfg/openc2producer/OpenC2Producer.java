package es.tfg.openc2producer;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class OpenC2Producer {
    public static final String CAPACIDADES = LycanCommands.query("capabilities");
    // Consulta el catálogo de investigaciones, sin filtros ni ejecución.
    public static final String INVESTIGACIONES = LycanCommands.query("huntflows");
    // Consulta la definición estática de fuentes, sin comprobar equipos.
    public static final String FUENTES = LycanCommands.query("datasources");
    private final URI endpoint;
    private static final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();

    public OpenC2Producer(String consumerUrl) {
        if (consumerUrl == null || consumerUrl.isBlank()) {
            throw new IllegalArgumentException("Introduce la dirección del Consumer.");
        }
        URI base = URI.create(consumerUrl.strip());
        if (!("http".equalsIgnoreCase(base.getScheme()) || "https".equalsIgnoreCase(base.getScheme()))
                || base.getHost() == null || base.getUserInfo() != null
                || base.getPort() == 0 || base.getPort() > 65535
                || base.getQuery() != null || base.getFragment() != null
                || !(base.getPath().isEmpty() || "/".equals(base.getPath()))) {
            throw new IllegalArgumentException("Introduce una dirección como http://localhost:8080, sin ruta.");
        }
        endpoint = base.resolve("/openc2/command");
    }

    public Resultado sendCommand(String json) throws IOException, InterruptedException {
        // El envío ocurre en Java. El navegador solo llama a la API local del Producer.
        HttpRequest request = HttpRequest.newBuilder(endpoint)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/openc2+json;version=1.0")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        // HttpClient devuelve también el cuerpo de errores HTTP como 400 y 501.
        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Resultado(response.statusCode(), response.body());
    }

    // Agrupa los dos datos que necesitamos mostrar en la página.
    public record Resultado(int estadoHttp, String cuerpo) { }
}
