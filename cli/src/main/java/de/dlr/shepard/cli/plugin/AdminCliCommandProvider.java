package de.dlr.shepard.cli.plugin;

import picocli.CommandLine;

/**
 * PM1d — SPI implemented by plugin JARs to contribute Picocli
 * subcommands to the {@code shepard-admin} CLI.
 *
 * <p>The shape mirrors the backend's
 * {@code de.dlr.shepard.plugin.PluginManifest} SPI shipped in PM1a —
 * plugin JARs declare their contribution in a
 * {@code META-INF/services/de.dlr.shepard.cli.plugin.AdminCliCommandProvider}
 * file, and {@link CliPluginBootstrap} discovers them at startup
 * via Java's {@link java.util.ServiceLoader}.
 *
 * <p>Each discovered provider's {@link #commandClass()} is registered
 * as a top-level subcommand on the {@code shepard-admin} root via
 * {@link CommandLine#addSubcommand(Object, Object)} — same shape as
 * the hard-coded subcommands the CLI ships in-tree (features, health,
 * migrations, semantic, plugins).
 *
 * <p>The plugin-JAR-side workflow:
 *
 * <ol>
 *   <li>Plugin module depends on {@code de.dlr.shepard:shepard-admin}
 *       with {@code <scope>provided</scope>} (same shape as the
 *       backend dependency) so the CLI jars do not get re-bundled
 *       into the plugin JAR.</li>
 *   <li>Plugin ships a Picocli {@code @Command}-annotated class — a
 *       no-op parent container with nested verb commands, mirroring
 *       the existing {@code UnhideCommand} / {@code SemanticCommand}
 *       shape.</li>
 *   <li>Plugin ships a small {@code AdminCliCommandProvider}
 *       implementation pointing at that command class.</li>
 *   <li>Plugin ships a
 *       {@code META-INF/services/de.dlr.shepard.cli.plugin.AdminCliCommandProvider}
 *       file naming the implementation.</li>
 * </ol>
 *
 * <p>For PM1d the simplest minimum is exposed — no {@code AdminCliContext}
 * yet. Each command class still extends
 * {@code de.dlr.shepard.cli.AbstractCommand}, which already does the
 * shared HTTP-client / config / output-format / error-envelope work.
 * A future {@code AdminCliContext} can be added when a real need
 * for shared deps surfaces (e.g. a shared {@code ShepardHttpClient}
 * factory beyond what {@code AbstractCommand} already provides).
 *
 * <p>The SPI is intentionally narrow: a single
 * {@link #commandClass()} method returning a Picocli-annotated class.
 * That keeps the contract durable — a plugin compiled against PM1d's
 * CLI keeps working through every later CLI shape change as long as
 * Picocli's {@code @Command} annotation stays stable (and Picocli's
 * compatibility track record there is very strong).
 */
public interface AdminCliCommandProvider {

  /**
   * The Picocli {@code @Command}-annotated class to register as a
   * top-level subcommand under {@code shepard-admin}. Must be a
   * concrete class with a public no-arg constructor; Picocli
   * instantiates it via reflection through
   * {@link CommandLine#addSubcommand(Object, Object)}.
   *
   * <p>The class's {@code @Command(name = ...)} attribute becomes the
   * subcommand name an operator types — so a provider returning a
   * class with {@code @Command(name = "unhide")} surfaces as
   * {@code shepard-admin unhide}.
   */
  Class<?> commandClass();
}
