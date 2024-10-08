package de.dlr.shepard.services;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExperimentalTimeseriesPayloadRepository implements PanacheRepository<ExperimentalTimeseriesPayload> {

}
