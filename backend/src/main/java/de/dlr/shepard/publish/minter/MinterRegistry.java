package de.dlr.shepard.publish.minter;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * KIP1a registry — discovers every {@link Minter} CDI bean and
 * resolves which one is "active" via the deploy-time
 * {@code shepard.publish.minter} key.
 *
 * <p>Designed in {@code aidocs/66 §5} and follows the
 * {@link de.dlr.shepard.context.references.git.adapters.GitAdapterRegistry}
 * idiom: an in-tree CDI registry that walks the discovered beans
 * and surfaces the matching one — modulo G1's host-substring
 * matching being replaced here with a deploy-time-pinned id.
 *
 * <p><strong>Optional posture (KIP1h).</strong> Pre-KIP1h the
 * registry fail-fasted on a missing configured-minter id —
 * appropriate when the in-core {@code MockMinter} guaranteed at
 * least one bean was always present. Post-KIP1h every minter lives
 * in a plugin (per CLAUDE.md plugin-first heuristic #3), so an
 * operator who hasn't picked a PID provider yet has a legitimate
 * "no minter installed" install state. The registry now degrades
 * cleanly:
 *
 * <ul>
 *   <li><strong>Configured id matches</strong> → that minter is
 *       active, {@link #activeMinter()} returns it wrapped in an
 *       {@code Optional}.</li>
 *   <li><strong>Configured id unset (or {@code none})</strong> →
 *       no active minter. WARN logged at startup; publish endpoint
 *       returns 503 {@code publish.minter.not-installed}.</li>
 *   <li><strong>Configured id set but no matching bean</strong> →
 *       no active minter. WARN logged at startup with the
 *       operator-actionable hint ("install
 *       shepard-plugin-minter-{id} or set shepard.publish.minter to
 *       a registered id"). Publish endpoint returns the same 503.
 *       <em>Not</em> fail-fast — the resolver
 *       ({@code GET /v2/.well-known/kip/{pid-suffix}} in the kip
 *       plugin) still works against pre-existing
 *       {@code :Publication} rows.</li>
 *   <li><strong>Configured id matches a disabled bean</strong> →
 *       no active minter. WARN logged. Same 503 path.</li>
 * </ul>
 *
 * <p>Duplicate ids ({@code two beans return id()="epic"}) log a
 * WARN and resolve to the first bean the iterator surfaces — that's
 * defensive rather than fail-fast because the plugin-first shape
 * means an operator running with both a stock and a forked ePIC
 * plugin should still boot (with the warning); the first-wins rule
 * matches G1's host-substring behaviour.
 */
@ApplicationScoped
public class MinterRegistry {

  /**
   * Sentinel value an operator can set to explicitly disable the
   * publish endpoint without uninstalling any plugin. Treated as
   * "no active minter" identically to the unset / empty case.
   */
  public static final String NONE = "none";

  /**
   * Deploy-time key. Default {@code "local"} matches the
   * {@code shepard-plugin-minter-local} id (KIP1h); {@code "epic"}
   * (KIP1c) and {@code "datacite"} (KIP1d) arrive with their
   * respective plugin Maven modules. Set to {@code "none"} (or
   * leave blank) to disable the publish endpoint without
   * uninstalling minter plugins.
   *
   * <p>Documented as deploy-time-only per {@code CLAUDE.md}
   * "cluster identity / topology" exception — switching the active
   * minter changes the PID provider, which is not safely
   * runtime-flippable.
   */
  @Inject
  @ConfigProperty(name = "shepard.publish.minter", defaultValue = "local")
  String configuredMinterId;

  @Inject
  Instance<Minter> minters;

  private volatile Minter activeMinter;
  private volatile Map<String, Minter> byId = Map.of();

  /** Constructor for CDI. */
  public MinterRegistry() {}

  /** Visible for testing. */
  MinterRegistry(String configuredMinterId, Instance<Minter> minters) {
    this.configuredMinterId = configuredMinterId;
    this.minters = minters;
    resolve();
  }

  /**
   * Quarkus startup hook — resolves the active minter once all CDI
   * beans are constructed.
   *
   * <p>Post-KIP1h this method <strong>never throws</strong>: missing
   * / disabled minters degrade to "no active minter" + a WARN, and
   * the publish endpoint emits 503 {@code publish.minter.not-installed}
   * on demand. The resolver path is unaffected.
   */
  void onStartup(@Observes StartupEvent ev) {
    resolve();
  }

  /**
   * Idempotent resolve. Builds the by-id map, picks the configured
   * adapter or leaves {@link #activeMinter} null if the install is
   * minter-less.
   */
  void resolve() {
    Map<String, Minter> map = new LinkedHashMap<>();
    List<Minter> discovered = new ArrayList<>();
    if (minters != null) {
      for (Minter m : minters) {
        discovered.add(m);
        if (m == null) continue;
        String id = m.id();
        if (id == null || id.isBlank()) {
          Log.warnf("MinterRegistry: skipping %s — id() returned null/blank", m.getClass().getName());
          continue;
        }
        Minter prior = map.putIfAbsent(id, m);
        if (prior != null) {
          Log.warnf(
            "MinterRegistry: duplicate Minter id '%s' — keeping %s, ignoring %s",
            id,
            prior.getClass().getName(),
            m.getClass().getName()
          );
        }
      }
    }
    this.byId = Map.copyOf(map);

    String available = byId.isEmpty() ? "<none>" : String.join(", ", byId.keySet());
    Log.infof("MinterRegistry: discovered %d minter(s): [%s]", byId.size(), available);

    String want = configuredMinterId == null ? "" : configuredMinterId.trim();

    if (want.isEmpty() || NONE.equalsIgnoreCase(want)) {
      this.activeMinter = null;
      Log.warnf(
        "MinterRegistry: shepard.publish.minter is unset (or 'none') — publish endpoint will return 503 " +
        "until shepard.publish.minter is set + the matching plugin is installed. " +
        "The KIP resolver remains active for any pre-existing :Publication rows."
      );
      return;
    }

    Minter picked = byId.get(want);
    if (picked == null) {
      this.activeMinter = null;
      Log.warnf(
        "MinterRegistry: shepard.publish.minter=%s — no matching Minter bean on the classpath. " +
        "Available: %s. Install shepard-plugin-minter-%s (or another minter plugin and " +
        "set shepard.publish.minter to its id). Publish endpoint will return 503; resolver still works.",
        want,
        available,
        want
      );
      return;
    }
    if (!picked.isEnabled()) {
      this.activeMinter = null;
      Log.warnf(
        "MinterRegistry: shepard.publish.minter=%s is registered but reports isEnabled()=false. " +
        "Check its configuration. Publish endpoint will return 503; resolver still works.",
        want
      );
      return;
    }
    this.activeMinter = picked;
    Log.infof(
      "MinterRegistry: active minter '%s' (%s); %d minter(s) discovered.",
      picked.id(),
      picked.getClass().getName(),
      discovered.size()
    );
  }

  /**
   * @return the currently-active minter, or {@code Optional.empty()}
   *         when the install has no minter wired (unset config,
   *         missing plugin, or disabled bean). Callers in the
   *         publish path translate empty into a 503 RFC 7807 problem
   *         response.
   */
  public Optional<Minter> activeMinter() {
    return Optional.ofNullable(activeMinter);
  }

  /**
   * @return the configured minter id (verbatim, post-trim), or
   *         {@code "<unset>"} when no minter is configured.
   *         Diagnostic / log surface; the {@link #activeMinter()}
   *         optional is the authoritative readiness signal.
   */
  public String activeMinterId() {
    return activeMinter == null ? "<unset>" : activeMinter.id();
  }
}
