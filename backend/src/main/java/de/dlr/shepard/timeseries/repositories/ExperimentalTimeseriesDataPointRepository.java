package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesDataPointEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExperimentalTimeseriesDataPointRepository
  implements PanacheRepositoryBase<ExperimentalTimeseriesDataPointEntity, Long> {}
