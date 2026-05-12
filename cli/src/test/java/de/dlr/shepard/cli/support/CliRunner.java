package de.dlr.shepard.cli.support;

import de.dlr.shepard.cli.AbstractCommand;
import de.dlr.shepard.cli.config.AdminConfig;
import de.dlr.shepard.cli.config.AdminConfigLoader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import picocli.CommandLine;

/**
 * Test fixture: invokes a {@link AbstractCommand} with explicit
 * URL + API-key and captures stdout / stderr. Lets tests assert on
 * the printed table or JSON without needing to fork a JVM.
 *
 * <p>The CLI is wired against an in-process {@link StubBackend}
 * (real localhost HTTP server, real {@link java.net.http.HttpClient})
 * — so this gives us a true integration test of the request/response
 * path without spinning up Quarkus.
 */
public final class CliRunner {

  public record Captured(int exit, String stdout, String stderr) {}

  /**
   * Run {@code argv} against {@code command}, pre-wiring an
   * {@link AdminConfigLoader} that reports {@code url} + {@code apiKey}
   * regardless of the host's env vars. Flag precedence stays intact
   * — a test wanting to verify a {@code --url} override can still pass
   * it on argv.
   */
  public static Captured run(AbstractCommand command, String url, String apiKey, String... argv) {
    StringWriter out = new StringWriter();
    StringWriter err = new StringWriter();
    CommandLine cmd = new CommandLine(command);
    cmd.setOut(new PrintWriter(out, true));
    cmd.setErr(new PrintWriter(err, true));

    Path home = Paths.get(System.getProperty("user.home", "."));
    Map<String, String> env = new HashMap<>();
    env.put(AdminConfig.ENV_URL, url);
    if (apiKey != null) env.put(AdminConfig.ENV_API_KEY, apiKey);
    injectLoader(command, new AdminConfigLoader(env::get, home));

    int exit = cmd.execute(argv);
    return new Captured(exit, out.toString(), err.toString());
  }

  /**
   * Reflectively set the test-only loader field on the command. The
   * field is package-private inside {@code de.dlr.shepard.cli} so
   * production code can't see it, but test code in the sub-package
   * needs a reflective entry point.
   */
  private static void injectLoader(AbstractCommand command, AdminConfigLoader loader) {
    try {
      Field field = AbstractCommand.class.getDeclaredField("configLoaderOverride");
      field.setAccessible(true);
      field.set(command, loader);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("AbstractCommand missing configLoaderOverride field", e);
    }
  }

  /** Helper for tests that want to read a fixture from {@code src/test/resources/}. */
  public static String fixture(String path) throws IOException {
    try (var in = CliRunner.class.getResourceAsStream(path)) {
      if (in == null) throw new IOException("fixture not found: " + path);
      return new String(in.readAllBytes());
    }
  }

  private CliRunner() {}
}
