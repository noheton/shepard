package de.dlr.shepard.timeseries.utilities;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAmount;

/*
 * Helper class that provides some convenience methods for handling
 * Instants and converting them to a long in nanoseconds.
 * All times are handled with ZoneOffset UTC and do not store any timezone information.
 */
public class InstantHelper {

  private Instant instant = Instant.now();

  public static InstantHelper fromGermanDate(String dateAsString) {
    return new InstantHelper(
      LocalDate.parse(dateAsString, DateTimeFormatter.ofPattern("dd.MM.yyyy"))
        .atStartOfDay(ZoneId.of("UTC"))
        .toInstant()
    );
  }

  public static InstantHelper now() {
    return new InstantHelper(Instant.now());
  }

  public InstantHelper(Instant instant) {
    this.instant = instant;
  }

  /*
   * TemporalAmount is the base interface type for amounts of time.
   * Use Period for dates and Duration for times.
   */
  public InstantHelper addDuration(TemporalAmount duration) {
    instant = this.instant.plus(duration);
    return this;
  }

  public InstantHelper addSeconds(long seconds) {
    return addDuration(Duration.ofSeconds(seconds));
  }

  public InstantHelper addMinutes(long minutes) {
    return addDuration(Duration.ofMinutes(minutes));
  }

  public InstantHelper addHours(long hours) {
    return addDuration(Duration.ofHours(hours));
  }

  public InstantHelper addDays(int days) {
    return addDuration(Period.ofDays(days));
  }

  public InstantHelper addMonths(int months) {
    return addDuration(Period.ofMonths(months));
  }

  public InstantHelper addYears(int years) {
    return addDuration(Period.ofYears(years));
  }

  public long toNano() {
    return this.instant.toEpochMilli() * 1_000_000;
  }
}
