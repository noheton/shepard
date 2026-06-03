package de.dlr.shepard.spi.ai;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AI1a — CDI dispatcher that discovers every {@link Transport} bean
 * at startup and resolves "which transport serves capability X"
 * queries from the rest of the platform.
 *
 * <p>Shape mirrors {@link de.dlr.shepard.publish.minter.MinterRegistry}
 * from KIP1a: walk all CDI beans, index by id, log conflicts, expose
 * a stable resolution API. The {@link LlmProvider} implementation
 * (in {@code shepard-plugin-ai}) reads the {@code :AiCapabilityConfig}
 * slot for a capability + transport id, asks this registry for the
 * matching bean, and dispatches.
 *
 * <p><b>Why the registry lives in core, not in the plugin:</b>
 * the {@link Transport} SPI is a stability contract; multiple
 * plugins (the canonical {@code LocalEchoTransport} reference impl
 * in {@code shepard-plugin-ai}, future vendor-specific adapters,
 * operator-supplied custom transports) all need to be discoverable
 * by the same registry. A registry-in-core / adapters-in-plugins
 * shape matches the established
 * {@link de.dlr.shepard.publish.minter.MinterRegistry} idiom and
 * the CLAUDE.md plugin-first heuristic #3.
 *
 * <p><b>Failure posture:</b> never fail-fast. Every degraded state
 * surfaces as a WARN at startup plus an empty {@link Optional}
 * return from the resolution methods; the call-site (the
 * {@link LlmProvider} impl) translates "no transport for capability"
 * into the doc 86 §4 BYOK chain's terminal "capability unconfigured"
 * HTTP 503 RFC 7807 problem. This matches MinterRegistry's
 * plugin-first posture: operators who haven't installed a transport
 * plugin yet should boot cleanly.
 */
@ApplicationScoped
public class AiRegistry {

  @Inject
  Instance<Transport> transports;

  /** transport id → bean. */
  private volatile Map<String, Transport> byId = Map.of();

  /** capability → set of transport ids that advertise it. */
  private volatile Map<AiCapability, Set<String>> byCapability = Map.of();

  /** Constructor for CDI. */
  public AiRegistry() {}

  /** Visible for testing — bypasses the {@link Observes} startup hook. */
  AiRegistry(Instance<Transport> transports) {
    this.transports = transports;
    resolve();
  }

  /**
   * Quarkus startup hook — walks the discovered transports once
   * all CDI beans are constructed. Never throws; degraded states
   * are warnings.
   */
  void onStartup(@Observes StartupEvent ev) {
    resolve();
  }

  /**
   * Idempotent re-discovery. Test code calls this directly; the
   * runtime uses {@link #onStartup}. Visibility is package-private
   * — tests in the same package re-run discovery after wiring
   * mock {@code Instance<Transport>} sources.
   */
  void resolve() {
    Map<String, Transport> idMap = new LinkedHashMap<>();
    Map<AiCapability, Set<String>> capMap = new EnumMap<>(AiCapability.class);
    List<Transport> discovered = new ArrayList<>();

    if (transports != null) {
      for (Transport t : transports) {
        if (t == null) continue;
        discovered.add(t);
        String id = t.id();
        if (id == null || id.isBlank()) {
          Log.warnf(
            "AiRegistry: skipping %s — id() returned null/blank",
            t.getClass().getName()
          );
          continue;
        }
        Transport prior = idMap.putIfAbsent(id, t);
        if (prior != null) {
          Log.warnf(
            "AiRegistry: duplicate Transport id '%s' — keeping %s, ignoring %s",
            id,
            prior.getClass().getName(),
            t.getClass().getName()
          );
          continue;
        }
        Set<AiCapability> caps = t.supportedCapabilities();
        if (caps == null || caps.isEmpty()) {
          Log.warnf(
            "AiRegistry: Transport '%s' (%s) declares no supportedCapabilities — " +
            "registered but won't be dispatched against any slot.",
            id,
            t.getClass().getName()
          );
          continue;
        }
        for (AiCapability c : caps) {
          capMap.computeIfAbsent(c, k -> new LinkedHashSet<>()).add(id);
        }
      }
    }

    // Preserve discovery (insertion) order — idMap is a LinkedHashMap built in
    // ServiceLoader order. Map.copyOf would drop that ordering, making allIds()
    // and the log line non-deterministic.
    this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(idMap));
    Map<AiCapability, Set<String>> sealed = new EnumMap<>(AiCapability.class);
    capMap.forEach((k, v) -> sealed.put(k, Collections.unmodifiableSet(new LinkedHashSet<>(v))));
    this.byCapability = Collections.unmodifiableMap(sealed);

    String available = byId.isEmpty() ? "<none>" : String.join(", ", byId.keySet());
    Log.infof(
      "AiRegistry: discovered %d transport(s): [%s]; %d capability/transport binding(s).",
      byId.size(),
      available,
      byCapability.values().stream().mapToInt(Set::size).sum()
    );
  }

  /**
   * @return the transport with the given id, or empty when the
   *         classpath has no such adapter installed. Used by the
   *         {@link LlmProvider} impl after reading the capability
   *         slot's {@code transport} field.
   */
  public Optional<Transport> byId(String id) {
    if (id == null || id.isBlank()) return Optional.empty();
    return Optional.ofNullable(byId.get(id));
  }

  /**
   * @return all transport ids that advertise the given capability
   *         (enabled or disabled). The set is order-preserving
   *         per discovery order so admin UIs surface a stable
   *         "available transports" picker.
   */
  public Set<String> idsForCapability(AiCapability capability) {
    if (capability == null) return Set.of();
    Set<String> s = byCapability.get(capability);
    return s == null ? Set.of() : s;
  }

  /**
   * Pick the first {@link Transport#isEnabled() enabled} transport
   * for {@code capability}. Convenience for tests / fallback paths
   * — production code resolves the explicit transport id from
   * {@code :AiCapabilityConfig} and calls {@link #byId} instead.
   *
   * @return the first enabled match, or empty when no installed
   *         transport advertises the capability in an enabled state
   */
  public Optional<Transport> firstEnabledFor(AiCapability capability) {
    for (String id : idsForCapability(capability)) {
      Transport t = byId.get(id);
      if (t != null && t.isEnabled()) return Optional.of(t);
    }
    return Optional.empty();
  }

  /**
   * @return an immutable view of the discovered transport ids.
   *         Surfaced through {@code GET /v2/admin/ai/providers}
   *         so admins see what's wireable.
   */
  public Set<String> allIds() {
    return byId.keySet();
  }

  /**
   * @return an immutable view of the discovered capability bindings.
   *         Surfaced through {@code GET /v2/admin/ai/capabilities}
   *         so admins see "for TEXT, you can pick from
   *         [openai-compat, anthropic, local-echo]".
   */
  public Map<AiCapability, Set<String>> bindings() {
    return byCapability;
  }
}
