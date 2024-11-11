package de.dlr.shepard.timeseries.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.timeseries.model.enums.ExperimentalDataPointValueType;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

public class ObjectTypeEvaluatorTest {

  @Test
  void testDetermineType() throws Exception {
    assertEquals(ExperimentalDataPointValueType.Boolean, ObjectTypeEvaluator.determineType(true).get());
    assertEquals(
      ExperimentalDataPointValueType.Boolean,
      ObjectTypeEvaluator.determineType(Boolean.valueOf("true")).get()
    );
    assertEquals(ExperimentalDataPointValueType.Double, ObjectTypeEvaluator.determineType(2.5).get());
    assertThrows(NoSuchElementException.class, () -> ObjectTypeEvaluator.determineType(2.5F).get());
    assertEquals(
      ExperimentalDataPointValueType.Double,
      ObjectTypeEvaluator.determineType(Double.parseDouble("2.5")).get()
    );
    assertThrows(NoSuchElementException.class, () -> ObjectTypeEvaluator.determineType(Float.parseFloat("2.5")).get());
    assertEquals(ExperimentalDataPointValueType.Double, ObjectTypeEvaluator.determineType(2.5).get());
    assertEquals(ExperimentalDataPointValueType.Integer, ObjectTypeEvaluator.determineType(5).get());
    assertEquals(ExperimentalDataPointValueType.String, ObjectTypeEvaluator.determineType("5").get());
    assertThrows(NoSuchElementException.class, () -> ObjectTypeEvaluator.determineType(new ArrayList<>()).get());
  }
}
