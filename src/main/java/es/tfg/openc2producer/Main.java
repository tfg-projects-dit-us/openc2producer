package es.tfg.openc2producer;

public final class Main {
    public static void main(String[] args) throws Exception {
        // Por defecto solo se accede desde este ordenador.
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8081;
        ProducerServer server = new ProducerServer(host, port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::close));
        server.start();
        System.out.println("Producer iniciado en http://" + host + ":" + server.port());
        System.out.println("Pulsa Ctrl+C para detenerlo.");
    }
}
