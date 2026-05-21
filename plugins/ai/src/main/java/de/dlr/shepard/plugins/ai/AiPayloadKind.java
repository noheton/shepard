package de.dlr.shepard.plugins.ai;

import de.dlr.shepard.spi.payload.PayloadKind;
import java.util.List;

/**
 * AI1 — {@link PayloadKind} registration for the AI plugin's Neo4j-OGM
 * entity packages.
 *
 * <p>Discovered by {@code NeoConnector.connect()} via {@code ServiceLoader}
 * before CDI is up, so {@code :AiCapabilityConfig} and {@code :AiActivity}
 * nodes are included in the OGM schema without needing to add the package
 * to the backend's own OGM configuration.
 */
public final class AiPayloadKind implements PayloadKind {

  @Override
  public String name() {
    return "ai";
  }

  @Override
  public List<String> entityPackages() {
    return List.of("de.dlr.shepard.plugins.ai.entities");
  }
}
