package de.dlr.shepard.plugins.spatiotemporal;

import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import de.dlr.shepard.spi.payload.PayloadKind;
import java.util.List;

/**
 * SPATIAL-V6-001 — ServiceLoader-discovered payload kind that tells
 * {@code NeoConnector} which Neo4j-OGM entity packages the
 * spatiotemporal plugin contributes.
 *
 * <p>This is a plain POJO — NOT a CDI bean. Discovery happens via
 * {@code ServiceLoader.load(PayloadKind.class)} inside
 * {@code NeoConnector.connect()}, which fires before CDI is up.
 * Making this a CDI bean would cause a second instance to exist
 * alongside the ServiceLoader instance; keeping it a POJO avoids
 * that confusion entirely.
 *
 * <p>The entity packages still live under
 * {@code de.dlr.shepard.data.spatialdata} and
 * {@code de.dlr.shepard.context.references.spatialdata} — the Java
 * package structure is unchanged to avoid churn across all existing
 * JPA / Neo4j-OGM annotations.
 */
public final class SpatiotemporalPayloadKind implements PayloadKind {

  @Override
  public String name() {
    return "spatiotemporal";
  }

  @Override
  public List<String> entityPackages() {
    return List.of(
      SpatialDataContainer.class.getPackageName(),
      SpatialDataReference.class.getPackageName()
    );
  }
}
