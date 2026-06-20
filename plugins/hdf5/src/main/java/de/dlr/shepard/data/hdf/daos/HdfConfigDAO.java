package de.dlr.shepard.data.hdf.daos;

import de.dlr.shepard.common.neo4j.daos.GenericDAO;
import de.dlr.shepard.common.neo4j.daos.NeoConnector;
import de.dlr.shepard.data.hdf.entities.HdfConfig;
import jakarta.enterprise.context.RequestScoped;
import java.util.Collection;

/**
 * FTOGGLE-HDF-ENABLE-1 — DAO for the {@link HdfConfig} singleton.
 * Mirrors {@code SqlTimeseriesConfigDAO}: no custom queries beyond
 * {@link #findSingleton()} (which loads any single node of this type).
 */
@RequestScoped
public class HdfConfigDAO extends GenericDAO<HdfConfig> {

  public HdfConfig findSingleton() {
    Collection<HdfConfig> all = NeoConnector.getInstance()
        .getNeo4jSession()
        .loadAll(HdfConfig.class, 0);
    if (all.isEmpty()) return null;
    return all.iterator().next();
  }

  @Override
  public Class<HdfConfig> getEntityType() {
    return HdfConfig.class;
  }
}
