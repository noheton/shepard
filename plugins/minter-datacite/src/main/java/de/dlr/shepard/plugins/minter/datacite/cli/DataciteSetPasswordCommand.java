package de.dlr.shepard.plugins.minter.datacite.cli;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.plugins.minter.datacite.cli.io.DataciteCredentialSet;
import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import picocli.CommandLine.Command;

/**
 * {@code shepard-admin minters datacite set-password} — KIP1d.
 *
 * <p>Sets (or rotates) the DataCite Member password. The plaintext
 * is read from an interactive terminal via {@link Console#readPassword}
 * when available (terminal echo off, no shell-history retention), and
 * falls back to reading one line from stdin for non-interactive use
 * (CI piping {@code echo $PASSWORD | shepard-admin ... set-password}).
 *
 * <p>The plaintext is <b>never</b> passed on the command line, so
 * {@code ps} / shell history can't leak it. The server-side admin
 * endpoint encrypts the value into {@code :DataciteMinterConfig.passwordCipher}
 * (AES-GCM keyed off the instance id) and stores the SHA-256 hash for
 * fingerprint display; the response only carries the fingerprint, so
 * the plaintext never round-trips.
 */
@Command(
  name = "set-password",
  mixinStandardHelpOptions = true,
  description = "Set or rotate the DataCite Member password. Reads from stdin / terminal — never on the command line."
)
public class DataciteSetPasswordCommand extends AbstractCommand {

  /**
   * Read the password. Visible to tests so they can stub a non-tty
   * stdin stream into the command.
   */
  String readPassword() {
    Console console = System.console();
    if (console != null) {
      err().println("Enter DataCite password (input not echoed):");
      char[] chars = console.readPassword();
      if (chars == null) {
        throw new AdminCliException("No password provided on the terminal.");
      }
      return new String(chars).trim();
    }
    // Non-tty (CI piping). Read one line of stdin.
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      String line = reader.readLine();
      if (line == null) {
        throw new AdminCliException("stdin closed before a password was read.");
      }
      return line.trim();
    } catch (IOException e) {
      throw new AdminCliException("Could not read password from stdin: " + e.getMessage(), e);
    }
  }

  @Override
  protected Integer run() {
    String password = readPassword();
    if (password == null || password.isBlank()) {
      throw new AdminCliException("Refusing to set a blank password.");
    }

    DataciteCredentialSet response = buildClient()
      .postJson(DataciteAdminPaths.CREDENTIAL, Map.of("password", password), new TypeReference<DataciteCredentialSet>() {});

    if (wantsJson()) {
      try {
        out().println(jsonMapper().writeValueAsString(response));
      } catch (JsonProcessingException e) {
        throw new AdminCliException("Could not serialise credential-set response: " + e.getMessage(), e);
      }
      return 0;
    }

    out().println(
      "DataCite credential stored. fingerprint=" +
      (response.getFingerprint() == null ? "(absent)" : response.getFingerprint()) +
      ", passwordSet=" +
      response.isPasswordSet()
    );
    return 0;
  }
}
