package de.dlr.shepard.mongoDB;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Date;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;

public class AbstractMongoObjectTest {

  @NoArgsConstructor
  class MongoObject extends AbstractMongoObject {

    public MongoObject(String oid) {
      super(oid);
    }

    public MongoObject(String oid, Date date) {
      super(oid, date);
    }
  }

  @Test
  public void constructorTest1() {
    var expected = new MongoObject();
    expected.setOid("oid");
    var actual = new MongoObject("oid");
    assertEquals(expected, actual);
  }

  @Test
  public void constructorTest2() {
    var date = new Date();
    var expected = new MongoObject();
    expected.setOid("oid");
    expected.setCreatedAt(date);
    var actual = new MongoObject("oid", date);
    assertEquals(expected, actual);
  }

  @Test
  public void getUniqueIdTest() {
    var obj = new MongoObject("oid");
    var actual = obj.getUniqueId();

    assertEquals("oid", actual);
  }
}
