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
    // Fix D: NaN / Infinity are not representable — treat as unclassifiable
    // so they are skipped rather than stored as a non-finite double or
    // misclassified as String when they arrive as the text "NaN".
    if (value instanceof Double d) {
      if (!Double.isFinite(d)) return Optional.empty();
      return Optional.of(DataPointValueType.Double);
    }
    if (value instanceof String s) {
      String t = s.trim().toLowerCase();
      if (t.equals("nan") || t.equals("infinity") || t.equals("-infinity") || t.equals("+infinity")) {
        return Optional.empty();
      }
      return Optional.of(DataPointValueType.String);
    }
    if (value instanceof Long || value instanceof Integer) return Optional.of(DataPointValueType.Integer);
    return Optional.empty();
  }
}
