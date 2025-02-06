package de.dlr.shepard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.logging.Log;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.stream.IntStream;

public class GenerateJsonPointsService {

  public static void generateJsonFile(int n, String fileName) {
    ObjectMapper mapper = new ObjectMapper();
    ArrayNode pointsArray = mapper.createArrayNode();
    Random random = new Random();

    for (int i = 0; i < n; i++) {
      ObjectNode pointNode = mapper.createObjectNode();

      // Location
      ObjectNode locationNode = mapper.createObjectNode();
      pointNode.put("x", getRandomDouble(random));
      pointNode.put("y", getRandomDouble(random));
      pointNode.put("z", getRandomDouble(random));
      //pointNode.set("location", locationNode);

      // Metadata
      ObjectNode metadataNode = mapper.createObjectNode();
      metadataNode.put("track", random.nextInt(200));
      metadataNode.put("layer", random.nextInt(10));

      // Base
      ObjectNode baseNode = mapper.createObjectNode();
      baseNode.put("X", getRandomDouble(random));
      baseNode.put("Y", getRandomDouble(random));
      baseNode.put("Z", getRandomDouble(random));
      baseNode.put("A", getRandomDouble(random));
      baseNode.put("B", getRandomDouble(random));
      baseNode.put("C", getRandomDouble(random));
      metadataNode.set("base", baseNode);

      pointNode.set("metadata", metadataNode);

      // Measurements
      ObjectNode measurementsNode = mapper.createObjectNode();
      ArrayNode dataArray = mapper.createArrayNode();
      IntStream.range(1, 500).forEach(s -> {
        dataArray.add(random.nextInt(s));
      });

      measurementsNode.set("data", dataArray);
      pointNode.set("measurements", measurementsNode);

      pointsArray.add(pointNode);
    }

    try {
      mapper.writerWithDefaultPrettyPrinter().writeValue(new File(fileName), pointsArray);
      Log.info("JSON file generated: " + fileName);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private static double getRandomDouble(Random random) {
    return -1000 + (2000 * random.nextDouble());
  }
}
