package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseries;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExperimentalTimeseriesRepository implements PanacheRepositoryBase<ExperimentalTimeseries, Integer> {}
