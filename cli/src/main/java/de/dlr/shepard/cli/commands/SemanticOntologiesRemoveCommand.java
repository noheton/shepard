package de.dlr.shepard.cli.commands;

import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin semantic ontologies remove <id>} — N1c2.
 *
 * <p>Calls {@code DELETE /v2/admin/semantic/ontologies/{id}}. Drops
 * the on-disk TTL plus the {@code :UserOntologyBundle} catalogue
 * row. 409 {@code semantic.bundle.builtin-not-removable} when the id
 * refers to a built-in bundle (those ship in the JAR and update via
 * release upgrades).
 */
@Command(
  name = "remove",
  mixinStandardHelpOptions = true,
  description = "Remove an operator-uploaded ontology bundle (built-ins are refused)."
)
public final class SemanticOntologiesRemoveCommand extends AbstractCommand {

  @Parameters(index = "0", paramLabel = "<id>", description = "Bundle id to remove.")
  String bundleId;

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();
    String path = "/v2/admin/semantic/ontologies/" + bundleId;
    client.delete(path); // throws AdminCliException on non-2xx

    if (wantsJson()) {
      out().println("{\"removed\":\"" + bundleId + "\"}");
      return 0;
    }
    out().println("Bundle '" + bundleId + "' removed.");
    return 0;
  }
}
