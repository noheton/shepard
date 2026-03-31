package de.dlr.shepard.data.timeseries.utilities;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.data.timeseries.model.TimeseriesTuple;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TimeseriesValidatorTest {

  @Test
  public void assertTimeseriesPropertiesAreValid_everythingIsCorrect_noException() {
    TimeseriesTuple timeseries = new TimeseriesTuple(
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
    TimeseriesTuple timeseries = new TimeseriesTuple(null, null, null, null, null);

    InvalidBodyException thrown = Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
    Assertions.assertTrue(thrown.getMessage().contains("not allowed to be empty"));
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsSpace_throwsException() {
    TimeseriesTuple timeseries = new TimeseriesTuple("my measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsPoint_throwsException() {
    TimeseriesTuple timeseries = new TimeseriesTuple("my.measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsComma_throwsException() {
    TimeseriesTuple timeseries = new TimeseriesTuple("my,measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsSlash_throwsException() {
    TimeseriesTuple timeseries = new TimeseriesTuple("my/measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_emptyString_throwsException() {
    TimeseriesTuple timeseries = new TimeseriesTuple("", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }
}
