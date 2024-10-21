package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.model.ExperimentalTimeseriesEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExperimentalTimeseriesRepository implements PanacheRepositoryBase<ExperimentalTimeseriesEntity, Integer> {}
