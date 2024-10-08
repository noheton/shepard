package de.dlr.shepard.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ExperimentalTimeseriesService {

  @Inject
  ExperimentalTimeseriesRepository timeseriesRepository;

  @Inject
  ExperimentalTimeseriesPayloadRepository timeseriesPayloadRepository;

  public List<ExperimentalTimeseries> getAll() {
    return null;
  }
}
