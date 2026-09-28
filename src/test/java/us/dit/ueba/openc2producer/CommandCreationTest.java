package us.dit.ueba.openc2producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.Test;
import us.dit.ueba.openc2producer.commands.OpenC2Commands;
import us.dit.ueba.openc2producer.profiles.th.ThreatHuntingCommands;
import org.oasis.openc2.lycan.OpenC2Message;
import org.oasis.openc2.lycan.profiles.th.args.HuntArgs;
import org.oasis.openc2.lycan.profiles.th.args.ThArgs;
import org.oasis.openc2.lycan.targets.Target;
import org.oasis.openc2.lycan.types.ActionType;
import org.oasis.openc2.lycan.types.FeatureType;
import org.oasis.openc2.lycan.profiles.th.targets.HuntflowSpecifiers;
import org.oasis.openc2.lycan.profiles.th.targets.HuntflowSection;
import org.oasis.openc2.lycan.json.JsonFormatter;
import static org.junit.Assert.*;

public class CommandCreationTest {
    private final OpenC2Commands commands = new OpenC2Commands();
    private final ThreatHuntingCommands thCommands = new ThreatHuntingCommands();
    private void checkCommand(String expected, OpenC2Message command) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String actual = JsonFormatter.getJson(command, false);
        assertEquals(mapper.readTree(expected), mapper.readTree(actual));
        OpenC2Message restored = JsonFormatter.readOpenC2Message(actual);
        assertEquals(mapper.readTree(expected), mapper.readTree(JsonFormatter.getJson(restored, false)));
    }

    @Test
    public void generaLasCuatroOperacionesDelPerfil() throws Exception {
        checkCommand("{\"action\":\"query\",\"target\":{\"features\":[\"pairs\"]}}",
                commands.createQueryFeatures(List.of(FeatureType.PAIRS)));
        checkCommand("{\"action\":\"query\",\"target\":{\"th\":{\"huntflows\":{}}}}",
                thCommands.createQueryHuntflows());
        checkCommand("{\"action\":\"query\",\"target\":{\"th\":{\"datasources\":\"\"}}}",
                thCommands.createQueryDatasources());
        checkCommand("{\"action\":\"investigate\",\"target\":{\"th\":{\"hunt\":\"ejemplo\"}}}",
                thCommands.createInvestigate("ejemplo"));
        ObjectMapper mapper = new ObjectMapper();
        assertEquals(4, mapper.readTree(JsonFormatter.getJson(commands.createQueryFeatures(), false))
                .at("/target/features").size());
    }

    @Test
    public void generaFiltrosYNombreDeDatasource() throws Exception {
        HuntflowSpecifiers filters = new HuntflowSpecifiers();
        filters.setPath("/catalogo");
        filters.setTags(List.of("endpoint"));
        filters.setReturnFormat(List.of(HuntflowSection.PATH));
        checkCommand("""
                {"action":"query","target":{"th":{"huntflows":{
                 "path":"/catalogo","tags":["endpoint"],"return_format":["path"]}}}}
                """, thCommands.createQueryHuntflows(filters));
        checkCommand("{\"action\":\"query\",\"target\":{\"th\":{\"datasources\":\"edr\"}}}",
                thCommands.createQueryDatasources("edr"));
    }

    @Test
    public void generaLaEstructuraCompletaDeArgumentos() throws Exception {
        HuntArgs args = new HuntArgs();
        args.setStringArgs(List.of("ejemplo"));
        args.setIntegerArgs(List.of(3L));
        OpenC2Message command = thCommands.createInvestigate("catalogo/ejemplo", args);
        command.getArgs().setDuration(1000L);
        command.setCommandId("id-1");
        checkCommand("""
                {"action":"investigate","target":{"th":{"hunt":"catalogo/ejemplo"}},
                 "args":{"duration":1000,"response_requested":"complete",
                         "th":{"huntargs":{"string_args":["ejemplo"],"integer_args":[3]}}},
                 "command_id":"id-1"}
                """, command);
    }

    @Test
    public void generaComandosGeneralesConLycan() throws Exception {
        org.oasis.openc2.lycan.targets.File file = new org.oasis.openc2.lycan.targets.File();
        file.setName("example.txt");
        Target target = new Target();
        target.setFile(file);
        checkCommand("{\"action\":\"query\",\"target\":{\"file\":{\"name\":\"example.txt\"}}}",
                commands.createCommand(ActionType.QUERY, target, null));
    }

    @Test
    public void rechazaDatosBasicosAusentesAlCrear() {
        assertThrows(IllegalArgumentException.class, () -> thCommands.createInvestigate(""));
        assertThrows(IllegalArgumentException.class, () -> thCommands.createQueryHuntflows(null));
        assertThrows(IllegalArgumentException.class, () -> thCommands.createQueryDatasources(null));
        assertThrows(IllegalArgumentException.class, () -> commands.createQueryFeatures(List.of()));
        assertThrows(IllegalArgumentException.class, () -> commands.createCommand(null, new Target(), null));
    }
}