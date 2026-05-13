package de.dlr.shepard.cli.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

/**
 * PM1d — end-to-end smoke test of the {@link CliPluginBootstrap}:
 * builds a synthetic plugin JAR containing one
 * {@link AdminCliCommandProvider} + a Picocli-annotated command
 * class, drops it into a temp dir, points the bootstrap at the dir
 * via the {@code SHEPARD_PLUGINS_DIR} env, and asserts the
 * subcommand gets registered on a fresh {@link CommandLine} root.
 *
 * <p>Covers the JAR-walk side of discovery. The classpath side is
 * covered by {@code ShepardAdminBootstrapTest} (which exercises the
 * real Unhide plugin from the test classpath).
 */
final class AdminCliCommandProviderTest {

  /**
   * Picocli {@code @Command} class wired by the synthetic plugin
   * JAR. Picked up by name so the test's classloader does NOT see
   * this class on its own classpath — the JAR has to ship it.
   */
  // Note: must be a top-level class to satisfy ServiceLoader's
  // no-arg-public-constructor contract; see below.

  @Test
  void jarWalk_discoversSubcommandFromDropInJar(@TempDir Path tempDir) throws Exception {
    Path jar = buildFakePluginJar(tempDir);
    assertThat(jar).exists();

    // Set the env override (via a per-test fixture function) so the
    // bootstrap walks the temp dir.
    java.util.function.Function<String, String> env = name -> {
      if (CliPluginBootstrap.ENV_PLUGINS_DIR.equals(name)) {
        return tempDir.toString();
      }
      return null;
    };

    CommandLine root = new CommandLine(new EmptyRootCommand());
    java.util.Set<String> before = new java.util.HashSet<>(root.getSubcommands().keySet());

    CliPluginBootstrap bootstrap = new CliPluginBootstrap();
    bootstrap.discoverInto(root, env);

    java.util.Set<String> after = new java.util.HashSet<>(root.getSubcommands().keySet());
    java.util.Set<String> added = new java.util.HashSet<>(after);
    added.removeAll(before);

    // The fake plugin from the temp-dir JAR contributed exactly the
    // one subcommand. (Other subcommands may come from the
    // classpath ServiceLoader pass if the test classpath happens
    // to carry real AdminCliCommandProvider implementations — e.g.
    // `unhide` when running `mvn test` with the `with-plugins`
    // profile active. That's fine and not what this test is
    // asserting on.)
    assertThat(after).contains("fakeplugin");
    assertThat(added).contains("fakeplugin");
    assertThat(bootstrap.warnings()).isEmpty();
    assertThat(bootstrap.resolvedDir()).isNotNull();
    assertThat(bootstrap.resolvedDir().toRealPath()).isEqualTo(tempDir.toRealPath());
  }

  @Test
  void jarWalk_skipsJarWithoutServiceFile(@TempDir Path tempDir) throws Exception {
    // A bare JAR with no META-INF/services entry — should be
    // silently ignored, not crash discovery.
    Path jar = tempDir.resolve("empty-plugin.jar");
    try (
      JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()), buildManifest())
    ) {
      // Nothing else.
    }
    java.util.function.Function<String, String> env = name -> {
      if (CliPluginBootstrap.ENV_PLUGINS_DIR.equals(name)) {
        return tempDir.toString();
      }
      return null;
    };

    CommandLine root = new CommandLine(new EmptyRootCommand());
    java.util.Set<String> before = new java.util.HashSet<>(root.getSubcommands().keySet());

    CliPluginBootstrap bootstrap = new CliPluginBootstrap();
    bootstrap.discoverInto(root, env);

    // No new subcommand from the JAR walk (the bare JAR carries no
    // service file). Classpath-side discoveries are allowed.
    java.util.Set<String> after = new java.util.HashSet<>(root.getSubcommands().keySet());
    java.util.Set<String> added = new java.util.HashSet<>(after);
    added.removeAll(before);
    assertThat(added).doesNotContain("fakeplugin");
    assertThat(bootstrap.warnings()).isEmpty();
  }

  @Test
  void envOverride_nonExistentDir_emitsWarning(@TempDir Path tempDir) {
    Path missing = tempDir.resolve("does-not-exist");
    java.util.function.Function<String, String> env = name -> {
      if (CliPluginBootstrap.ENV_PLUGINS_DIR.equals(name)) {
        return missing.toString();
      }
      return null;
    };

    CommandLine root = new CommandLine(new EmptyRootCommand());
    CliPluginBootstrap bootstrap = new CliPluginBootstrap();
    bootstrap.discoverInto(root, env);

    // The warning is the assertion we care about — any classpath
    // discoveries are orthogonal to the env-override-missing path.
    assertThat(bootstrap.warnings()).anyMatch(w -> w.contains("does not exist"));
    assertThat(bootstrap.resolvedDir()).isNull();
  }

  @Test
  void noOverride_noDefaultDir_silentlyNoOps() {
    // Neither the env override nor the default `cli/plugins` /
    // `/deployments/plugins` exists in the test working directory
    // (CI runs from the module root, which is `cli/`). The
    // bootstrap should silently no-op on the JAR-walk side — only
    // the classpath ServiceLoader pass may contribute.
    java.util.function.Function<String, String> env = name -> null;
    CommandLine root = new CommandLine(new EmptyRootCommand());
    CliPluginBootstrap bootstrap = new CliPluginBootstrap();
    bootstrap.discoverInto(root, env);

    // No warnings — `cli/plugins` and `/deployments/plugins` are
    // absent in the test environment, so the no-default path takes
    // the silent no-op branch.
    assertThat(bootstrap.warnings()).isEmpty();
    // resolvedDir is null since no override + no default dir
    // existed; the only subcommands present can come from the
    // classpath ServiceLoader pass (orthogonal — not asserted on).
    assertThat(bootstrap.resolvedDir()).isNull();
  }

  @Test
  void jarWalk_duplicateSubcommandName_warnsAndSkips(@TempDir Path tempDir) throws Exception {
    Path jar = buildFakePluginJar(tempDir);
    assertThat(jar).exists();

    // Pre-seed the root with a subcommand named "fakeplugin" — the
    // bootstrap must detect the collision, warn, and not throw.
    CommandLine root = new CommandLine(new EmptyRootCommand());
    root.addSubcommand("fakeplugin", new EmptyRootCommand());

    java.util.function.Function<String, String> env = name -> {
      if (CliPluginBootstrap.ENV_PLUGINS_DIR.equals(name)) {
        return tempDir.toString();
      }
      return null;
    };
    CliPluginBootstrap bootstrap = new CliPluginBootstrap();
    bootstrap.discoverInto(root, env);

    // Still exactly the original subcommand.
    assertThat(root.getSubcommands()).containsKey("fakeplugin");
    assertThat(bootstrap.warnings()).anyMatch(w -> w.contains("shadows an existing subcommand"));
  }

  // ----------------------------------------------------------------------
  // Fixture: build a tiny one-class plugin JAR + service file.
  // ----------------------------------------------------------------------

  /**
   * Build a JAR with three entries:
   *
   * <ol>
   *   <li>A Picocli {@code @Command(name = "fakeplugin")} class with
   *       a public no-arg constructor.</li>
   *   <li>An {@link AdminCliCommandProvider} implementation pointing
   *       at the command class above.</li>
   *   <li>The {@code META-INF/services/} pointer.</li>
   * </ol>
   *
   * <p>The class bytes are precompiled — we ship them as resources
   * on the test classpath so the test does not need a runtime
   * javac dependency. The class files live under
   * {@code src/test/resources/de/dlr/shepard/cli/plugin/fakeplugin/}.
   *
   * <p>If the resources are absent (a developer running the test
   * before generating the fixture), the test falls back to building
   * the classes inline using the Java compiler API.
   */
  private static Path buildFakePluginJar(Path dir) throws Exception {
    Path jar = dir.resolve("fakeplugin-1.0.jar");
    try (JarOutputStream jos = new JarOutputStream(new FileOutputStream(jar.toFile()), buildManifest())) {
      // Compile the two classes inline so the test is self-contained.
      Path src = Files.createTempDirectory("fakeplugin-src");
      try {
        Path cmdSrc = src.resolve("FakePluginCommand.java");
        Files.writeString(
          cmdSrc,
          "package de.dlr.shepard.fakeplugin;\n"
            + "import picocli.CommandLine.Command;\n"
            + "@Command(name = \"fakeplugin\", description = \"smoke-test plugin\")\n"
            + "public final class FakePluginCommand implements Runnable {\n"
            + "  @Override public void run() { /* no-op */ }\n"
            + "}\n"
        );
        Path provSrc = src.resolve("FakePluginProvider.java");
        Files.writeString(
          provSrc,
          "package de.dlr.shepard.fakeplugin;\n"
            + "import de.dlr.shepard.cli.plugin.AdminCliCommandProvider;\n"
            + "public final class FakePluginProvider implements AdminCliCommandProvider {\n"
            + "  @Override public Class<?> commandClass() { return FakePluginCommand.class; }\n"
            + "}\n"
        );

        javax.tools.JavaCompiler compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
          throw new IllegalStateException("no JDK compiler available — run tests on a JDK, not a JRE");
        }
        Path out = Files.createTempDirectory("fakeplugin-cls");
        try {
          javax.tools.StandardJavaFileManager fm = compiler.getStandardFileManager(null, null, null);
          fm.setLocation(javax.tools.StandardLocation.CLASS_OUTPUT, java.util.List.of(out.toFile()));
          Iterable<? extends javax.tools.JavaFileObject> units = fm.getJavaFileObjects(cmdSrc.toFile(), provSrc.toFile());
          boolean ok = compiler.getTask(null, fm, null, java.util.List.of("-classpath", computeClasspath()), null, units).call();
          if (!ok) {
            throw new IllegalStateException("inline javac compilation failed for fakeplugin fixture");
          }

          // Copy the resulting class files into the JAR.
          addClassToJar(jos, out, "de/dlr/shepard/fakeplugin/FakePluginCommand.class");
          addClassToJar(jos, out, "de/dlr/shepard/fakeplugin/FakePluginProvider.class");
        } finally {
          deleteRecursively(out);
        }
      } finally {
        deleteRecursively(src);
      }

      // ServiceLoader pointer.
      JarEntry svc = new JarEntry("META-INF/services/" + AdminCliCommandProvider.class.getName());
      jos.putNextEntry(svc);
      jos.write("de.dlr.shepard.fakeplugin.FakePluginProvider\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      jos.closeEntry();
    }
    return jar;
  }

  private static Manifest buildManifest() {
    Manifest m = new Manifest();
    m.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    return m;
  }

  private static void addClassToJar(JarOutputStream jos, Path classDir, String entryName) throws Exception {
    Path classFile = classDir.resolve(entryName.replace('/', java.io.File.separatorChar));
    JarEntry entry = new JarEntry(entryName);
    jos.putNextEntry(entry);
    jos.write(Files.readAllBytes(classFile));
    jos.closeEntry();
  }

  private static String computeClasspath() {
    // Pull the current test classpath verbatim so the fixture's
    // FakePluginProvider can see AdminCliCommandProvider + Picocli.
    StringBuilder cp = new StringBuilder();
    String sep = File.pathSeparator;
    for (String entry : System.getProperty("java.class.path").split(sep)) {
      if (cp.length() > 0) cp.append(sep);
      cp.append(entry);
    }
    return cp.toString();
  }

  private static void deleteRecursively(Path root) throws Exception {
    if (!Files.exists(root)) return;
    Files.walk(root).sorted((a, b) -> b.compareTo(a)).forEach(p -> {
      try {
        Files.delete(p);
      } catch (java.io.IOException ignored) {
        // best effort
      }
    });
  }

  /** Bare root command for tests that need a fresh CommandLine. */
  @CommandLine.Command(name = "test-root")
  static final class EmptyRootCommand implements Runnable {

    @Override
    public void run() {
      // no-op
    }
  }
}
