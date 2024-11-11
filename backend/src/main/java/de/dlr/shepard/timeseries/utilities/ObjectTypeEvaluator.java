package de.dlr.shepard.timeseries.utilities;

import de.dlr.shepard.timeseries.model.enums.ExperimentalDataPointValueType;
import java.util.Optional;

public class ObjectTypeEvaluator {

  /**
   * Tries to evaluate if the specific type of the object is a String, Double, Integer or Boolean.
   * @param value the object whos type should be determined
   * @return an Optional representing the evaluated type
   */
  public static Optional<ExperimentalDataPointValueType> determineType(Object value) {
    if (value instanceof Boolean) return Optional.of(ExperimentalDataPointValueType.Boolean);
    if (value instanceof Double) return Optional.of(ExperimentalDataPointValueType.Double);
    if (value instanceof String) return Optional.of(ExperimentalDataPointValueType.String);
    if (value instanceof Integer) return Optional.of(ExperimentalDataPointValueType.Integer);
    return Optional.empty();
  }
}
