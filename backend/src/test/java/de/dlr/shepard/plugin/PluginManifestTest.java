package de.dlr.shepard.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * PM1c — covers the new default methods on {@link PluginManifest}.
 * A manifest that only overrides the three mandatory abstract methods
 * ({@code id}, {@code version}, {@code shepardCompatibility}) should
 * still produce sensible defaults for every PM1c-added field — that's
 * the SPI's backward-compatibility contract.
 */
class PluginManifestTest {

  /** Bare manifest — overrides only the three mandatory methods. */
  private static final class BareManifest implements PluginManifest {

    @Override
    public String id() {
      return "bare";
    }

    @Override
    public String version() {
      return "0.1.0";
    }

    @Override
    public String shepardCompatibility() {
      return ">=5.2.0,<6";
    }
  }

  /** Rich manifest — overrides every default method. */
  private static final class RichManifest implements PluginManifest {

    @Override
    public String id() {
      return "rich";
    }

    @Override
    public String version() {
      return "2.5.0";
    }

    @Override
    public String shepardCompatibility() {
      return ">=5.2.0,<6";
    }

    @Override
    public String title() {
      return "Rich Plugin";
    }

    @Override
    public String description() {
      return "A plugin with all the new PM1c metadata filled in.";
    }

    @Override
    public Optional<URI> homepageUrl() {
      return Optional.of(URI.create("https://example.com/rich"));
    }

    @Override
    public Optional<URI> repositoryUrl() {
      return Optional.of(URI.create("https://github.com/example/rich"));
    }

    @Override
    public String licence() {
      return "MIT";
    }

    @Override
    public List<PluginDependency> dependencies() {
      return List.of(new PluginDependency("base", "[1.0,2.0)"));
    }
  }

  @Test
  void bareManifest_titleDefaultsToId() {
    assertThat(new BareManifest().title()).isEqualTo("bare");
  }

  @Test
  void bareManifest_descriptionDefaultsToEmpty() {
    assertThat(new BareManifest().description()).isEmpty();
  }

  @Test
  void bareManifest_homepageDefaultsToEmpty() {
    assertThat(new BareManifest().homepageUrl()).isEmpty();
  }

  @Test
  void bareManifest_repositoryDefaultsToEmpty() {
    assertThat(new BareManifest().repositoryUrl()).isEmpty();
  }

  @Test
  void bareManifest_licenceDefaultsToEmpty() {
    assertThat(new BareManifest().licence()).isEmpty();
  }

  @Test
  void bareManifest_dependenciesDefaultsToEmptyList() {
    assertThat(new BareManifest().dependencies()).isEmpty();
  }

  @Test
  void richManifest_returnsOverriddenValues() {
    RichManifest m = new RichManifest();
    assertThat(m.title()).isEqualTo("Rich Plugin");
    assertThat(m.description()).startsWith("A plugin with all");
    assertThat(m.homepageUrl()).contains(URI.create("https://example.com/rich"));
    assertThat(m.repositoryUrl()).contains(URI.create("https://github.com/example/rich"));
    assertThat(m.licence()).isEqualTo("MIT");
    assertThat(m.dependencies()).hasSize(1);
    PluginDependency dep = m.dependencies().get(0);
    assertThat(dep.pluginId()).isEqualTo("base");
    assertThat(dep.versionConstraint()).isEqualTo("[1.0,2.0)");
  }

  @Test
  void pluginDependency_rejectsBlankId() {
    org.assertj.core.api.Assertions.assertThatThrownBy(() -> new PluginDependency("", "[1.0,)"))
      .isInstanceOf(IllegalArgumentException.class);
  }
}
