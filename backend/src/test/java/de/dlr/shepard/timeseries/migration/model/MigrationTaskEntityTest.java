package de.dlr.shepard.timeseries.migration.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import de.dlr.shepard.util.StringListConverter;
import org.junit.jupiter.api.Test;

public class MigrationTaskEntityTest {

  @Test
  public void test() {
    MigrationTaskEntity entity = new MigrationTaskEntity();
    entity.addError("error" + StringListConverter.SPLIT_CHAR + "message");
    var expected = "error" + MigrationTaskEntity.SPLIT_CHAR_REPLACEMENT + "message";

    var actual = entity.getErrors().get(0);

    assertEquals(expected, actual);
  }
}
