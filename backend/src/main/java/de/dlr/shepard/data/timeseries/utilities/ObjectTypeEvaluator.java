package de.dlr.shepard.data.timeseries.utilities;

import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import java.util.Optional;

public class ObjectTypeEvaluator {

  /**
   * Tries to evaluate if the specific type of the object is a String, Double, Integer or Boolean.
   * @param value the object whos type should be determined
   * @return an Optional representing the evaluated type
   */
  public static Optional<DataPointValueType> determineType(Object value) {
    if (value instanceof Boolean) return Optional.of(DataPointValueType.Boolean);
    if (value instanceof Double) return Optional.of(DataPointValueType.Double);
    if (value instanceof String) return Optional.of(DataPointValueType.String);
    if (value instanceof Integer) return Optional.of(DataPointValueType.Integer);
    return Optional.empty();
  }
}
