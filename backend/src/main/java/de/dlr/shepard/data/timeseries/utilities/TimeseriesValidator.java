package de.dlr.shepard.data.timeseries.utilities;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.data.timeseries.model.TimeseriesFiveTuple;

public class TimeseriesValidator {

  private static String forbiddenCharsRegEx = ".*[ .,/].*";
  private static String errorStringFormat =
    "%s is not allowed to be empty or contain one of those characters: 'Space, Comma, Point, Slash'";

  public static void assertTimeseriesPropertiesAreValid(TimeseriesFiveTuple timeseries) {
    validateString(timeseries.device(), "device");
    validateString(timeseries.field(), "field");
    validateString(timeseries.location(), "location");
    validateString(timeseries.measurement(), "measurement");
    validateString(timeseries.symbolicName(), "symbolicName");
  }

  private static void validateString(String input, String fieldName) {
    if (input == null || input.isEmpty() || input.matches(forbiddenCharsRegEx)) throw new InvalidBodyException(
      errorStringFormat,
      fieldName
    );
  }
}
