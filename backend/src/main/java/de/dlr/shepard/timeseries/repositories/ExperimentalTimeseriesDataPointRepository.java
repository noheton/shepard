package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseriesDataPoint;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExperimentalTimeseriesDataPointRepository
  implements PanacheRepositoryBase<ExperimentalTimeseriesDataPoint, Long> {}
