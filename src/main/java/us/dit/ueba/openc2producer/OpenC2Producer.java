package us.dit.ueba.openc2producer;
/*
 * Copyright 2026 Universidad de Sevilla/Departamento de Ingeniería Telemática
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file is part of OpenC2Producer, a SOAR4FUEBA (SOAR solution based on BPM paradigm) component
 */
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.oasis.openc2.lycan.OpenC2Message;
import org.oasis.openc2.lycan.OpenC2Response;
import org.oasis.openc2.lycan.json.JsonFormatter;

/** Envia comandos al Consumer por HTTP y lee sus respuestas usando Lycan. */
public class OpenC2Producer {
    private final URI consumerAddress;
    private final HttpClient httpClient;

    /**
     * Prepara el Producer con los datos del Consumer, sin abrir una conexion.
     * Copia la direccion actual: cambios posteriores en consumer no la modifican.
     */
    public OpenC2Producer(ConsumerConnection consumer) {
        if (consumer == null) {
            throw new IllegalArgumentException("Indica los datos de conexion del Consumer.");
        }
        consumerAddress = createConsumerAddress(consumer);
        httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    /** Construye la direccion HTTP a partir de los datos del Consumer. */
    private URI createConsumerAddress(ConsumerConnection consumer) {
        String ip = consumer.getIp();
        int port = consumer.getPort();
        String path = consumer.getCommandPath();
        if (ip == null || ip.isBlank()) {
            throw new IllegalArgumentException("Indica la IP del Consumer.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("El puerto debe estar entre 1 y 65535.");
        }
        if (path == null || !path.startsWith("/")) {
            throw new IllegalArgumentException("La ruta debe empezar por /.");
        }

        try {
            // URI coloca correctamente la IP, el puerto y la ruta, tambien con IPv6.
            return new URI("http", null, ip.trim(), port, path, null, null);
        } catch (URISyntaxException error) {
            throw new IllegalArgumentException("La direccion del Consumer no es valida.", error);
        }
    }

    /** Permite ver el JSON de un comando sin enviarlo. */
    public String toJson(OpenC2Message command) throws IOException {
        if (command == null) {
            throw new IllegalArgumentException("El comando no puede ser null.");
        }
        if (command.getAction() == null || command.getTarget() == null) {
            throw new IllegalArgumentException("El comando necesita action y target.");
        }
        return JsonFormatter.getJson(command, false);
    }

    /** Envia el comando y espera la respuesta. Devuelve null si no hay cuerpo. */
    public OpenC2Response sendCommand(OpenC2Message command)
            throws IOException, InterruptedException {
        String commandJson = toJson(command);
        HttpRequest request = createRequest(commandJson);
        HttpResponse<String> response = httpClient.send(
                request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return readResponse(response);
    }

    /** Prepara una peticion POST cuyo cuerpo es el comando JSON generado por Lycan. */
    private HttpRequest createRequest(String commandJson) {
        return HttpRequest.newBuilder(consumerAddress)
                .header("Content-Type", "application/openc2+json;version=1.0")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(commandJson, StandardCharsets.UTF_8))
                .build();
    }

    /** Comprueba el resultado HTTP y convierte el cuerpo a OpenC2Response. */
    private OpenC2Response readResponse(HttpResponse<String> response) throws IOException {
        int httpStatus = response.statusCode();
        String responseJson = response.body();

        if (httpStatus < 200 || httpStatus >= 300) {
            throw new IOException("El Consumer respondio con HTTP " + httpStatus + ": " + responseJson);
        }
        if (responseJson.isBlank()) {
            return null;
        }

        OpenC2Response openc2Response = JsonFormatter.readOpenC2Response(responseJson);
        if (openc2Response == null || openc2Response.getStatus() == null) {
            throw new IOException("La respuesta no contiene un status OpenC2.");
        }
        // La aplicacion decide que hacer con el status y los resultados OpenC2.
        return openc2Response;
    }
}
