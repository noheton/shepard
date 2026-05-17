package de.dlr.shepard.plugins.minter.epic.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.epic.cli.io.EpicConfig;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin minters epic set-prefix <prefix>} — KIP1c.
 *
 * <p>Updates {@code :EpicMinterConfig.handlePrefix}. ePIC prefixes
 * are numeric strings assigned by the ePIC consortium (e.g.
 * {@code 21.T11148} for the Helmholtz test environment). Pass an
 * empty string to clear.
 */
@Command(
  name = "set-prefix",
  mixinStandardHelpOptions = true,
  description = "Set the ePIC-allocated handle prefix (e.g. 21.T11148)."
)
public final class EpicSetPrefixCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    arity = "0..1",
    paramLabel = "<prefix>",
    description = "ePIC-allocated handle prefix. Omit or pass an empty string to clear."
  )
  String handlePrefix;

  @Override
  protected Integer run() {
    Map<String, Object> body = new HashMap<>();
    body.put("handlePrefix", (handlePrefix == null || handlePrefix.isBlank()) ? null : handlePrefix);

    EpicConfig cfg = buildClient()
      .patchJson(EpicAdminPaths.CONFIG, body, new TypeReference<EpicConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise ePIC config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("handlePrefix = " + (cfg.getHandlePrefix() == null ? "(unset)" : cfg.getHandlePrefix()));
    return 0;
  }
}
