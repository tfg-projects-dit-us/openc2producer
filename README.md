# openc2producer

Aplicación de interfaz web para preparar comandos OpenC2, revisar su contenido y enviarlos a un Consumer.

El proyecto se ejecuta de forma independiente de 'openc2consumer'. La integración actual permite consultar información y validar peticiones de investigación, pero todavía no permite ejecutar investigaciones reales.

## Resumen del desarrollo

La aplicación está desarrollada en tres partes: la interfaz web, el servidor del Producer y la comunicación con el Consumer.

La interfaz está desarrollada con HTML, CSS y JavaScript. Desde ella se selecciona la operación mediante botones o se introducen los datos de una petición 'userlogon'. El servidor Java genera el JSON y lo devuelve para mostrarlo en un panel de vista previa. Esta preparación no envía ninguna petición al Consumer.

El envío se realiza mediante un botón independiente. A continuación, la interfaz muestra el comando enviado, el estado HTTP del Consumer y el cuerpo de su respuesta.

Para mantener una estructura sencilla, he utilizado 'HttpServer' como servidor HTTP y 'HttpClient' para la comunicación entre aplicaciones. Está incorporado además Lycan 1.0.0, la misma dependencia que utiliza el Consumer, y Jackson para la serialización de los comandos.

Navegador → HttpServer del Producer → construcción del comando con Lycan → HttpClient → Consumer

Las peticiones al Consumer se realizan desde Java. El navegador únicamente se comunica con el Producer.

## Funcionalidades actuales

- Consultar capacidades → Solicita las parejas de acciones y objetivos disponibles mediante features: ["pairs"].
- Consultar investigaciones → Obtiene el catálogo de huntflows sin filtros. Actualmente el Consumer devuelve la definición de userlogon.
- Consultar fuentes → Obtiene las fuentes definidas, sin filtros. La respuesta actual es estática y no comprueba equipos ni Velociraptor.
- Preparar una investigación → Construye una petición userlogon con username y un nivel de vigilancia opcional.
- Revisar el comando → Muestra el JSON generado antes del envío y actualiza la vista previa al cambiar los argumentos.
- Consultar la referencia del perfil → Presenta una guía sobre la estructura y la finalidad de los comandos del perfil (Actuator Profile).

En 'userlogon', el nombre de usuario es obligatorio. Los niveles admitidos son 'STANDARD', 'SUSPICIOUS' y 'CRITICAL'. Si se omite el nivel, el Consumer utiliza 'STANDARD'.

Una petición válida de investigación devuelve actualmente **HTTP 501**, porque la ejecución no está implementada. La interfaz explica esta situación y no presenta la investigación como realizada. La convención 'nombre=valor' dentro de 'string_args' pertenece a este huntflow; no es una regla general de OpenC2.

## Organización del código

Las clases Java se encuentran en 'src/main/java/es/tfg/openc2producer/'.

- Main.java → Arranque del servidor y configuración de la dirección y el puerto de escucha.
- ProducerServer.java → Publicación de los recursos web y atención de las peticiones de la API local.
- LycanCommands.java → Construcción y serialización de los comandos con Lycan y la extensión local de Threat Hunting.
- UserlogonCommand.java → Validación de los argumentos de userlogon.
- OpenC2Producer.java → Validación de la dirección del Consumer y envío de las peticiones HTTP.

Los recursos de la interfaz están en 'src/main/resources/web/' y las pruebas en 'src/test/java/'.

### Uso de Lycan

Se utiliza org.oasis.openc2:lycan:1.0.0 para construir mensajes 'OpenC2Message' con sus acciones y objetivos. Esta versión no contiene clases específicas del perfil Threat Hunting, por lo que se han añadido las extensiones de nuestro Actuator Profile de 'Target' y 'Args' para representar el campo 'th'.

Jackson serializa los objetos a JSON. La serialización omite los campos nulos y el argumento 'response_requested' que Lycan añade por defecto, ya que el Consumer actual no lo admite en la petición userlogon.

La vista previa y el envío utilizan los mismos constructores Java. Lycan no valida por sí sola todo el perfil: las reglas del subconjunto implementado se comprueban en el código y en las pruebas.

## Requisitos

Para compilar el proyecto se necesita:

- JDK Java 21.
- Maven; en mi entorno he utilizado la versión 3.9.12.
- La biblioteca Lycan 1.0.0 del mismo fork utilizado por el Consumer, instalada en el repositorio Maven local.

Para ejecutar el JAR ya generado basta con disponer de Java 21. Maven Shade incluye las dependencias y los recursos web en el archivo ejecutable.

## Despliegue en Windows con PowerShell

### 1. Preparar el entorno

En PowerShell, desde cualquier carpeta, se pueden comprobar las versiones con:
java -version
mvn -version

Los comandos de compilación y arranque se ejecutan desde la raíz de openc2producer.

### 2. Instalar Lycan si no está disponible

La biblioteca utilizada procede del [fork openc2-lycan-java](https://github.com/tfg-projects-dit-us/openc2-lycan-java). Para reproducir el entorno debe utilizarse la misma revisión del fork que emplea el Consumer.

Si Lycan 1.0.0 ya está instalada en el repositorio Maven local, este paso no es necesario. 
Si el repositorio está en otra ubicación, hay que ajustar la ruta al pom.xml de Lycan. 

Esta instalación se realiza al preparar el entorno, no en cada arranque.

### 3. Compilar y generar el ejecutable

Desde la raíz de openc2producer:

mvn package

El comando ejecuta las pruebas y genera **target/openc2producer.jar**, que incluye las dependencias necesarias. La primera compilación puede necesitar acceso a Internet para descargar dependencias y plugins de Maven.

### 4. Arrancar el Producer

Desde la misma carpeta:

java -jar target/openc2producer.jar

La aplicación escucha por defecto en **http://localhost:8081**. El proceso permanece activo en la consola y se detiene con **Ctrl+C**.

La interfaz y la preparación de comandos funcionan sin arrancar el Consumer. Para realizar un envío y obtener su respuesta, el Consumer debe estar disponible.

### 5. Configurar el Consumer y comprobar el funcionamiento

En el navegador, abre **http://localhost:8081** e introduce la dirección del Consumer. El valor inicial es **http://localhost:8080**.

Selecciona **Consultar capacidades**, revisa el JSON del panel lateral y pulsa **Enviar al Consumer**. Si el Consumer está disponible y admite la consulta, debe mostrarse su respuesta con estado HTTP 200.

El Producer añade /openc2/command a la dirección indicada y envía el comando mediante POST, con el Content-Type application/openc2+json;version=1.0.

La dirección debe utilizar HTTP o HTTPS, sin rutas, parámetros, fragmentos ni credenciales. Por ejemplo, debe introducirse http://localhost:8080

### 6. Ejecutar en otro equipo o acceder desde la red (TODAVÍA NO SE HA PROBADO)

Para trasladar la aplicación ya compilada, basta con copiar target/openc2producer.jar al equipo de destino, que debe disponer de Java 21. Desde la carpeta donde se haya copiado:

java -jar openc2producer.jar

El Consumer puede estar en un equipo diferente. En ese caso, se indica su dirección en la interfaz, por ejemplo http://192.168.1.30:8080.

La dirección se interpreta desde el equipo que ejecuta el Producer: localhost se refiere a ese equipo, aunque el navegador esté en otro ordenador.

Para acceder al Producer desde otros equipos, desde la raíz del proyecto ejecuta:

java -jar target/openc2producer.jar 0.0.0.0 8081

El primer argumento establece la dirección de escucha y el segundo el puerto. Desde otro equipo, la interfaz se abre en http://IP_DEL_PRODUCER:8081. La conectividad de red y el cortafuegos deben permitir el acceso a los puertos utilizados por ambas aplicaciones.


## Tratamiento de las respuestas

He configurado un tiempo de espera de 3 segundos para establecer la conexión y de 5 segundos para la petición. No se siguen redirecciones automáticamente.

La interfaz conserva tanto el estado HTTP como el cuerpo de las respuestas del Consumer, incluidos errores como 400 y 501. Si no se obtiene una respuesta HTTP, muestra **Sin respuesta HTTP** junto con la descripción del error.

Los datos se manejan en UTF-8. Los valores introducidos se validan en Java y el contenido recibido se muestra mediante textContent, sin interpretarlo como HTML.

En la API local, el estado HTTP del Producer y el del Consumer se mantienen separados. Si el Consumer responde, la API devuelve HTTP 200 y transmite su estado real en X-Consumer-Status. El comando generado se incluye en X-OpenC2-Command. Los errores locales no incluyen un estado atribuido al Consumer.

## API local

POST /api/preview → Genera el JSON de la operación seleccionada sin contactar con el Consumer.
POST /api/capabilities → Envía la consulta de capacidades. 
POST /api/huntflows → Envía la consulta de investigaciones. 
POST /api/datasources → Envía la consulta de fuentes. 
POST /api/investigate → Envía la petición userlogon. 
GET /perfil → Sirve la página de referencia del perfil. 


## Pruebas

Desde la raíz del proyecto, en PowerShell:

mvn test

Se han utilizado servidores HTTP locales simulados para que las pruebas no dependan del Consumer real ni de Velociraptor. Se comprueban los comandos generados, el método y la ruta HTTP, el Content-Type, las respuestas 200/400/501 y los errores de conexión y tiempo de espera.

También se verifican los niveles de vigilancia, los caracteres especiales del usuario y los campos inválidos. Las pruebas de vista previa comprueban que preparar un comando no provoca un envío y que el JSON preparado coincide con el que recibe posteriormente el Consumer.


## Estado y trabajo pendiente

La implementación actual es parcial. Las consultas funcionan sin filtros y la petición userlogon se utiliza únicamente para validación.

Quedan pendientes la ejecución real de investigaciones en el Consumer, la incorporación de timeranges, datasources... a las peticiones, y el soporte de otros huntflows como userlogout, usersession y usersessionduration.
