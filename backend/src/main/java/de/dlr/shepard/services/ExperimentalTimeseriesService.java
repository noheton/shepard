package de.dlr.shepard.services;

import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

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
