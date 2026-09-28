# OpenC2 Producer

Este proyecto proporciona las clases Java para crear comandos OpenC2, enviarlos a
un Consumer y recibir sus respuestas. Utiliza la librería Lycan para representar
los comandos, sus targets, sus argumentos y las respuestas.

Actualmente es un componente que se utiliza desde otra aplicación Java: no tiene
un `main`, una interfaz gráfica ni un servidor que arranque por sí solo.
Crear un comando no lo envía. El envío se realiza al llamar a `sendCommand`.

## Responsabilidad del Producer

El Producer solicita operaciones al Consumer. Por ejemplo, puede preguntar qué
huntflows hay disponibles o solicitar que se ejecute un hunt concreto.

```text
Producer ── comando OpenC2 ──→ Consumer
Producer ←─ respuesta OpenC2 ── Consumer
```

Si el Consumer utiliza Velociraptor, es el Consumer quien traduce el comando a la
operación correspondiente. El Producer no ejecuta hunts en Velociraptor y no
tiene hunts concretos, como userlogon, incorporados en su código.

En Java se trabaja con objetos de Lycan. Para enviarlos por la red, el Producer
utiliza Lycan para convertirlos a JSON. Al recibir una respuesta, utiliza Lycan
para convertir el JSON recibido en un objeto `OpenC2Response`.

## Organización del proyecto

Las clases principales están dentro de:

```text
src/main/java/us/dit/ueba/openc2producer/
├── ConsumerConnection.java
├── OpenC2Producer.java
├── commands/
│   └── OpenC2Commands.java
└── profiles/
    └── th/
        └── ThreatHuntingCommands.java
```

| Archivo | Para qué sirve |
| --- | --- |
| `ConsumerConnection.java` | Guarda la IP, el puerto y la ruta HTTP del Consumer. |
| `OpenC2Producer.java` | Envía comandos y lee las respuestas utilizando Lycan. |
| `commands/OpenC2Commands.java` | Construye comandos generales y consultas `query features`. |
| `profiles/th/ThreatHuntingCommands.java` | Construye los comandos específicos de Threat Hunting. |

Esta separación permite distinguir los datos de conexión, la construcción de
comandos y su envío. Lo común está separado de lo específico del perfil TH.
Los modelos OpenC2 se reutilizan desde Lycan.

## Ejemplo completo: consultar las capacidades del Consumer

El siguiente ejemplo muestra cómo una aplicación puede utilizar estas clases.
La IP y el puerto son ilustrativos: deben sustituirse por los del Consumer real.

```java
import java.io.IOException;
import org.oasis.openc2.lycan.OpenC2Message;
import org.oasis.openc2.lycan.OpenC2Response;
import us.dit.ueba.openc2producer.ConsumerConnection;
import us.dit.ueba.openc2producer.OpenC2Producer;
import us.dit.ueba.openc2producer.commands.OpenC2Commands;

public class Ejemplo {
    public static void main(String[] args)
            throws IOException, InterruptedException {
        // 1. Configuramos dónde está el Consumer.
        ConsumerConnection consumer = new ConsumerConnection();
        consumer.setIp("192.168.1.50");
        consumer.setPort(8080);

        // 2. Preparamos el Producer. Todavía no se envía nada.
        OpenC2Producer producer = new OpenC2Producer(consumer);

        // 3. Construimos el comando con Lycan.
        OpenC2Commands commands = new OpenC2Commands();
        OpenC2Message command = commands.createQueryFeatures();

        // 4. Enviamos el comando y recibimos la respuesta.
        OpenC2Response response = producer.sendCommand(command);

        // 5. Consultamos la respuesta recibida.
        if (response == null) {
            System.out.println("El Consumer no devolvió un cuerpo de respuesta.");
            return;
        }

        System.out.println("Estado OpenC2: " + response.getStatus());
        System.out.println("Resultados: " + response.getResults());
    }
}
```

`response` guarda la respuesta recibida del Consumer; no se envía a nadie.
Mostrar `getResults()` permite inspeccionar los datos, pero todavía no los
convierte en una lista de capacidades utilizable por la aplicación.

El ejemplo declara las excepciones para mantenerlo sencillo. La aplicación que
integre el Producer debe decidir cómo gestionar un fallo de comunicación o una
interrupción de la espera.

## Configuración de la conexión

`ConsumerConnection` contiene:

- `ip`: dirección del Consumer, sin `http://`.
- `port`: puerto en el que escucha, entre 1 y 65535.
- `commandPath`: ruta HTTP donde recibe comandos; su valor inicial es `/`.

Si el Consumer recibe en `/command`, se configura antes de crear el Producer:

```java
consumer.setCommandPath("/command");
OpenC2Producer producer = new OpenC2Producer(consumer);
```

Con la IP y el puerto del ejemplo, la dirección resultante sería
`http://192.168.1.50:8080/command`. La ruta debe coincidir con la del Consumer;
`/command` es solo un ejemplo.

El código actual utiliza HTTP. El Producer copia la dirección al construirse:
modificar después `consumer` no cambia el destino de ese Producer. Para utilizar
otra dirección, se crea otro `OpenC2Producer` con la nueva configuración.

## Comandos disponibles

Todos estos métodos construyen y devuelven un `OpenC2Message`; ninguno lo envía.

| Clase y método | Comando que prepara |
| --- | --- |
| `OpenC2Commands.createQueryFeatures()` | `query features`: consulta versiones, perfiles, parejas acción/target y límite de frecuencia. |
| `OpenC2Commands.createQueryFeatures(lista)` | Consulta solo las capacidades indicadas mediante `FeatureType` de Lycan. |
| `OpenC2Commands.createCommand(action, target, args)` | Construye un comando con los objetos Lycan proporcionados. |
| `ThreatHuntingCommands.createQueryHuntflows()` | `query huntflows` con filtros vacíos. |
| `ThreatHuntingCommands.createQueryHuntflows(filters)` | `query huntflows` con un `HuntflowSpecifiers` de Lycan. |
| `ThreatHuntingCommands.createQueryDatasources(name)` | `query datasources` con el nombre indicado. |
| `ThreatHuntingCommands.createQueryDatasources()` | `query datasources` con una cadena vacía. |
| `ThreatHuntingCommands.createInvestigate(hunt)` | `investigate hunt` sin argumentos. |
| `ThreatHuntingCommands.createInvestigate(hunt, huntargs)` | `investigate hunt` con los argumentos dentro de `args.th.huntargs`. |

La cadena vacía de `createQueryDatasources()` está admitida por la estructura del
JADN, pero no debe darse por garantizado que el Consumer la interprete como
«devolver todos los datasources».

### Consultar huntflows y datasources

Los siguientes fragmentos se utilizan dentro de un método, con un `producer`
ya configurado y gestionando las mismas excepciones que en el ejemplo completo.

```java
ThreatHuntingCommands commands = new ThreatHuntingCommands();

OpenC2Message command = commands.createQueryHuntflows();
OpenC2Response response = producer.sendCommand(command);
```

Para filtrar por ubicación:

```java
HuntflowSpecifiers filters = new HuntflowSpecifiers();
filters.setPath("/catalogo");

OpenC2Message command = commands.createQueryHuntflows(filters);
OpenC2Response response = producer.sendCommand(command);
```

El filtro indica la ubicación y los huntflows situados por debajo de ella.

Para consultar un datasource por nombre:

```java
OpenC2Message command = commands.createQueryDatasources("edr");
OpenC2Response response = producer.sendCommand(command);
```

### Solicitar un hunt con argumentos

```java
HuntArgs arguments = new HuntArgs();
arguments.setStringArgs(List.of("equipo-01"));

OpenC2Message command =
        commands.createInvestigate("/catalogo/mi-hunt", arguments);
OpenC2Response response = producer.sendCommand(command);
```

El hunt y el argumento son ilustrativos. Debe utilizarse un hunt disponible en
el Consumer y los argumentos que ese hunt requiera. El Producer actual no
descubre ni comprueba automáticamente esos requisitos.

Para estos fragmentos se necesitan, según el ejemplo, los siguientes imports:

```java
import java.util.List;
import org.oasis.openc2.lycan.profiles.th.args.HuntArgs;
import org.oasis.openc2.lycan.profiles.th.targets.HuntflowSpecifiers;
import us.dit.ueba.openc2producer.profiles.th.ThreatHuntingCommands;
```

`HuntArgs` permite proporcionar los tipos de argumentos representados por Lycan,
como cadenas, enteros, timeranges y datasources. `ThArgs` y `Args` los colocan
en los niveles correspondientes del comando.

El identificador del comando puede establecerse con
`command.setCommandId(...)`. Si ya se han añadido argumentos TH, los argumentos
generales pueden modificarse mediante `command.getArgs()` para conservarlos.

## Qué sucede al enviar un comando

`sendCommand(command)` realiza estos pasos:

1. `toJson` comprueba que exista el comando y tenga acción y target, y lo
   convierte a JSON mediante `JsonFormatter` de Lycan.
2. `createRequest` prepara una petición HTTP POST con ese JSON.
3. El cliente HTTP envía la petición y espera la respuesta.
4. `readResponse` comprueba el código HTTP y lee el cuerpo con Lycan.
5. Devuelve el objeto `OpenC2Response` a la aplicación.

Para revisar el JSON sin enviarlo se puede llamar a:

```java
String json = producer.toJson(command);
System.out.println(json);
```

No es necesario llamar a `toJson` antes de `sendCommand`.

La petición utiliza UTF-8 y el encabezado
`Content-Type: application/openc2+json;version=1.0`.
El límite para establecer la conexión es de 3 segundos y el de la petición es
de 5 segundos. Son límites de espera del Producer, no límites de ejecución del
hunt en el Consumer. No se siguen redirecciones ni se realizan reintentos
automáticos.

## Respuestas y errores

| Situación | Comportamiento actual |
| --- | --- |
| HTTP 200–299 con una respuesta OpenC2 interpretable y con `status` | Devuelve `OpenC2Response`. |
| HTTP 200–299 sin cuerpo o con cuerpo en blanco | Devuelve `null`. |
| Otro código HTTP | Lanza `IOException` con el código y el cuerpo recibido. |
| Cuerpo no interpretable o sin `status` OpenC2 | Comunica un error de lectura mediante `IOException`. |
| Fallo de conexión o tiempo de espera agotado | Lanza `IOException` o una subclase. |
| Interrupción de la espera | Propaga `InterruptedException`. |
| Datos de entrada rechazados por las comprobaciones | Lanza `IllegalArgumentException`. |

El código HTTP y el `status` OpenC2 son distintos. Por ejemplo, una respuesta
HTTP 200 puede contener un `status` OpenC2 501. En ese caso, el método devuelve
el objeto y la aplicación debe revisar `response.getStatus()`.

Recibir una respuesta no significa necesariamente que la investigación haya
terminado correctamente. El Producer todavía no interpreta los estados ni
gestiona el seguimiento de investigaciones pendientes.

## Estado actual y trabajo pendiente

Ya está implementado:

- Construcción de los comandos anteriores mediante Lycan.
- Configuración del destino mediante `ConsumerConnection`.
- Envío HTTP y lectura de la respuesta como `OpenC2Response`.
- Pruebas de construcción de comandos y de comunicación con un servidor local simulado.

Queda pendiente:

- Interpretar las capacidades devueltas por `query features`, incluidas las
  combinaciones de acciones y targets.
- Extraer los huntflows y datasources recibidos para ponerlos a disposición de
  la aplicación y permitir seleccionar un huntflow.
- Interpretar los resultados de `investigate hunt`.
- Incorporar el actuator, cuyo desarrollo se ha dejado para más adelante.

Las comprobaciones actuales son básicas, no una validación completa contra el
JADN. `createCommand` no verifica todas las parejas acción/target de cada
perfil, ni se validan los esquemas externos STIX/OCA. El envío HTTP actual no
supone una implementación completa de una especificación OpenC2 de transferencia.

## Compilación y pruebas

Se necesita Java 21, Maven y la versión ampliada de Lycan con soporte TH,
disponible para Maven como `org.oasis.openc2:lycan:1.0.0`.
No basta con que coincida el número de versión: esa dependencia debe incluir
las clases de `org.oasis.openc2.lycan.profiles.th` utilizadas por el proyecto.

Desde la carpeta de este Producer:

```powershell
mvn test
```

Para compilar, ejecutar las pruebas y generar el JAR:

```powershell
mvn clean package
```

El resultado es `target/openc2producer.jar`. Es un JAR para utilizar desde una
aplicación Java, sin punto de entrada ejecutable ni dependencias incorporadas.

Las pruebas están en `src/test/java/us/dit/ueba/openc2producer/`:

- `CommandCreationTest.java`: comprueba los comandos construidos con Lycan.
- `OpenC2ProducerTest.java`: comprueba envíos, respuestas y errores usando un
  servidor local, sin necesitar un Consumer real.

Las coordenadas Maven del proyecto son
`us.dit.ueba:openc2producer:1.0-SNAPSHOT`.

## Referencias

- [Arquitectura OpenC2](https://docs.oasis-open.org/openc2/oc2arch/v1.0/oc2arch-v1.0.html).
- [Threat Hunting Actuator Profile, CSD02](https://docs.oasis-open.org/openc2/ap-hunt/v1.0/csd02/ap-hunt-v1.0-csd02.html).
- `ap-hunt-0.7.jadn`, proporcionado como referencia para las estructuras TH.
  El anexo A del perfil establece la prioridad de ese esquema ante discrepancias.

El proyecto usa la ampliación local de Lycan para representar esas estructuras.
Los ejemplos de este README describen el comportamiento implementado; no
certifican conformidad completa del Producer con todas las especificaciones.
