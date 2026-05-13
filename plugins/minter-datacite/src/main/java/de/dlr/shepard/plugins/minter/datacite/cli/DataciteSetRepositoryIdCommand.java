package de.dlr.shepard.plugins.minter.datacite.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.datacite.cli.io.DataciteConfig;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin minters datacite set-repository-id <id>} — KIP1d.
 *
 * <p>Updates {@code :DataciteMinterConfig.repositoryId} — the
 * DataCite Member account login, used as the HTTP Basic auth user
 * portion against the DataCite REST API. Pass an empty string to
 * clear.
 */
@Command(
  name = "set-repository-id",
  mixinStandardHelpOptions = true,
  description = "Set the DataCite Member account login (HTTP Basic user)."
)
public final class DataciteSetRepositoryIdCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    arity = "0..1",
    paramLabel = "<id>",
    description = "DataCite Member account login (e.g. DLR.SHEPARD). Omit or pass an empty string to clear."
  )
  String repositoryId;

  @Override
  protected Integer run() {
    Map<String, Object> body = new HashMap<>();
    body.put("repositoryId", (repositoryId == null || repositoryId.isBlank()) ? null : repositoryId);

    DataciteConfig cfg = buildClient()
      .patchJson(DataciteAdminPaths.CONFIG, body, new TypeReference<DataciteConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise DataCite config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("repositoryId = " + (cfg.getRepositoryId() == null ? "(unset)" : cfg.getRepositoryId()));
    return 0;
  }
}
