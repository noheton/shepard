package de.dlr.shepard.data.timeseries.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import java.util.ArrayList;
import java.util.NoSuchElementException;
import org.junit.jupiter.api.Test;

public class ObjectTypeEvaluatorTest {

  @Test
  void testDetermineType() throws Exception {
    assertEquals(DataPointValueType.Boolean, ObjectTypeEvaluator.determineType(true).get());
    assertEquals(DataPointValueType.Boolean, ObjectTypeEvaluator.determineType(Boolean.valueOf("true")).get());
    assertEquals(DataPointValueType.Double, ObjectTypeEvaluator.determineType(2.5).get());
    assertThrows(NoSuchElementException.class, () -> ObjectTypeEvaluator.determineType(2.5F).get());
    assertEquals(DataPointValueType.Double, ObjectTypeEvaluator.determineType(Double.parseDouble("2.5")).get());
    assertThrows(NoSuchElementException.class, () -> ObjectTypeEvaluator.determineType(Float.parseFloat("2.5")).get());
    assertEquals(DataPointValueType.Double, ObjectTypeEvaluator.determineType(2.5).get());
    assertEquals(DataPointValueType.Integer, ObjectTypeEvaluator.determineType(5).get());
    assertEquals(DataPointValueType.String, ObjectTypeEvaluator.determineType("5").get());
    assertThrows(NoSuchElementException.class, () -> ObjectTypeEvaluator.determineType(new ArrayList<>()).get());
  }
}
