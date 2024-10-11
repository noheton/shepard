package de.dlr.shepard.services;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class ExperimentalTimeseriesService {

  @Inject
  ExperimentalTimeseriesRepository timeseriesRepository;

  @Inject
  ExperimentalTimeseriesPayloadRepository timeseriesPayloadRepository;

  public List<ExperimentalTimeseries> getAll() {
    return timeseriesRepository.listAll();
  }

  public List<ExperimentalTimeseries> getAllByContainerId(long containerId) {
    return timeseriesRepository.list("containerId", containerId);
  }

  public ExperimentalTimeseries getById(Integer id) {
    return timeseriesRepository.findById(id);
  }

  @Transactional
  public void add(ExperimentalTimeseries entity) {
    timeseriesRepository.persist(entity);
  }

  @Transactional
  public void update(ExperimentalTimeseries entity) {
    // var entityToUpdate = timeseriesRepository.findById(entity.getId());
    // entityToUpdate.setDevice(entity.getDevice());
    // entityToUpdate.setField(entity.getField());
    // entityToUpdate.setLocation(entity.getLocation());
    // entityToUpdate.setMeasurement(entity.getMeasurement());
    // entityToUpdate.setSymbolicName(entity.getSymbolicName());
    // timeseriesRepository.persist(entityToUpdate);
  }

  public void deleteById(Integer id) {
    timeseriesRepository.deleteById(id);
  }
}
