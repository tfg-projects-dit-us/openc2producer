package us.dit.ueba.openc2producer.commands;

import java.util.List;
import org.oasis.openc2.lycan.OpenC2Message;
import org.oasis.openc2.lycan.args.Args;
import org.oasis.openc2.lycan.targets.Features;
import org.oasis.openc2.lycan.targets.Target;
import org.oasis.openc2.lycan.types.ActionType;
import org.oasis.openc2.lycan.types.FeatureType;

/** Construye comandos comunes a todos los perfiles usando Lycan. */
public class OpenC2Commands {

    public OpenC2Commands() {
    }
    /**
     * Crea un comando general a partir de una accion, un target y sus argumentos.
     * Crear un comando no lo envia.
     */
    public OpenC2Message createCommand(ActionType action, Target target, Args args) {
        if (action == null || target == null) {
            throw new IllegalArgumentException("Indica la accion y el target.");
        }

        OpenC2Message command = new OpenC2Message(action, target, args);
        return command;
    }

    /** Crea query features para consultar las capacidades del consumer. */
    public OpenC2Message createQueryFeatures() {
        List<FeatureType> requested = List.of(
                FeatureType.VERSIONS,
                FeatureType.PROFILES,
                FeatureType.PAIRS,
                FeatureType.RATE_LIMIT);
        return createQueryFeatures(requested);
    }

    /** Permite elegir las capacidades(features) que se quieren consultar. */
    public OpenC2Message createQueryFeatures(List<FeatureType> requested) {
        if (requested == null || requested.isEmpty()) {
            throw new IllegalArgumentException("Selecciona alguna capacidad (feature).");
        }

        Features features = new Features();
        for (FeatureType feature : requested) {
            if (feature == null) {
                throw new IllegalArgumentException("La capacidad(feature) no puede ser null.");
            }
            features.addFeature(feature);
        }

        Target target = new Target();
        target.setFeatures(features);

        return createCommand(ActionType.QUERY, target, null);
    }

}
