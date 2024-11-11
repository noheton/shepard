package de.dlr.shepard.timeseries.utilities;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TimeseriesValidatorTest {

  @Test
  public void assertTimeseriesPropertiesAreValid_everythingIsCorrect_noException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries(
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
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries(null, null, null, null, null);

    InvalidBodyException thrown = Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
    Assertions.assertTrue(thrown.getMessage().contains("not allowed to be empty"));
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsSpace_throwsException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries("my measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsPoint_throwsException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries("my.measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsComma_throwsException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries("my,measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsSlash_throwsException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries("my/measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_emptyString_throwsException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries("", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }
}
