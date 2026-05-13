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
 * {@code shepard-admin semantic ontologies disable <id>} — N1c2.
 *
 * <p>Calls {@code POST /v2/admin/semantic/ontologies/{id}/disable} —
 * adds the bundle id to {@code :SemanticConfig.disabledBundles}. The
 * bundle stops seeding on the next startup.
 *
 * <p>409 {@code semantic.bundle.required} when the bundle's manifest
 * entry carries {@code required: true} (prov-o, obo-relations today).
 */
@Command(
  name = "disable",
  mixinStandardHelpOptions = true,
  description = "Runtime-disable an ontology bundle (refused for required bundles)."
)
public final class SemanticOntologiesDisableCommand extends AbstractCommand {

  @Parameters(index = "0", paramLabel = "<id>", description = "Bundle id to disable.")
  String bundleId;

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();
    String path = "/v2/admin/semantic/ontologies/" + bundleId + "/disable";
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
