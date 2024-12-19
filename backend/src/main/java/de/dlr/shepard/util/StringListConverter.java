package de.dlr.shepard.util;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

@Converter
public class StringListConverter implements AttributeConverter<List<String>, String> {

  public static final String SPLIT_CHAR = "|";

  @Override
  public String convertToDatabaseColumn(List<String> stringList) {
    return stringList != null ? String.join(SPLIT_CHAR, stringList) : "";
  }

  @Override
  public List<String> convertToEntityAttribute(String string) {
    return (string == null || string.isBlank())
      ? new ArrayList<String>()
      : Arrays.asList(string.split(Pattern.quote(SPLIT_CHAR)));
  }
}
