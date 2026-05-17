package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import de.dlr.shepard.cli.io.InstanceRorConfig;
import de.dlr.shepard.cli.output.TableFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * ROR1 — {@code shepard-admin instance ror set}. Mutates the
 * instance-level Research Organization Registry configuration via
 * {@code PATCH /v2/admin/instance/ror} (RFC 7396 merge-patch).
 *
 * <p>At least one of {@code --ror-id} or {@code --org-name} must be
 * supplied; otherwise exits 2 with a usage hint. Passing an empty
 * string ({@code ""}) for a field sends an explicit JSON null in the
 * PATCH body, which clears the field on the server.
 *
 * <p>Exit codes: 0 on success; 2 on validation failure (no flags
 * given).
 */
@Command(
  name = "set",
  mixinStandardHelpOptions = true,
  description = "Update the instance-level ROR (Research Organization Registry) configuration."
)
public final class InstanceRorSetCommand extends AbstractCommand {

  static final String ROR_PATH = "/v2/admin/instance/ror";

  /**
   * ROR identifier, e.g. "04cvxnb49". Pass an empty string to clear
   * the field. Null (absent) means "leave unchanged" (RFC 7396).
   */
  @Option(
    names = { "--ror-id" },
    description = "ROR identifier for the deploying institution, e.g. 04cvxnb49. Pass empty string to clear."
  )
  private String rorId;

  /**
   * Human-readable organisation name, e.g. "DLR e.V.". Pass an
   * empty string to clear. Null (absent) means "leave unchanged".
   */
  @Option(
    names = { "--org-name" },
    description = "Human-readable organisation name, e.g. \"DLR e.V.\". Pass empty string to clear."
  )
  private String orgName;

  @Override
  protected Integer run() {
    // Validate: at least one flag must be supplied.
    if (rorId == null && orgName == null) {
      err().println("error: at least one of --ror-id or --org-name must be provided.");
      err().println("       Use 'shepard-admin instance ror set --help' for usage.");
      return 2;
    }

    // Build the RFC 7396 merge-patch body.  We use LinkedHashMap so
    // the serialised key order is predictable (aids test assertions).
    // Empty string → explicit JSON null (= clear the field).
    // Absent (null Java reference after Picocli) → field omitted from map.
    Map<String, Object> patch = new LinkedHashMap<>();
    if (rorId != null) {
      patch.put("rorId", rorId.isEmpty() ? null : rorId);
    }
    if (orgName != null) {
      patch.put("organizationName", orgName.isEmpty() ? null : orgName);
    }

    ShepardHttpClient client = buildClient();

    InstanceRorConfig config;
    try {
      config = client.patchJson(ROR_PATH, patch, new TypeReference<InstanceRorConfig>() {});
    } catch (AdminCliException e) {
      throw e;
    } catch (Exception ex) {
      throw new AdminCliException("Could not update ROR config at " + ROR_PATH + ": " + ex.getMessage(), ex);
    }

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(config));
      } catch (Exception e) {
        throw new AdminCliException("Could not serialise ROR config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("INSTANCE ROR — configuration updated");
    out().println();
    TableFormatter table = new TableFormatter("FIELD", "VALUE");
    table.addRow("rorId", config.getRorId() != null ? config.getRorId() : "(not set)");
    table.addRow("organizationName", config.getOrganizationName() != null ? config.getOrganizationName() : "(not set)");
    table.addRow("rorUrl", config.getRorUrl() != null ? config.getRorUrl() : "(not set)");
    out().print(table.render());

    return 0;
  }
}
