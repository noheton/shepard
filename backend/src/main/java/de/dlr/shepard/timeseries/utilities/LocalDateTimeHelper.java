package de.dlr.shepard.timeseries.utilities;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import lombok.val;

// TODO: let go of this class
public class LocalDateTimeHelper {

  public static LocalDateTime fromMilliseconds(long milliseconds) {
    return Instant.ofEpochMilli(milliseconds).atZone(ZoneId.systemDefault()).toLocalDateTime();
  }

  public static long fromLocalDateTime(LocalDateTime dateTime) {
    val milliSeconds = dateTime.atZone(ZoneOffset.UTC).toInstant().toEpochMilli(); // Todo: we need nanoseconds here
    return milliSeconds;
  }
}
