package de.dlr.shepard.data.spatialdata.endpoints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.dlr.shepard.data.spatialdata.model.geometryFilter.AbstractGeometryFilter;
import jakarta.ws.rs.BadRequestException;
import java.util.Map;
import java.util.Optional;

public final class SpatialDataParamParser {

  private static final ObjectMapper objectMapper = new ObjectMapper();

  public static AbstractGeometryFilter parseGeometryFilter(Optional<String> paramString) {
    if (paramString.isEmpty()) throw new BadRequestException("Invalid geometry filter param");
    try {
      return objectMapper.readValue(paramString.get(), AbstractGeometryFilter.class);
    } catch (JsonProcessingException e) {
      throw new BadRequestException("Invalid geometry filter param");
    }
  }

  public static Optional<Map<String, Object>> parseMetadata(Optional<String> metadataParam) {
    if (metadataParam.isEmpty()) return Optional.empty();
    try {
      return Optional.of(objectMapper.readValue(metadataParam.get(), new TypeReference<Map<String, Object>>() {}));
    } catch (JsonProcessingException e) {
      throw new BadRequestException("Invalid metadata param", e);
    }
  }
}
