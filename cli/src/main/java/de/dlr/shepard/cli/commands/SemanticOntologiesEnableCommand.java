package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.OntologyBundle;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin semantic ontologies enable <id>} — N1c2.
 *
 * <p>Calls {@code POST /v2/admin/semantic/ontologies/{id}/enable} —
 * removes the bundle id from {@code :SemanticConfig.disabledBundles}.
 * No-op if the id is not currently disabled. The bundle (re-)joins
 * the seed loop on the next startup.
 */
@Command(
  name = "enable",
  mixinStandardHelpOptions = true,
  description = "Runtime-enable a previously-disabled ontology bundle."
)
public final class SemanticOntologiesEnableCommand extends AbstractCommand {

  @Parameters(index = "0", paramLabel = "<id>", description = "Bundle id to enable.")
  String bundleId;

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();
    String path = "/v2/admin/semantic/ontologies/" + bundleId + "/enable";
    OntologyBundle row = client.postJson(path, Map.of(), new TypeReference<OntologyBundle>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(row));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise bundle to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("Bundle '" + row.getId() + "' is now enabled=" + row.isEnabled() + " (source=" + row.getSource() + ").");
    return 0;
  }
}
