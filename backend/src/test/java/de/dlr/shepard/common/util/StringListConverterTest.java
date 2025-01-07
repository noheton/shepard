package de.dlr.shepard.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

public class StringListConverterTest {

  private final String specialCharacters = "!\"§$%&/()=?``'_:;-*\\{}[]^<>";
  private final String SPLIT_CHAR = StringListConverter.SPLIT_CHAR;

  @Test
  public void convertToDatabaseColumn_concatListOfStringsToOneString_success() {
    StringListConverter converter = new StringListConverter();
    var data = new String[] { "Hello", "world", "!" };
    var expected = "Hello" + SPLIT_CHAR + "world" + SPLIT_CHAR + "!";

    var actual = converter.convertToDatabaseColumn(Arrays.asList(data));

    assertEquals(expected, actual);
  }

  @Test
  public void convertToDatabaseColumn_containsEmptyString_willNotBeRemoved() {
    StringListConverter converter = new StringListConverter();
    var data = new String[] { "Hello", "", "world" };
    var expected = "Hello" + SPLIT_CHAR + SPLIT_CHAR + "world";

    var actual = converter.convertToDatabaseColumn(Arrays.asList(data));

    assertEquals(expected, actual);
  }

  @Test
  public void convertToDatabaseColumn_containsSpecialCharacters_noConflicts() {
    StringListConverter converter = new StringListConverter();
    var data = new String[] { specialCharacters, "\r\n", "<html>" };
    var expected = specialCharacters + SPLIT_CHAR + "\r\n" + SPLIT_CHAR + "<html>";

    var actual = converter.convertToDatabaseColumn(Arrays.asList(data));

    assertEquals(expected, actual);
  }

  @Test
  public void convertToEntityAttribute_splitStringToArray_success() {
    StringListConverter converter = new StringListConverter();
    var data = "Hello|world|!";
    var expected = Arrays.asList(new String[] { "Hello", "world", "!" });

    var actual = converter.convertToEntityAttribute(data);

    assertEquals(expected, actual);
  }

  @Test
  public void convertToEntityAttribute_containsEmptyString_indexIsCreated() {
    StringListConverter converter = new StringListConverter();
    var data = "Hello" + SPLIT_CHAR + SPLIT_CHAR + "world";
    var expected = Arrays.asList(new String[] { "Hello", "", "world" });

    var actual = converter.convertToEntityAttribute(data);

    assertEquals(expected, actual);
  }

  @Test
  public void convertToEntityAttribute_containsSpecialCharacters_noConflicts() {
    StringListConverter converter = new StringListConverter();
    var data = specialCharacters + SPLIT_CHAR + "\r\n" + SPLIT_CHAR + "<html>";
    var expected = Arrays.asList(new String[] { specialCharacters, "\r\n", "<html>" });

    var actual = converter.convertToEntityAttribute(data);

    assertEquals(expected, actual);
  }
}
