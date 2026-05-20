package de.dlr.shepard.plugins.aas;

import de.dlr.shepard.plugins.aas.entities.AasRegistration;
import de.dlr.shepard.spi.payload.PayloadKind;
import java.util.List;

/**
 * Registers the AasRegistration Neo4j-OGM entity package with NeoConnector.
 *
 * <p>AasRegistration is a @NodeEntity used by the AAS outbox sync. Without this
 * registration OGM can't deserialize :AasRegistration nodes and admin calls fail.
 * Discovered via META-INF/services/de.dlr.shepard.spi.payload.PayloadKind before
 * CDI is up, same as GitPayloadKind and SpatialPayloadKind.
 */
public final class AasPayloadKind implements PayloadKind {

  @Override
  public String name() {
    return "aas";
  }

  @Override
  public List<String> entityPackages() {
    return List.of(AasRegistration.class.getPackageName());
  }
}
