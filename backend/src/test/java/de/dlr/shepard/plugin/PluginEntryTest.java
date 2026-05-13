package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class PluginEntryTest {

  @Test
  void state_startsDiscovered() {
    PluginEntry entry = new PluginEntry(new PluginRegistryTestSupport.RecordingManifest$flip(), Paths.get("/tmp/x.jar"));
    assertThat(entry.state()).isEqualTo(PluginState.DISCOVERED);
    assertThat(entry.failureMessage()).isNull();
  }

  @Test
  void transitions_areVisible() {
    PluginEntry entry = new PluginEntry(new PluginRegistryTestSupport.RecordingManifest$flip(), null);
    entry.markEnabled();
    assertThat(entry.state()).isEqualTo(PluginState.ENABLED);
    entry.markDisabled();
    assertThat(entry.state()).isEqualTo(PluginState.DISABLED);
    entry.markFailed("nope");
    assertThat(entry.state()).isEqualTo(PluginState.FAILED);
    assertThat(entry.failureMessage()).isEqualTo("nope");
    entry.markEnabled();
    assertThat(entry.failureMessage()).isNull();
  }

  @Test
  void manifest_passthrough_works() {
    var manifest = new PluginRegistryTestSupport.RecordingManifest$flip();
    PluginEntry entry = new PluginEntry(manifest, null);
    assertThat(entry.id()).isEqualTo("flip");
    assertThat(entry.version()).isEqualTo("0.0.1-test");
    assertThat(entry.shepardCompatibility()).isEqualTo(">=5.2.0,<6");
    assertThat(entry.manifest()).isSameAs(manifest);
  }

  @Test
  void requiresManifest() {
    assertThatThrownBy(() -> new PluginEntry(null, null))
      .isInstanceOf(NullPointerException.class);
  }
}
