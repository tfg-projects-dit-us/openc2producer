package es.tfg.openc2producer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.json.JsonWriteFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.oasis.openc2.lycan.OpenC2Message;
import org.oasis.openc2.lycan.args.Args;
import org.oasis.openc2.lycan.targets.Features;
import org.oasis.openc2.lycan.targets.Target;
import org.oasis.openc2.lycan.types.ActionType;
import org.oasis.openc2.lycan.types.FeatureType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Construye el subconjunto del Actuator Profile. */
public final class LycanCommands {
    private static final ObjectMapper JSON = JsonMapper.builder()
            .enable(JsonWriteFeature.ESCAPE_NON_ASCII).build()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .addMixIn(OpenC2Message.class, CommandOrder.class);

    private LycanCommands() { }

    public static String query(String operation) {
        Target target = switch (operation) {
            case "capabilities" -> new Target().setFeatures(new Features().addFeature(FeatureType.PAIRS));
            case "huntflows" -> new ThTarget(new ThTargetValue(Map.of(), null, null));
            case "datasources" -> new ThTarget(new ThTargetValue(null, "", null));
            default -> throw new IllegalArgumentException("Consulta no admitida.");
        };
        return serialize(new OpenC2Message(ActionType.QUERY, target));
    }

    public static String userlogon(String username, String level) {
        List<String> values = new ArrayList<>();
        // Esta convención nombre=valor pertenece a userlogon.
        values.add("username=" + username);
        if (level != null) values.add("vigilance_level=" + level);
        var target = new ThTarget(new ThTargetValue(null, null, "userlogon"));
        var args = new ThArgs(new ThArguments(new HuntArguments(values)));
        return serialize(new OpenC2Message(ActionType.INVESTIGATE, target, args));
    }

    private static String serialize(OpenC2Message command) {
        try {
            return JSON.writeValueAsString(command);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("No se pudo generar el comando OpenC2.", e);
        }
    }

    @JsonPropertyOrder({"action", "target", "args", "commandId"})
    private abstract static class CommandOrder { }

    // Lycan 1.0.0 no incluye TH (Threat Hunting). Estas extensiones conservan los tipos base de Lycan.
    public static final class ThTarget extends Target {
        private final ThTargetValue th;
        public ThTarget(ThTargetValue th) { this.th = th; }
        public ThTargetValue getTh() { return th; }
    }

    public record ThTargetValue(Map<String, String> huntflows, String datasources, String hunt) { }

    public static final class ThArgs extends Args {
        private final ThArguments th;
        public ThArgs(ThArguments th) { this.th = th; }
        public ThArguments getTh() { return th; }

        // Args añade COMPLETE por defecto; el Consumer no admite ese campo en este comando.
        @Override
        @JsonIgnore
        public String getResponseRequested() { return null; }
    }

    public record ThArguments(HuntArguments huntargs) { }
    public record HuntArguments(List<String> string_args) { }
}
