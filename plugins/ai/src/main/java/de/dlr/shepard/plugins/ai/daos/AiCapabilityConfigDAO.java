package de.dlr.shepard.plugins.ai.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.plugins.ai.entities.AiCapabilityConfig;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.Optional;

/**
 * AI1 — DAO for {@link AiCapabilityConfig} nodes.
 *
 * <p>{@code @ApplicationScoped} — configs are read on every LLM call.
 * The per-capability uniqueness invariant (at most one
 * {@code :AiCapabilityConfig} node per {@code capability} value) is
 * maintained by {@code AiCapabilityConfigService.upsertConfig()}.
 */
@ApplicationScoped
public class AiCapabilityConfigDAO extends GenericDAO<AiCapabilityConfig> {

  @Override
  public Class<AiCapabilityConfig> getEntityType() {
    return AiCapabilityConfig.class;
  }

  /**
   * Find the config node for a specific capability, or
   * {@link Optional#empty()} if none has been seeded.
   *
   * @param capability the {@code AiCapability.name()} string (e.g. {@code "TEXT"})
   */
  public Optional<AiCapabilityConfig> findByCapability(String capability) {
    Iterable<AiCapabilityConfig> results = findByQuery(
      "MATCH (n:AiCapabilityConfig {capability: $cap}) RETURN n",
      Map.of("cap", capability)
    );
    for (AiCapabilityConfig cfg : results) {
      return Optional.of(cfg);
    }
    return Optional.empty();
  }
}
