package de.dlr.shepard.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.container.ContainerRequestContext;
import java.io.InputStream;

public class RequestHelper {

  /**
   * Returns a parameter from the request json body, or null
   * @param requestContext
   * @param param
   */
  public static String getParamFromJsonBody(ContainerRequestContext requestContext, String param) {
    try {
      InputStream entityStream = requestContext.getEntityStream();
      ObjectMapper mapper = new ObjectMapper();
      JsonNode rootNode = mapper.readTree(entityStream);

      String paramValue = rootNode.get(param).asText();
      return paramValue;
    } catch (Exception e) {
      return null;
    }
  }
}
