package de.dlr.shepard.neo4Core.io.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.Map;

public class NoDelimiterInMapKeysValidator implements ConstraintValidator<NoDelimiterInMapKeys, Map<String, String>> {

  private static final String SPECIAL_CHARACTERS = "||";

  @Override
  public boolean isValid(Map<String, String> map, ConstraintValidatorContext context) {
    for (String key : map.keySet()) {
      if (key.contains(SPECIAL_CHARACTERS)) {
        return false;
      }
    }
    return true;
  }
}
