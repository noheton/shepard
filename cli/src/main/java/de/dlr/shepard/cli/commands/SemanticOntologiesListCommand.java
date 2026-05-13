package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.OntologyBundle;
import de.dlr.shepard.cli.io.OntologyBundleList;
import de.dlr.shepard.cli.output.TableFormatter;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin semantic ontologies list} — N1c2.
 *
 * <p>Calls {@code GET /v2/admin/semantic/ontologies} on the backend
 * and renders the merged built-in + user view. Built-ins first
 * (manifest order), then user uploads ({@code id} ASC). Each row's
 * {@code ENABLED} reflects the effective precedence (required wins;
 * otherwise "not in runtime disabledBundles ∪ deploy-time skip-bundles").
 *
 * <p>Exit codes: always 0 on success (this command is read-only);
 * propagates {@link AdminCliException}-shaped exits (1) on HTTP / IO
 * errors; 2 on unexpected runtime exceptions.
 */
@Command(
  name = "list",
  mixinStandardHelpOptions = true,
  description = "List every pre-seeded + operator-uploaded ontology bundle."
)
public final class SemanticOntologiesListCommand extends AbstractCommand {

  static final String PATH = "/v2/admin/semantic/ontologies";

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();
    OntologyBundleList list = client.getJson(PATH, new TypeReference<OntologyBundleList>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(list));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise bundle list to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    TableFormatter table = new TableFormatter("ID", "SOURCE", "ENABLED", "REQ", "LICENSE", "IRI PREFIX");
    for (OntologyBundle b : list.getBundles()) {
      table.addRow(
        safe(b.getId()),
        safe(b.getSource()),
        b.isEnabled() ? "true" : "false",
        b.isRequired() ? "yes" : "-",
        safe(b.getLicense()),
        safe(b.getIriPrefix())
      );
    }
    out().print(table.render());
    return 0;
  }

  private static String safe(String s) {
    return s == null ? "-" : s;
  }
}
