package de.dlr.shepard.timeseries.utilities;

import de.dlr.shepard.exceptions.InvalidBodyException;
import de.dlr.shepard.timeseries.model.ExperimentalTimeseries;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TimeseriesValidatorTest {

  @Test
  public void validate_everythingIsCorrect_noException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries(
      "measurement",
      "field",
      "device",
      "location",
      "symbolicName"
    );

    Assertions.assertDoesNotThrow(() -> {
      TimeseriesValidator.validate(timeseries);
    });
  }

  @Test
  public void validate_everythingIsNull_throwsException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries(null, null, null, null, null);

    InvalidBodyException thrown = Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.validate(timeseries);
    });
    Assertions.assertTrue(thrown.getMessage().contains("not allowed to be empty"));
  }

  @Test
  public void validate_containsSpace_throwsException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries("my measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.validate(timeseries);
    });
  }

  @Test
  public void validate_containsPoint_throwsException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries("my.measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.validate(timeseries);
    });
  }

  @Test
  public void validate_containsComma_throwsException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries("my,measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.validate(timeseries);
    });
  }

  @Test
  public void validate_containsSlash_throwsException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries("my/measurement", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.validate(timeseries);
    });
  }

  @Test
  public void validate_emptyString_throwsException() {
    ExperimentalTimeseries timeseries = new ExperimentalTimeseries("", "a", "b", "c", "d");

    Assertions.assertThrowsExactly(InvalidBodyException.class, () -> {
      TimeseriesValidator.validate(timeseries);
    });
  }
}
