package de.dlr.shepard.data.timeseries.utilities;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.data.timeseries.model.TimeseriesFiveTuple;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TimeseriesValidatorTest {

  @Test
  public void assertTimeseriesPropertiesAreValid_everythingIsCorrect_noException() {
    TimeseriesFiveTuple timeseries = new TimeseriesFiveTuple(
      "measurement",
      "field",
      "device",
      "location",
      "symbolicName"
    );

    Assertions.assertDoesNotThrow(() -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_everythingIsNull_throwsException() {
    TimeseriesFiveTuple timeseries = new TimeseriesFiveTuple(null, null, null, null, null);

    InvalidBodyException thrown = Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
    Assertions.assertTrue(thrown.getMessage().contains("not allowed to be empty"));
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsSpace_throwsException() {
    TimeseriesFiveTuple timeseries = new TimeseriesFiveTuple("my measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsPoint_throwsException() {
    TimeseriesFiveTuple timeseries = new TimeseriesFiveTuple("my.measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsComma_throwsException() {
    TimeseriesFiveTuple timeseries = new TimeseriesFiveTuple("my,measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsSlash_throwsException() {
    TimeseriesFiveTuple timeseries = new TimeseriesFiveTuple("my/measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_emptyString_throwsException() {
    TimeseriesFiveTuple timeseries = new TimeseriesFiveTuple("", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }
}
