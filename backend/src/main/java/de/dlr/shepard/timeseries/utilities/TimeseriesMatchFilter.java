package de.dlr.shepard.timeseries.utilities;

import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import java.util.Set;

public class TimeseriesMatchFilter {

  public static boolean matchFilter(
    ExperimentalTimeseries timeseries,
    Set<String> device,
    Set<String> location,
    Set<String> symName
  ) {
    var deviceMatches = true;
    var locatioMatches = true;
    var symbolicNameMatches = true;
    if (!device.isEmpty()) {
      deviceMatches = device.contains(timeseries.getDevice());
    }
    if (!location.isEmpty()) {
      locatioMatches = location.contains(timeseries.getLocation());
    }
    if (!symName.isEmpty()) {
      symbolicNameMatches = symName.contains(timeseries.getSymbolicName());
    }
    return deviceMatches && locatioMatches && symbolicNameMatches;
  }
}
