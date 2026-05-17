package de.dlr.shepard.plugins.minter.epic.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.epic.cli.io.EpicCredentialSet;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin minters epic set-credential} — KIP1c.
 *
 * <p>Sets (or rotates) the ePIC credential. The plaintext is read
 * from an interactive terminal via {@link Console#readPassword} when
 * available (terminal echo off, no shell-history retention), and falls
 * back to reading one line from stdin for non-interactive use.
 *
 * <p>The plaintext is <b>never</b> passed on the command line, so
 * {@code ps} / shell history can't leak it. The server-side admin
 * endpoint encrypts the value into
 * {@code :EpicMinterConfig.credentialKey} (AES-GCM keyed off the
 * instance id) and stores the SHA-256 hash for fingerprint display;
 * the response only carries the fingerprint.
 */
@Command(
  name = "set-credential",
  mixinStandardHelpOptions = true,
  description = "Set or rotate the ePIC credential. Reads from stdin / terminal — never on the command line."
)
public class EpicSetCredentialCommand extends AbstractCommand {

  /**
   * Read the credential. Visible to tests so they can stub a non-tty
   * stdin stream into the command.
   */
  String readCredential() {
    Console console = System.console();
    if (console != null) {
      err().println("Enter ePIC credential (input not echoed):");
      char[] chars = console.readPassword();
      if (chars == null) {
        throw new AdminCliException("No credential provided on the terminal.");
      }
      return new String(chars).trim();
    }
    // Non-tty (CI piping). Read one line of stdin.
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      if (line == null) {
        throw new AdminCliException("stdin closed before a credential was read.");
      }
      return line.trim();
    } catch (IOException e) {
      throw new AdminCliException("Could not read credential from stdin: " + e.getMessage(), e);
    }
  }

  @Override
  protected Integer run() {
    String credential = readCredential();
    if (credential == null || credential.isBlank()) {
      throw new AdminCliException("Refusing to set a blank credential.");
    }

    EpicCredentialSet response = buildClient()
      .postJson(EpicAdminPaths.CREDENTIAL, Map.of("credential", credential), new TypeReference<EpicCredentialSet>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(response));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise credential-set response: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "ePIC credential stored. fingerprint=" +
      (response.getFingerprint() == null ? "(absent)" : response.getFingerprint()) +
      ", credentialSet=" +
      response.isCredentialSet()
    );
    return 0;
  }
}
