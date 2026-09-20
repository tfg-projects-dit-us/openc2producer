package es.tfg.openc2producer;

public final class UserlogonCommand {
    private UserlogonCommand() { }

    public static String build(String username, String level) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("username es obligatorio.");
        }
        if (level != null && !java.util.Set.of("STANDARD", "SUSPICIOUS", "CRITICAL").contains(level)) {
            throw new IllegalArgumentException("vigilance_level debe ser STANDARD, SUSPICIOUS o CRITICAL.");
        }
        return LycanCommands.userlogon(username, level);
    }
}
