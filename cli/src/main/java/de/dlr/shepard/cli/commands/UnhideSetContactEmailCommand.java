package de.dlr.shepard.cli.commands;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.io.UnhideConfig;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

/**
 * {@code shepard-admin unhide set-contact-email <email>} — UH1a.
 *
 * <p>Updates {@code :UnhideConfig.contactEmail}, surfaced in the
 * feed's {@code _meta.contactEmail} so the Unhide harvester knows
 * whom to ping if a feed-shape issue crops up. Pass an empty string
 * to clear.
 */
@Command(
  name = "set-contact-email",
  mixinStandardHelpOptions = true,
  description = "Set or clear the Unhide contactEmail surfaced in the feed metadata."
)
public final class UnhideSetContactEmailCommand extends AbstractCommand {

  @Parameters(
    index = "0",
    arity = "0..1",
    paramLabel = "<email>",
    description = "Contact email surfaced to Unhide. Omit or pass an empty string to clear."
  )
  String email;

  @Override
  protected Integer run() {
    // RFC 7396: explicit null = clear. We send the field with the
    // chosen value (possibly null) so the server's
    // `contactEmailTouched` flag flips on both set + clear paths.
    Map<String, Object> body = new HashMap<>();
    body.put("contactEmail", (email == null || email.isBlank()) ? null : email);

    UnhideConfig cfg = buildClient().patchJson(UnhideAdminPaths.CONFIG, body, new TypeReference<UnhideConfig>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(cfg));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise Unhide config to JSON: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println("contactEmail = " + (cfg.getContactEmail() == null ? "(unset)" : cfg.getContactEmail()));
    return 0;
  }
}
