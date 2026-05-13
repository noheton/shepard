package de.dlr.shepard.plugins.minter.datacite.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.datacite.cli.io.DataciteConfig;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin minters datacite enable} — KIP1d.
 *
 * <p>Flips {@code :DataciteMinterConfig.enabled=true} via RFC 7396
 * merge-patch. The minter is only useful once
 * {@code handlePrefix}, {@code repositoryId}, the password, and
 * {@code landingPageBase} are also set — the server-side
 * {@code DataciteMinter.isEnabled()} surfaces the conjunction, and
 * {@code mint()} throws a clean {@code MinterException} when any
 * piece is still missing.
 */
@Command(
  name = "enable",
  mixinStandardHelpOptions = true,
  description = "Enable the DataCite minter (sets enabled=true)."
)
public final class DataciteEnableCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    DataciteConfig cfg = buildClient()
      .patchJson(DataciteAdminPaths.CONFIG, Map.of("enabled", true), new TypeReference<DataciteConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise DataCite config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "DataCite minter enabled — apiBaseUrl=" + (cfg.getApiBaseUrl() == null ? "(unset)" : cfg.getApiBaseUrl()) +
      ", handlePrefix=" + (cfg.getHandlePrefix() == null ? "(unset)" : cfg.getHandlePrefix()) +
      ", passwordSet=" + cfg.isPasswordSet()
    );
    if (!cfg.isPasswordSet()) {
      err().println(
        "warning: passwordSet=false — mint will fail until you run `shepard-admin minters datacite set-password`"
      );
    }
    if (cfg.getHandlePrefix() == null || cfg.getHandlePrefix().isBlank()) {
      err().println("warning: handlePrefix unset — set via `shepard-admin minters datacite set-prefix <prefix>`");
    }
    return 0;
  }
}
