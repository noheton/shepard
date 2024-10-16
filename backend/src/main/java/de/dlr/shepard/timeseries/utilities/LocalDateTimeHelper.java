package de.dlr.shepard.timeseries.utilities;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class LocalDateTimeHelper {

  public static LocalDateTime fromMilliseconds(long milliseconds) {
    return Instant.ofEpochMilli(milliseconds).atZone(ZoneId.systemDefault()).toLocalDateTime();
  }
}
