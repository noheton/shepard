package de.dlr.shepard.plugins.spatial;

import de.dlr.shepard.context.references.spatialdata.entities.SpatialDataReference;
import de.dlr.shepard.data.spatialdata.model.SpatialDataContainer;
import de.dlr.shepard.spi.payload.PayloadKind;
import java.util.List;

/**
 * SPI1a — ServiceLoader-discovered payload kind that tells
 * {@code NeoConnector} which Neo4j-OGM entity packages the
 * spatial plugin contributes.
 *
 * <p>This is a plain POJO — NOT a CDI bean. Discovery happens via
 * {@code ServiceLoader.load(PayloadKind.class)} inside
 * {@code NeoConnector.connect()}, which fires before CDI is up.
 * Making this a CDI bean would cause a second instance to exist
 * alongside the ServiceLoader instance; keeping it a POJO avoids
 * that confusion entirely.
 */
public final class SpatialPayloadKind implements PayloadKind {

  @Override
  public String name() {
    return "spatial";
  }

  @Override
  public List<String> entityPackages() {
    return List.of(
      SpatialDataContainer.class.getPackageName(),
      SpatialDataReference.class.getPackageName()
    );
  }
}
