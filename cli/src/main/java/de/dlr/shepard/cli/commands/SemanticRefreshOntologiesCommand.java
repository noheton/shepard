package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.RefreshOntologiesResult;
import de.dlr.shepard.cli.io.RefreshOntologiesResult.BundleError;
import de.dlr.shepard.cli.output.TableFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * {@code shepard-admin semantic refresh-ontologies} — N1c.
 *
 * <p>Calls {@code POST /v2/admin/semantic/refresh-ontologies} on the
 * backend; the server-side {@code OntologyRefreshService} walks the
 * pre-seeded ontologies manifest (or the subset named on the CLI),
 * fetches each bundle's pinned canonical URL, recomputes its SHA-256,
 * and re-imports into n10s when the hash differs from the bundled
 * stub.
 *
 * <p>Per ADR-0019 the pre-seed pass on startup ships minimum-viable
 * Turtle stubs (so the casual annotation flow works out of the box);
 * this command is the operator's escape hatch for landing the full
 * canonical Turtle without waiting for the next shepard release.
 *
 * <p>Exit codes: 0 when every bundle either refreshed cleanly or was
 * already current; 1 when at least one bundle errored (and the table
 * surfaces the per-bundle reason); 2 on unexpected runtime
 * exception.
 *
 * <p>See {@code aidocs/22 §4.x} and {@code aidocs/48 §4}.
 */
@Command(
  name = "refresh-ontologies",
  mixinStandardHelpOptions = true,
  description = "Refresh bundled ontologies from each bundle's canonical URL."
)
public final class SemanticRefreshOntologiesCommand extends AbstractCommand {

  static final String PATH = "/v2/admin/semantic/refresh-ontologies";

  @Option(
    names = { "--bundles" },
    description = "Comma-separated list of bundle ids to refresh (e.g. prov-o,qudt). " +
    "Default: all bundles in the manifest.",
    split = ","
  )
  List<String> bundles;

  @Option(
    names = { "--force" },
    description = "Re-import even when the canonical Turtle hash already matches the bundled stub.",
    defaultValue = "false"
  )
  boolean force;

  @Override
  protected Integer run() {
    ShepardHttpClient client = buildClient();

    Map<String, Object> requestBody = new LinkedHashMap<>();
    requestBody.put("bundles", bundles == null ? List.of() : bundles);
    requestBody.put("force", force);

    RefreshOntologiesResult result = client.postJson(
      PATH,
      requestBody,
      new TypeReference<RefreshOntologiesResult>() {}
    );

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(result));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise refresh result to JSON: " + e.getMessage(), e);
      }
      return result.hasErrors() ? 1 : 0;
    }

    out().println(
      "Refresh complete — requested=" +
      result.getRequested() +
      " refreshed=" +
      result.getRefreshed() +
      " alreadyCurrent=" +
      result.getAlreadyCurrent() +
      " errors=" +
      result.getErrors().size()
    );

    if (result.hasErrors()) {
      out().println();
      TableFormatter table = new TableFormatter("BUNDLE", "REASON");
      for (BundleError e : result.getErrors()) {
        table.addRow(safe(e.getBundle()), safe(e.getReason()));
      }
      out().print(table.render());
    }

    return result.hasErrors() ? 1 : 0;
  }

  private static String safe(String s) {
    return s == null ? "-" : s;
  }
}
