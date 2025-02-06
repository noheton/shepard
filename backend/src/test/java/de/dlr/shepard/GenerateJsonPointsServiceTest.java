package de.dlr.shepard;

import org.junit.jupiter.api.Test;

public class GenerateJsonPointsServiceTest {

  @Test
  public void generateJsonFileTest() {
    GenerateJsonPointsService.generateJsonFile(10, "sample_points.json");
  }
}
