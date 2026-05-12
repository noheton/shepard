package de.dlr.shepard.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import de.dlr.shepard.cli.config.AdminConfig;
import de.dlr.shepard.cli.config.AdminConfigLoader;
import de.dlr.shepard.cli.http.AdminCliException;
import de.dlr.shepard.cli.http.ShepardHttpClient;
import java.io.PrintWriter;
import java.net.http.HttpClient;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParentCommand;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;

/**
 * Common scaffolding for every {@code shepard-admin} subcommand:
 * <ul>
 *   <li>Config + HTTP-client wiring from {@link AdminConfigLoader}.</li>
 *   <li>{@code --url} / {@code --api-key} overrides at every level
 *       (Picocli inheritedOption).</li>
 *   <li>{@code --output} (human / json) selector.</li>
 *   <li>Error envelope — {@link AdminCliException} renders as a
 *       single stderr line (or full stack with
 *       {@code --verbose}).</li>
 * </ul>
 *
 * <p>Test injection: the {@link HttpClient} and {@link AdminConfigLoader}
 * factories are {@code protected}, so a test subclass can stub
 * either one.
 */
public abstract class AbstractCommand implements Callable<Integer> {

  /** Human-readable table output (default). */
  public static final String OUTPUT_HUMAN = "human";

  /** Pretty-printed JSON output. */
  public static final String OUTPUT_JSON = "json";

  @ParentCommand
  protected Object parent;

  @Spec
  protected CommandSpec spec;

  @Option(
    names = { "--url" },
    description = "Shepard base URL (overrides config + env). Default: $" + AdminConfig.ENV_URL + " or http://localhost:8080.",
    scope = CommandLine.ScopeType.INHERIT
  )
  protected String urlFlag;

  @Option(
    names = { "--api-key" },
    description = "API key (overrides config + env). Default: $" + AdminConfig.ENV_API_KEY + ".",
    scope = CommandLine.ScopeType.INHERIT
  )
  protected String apiKeyFlag;

  @Option(
    names = { "-o", "--output" },
    description = "Output format: ${COMPLETION-CANDIDATES}. Default: human.",
    defaultValue = OUTPUT_HUMAN,
    scope = CommandLine.ScopeType.INHERIT
  )
  protected String output;

  @Option(
    names = { "-v", "--verbose" },
    description = "Print stack traces on error.",
    defaultValue = "false",
    scope = CommandLine.ScopeType.INHERIT
  )
  protected boolean verbose;

  private ObjectMapper prettyMapper;

  /** Lazily-built shared Jackson mapper for {@code --output=json}. */
  protected ObjectMapper jsonMapper() {
    if (prettyMapper == null) {
      prettyMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .enable(SerializationFeature.INDENT_OUTPUT)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }
    return prettyMapper;
  }

  /** Test seam — overridden in unit tests to inject a mock client. */
  protected HttpClient httpClient() {
    return ShepardHttpClient.defaultClient();
  }

  /** Test seam — overridden in unit tests to inject a fixture loader. */
  protected AdminConfigLoader configLoader() {
    return new AdminConfigLoader();
  }

  /**
   * Resolve and construct an authenticated HTTP client. Reads the
   * layered config sources (flags > env > file > defaults).
   */
  protected ShepardHttpClient buildClient() {
    AdminConfig config;
    try {
      config = configLoader().load(urlFlag, apiKeyFlag);
    } catch (Exception e) {
      throw new AdminCliException("Could not read config: " + e.getMessage(), e);
    }
    return new ShepardHttpClient(httpClient(), config.getUrl(), config.getApiKey().orElse(null));
  }

  /** Whether the user wants pretty JSON instead of a human table. */
  protected boolean wantsJson() {
    return OUTPUT_JSON.equalsIgnoreCase(output);
  }

  protected PrintWriter out() {
    return spec.commandLine().getOut();
  }

  protected PrintWriter err() {
    return spec.commandLine().getErr();
  }

  /**
   * Subclasses implement {@link #run()}; the wrapper converts
   * {@link AdminCliException} into a clean stderr line plus the
   * documented exit codes.
   */
  @Override
  public final Integer call() {
    try {
      return run();
    } catch (AdminCliException e) {
      err().println("error: " + e.getMessage());
      if (verbose && e.getCause() != null) {
        e.getCause().printStackTrace(err());
      }
      return 1;
    } catch (RuntimeException e) {
      err().println("error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
      if (verbose) {
        e.printStackTrace(err());
      }
      return 2;
    }
  }

  /** Subclass entry point. Return the exit code (0 = success). */
  protected abstract Integer run();
}
