package de.dlr.shepard.common.util;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

@FunctionalInterface
public interface HasId {
  /**
   * Returns a specific unique identifier for this object
   *
   * @return String of the unique identifier
   */
  @JsonIgnore
  String getUniqueId();

  /**
   * This function compares two lists of objects.
   * These lists are considered equal iff the sets of their unique Ids are identical.
   * In particular, this does not take the order of the elements and their multiplicity into account.
   *
   * @param a The first list
   * @param b The second list
   * @return True iff both lists are equal by means described above
   */
  static boolean areEqualSetsByUniqueId(List<? extends HasId> a, List<? extends HasId> b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    HashSet<String> IdSetA = new HashSet<String>();
    HashSet<String> IdSetB = new HashSet<String>();
    boolean hasNullA = false;
    boolean hasNullB = false;
    for (int i = 0; i < a.size(); i++) {
      if (a.get(i) != null) IdSetA.add(a.get(i).getUniqueId());
      else hasNullA = true;
    }
    for (int i = 0; i < b.size(); i++) {
      if (b.get(i) != null) IdSetB.add(b.get(i).getUniqueId());
      else hasNullB = true;
    }
    if ((hasNullA && !hasNullB) || (!hasNullA && hasNullB)) return false;
    return IdSetA.equals(IdSetB);
  }

  static boolean areEqualSets(long[] firstArray, long[] secondArray) {
    if (firstArray == null && secondArray == null) return true;
    if (firstArray == null || secondArray == null) return false;
    HashSet<Long> firstArraySet = new HashSet<Long>();
    HashSet<Long> secondArraySet = new HashSet<Long>();
    for (int i = 0; i < firstArray.length; i++) firstArraySet.add(firstArray[i]);
    for (int i = 0; i < secondArray.length; i++) secondArraySet.add(secondArray[i]);
    return firstArraySet.equals(secondArraySet);
  }

  static boolean areEqualSets(String[] firstArray, String[] secondArray) {
    if (firstArray == null && secondArray == null) return true;
    if (firstArray == null || secondArray == null) return false;
    HashSet<String> firstArraySet = new HashSet<String>();
    HashSet<String> secondArraySet = new HashSet<String>();
    for (int i = 0; i < firstArray.length; i++) firstArraySet.add(firstArray[i]);
    for (int i = 0; i < secondArray.length; i++) secondArraySet.add(secondArray[i]);
    return firstArraySet.equals(secondArraySet);
  }

  /**
   * This function compares two objects. These objects are equal if their unique
   * ID is equal. Other attributes are ignored.
   *
   * @param a The first object
   * @param b The second object
   * @return True if both objects are equal
   */
  static boolean equalsHelper(HasId a, HasId b) {
    if (a == null && b == null) return true;
    if (a == null || b == null) return false;
    return a.getUniqueId().equals(b.getUniqueId());
  }

  /**
   * This function calculates the hash code of a list of objects. Only the unique
   * ID is included in the calculation.
   *
   * @param a The list of objects
   * @return The calculated hash code
   */
  static int hashcodeHelper(List<? extends HasId> a) {
    if (a == null) return 0;
    final int prime = 31;
    int result = 1;
    for (HasId element : a) {
      result = prime * result + hashcodeHelper(element);
    }
    return result;
  }

  /**
   * This function calculates the hash code of a object. Only the unique ID is
   * included in the calculation.
   *
   * @param a The object
   * @return The calculated hash code
   */
  static int hashcodeHelper(HasId a) {
    if (a == null) return 0;
    return a.getUniqueId().hashCode();
  }

  static int hashcodeHelper(long[] array) {
    if (array == null) return Objects.hash(array);
    HashSet<Long> arrayAsHashSet = new HashSet<Long>();
    for (int i = 0; i < array.length; i++) arrayAsHashSet.add(array[i]);
    return arrayAsHashSet.hashCode();
  }

  static int hashcodeHelper(String[] array) {
    if (array == null) return Objects.hash(array);
    HashSet<String> arrayAsHashSet = new HashSet<String>();
    for (int i = 0; i < array.length; i++) arrayAsHashSet.add(array[i]);
    return arrayAsHashSet.hashCode();
  }
}
