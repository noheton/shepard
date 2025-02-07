package de.dlr.shepard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.quarkus.logging.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;
import java.util.stream.IntStream;

public class GenerateJsonPointsService {

  static final double precision = 0.01;

  public enum Type {
    TAPE_LAYING,
    ULTRASONIC,
  }

  public static void generateJsonFile(int n, String fileName, Type type) {
    ObjectMapper mapper = new ObjectMapper();
    Random random = new Random();

    int track = 0; //increases every 1000 points
    int layer = 0; //increases every 100 tracks
    double x = 0;
    double y = 0;
    double z = 0;
    double excitationFrequency = 0; //  1-10 khz <increasing every 1_000_000> points

    try (FileWriter writer = new FileWriter(new File(fileName))) {
      writer.write("[");

      for (int i = 0; i < n; i++) {
        ObjectNode pointNode = mapper.createObjectNode();

        // Location
        pointNode.put("x", x);
        pointNode.put("y", y);
        pointNode.put("z", z);

        x += precision;

        // Metadata
        ObjectNode metadataNode = mapper.createObjectNode();
        ObjectNode measurementsNode = mapper.createObjectNode();
        switch (type) {
          case TAPE_LAYING:
            if (i % 1000 == 0) {
              y += precision;
              track++;
            }
            if (track % 100 == 0) {
              layer++;

              z += precision;
            }
            metadataNode.put("track", track);
            metadataNode.put("layer", layer);

            // Base
            ObjectNode baseNode = mapper.createObjectNode();
            baseNode.put("X", getRandomDouble(random));
            baseNode.put("Y", getRandomDouble(random));
            baseNode.put("Z", getRandomDouble(random));
            baseNode.put("A", getRandomDouble(random));
            baseNode.put("B", getRandomDouble(random));
            baseNode.put("C", getRandomDouble(random));
            metadataNode.set("base", baseNode);

            // Measurements
            ArrayNode tapeLayingDataArray = mapper.createArrayNode();
            IntStream.range(1, 500).forEach(s -> {
              tapeLayingDataArray.add(random.nextInt(100) - 50);
            });

            measurementsNode.set("data", tapeLayingDataArray);
            break;
          case ULTRASONIC:
            metadataNode.put("excitation_frequency", excitationFrequency);
            if (i % 1000_000 == 0) excitationFrequency++;
            ArrayNode ultrasonicDataArray = mapper.createArrayNode();
            IntStream.range(1, 500).forEach(s -> {
              ultrasonicDataArray.add(random.nextInt(100) - 50);
            });
            measurementsNode.put("dampening", random.nextDouble());
            measurementsNode.set("data", ultrasonicDataArray);

            break;
        }

        pointNode.set("metadata", metadataNode);
        pointNode.set("measurements", measurementsNode);

        String jsonPoint = "";

        jsonPoint = mapper.writeValueAsString(pointNode);

        writer.write(jsonPoint);
        if (i < n - 1) {
          writer.write(",");
        }
      }

      writer.write("]"); // Close JSON array
      Log.info("file generated: " + fileName);
    } catch (IOException e) {
      Log.error("Error while exporting file");
      Log.error(e);
      e.printStackTrace();
    }
  }

  private static double getRandomDouble(Random random) {
    return -1000 + (2000 * random.nextDouble());
  }
}
