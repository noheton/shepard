package de.dlr.shepard.timeseries.utilities;

import de.dlr.shepard.timeseries.model.ExperimentalDataPointValueTypes;

public class ObjectTypeEvaluator {

  /**
   * Tries to evaluate if the specific type of the object is a String, Double, Integer or Boolean.
   * @param value the object whos type should be determined
   * @throws Exception if the object cannot be parsed to the above types
   * @return ExperimentalDataPointValueTypes representing the evaluated type
   */
  public static ExperimentalDataPointValueTypes evaluate(Object value) throws Exception {
    if (value instanceof Boolean) return ExperimentalDataPointValueTypes.Boolean;
    if (value instanceof Double) return ExperimentalDataPointValueTypes.Double;
    if (value instanceof String) return ExperimentalDataPointValueTypes.String;
    if (value instanceof Integer) return ExperimentalDataPointValueTypes.Integer;
    throw new Exception();
  }
}
