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
 * {@code shepard-admin minters datacite set-publisher <name>} — KIP1d.
 *
 * <p>Updates {@code :DataciteMinterConfig.publisher} — the publisher
 * string embedded in every minted DOI's metadata. Typically the host
 * institution's official name. Pass an empty string to clear.
 */
@Command(
  name = "set-publisher",
  mixinStandardHelpOptions = true,
  description = "Set the publisher name embedded in minted DOI metadata."
)
public final class DataciteSetPublisherCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    arity = "0..1",
    paramLabel = "<name>",
    description = "Publisher name (e.g. 'DLR e.V.'). Omit or pass an empty string to clear."
  )
  String publisher;

  @Override
  protected Integer run() {
    Map<String, Object> body = new HashMap<>();
    body.put("publisher", (publisher == null || publisher.isBlank()) ? null : publisher);

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

    out().println("publisher = " + (cfg.getPublisher() == null ? "(unset)" : cfg.getPublisher()));
    return 0;
  }
}
