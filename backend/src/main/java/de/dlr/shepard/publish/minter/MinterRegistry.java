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
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * KIP1a registry — discovers every {@link Minter} CDI bean and
 * resolves which one is "active" via the deploy-time
 * {@code shepard.publish.minter} key (default {@code "mock"}).
 *
 * <p>Designed in {@code aidocs/66 §5} and follows the
 * {@link de.dlr.shepard.context.references.git.adapters.GitAdapterRegistry}
 * idiom: an in-tree CDI registry that walks the discovered beans
 * and surfaces the matching one — modulo G1's host-substring
 * matching being replaced here with a deploy-time-pinned id.
 *
 * <p>Fail-fast posture per {@code CLAUDE.md} "Migrations must be
 * idempotent and fail-fast" + the broader application-startup
 * convention: if the configured minter id has no matching bean the
 * registry aborts startup with a clear message. Operators see the
 * miss-config at the {@code mvn quarkus:dev} log line rather than
 * mid-request weeks later.
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
   * Deploy-time key. {@code "mock"} is the only id that ships in
   * KIP1a; {@code "epic"} (KIP1c) and {@code "datacite"} (KIP1d)
   * arrive with their respective plugin Maven modules.
   *
   * <p>Documented as deploy-time-only per {@code CLAUDE.md}
   * "cluster identity / topology" exception — switching the active
   * minter changes the PID provider, which is not safely
   * runtime-flippable.
   */
  @Inject
  @ConfigProperty(name = "shepard.publish.minter", defaultValue = "mock")
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
   * beans are constructed. Failing here aborts startup; that's the
   * intended posture (see class javadoc).
   */
  void onStartup(@Observes StartupEvent ev) {
    resolve();
  }

  /**
   * Idempotent resolve. Builds the by-id map, picks the configured
   * adapter, aborts if missing.
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

    String want = configuredMinterId == null ? "mock" : configuredMinterId.trim();
    Minter picked = byId.get(want);
    if (picked == null) {
      String available = byId.isEmpty() ? "<none>" : String.join(", ", byId.keySet());
      throw new IllegalStateException(
        "shepard.publish.minter=" +
        want +
        " — no Minter bean with that id is registered. Available: " +
        available +
        ". Did you forget to ship the shepard-plugin-minter-" +
        want +
        " module?"
      );
    }
    if (!picked.isEnabled()) {
      throw new IllegalStateException(
        "shepard.publish.minter=" + want + " is registered but reports isEnabled()=false. Check its configuration."
      );
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
   * @return the currently-active minter — guaranteed non-null
   *         post-startup (otherwise startup would have aborted).
   */
  public Minter activeMinter() {
    if (activeMinter == null) resolve();
    return activeMinter;
  }

  /** @return the configured minter id (verbatim, post-trim). */
  public String activeMinterId() {
    return activeMinter().id();
  }
}
