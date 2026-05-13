package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.io.UnhideConfig;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin unhide revoke-harvest-key} — UH1a.
 *
 * <p>Clears {@code :UnhideConfig.harvestApiKeyHash}. After revoke,
 * {@code feedPublic=false} feeds return 401 unhide.harvest-key.absent
 * until a fresh key is minted (or feedPublic is flipped to
 * {@code true}).
 */
@Command(
  name = "revoke-harvest-key",
  mixinStandardHelpOptions = true,
  description = "Revoke the current harvest API key. The non-public feed becomes inaccessible until a fresh key is minted."
)
public final class UnhideRevokeHarvestKeyCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    UnhideConfig cfg = buildClient()
      .postJson(UnhideAdminPaths.REVOKE_HARVEST_KEY, null, new TypeReference<UnhideConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise Unhide config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "Harvest API key revoked — feedPublic=" +
      cfg.isFeedPublic() +
      ", harvest-key=" +
      (cfg.getHarvestApiKeyFingerprint() == null ? "(none)" : cfg.getHarvestApiKeyFingerprint())
    );
    return 0;
  }
}
