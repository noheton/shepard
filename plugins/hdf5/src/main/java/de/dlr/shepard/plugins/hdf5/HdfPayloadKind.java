package de.dlr.shepard.plugins.hdf5;

import de.dlr.shepard.data.hdf.entities.HdfContainer;
import de.dlr.shepard.spi.payload.PayloadKind;
import java.util.List;

/**
 * PL1c — ServiceLoader-discovered payload kind that tells
 * {@code NeoConnector} which Neo4j-OGM entity packages the
 * HDF5/HSDS plugin contributes.
 *
 * <p>This is a plain POJO — NOT a CDI bean. Discovery happens via
 * {@code ServiceLoader.load(PayloadKind.class)} inside
 * {@code NeoConnector.connect()}, which fires before CDI is up.
 *
 * <p>This also fixes a latent OGM-gap bug: {@code HdfContainer}'s
 * package ({@code de.dlr.shepard.data.hdf.entities}) was previously
 * absent from the {@code NeoConnector} base-package list, so
 * Neo4j-OGM would fail to hydrate it on first load of an existing
 * install. Registering it here (via ServiceLoader) is the correct
 * fix: the plugin adds only what it owns, and removal of the plugin
 * cleanly removes the registration.
 */
public final class HdfPayloadKind implements PayloadKind {

  @Override
  public String name() {
    return "hdf5";
  }

  @Override
  public List<String> entityPackages() {
    return List.of(HdfContainer.class.getPackageName());
  }
}
