package de.dlr.shepard.timeseries.utilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.timeseries.model.ExperimentalDataPointValueTypes;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

public class ObjectTypeEvaluatorTest {

  @Test
  void testEvaluate() throws Exception {
    assertEquals(ExperimentalDataPointValueTypes.Boolean, ObjectTypeEvaluator.evaluate(true));
    assertEquals(ExperimentalDataPointValueTypes.Boolean, ObjectTypeEvaluator.evaluate(Boolean.valueOf("true")));
    assertEquals(ExperimentalDataPointValueTypes.Double, ObjectTypeEvaluator.evaluate(2.5));
    assertEquals(ExperimentalDataPointValueTypes.Integer, ObjectTypeEvaluator.evaluate(5));
    assertEquals(ExperimentalDataPointValueTypes.String, ObjectTypeEvaluator.evaluate("5"));
    assertThrows(Exception.class, () -> ObjectTypeEvaluator.evaluate(new ArrayList<>()));
  }
}
