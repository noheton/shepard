package de.dlr.shepard.plugin;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test fixture for {@link PluginRegistryTest}. Provides hand-written
 * {@link PluginManifest} implementations with distinct ids — one
 * concrete class per test scenario, so each can be the target of a
 * {@code META-INF/services/PluginManifest} entry in the test JAR
 * fixture without the synthetic-subclass dance.
 *
 * <p>The {@code OnRegisterCalls} / {@code OnUnregisterCalls} counters
 * are static because the {@code ServiceLoader.load} instantiates
 * each manifest in the child classloader's namespace — and we still
 * need the test code (running in the parent loader's namespace) to
 * observe whether {@code onRegister} fired. Since the support class
 * lives on the parent loader (the child's parent), the same static
 * counter is visible to both sides.
 */
public final class PluginRegistryTestSupport {

  private PluginRegistryTestSupport() {}

  /**
   * Shared counter — incremented once per {@code onRegister} call
   * across all {@code RecordingManifest} subclasses.
   */
  public static final class OnRegisterCalls {

    private static final AtomicInteger COUNT = new AtomicInteger();

    private OnRegisterCalls() {}

    public static int get() {
      return COUNT.get();
    }

    public static void reset() {
      COUNT.set(0);
    }

    public static void increment() {
      COUNT.incrementAndGet();
    }
  }

  /** Same shape for {@code onUnregister}. */
  public static final class OnUnregisterCalls {

    private static final AtomicInteger COUNT = new AtomicInteger();

    private OnUnregisterCalls() {}

    public static int get() {
      return COUNT.get();
    }

    public static void reset() {
      COUNT.set(0);
    }

    public static void increment() {
      COUNT.incrementAndGet();
    }
  }

  /**
   * Base recording manifest. Subclasses fix the plugin id via the
   * abstract {@link #id()} override.
   */
  public abstract static class RecordingManifest implements PluginManifest {

    @Override
    public String version() {
      return "0.0.1-test";
    }

    @Override
    public String shepardCompatibility() {
      return ">=5.2.0,<6";
    }

    @Override
    public void onRegister(PluginContext ctx) {
      OnRegisterCalls.increment();
    }

    @Override
    public void onUnregister(PluginContext ctx) {
      OnUnregisterCalls.increment();
    }
  }

  /** Plugin id = "test-plugin". */
  public static final class RecordingManifest$test_plugin extends RecordingManifest {

    @Override
    public String id() {
      return "test-plugin";
    }
  }

  /** Plugin id = "test-plugin-disabled". */
  public static final class RecordingManifest$test_plugin_disabled extends RecordingManifest {

    @Override
    public String id() {
      return "test-plugin-disabled";
    }
  }

  /** Plugin id = "recording". */
  public static final class RecordingManifest$recording extends RecordingManifest {

    @Override
    public String id() {
      return "recording";
    }
  }

  /** Plugin id = "flip". */
  public static final class RecordingManifest$flip extends RecordingManifest {

    @Override
    public String id() {
      return "flip";
    }
  }

  /** Plugin id = "dup". */
  public static final class RecordingManifest$dup extends RecordingManifest {

    @Override
    public String id() {
      return "dup";
    }
  }

  /** Plugin id = "shutdownp". */
  public static final class RecordingManifest$shutdownp extends RecordingManifest {

    @Override
    public String id() {
      return "shutdownp";
    }
  }

  /**
   * Throws from {@code onRegister} — the registry should mark it
   * FAILED and continue with the next plugin.
   */
  public abstract static class ThrowingManifest implements PluginManifest {

    @Override
    public String version() {
      return "0.0.1-test";
    }

    @Override
    public String shepardCompatibility() {
      return ">=5.2.0,<6";
    }

    @Override
    public void onRegister(PluginContext ctx) {
      throw new IllegalStateException("kaboom");
    }
  }

  /** Plugin id = "throwing". */
  public static final class ThrowingManifest$throwing extends ThrowingManifest {

    @Override
    public String id() {
      return "throwing";
    }
  }
}
