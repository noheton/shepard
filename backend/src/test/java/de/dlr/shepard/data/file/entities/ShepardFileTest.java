package de.dlr.shepard.data.file.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

public class ShepardFileTest {

  @Test
  public void equalsContract() {
    EqualsVerifier.simple().forClass(ShepardFile.class).verify();
  }

  @Test
  public void constructorTest1() {
    var date = new Date();
    var expected = new ShepardFile();
    expected.setCreatedAt(date);
    expected.setFilename("name");
    expected.setMd5("md5");
    var actual = new ShepardFile(date, "name", "md5");
    assertEquals(expected, actual);
  }

  @Test
  public void constructorTest2() {
    var date = new Date();
    var expected = new ShepardFile();
    expected.setOid("oid");
    expected.setCreatedAt(date);
    expected.setFilename("name");
    expected.setMd5("md5");
    var actual = new ShepardFile("oid", date, "name", "md5");
    assertEquals(expected, actual);
  }

  @Test
  public void getUniqueIdTest() {
    var file = new ShepardFile("oid", new Date(), "name", "md5");
    var actual = file.getUniqueId();

    assertEquals("oid", actual);
  }
}
