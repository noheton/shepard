package de.dlr.shepard.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.exceptions.InvalidBodyException;
import org.junit.jupiter.api.Test;

public class QueryValidatorTest extends BaseTestCase {

  String query = "{\"property\": \"name\",\"value\": \"MyName\",\"operator\": \"eq\"}";

  @Test
  public void correctQueryTest() {
    String query = "{\"property\": \"name\",\"value\": \"MyName\",\"operator\": \"eq\"}";
    assertEquals(true, QueryValidator.checkQuery(query));
  }

  @Test
  public void incorrectQueryTest() {
    String query = "{\"property\": \"name\",\"value\": \"WheRE \",\"operator\": \"eq\"}";
    assertThrows(InvalidBodyException.class, () -> QueryValidator.checkQuery(query));
  }
}
