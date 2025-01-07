package de.dlr.shepard.data.timeseries.utilities;

import de.dlr.shepard.common.exceptions.InvalidBodyException;
import de.dlr.shepard.data.timeseries.model.Timeseries;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TimeseriesValidatorTest {

  @Test
  public void assertTimeseriesPropertiesAreValid_everythingIsCorrect_noException() {
    Timeseries timeseries = new Timeseries("measurement", "field", "device", "location", "symbolicName");

    Assertions.assertDoesNotThrow(() -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_everythingIsNull_throwsException() {
    Timeseries timeseries = new Timeseries(null, null, null, null, null);

    InvalidBodyException thrown = Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
    Assertions.assertTrue(thrown.getMessage().contains("not allowed to be empty"));
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsSpace_throwsException() {
    Timeseries timeseries = new Timeseries("my measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsPoint_throwsException() {
    Timeseries timeseries = new Timeseries("my.measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsComma_throwsException() {
    Timeseries timeseries = new Timeseries("my,measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_containsSlash_throwsException() {
    Timeseries timeseries = new Timeseries("my/measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }

  @Test
  public void assertTimeseriesPropertiesAreValid_emptyString_throwsException() {
    Timeseries timeseries = new Timeseries("", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.assertTimeseriesPropertiesAreValid(timeseries);
    });
  }
}
