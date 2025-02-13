package de.dlr.shepard.common.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.logging.Log;

public final class JsonConverter {

  public static String convertToString(Object object) {
    try {
      if (object == null) return null;
      return new ObjectMapper().writeValueAsString(object);
    } catch (Exception e) {
      Log.errorf("Error while converting metadata to JSON string. %s", e);
      throw new RuntimeException(e);
    }
  }

  public static Object convertToObject(String jsonString) {
    try {
      if (jsonString == null) return null;
      return new ObjectMapper().readValue(jsonString, Object.class);
    } catch (Exception e) {
      Log.errorf("Error while converting JSON string to metadata object. %s", e);
      throw new RuntimeException(e);
    }
  }
}
