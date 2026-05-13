package de.dlr.shepard.plugins.unhide.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.unhide.cli.io.HarvestKeyMinted;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin unhide rotate-harvest-key} — UH1a.
 *
 * <p>Calls {@code POST /v2/admin/unhide/harvest-key/rotate}.
 * Returns the plaintext key <b>exactly once</b>; the operator is
 * responsible for piping it into a secret manager immediately. In
 * the human (default) output the key is on its own line so a
 * {@code | tee >(grep ...)} idiom can extract it; the warning is
 * printed to stderr so it doesn't pollute machine-readable output.
 */
@Command(
  name = "rotate-harvest-key",
  mixinStandardHelpOptions = true,
  description = "Mint a fresh harvest API key. The plaintext is shown exactly once — save it now."
)
public final class UnhideRotateHarvestKeyCommand extends AbstractCommand {

  @Override
  protected Integer run() {
    HarvestKeyMinted minted = buildClient()
      .postJson(UnhideAdminPaths.ROTATE_HARVEST_KEY, null, new TypeReference<HarvestKeyMinted>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(minted));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise harvest-key response to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    // Human output — plaintext on its own line; everything else on stderr.
    err().println("Fresh harvest API key minted. SAVE IT NOW — it cannot be retrieved later.");
    err().println("fingerprint: " + minted.getFingerprint());
    if (minted.getMintedAt() != null) {
      err().println("mintedAt: " + minted.getMintedAt());
    }
    if (minted.getWarning() != null) {
      err().println("WARNING: " + minted.getWarning());
    }
    out().println(minted.getHarvestApiKey());
    return 0;
  }
}
