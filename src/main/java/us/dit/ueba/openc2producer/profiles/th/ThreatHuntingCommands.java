package us.dit.ueba.openc2producer.profiles.th;

import org.oasis.openc2.lycan.OpenC2Message;
import org.oasis.openc2.lycan.args.Args;
import org.oasis.openc2.lycan.targets.Target;
import org.oasis.openc2.lycan.types.ActionType;
import org.oasis.openc2.lycan.profiles.th.targets.ThTarget;
import org.oasis.openc2.lycan.profiles.th.targets.HuntflowSpecifiers;
import org.oasis.openc2.lycan.profiles.th.args.HuntArgs;
import org.oasis.openc2.lycan.profiles.th.args.ThArgs;
import us.dit.ueba.openc2producer.commands.OpenC2Commands;

/** Construye los comandos del perfil TH. */
public class ThreatHuntingCommands {
    private final OpenC2Commands commands;

    public ThreatHuntingCommands() {
        commands = new OpenC2Commands();
    }
    /** Crea query huntflows sin filtros. */
    public OpenC2Message createQueryHuntflows() {
        HuntflowSpecifiers filters = new HuntflowSpecifiers();
        return createQueryHuntflows(filters);
    }

    /** Crea query huntflows con los filtros de Lycan que se indiquen. */
    public OpenC2Message createQueryHuntflows(HuntflowSpecifiers filters) {
        if (filters == null) {
            throw new IllegalArgumentException("Los filtros no pueden ser null.");
        }

        ThTarget thTarget = ThTarget.huntflows(filters);

        Target target = new Target();
        target.setTh(thTarget);

        return commands.createCommand(ActionType.QUERY, target, null);
    }

    /** Crea query datasources sin indicar un nombre concreto. */
    public OpenC2Message createQueryDatasources() {
        return createQueryDatasources("");
    }

    /** Crea query datasources con el nombre indicado. */
    public OpenC2Message createQueryDatasources(String name) {
        if (name == null) {
            throw new IllegalArgumentException("Indica un nombre o una cadena vacia.");
        }

        ThTarget thTarget = ThTarget.datasources(name);

        Target target = new Target();
        target.setTh(thTarget);

        return commands.createCommand(ActionType.QUERY, target, null);
    }

    /** Crea investigate hunt sin argumentos. */
    public OpenC2Message createInvestigate(String hunt) {
        return createInvestigate(hunt, null);
    }

    /**
     * Crea investigate hunt y coloca los parametros en args.th.huntargs.
     * HuntArgs permite usar todos los tipos de argumentos que representa Lycan.
     */
    public OpenC2Message createInvestigate(String hunt, HuntArgs huntargs) {
        if (hunt == null || hunt.isBlank()) {
            throw new IllegalArgumentException("Indica el hunt que quieres solicitar.");
        }

        ThTarget thTarget = ThTarget.hunt(hunt);

        Target target = new Target();
        target.setTh(thTarget);

        Args args = null;
        if (huntargs != null) {
            ThArgs thArgs = new ThArgs();
            thArgs.setHuntargs(huntargs);

            args = new Args();
            args.setTh(thArgs);
        }

        return commands.createCommand(ActionType.INVESTIGATE, target, args);
    }
}
