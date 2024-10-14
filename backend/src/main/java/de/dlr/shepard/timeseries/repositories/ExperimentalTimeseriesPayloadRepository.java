package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseriesPayload;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExperimentalTimeseriesPayloadRepository implements PanacheRepository<ExperimentalTimeseriesPayload> {}
