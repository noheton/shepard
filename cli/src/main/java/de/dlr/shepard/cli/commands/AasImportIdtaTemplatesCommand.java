package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.io.AasIdtaImportResult;
import de.dlr.shepard.cli.io.AasIdtaImportResult.CreatedTemplate;
import de.dlr.shepard.cli.output.TableFormatter;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin aas import-idta-templates} — AAS1d.
 *
 * <p>Calls {@code POST /v2/admin/aas/import-idta-templates} on the backend.
 * The server-side {@code AasIdtaTemplateImportService} idempotently upserts
 * the three bundled IDTA Submodel Templates (Digital Nameplate v3.0,
 * Technical Data v2.0, Time Series Data v1.1) as {@code ShepardTemplate}
 * entities with {@code templateKind = AAS_SUBMODEL_TEMPLATE}.
 *
 * <p>Running the command twice is safe: on the second run all three entries
 * are skipped (body, description, and tags match the existing live rows).
 *
 * <p>Exit codes: 0 on success; 2 on unexpected runtime exception.
 */
@Command(
    name = "import-idta-templates",
    mixinStandardHelpOptions = true,
    description = "Import bundled IDTA Submodel Templates into the shepard template registry.")
public final class AasImportIdtaTemplatesCommand extends AbstractCommand {

  static final String PATH = "/v2/admin/aas/import-idta-templates";

  @Override
  protected Integer run() {
    AasIdtaImportResult result = buildClient().postJson(
        PATH,
        null,
        new TypeReference<AasIdtaImportResult>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(result));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise import result to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
        "Import complete — created=" + result.getCreated().size()
            + " skipped=" + result.getSkipped());

    if (!result.getCreated().isEmpty()) {
      out().println();
      TableFormatter table = new TableFormatter("NAME", "VERSION", "APP-ID");
      for (CreatedTemplate t : result.getCreated()) {
        table.addRow(
            safe(t.getName()),
            t.getVersion() == null ? "-" : String.valueOf(t.getVersion()),
            safe(t.getAppId()));
      }
      out().print(table.render());
    }

    return 0;
  }

  private static String safe(String s) {
    return s == null ? "-" : s;
  }
}
