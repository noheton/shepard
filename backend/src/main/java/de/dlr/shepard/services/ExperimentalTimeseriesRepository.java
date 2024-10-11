package de.dlr.shepard.services;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExperimentalTimeseriesRepository implements PanacheRepositoryBase<ExperimentalTimeseries, Integer> {}
