package de.dlr.shepard.common.configuration.infrastructure;

import io.quarkus.logging.Log;
import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.ConfigValue;

/**
 * Indirection bean for the spatial-data infrastructure toggle.
 *
 * <p>Reads both the new {@value #NEW_PROPERTY} key and the legacy
 * {@value #OLD_PROPERTY} key. The new key takes precedence; the old key
 * is honoured as a backward-compatibility alias and logs a one-shot
 * deprecation warning at startup. Planned removal in v6.0.
 */
@Startup
@ApplicationScoped
public class SpatialDataConfig {

  static final String NEW_PROPERTY = "shepard.infrastructure.spatial.enabled";
  static final String OLD_PROPERTY = "shepard.spatial-data.enabled";
  static final String DEFAULT_SOURCE = "PropertiesConfigSource[source=application.properties]";
  static final String REMOVAL_DEADLINE = "v6.0";

  private boolean enabled;

  public SpatialDataConfig() {
    // CDI
  }

  @PostConstruct
  void init() {
    Config config = ConfigProvider.getConfig();
    resolve(config);
  }

  void resolve(Config config) {
    ConfigValue newValue = config.getConfigValue(NEW_PROPERTY);
    ConfigValue oldValue = config.getConfigValue(OLD_PROPERTY);

    boolean newOverridden = isExternallyOverridden(newValue);
    boolean oldOverridden = isExternallyOverridden(oldValue);

    String chosen;
    if (newOverridden) {
      chosen = newValue.getValue();
      if (oldOverridden && !sameValue(newValue, oldValue)) {
        Log.warnf(
          "Both '%s' (=%s) and deprecated '%s' (=%s) are set with different values; using '%s'. " +
          "Remove the deprecated key. Planned removal in %s.",
          NEW_PROPERTY,
          newValue.getValue(),
          OLD_PROPERTY,
          oldValue.getValue(),
          NEW_PROPERTY,
          REMOVAL_DEADLINE
        );
      }
    } else if (oldOverridden) {
      chosen = oldValue.getValue();
      Log.warnf(
        "Configuration key '%s' is deprecated; please migrate to '%s'. Planned removal in %s.",
        OLD_PROPERTY,
        NEW_PROPERTY,
        REMOVAL_DEADLINE
      );
    } else {
      chosen = newValue != null && newValue.getValue() != null ? newValue.getValue() : "false";
    }

    this.enabled = Boolean.parseBoolean(chosen);
  }

  public boolean isEnabled() {
    return enabled;
  }

  private static boolean isExternallyOverridden(ConfigValue value) {
    if (value == null || value.getValue() == null) {
      return false;
    }
    String source = value.getSourceName();
    if (source == null) {
      return false;
    }
    // application.properties holds the bundled defaults / alias chain;
    // any other source (env vars, system properties, external config) is
    // treated as a deliberate user override.
    return !source.contains("application.properties");
  }

  private static boolean sameValue(ConfigValue a, ConfigValue b) {
    String av = a == null ? null : a.getValue();
    String bv = b == null ? null : b.getValue();
    if (av == null) {
      return bv == null;
    }
    return av.equals(bv);
  }
}
