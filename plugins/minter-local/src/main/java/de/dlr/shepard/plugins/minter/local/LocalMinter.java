package de.dlr.shepard.plugins.minter.local;

import de.dlr.shepard.publish.minter.MintRequest;
import de.dlr.shepard.publish.minter.MintResult;
import de.dlr.shepard.publish.minter.Minter;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import java.time.Clock;
import java.time.Instant;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * KIP1h — default minter for fresh shepard installs.
 *
 * <p>Mints stable, versioned, local-instance PIDs of the form
 * {@code shepard:<instance.id>:<kind>:<appId>:v<n>}.
 *
 * <p>Phase-1 versioning shape (per KIP1h spec): the version segment
 * comes from {@link MintRequest#versionNumber()}, computed by the
 * core {@code PublishService} as {@code findLatestVersionNumber + 1}
 * on the entity's existing {@code :Publication} rows. Same entity +
 * same version = same PID — the local PID is now stable (no
 * epoch-millis timestamp). A forced re-mint via
 * {@code POST /publish?force=true} bumps the version, producing a
 * distinct PID.
 *
 * <p>Format components:
 * <ul>
 *   <li>{@code shepard:} — fixed scheme prefix marking the PID as a
 *       local-instance minter output. The {@code mock:} prefix from
 *       pre-KIP1h is gone; "mock" misled operators into thinking it
 *       was a test fixture rather than the legitimate default.
 *   <li>{@code <instance.id>} — value of the deploy-time
 *       {@code shepard.instance.id} config key (cluster-identity
 *       exception per CLAUDE.md). Operators should set this to a
 *       namespaced string like {@code dlr.de/shepard-prod} so PIDs
 *       minted by different instances don't collide. Fallback
 *       {@code local} on unset / blank, with a WARN at startup.
 *   <li>{@code <kind>} — the entity-kind segment ({@code collections},
 *       {@code data-objects}, etc.) from {@link MintRequest#entityKind()}.
 *   <li>{@code <appId>} — the entity's UUID v7 appId.
 *   <li>{@code v<n>} — the version number ({@code v1} on first
 *       publish, {@code v2} after the first {@code force=true} bump,
 *       etc.). Phase 2 (ENT1 umbrella) will replace this with a
 *       full {@code :EntityVersion} graph; the wire format here is
 *       compatible — a v<n> segment slots cleanly into a future
 *       version-graph node.
 * </ul>
 *
 * <p>The PID never throws {@link de.dlr.shepard.publish.minter.MinterException}
 * — it's a pure-local format computation with no external dependency.
 * The {@code MinterException} contract is part of the SPI for ePIC /
 * DataCite use later (KIP1c / KIP1d).
 *
 * <p>Per CLAUDE.md plugin-first heuristic #3 ("SPIs stay in-tree,
 * adapters ship as plugins"), this minter — the legitimate default
 * — ships as a drop-in plugin module. The in-core
 * {@link Minter} interface, {@link MintRequest},
 * {@link MintResult}, {@code MinterRegistry}, and
 * {@code MinterException} all stay in {@code backend/}; only the
 * impls live in {@code plugins/}.
 */
@ApplicationScoped
public class LocalMinter implements Minter {

  /**
   * Stable id used in {@code shepard.publish.minter=local}.
   * Matches the plugin's manifest id (minus the trailing
   * "{@code -local}" suffix) for operator clarity.
   */
  public static final String ID = "local";

  /**
   * Fallback {@code instance.id} when {@code shepard.instance.id} is
   * unset / blank. Logged at WARN on startup so operators know to
   * set a proper namespaced value before any PIDs are minted; the
   * fallback exists so that fresh greenfield installs boot without
   * blocking on configuration.
   */
  public static final String INSTANCE_ID_FALLBACK = "local";

  /**
   * Deploy-time cluster identity. The {@code shepard.instance.id}
   * key is already used by PROV1 (stamped onto every {@code :Activity}
   * row) — KIP1h reuses it rather than introducing a duplicate
   * {@code shepard.publish.minter.local.instance-id} key. Both
   * surfaces share the same operator semantics (per-instance
   * namespace).
   *
   * <p>The {@code defaultValue} mirrors {@code application.properties}'
   * {@code shepard.instance.id=local} default. Operators upgrading
   * an old install where {@code shepard.instance.id} was never set
   * see {@code local} here.
   */
  @Inject
  @ConfigProperty(name = "shepard.instance.id", defaultValue = INSTANCE_ID_FALLBACK)
  String configuredInstanceId;

  private final Clock clock;

  public LocalMinter() {
    this.clock = Clock.systemUTC();
  }

  /** Visible for testing. */
  LocalMinter(Clock clock, String configuredInstanceId) {
    this.clock = clock;
    this.configuredInstanceId = configuredInstanceId;
  }

  /**
   * Startup hook — log a WARN when the operator is running on the
   * {@link #INSTANCE_ID_FALLBACK} default. Production deployments
   * should set {@code shepard.instance.id} to a namespaced string
   * ({@code dlr.de/shepard-prod}, {@code example.org/lab-a}) so PIDs
   * minted by different instances don't collide.
   */
  void onStartup(@Observes StartupEvent ev) {
    if (isFallbackInstanceId()) {
      Log.warnf(
        "LocalMinter: shepard.instance.id is unset or blank — falling back to '%s'. " +
        "Production installs should set this to a namespaced value (e.g. 'dlr.de/shepard-prod') " +
        "so PIDs minted by different instances don't collide.",
        INSTANCE_ID_FALLBACK
      );
    } else {
      Log.infof("LocalMinter: ready. instance.id=%s", instanceId());
    }
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public boolean isEnabled() {
    return true;
  }

  @Override
  public MintResult mint(MintRequest req) {
    int version = req.versionNumber();
    if (version < 1) {
      // Defensive guard — PublishService computes the version as
      // findLatestVersionNumber + 1 so it should always be >= 1.
      // Treat invalid versions as a fresh first publication.
      version = 1;
    }
    String pid = "shepard:" + instanceId() + ":" + req.entityKind() + ":" + req.appId() + ":v" + version;
    return new MintResult(pid, clock.instant(), ID);
  }

  /**
   * Resolve the {@code instance.id} segment, applying the fallback
   * when the configured value is null / blank. Package-private so
   * tests can assert the fallback shape.
   */
  String instanceId() {
    if (configuredInstanceId == null || configuredInstanceId.isBlank()) return INSTANCE_ID_FALLBACK;
    return configuredInstanceId.trim();
  }

  /** Test-visible — whether we're running on the fallback. */
  boolean isFallbackInstanceId() {
    return configuredInstanceId == null || configuredInstanceId.isBlank();
  }

  /** Visible for testing — observable mint timestamp. */
  Instant nowForTests() {
    return clock.instant();
  }
}
