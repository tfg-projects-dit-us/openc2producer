package us.dit.ueba.openc2producer;

/** Datos para conectarse al Consumer. No son argumentos del comando OpenC2. */
public class ConsumerConnection {
    private String ip;
    private int port;
    private String commandPath = "/";

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    /** Ruta HTTP donde el Consumer recibe comandos. Por defecto es /. */
    public String getCommandPath() {
        return commandPath;
    }

    public void setCommandPath(String commandPath) {
        this.commandPath = commandPath;
    }
}