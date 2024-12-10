package de.dlr.shepard.timeseries.io;

import de.dlr.shepard.timeseries.model.TimeseriesContainer;
import java.util.ArrayList;
import java.util.List;

public class TimeseriesContainerIOMapper {

  public static TimeseriesContainerIO map(TimeseriesContainer entity) {
    return new TimeseriesContainerIO(entity);
  }

  public static List<TimeseriesContainerIO> map(List<TimeseriesContainer> entities) {
    ArrayList<TimeseriesContainerIO> result = new ArrayList<>(entities.size());
    for (TimeseriesContainer timeseriesContainer : entities) {
      result.add(new TimeseriesContainerIO(timeseriesContainer));
    }
    return result;
  }
}
