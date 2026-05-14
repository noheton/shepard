package de.dlr.shepard.plugins.references.dbpediadatabus.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.references.dbpediadatabus.cli.io.DbpediaDatabusConfig;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin references dbpedia-databus enable} — REF1c.
 *
 * <p>Flips {@code :DbpediaDatabusConfig.enabled=true}.
 */
@Command(
  name = "enable",
  mixinStandardHelpOptions = true,
  description = "Enable the DBpedia Databus reference plugin (sets enabled=true)."
)
public final class DbpediaDatabusEnableCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    DbpediaDatabusConfig cfg = buildClient()
      .patchJson(DbpediaDatabusAdminPaths.CONFIG, Map.of("enabled", true), new TypeReference<DbpediaDatabusConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("DBpedia Databus reference plugin enabled — endpoint=" + cfg.getDefaultEndpoint());
    return 0;
  }
}
