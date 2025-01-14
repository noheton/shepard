package de.dlr.shepard.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

public class HasIdTest {

  HasId a = new HasId() {
    @Override
    public String getUniqueId() {
      return "a";
    }
  };
  HasId aEquals = new HasId() {
    @Override
    public String getUniqueId() {
      return "a";
    }
  };
  HasId aDiffers = new HasId() {
    @Override
    public String getUniqueId() {
      return "b";
    }
  };

  @Test
  public void equalsHelperTest_bothNull() {
    assertTrue(HasId.equalsHelper((HasId) null, (HasId) null));
  }

  @Test
  public void equalsHelperTest_oneNull() {
    assertFalse(HasId.equalsHelper((HasId) null, aDiffers));
    assertFalse(HasId.equalsHelper(a, null));
  }

  @Test
  public void equalsHelperTest_equal() {
    assertTrue(HasId.equalsHelper(a, a));
    assertTrue(HasId.equalsHelper(a, aEquals));
    assertFalse(HasId.equalsHelper(a, aDiffers));
  }

  @Test
  public void equalsHelpersTest_bothNull() {
    assertTrue(HasId.areEqualSetsByUniqueId((List<HasId>) null, (List<HasId>) null));
  }

  @Test
  public void equalsHelpersTest_oneNull() {
    assertFalse(HasId.areEqualSetsByUniqueId(null, List.of(aDiffers)));
    assertFalse(HasId.areEqualSetsByUniqueId(List.of(a), null));
  }

  @Test
  public void equalsHelpersTest_equal() {
    List<HasId> nullList = new ArrayList<>();
    nullList.add(null);
    assertTrue(HasId.areEqualSetsByUniqueId(List.of(a), List.of(aEquals)));
    assertFalse(HasId.areEqualSetsByUniqueId(List.of(a), List.of(aDiffers)));
    assertFalse(HasId.areEqualSetsByUniqueId(nullList, List.of(aDiffers)));
    assertFalse(HasId.areEqualSetsByUniqueId(List.of(a, aDiffers), List.of(aDiffers)));
    assertTrue(HasId.areEqualSetsByUniqueId(List.of(a, aDiffers), List.of(aDiffers, aEquals)));
    assertTrue(HasId.areEqualSetsByUniqueId(List.of(a), List.of(a, a)));
    assertTrue(HasId.areEqualSetsByUniqueId(List.of(aEquals), List.of(a, a, aEquals)));
    ArrayList<HasId> listWithNull = new ArrayList<HasId>();
    listWithNull.add(a);
    listWithNull.add(null);
    ArrayList<HasId> anotherListWithNull = new ArrayList<HasId>();
    anotherListWithNull.add(null);
    anotherListWithNull.add(a);
    anotherListWithNull.add(null);
    ArrayList<HasId> thirdListWithNull = new ArrayList<HasId>();
    thirdListWithNull.add(aDiffers);
    thirdListWithNull.add(null);
    assertFalse(HasId.areEqualSetsByUniqueId(List.of(a), listWithNull));
    assertTrue(HasId.areEqualSetsByUniqueId(listWithNull, anotherListWithNull));
    assertFalse(HasId.areEqualSetsByUniqueId(anotherListWithNull, thirdListWithNull));
  }

  @Test
  public void hashCodeHelperTest() {
    assertEquals(0, HasId.hashcodeHelper((HasId) null));
    assertEquals("a".hashCode(), HasId.hashcodeHelper(a));
  }

  @Test
  public void hashCodeHelpersTest() {
    List<HasId> nullList = new ArrayList<>();
    nullList.add(null);
    assertEquals(0, HasId.hashcodeHelper((List<HasId>) null));
    assertEquals(31, HasId.hashcodeHelper(nullList));
    assertEquals(HasId.hashcodeHelper(List.of(a)), HasId.hashcodeHelper(List.of(aEquals)));
  }
}
