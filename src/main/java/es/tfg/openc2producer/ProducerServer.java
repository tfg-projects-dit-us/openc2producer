package es.tfg.openc2producer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class ProducerServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ProducerServer(String host, int port) throws IOException {
        server = HttpServer.create(new InetSocketAddress(host, port), 0);
        server.setExecutor(executor);
        server.createContext("/", this::handle);
    }

    public void start() { server.start(); }
    public int port() { return server.getAddress().getPort(); }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            String path = exchange.getRequestURI().getPath();
            if ("/api/preview".equals(path)) {
                if (requireMethod(exchange, "POST")) { preview(exchange); }
                return;
            }
            if ("/api/investigate".equals(path)) {
                if (requireMethod(exchange, "POST")) { investigate(exchange); }
                return;
            }
            String command = switch (path) {
                case "/api/capabilities" -> OpenC2Producer.CAPACIDADES;
                case "/api/huntflows" -> OpenC2Producer.INVESTIGACIONES;
                case "/api/datasources" -> OpenC2Producer.FUENTES;
                default -> null;
            };
            if (command != null) {
                if (requireMethod(exchange, "POST")) {
                    query(exchange, command);
                }
                return;
            }
            // Lista cerrada: nunca usamos una ruta del navegador para leer archivos del disco.
            String resource = switch (path) {
                case "/" -> "index.html";
                case "/perfil" -> "profile.html";
                case "/style.css" -> "style.css";
                case "/app.js" -> "app.js";
                case "/universidad-sevilla.png" -> "universidad-sevilla.png";
                default -> null;
            };
            if (resource == null) {
                send(exchange, 404, "text/plain", "Recurso no encontrado.");
                return;
            }
            if (!requireMethod(exchange, "GET")) { return; }
            String type = switch (resource) {
                case "index.html", "profile.html" -> "text/html";
                case "style.css" -> "text/css";
                case "universidad-sevilla.png" -> "image/png";
                default -> "text/javascript";
            };
            try (var input = ProducerServer.class.getResourceAsStream("/web/" + resource)) {
                if (input == null) {
                    send(exchange, 500, "text/plain", "No se ha encontrado el recurso web.");
                } else {
                    byte[] bytes = input.readAllBytes();
                    exchange.getResponseHeaders().set("Content-Type", type.startsWith("image/") ? type : type + "; charset=UTF-8");
                    exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
                    exchange.getResponseHeaders().set("Cache-Control", "no-store");
                    exchange.sendResponseHeaders(200, bytes.length);
                    exchange.getResponseBody().write(bytes);
                }
            }
        }
    }

    private void query(HttpExchange exchange, String command) throws IOException {
        exchange.getResponseHeaders().set("X-OpenC2-Command", command);
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !contentType.split(";", 2)[0].strip().equalsIgnoreCase("text/plain")) {
            send(exchange, 415, "text/plain", "Envía la dirección como text/plain en UTF-8.");
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(4097);
        if (body.length > 4096) {
            send(exchange, 413, "text/plain", "La dirección es demasiado larga.");
            return;
        }
        String consumerUrl = new String(body, StandardCharsets.UTF_8);
        forward(exchange, consumerUrl, command);
    }



    // Preparar no contacta con el Consumer: utiliza los mismos constructores que el envío.
    private void preview(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";", 2)[0].strip().equalsIgnoreCase("application/x-www-form-urlencoded")) {
            send(exchange, 415, "text/plain", "Envía los campos como formulario URL-encoded.");
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(4097);
        if (body.length > 4096) {
            send(exchange, 413, "text/plain", "El formulario es demasiado largo.");
            return;
        }
        try {
            var fields = new java.util.HashMap<String, String>();
            for (String entry : new String(body, StandardCharsets.UTF_8).split("&")) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2) throw new IllegalArgumentException("Campo de formulario incorrecto.");
                String name = java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                if (!java.util.Set.of("operation", "username", "vigilance_level").contains(name)
                        || fields.putIfAbsent(name, value) != null) {
                    throw new IllegalArgumentException("Campo desconocido o duplicado.");
                }
            }
            String operation = fields.get("operation");
            if (operation == null) throw new IllegalArgumentException("Selecciona una operación.");
            if (!operation.equals("investigate") && fields.size() != 1) {
                throw new IllegalArgumentException("Las consultas no admiten filtros ni argumentos.");
            }
            String command = operation.equals("investigate")
                    ? UserlogonCommand.build(fields.get("username"), fields.get("vigilance_level"))
                    : LycanCommands.query(operation);
            send(exchange, 200, "application/json", command);
        } catch (IllegalArgumentException e) {
            send(exchange, 400, "text/plain", e.getMessage());
        }
    }

    private void investigate(HttpExchange exchange) throws IOException {
        String type = exchange.getRequestHeaders().getFirst("Content-Type");
        if (type == null || !type.split(";", 2)[0].strip().equalsIgnoreCase("application/x-www-form-urlencoded")) {
            send(exchange, 415, "text/plain", "Envía los campos como formulario URL-encoded.");
            return;
        }
        byte[] body = exchange.getRequestBody().readNBytes(4097);
        if (body.length > 4096) {
            send(exchange, 413, "text/plain", "El formulario es demasiado largo.");
            return;
        }
        try {
            // URLDecoder conserva signos +, &, = y Unicode enviados con URLSearchParams.
            var fields = new java.util.HashMap<String, String>();
            for (String entry : new String(body, StandardCharsets.UTF_8).split("&")) {
                String[] pair = entry.split("=", 2);
                if (pair.length != 2) throw new IllegalArgumentException("Campo de formulario incorrecto.");
                String name = java.net.URLDecoder.decode(pair[0], StandardCharsets.UTF_8);
                String value = java.net.URLDecoder.decode(pair[1], StandardCharsets.UTF_8);
                if (!java.util.Set.of("consumerUrl", "username", "vigilance_level").contains(name)
                        || fields.putIfAbsent(name, value) != null) {
                    throw new IllegalArgumentException("Campo desconocido o duplicado.");
                }
            }
            String command = UserlogonCommand.build(fields.get("username"), fields.get("vigilance_level"));
            forward(exchange, fields.get("consumerUrl"), command);
        } catch (IllegalArgumentException e) {
            send(exchange, 400, "text/plain", "Formulario no válido: " + e.getMessage());
        }
    }


    private void forward(HttpExchange exchange, String consumerUrl, String command) throws IOException {
        exchange.getResponseHeaders().set("X-OpenC2-Command", command);
        try {
            var result = new OpenC2Producer(consumerUrl).sendCommand(command);
            // 200 indica que nuestra API obtuvo una respuesta. El estado del Consumer va separado.
            exchange.getResponseHeaders().set("X-Consumer-Status", Integer.toString(result.estadoHttp()));
            send(exchange, 200, "text/plain", result.cuerpo());
        } catch (IllegalArgumentException e) {
            send(exchange, 400, "text/plain", "Dirección no válida. Usa http://localhost:8080, sin ruta, parámetros ni credenciales.");
        } catch (HttpTimeoutException e) {
            send(exchange, 504, "text/plain", "Se ha agotado el tiempo de espera del Consumer.");
        } catch (IOException e) {
            send(exchange, 502, "text/plain", "No se pudo completar la conexión con el Consumer. Comprueba la dirección y que esté arrancado.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            send(exchange, 503, "text/plain", "La petición al Consumer se ha interrumpido.");
        }
    }

    private boolean requireMethod(HttpExchange exchange, String method) throws IOException {
        if (method.equals(exchange.getRequestMethod())) { return true; }
        exchange.getResponseHeaders().set("Allow", method);
        send(exchange, 405, "text/plain", "Método no permitido.");
        return false;
    }

    private void send(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type + "; charset=UTF-8");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
