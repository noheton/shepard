package de.dlr.shepard.timeseries.services;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseries;
import de.dlr.shepard.timeseries.io.TimeseriesPayloadIO;
import de.dlr.shepard.timeseries.repositories.ExperimentalTimeseriesPayloadDataPointRepository;
import de.dlr.shepard.timeseries.repositories.ExperimentalTimeseriesRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class ExperimentalTimeseriesService {

  @Inject
  ExperimentalTimeseriesRepository timeseriesRepository;

  @Inject
  ExperimentalTimeseriesPayloadDataPointRepository timeseriesPayloadRepository;

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

  @Transactional
  public void deleteByContainerId(long timeSeriesContainerId) {
    timeseriesRepository.delete("containerId", timeSeriesContainerId);
  }

  public ExperimentalTimeseries createTimeseriesPayload(long timeseriesContainerId, TimeseriesPayloadIO payload) {
    // String sanityCheck = InfluxUtil.sanitize(payload.getTimeseries());
    // if (!sanityCheck.isBlank()) throw new InvalidBodyException(sanityCheck);
    // var timeseries = payload.getTimeseries();
    // // get type of payload points
    // var firstPointValue = payload.getPoints().get(0).getValue();

    // parse points to correct model ExperimentalTimeseriesPayload
    // Persist points in database
    return payload.getTimeseries();
  }
}
