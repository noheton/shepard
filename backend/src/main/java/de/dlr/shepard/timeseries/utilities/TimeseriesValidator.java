package de.dlr.shepard.timeseries.utilities;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.timeseries.model.Timeseries;

public class TimeseriesValidator {

  private static String forbiddenCharsRegEx = ".*[ .,/].*";
  private static String errorStringFormat =
    "%s is not allowed to be empty or contain one of those characters: 'Space, Comma, Point, Slash'";

  public static void assertTimeseriesPropertiesAreValid(Timeseries timeseries) {
    validateString(timeseries.getDevice(), "device");
    validateString(timeseries.getField(), "field");
    validateString(timeseries.getLocation(), "location");
    validateString(timeseries.getMeasurement(), "measurement");
    validateString(timeseries.getSymbolicName(), "symbolicName");
  }

  private static void validateString(String input, String fieldName) {
    if (input == null || input.isEmpty() || input.matches(forbiddenCharsRegEx)) throw new InvalidBodyException(
      errorStringFormat,
      fieldName
    );
  }
}
