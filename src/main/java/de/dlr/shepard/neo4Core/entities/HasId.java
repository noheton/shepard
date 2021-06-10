package de.dlr.shepard.neo4Core.entities;

import java.util.List;

@FunctionalInterface
public interface HasId {

	/**
	 * Returns a specific unique identifier for this object
	 * 
	 * @return String of the unique identifier
	 */
	String getUniqueId();

	/**
	 * This function compares two lists of objects. These lists are equal if both
	 * are sorted in the same way and each object is equal when compared by its
	 * unique ID. Other attributes are ignored.
	 * 
	 * @param a The first list
	 * @param b The second list
	 * @return True of both lists are equal
	 */
	static boolean equalsHelper(List<? extends HasId> a, List<? extends HasId> b) {
		if (a == null && b == null)
			return true;
		if (a == null || b == null)
			return false;
		if (a.size() != b.size())
			return false;
		// TODO: Should we sort these lists?
		for (int i = 0; i < a.size(); i++) {
			if (!equalsHelper(a.get(i), b.get(i)))
				return false;
		}
		return true;
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
		if (a == null && b == null)
			return true;
		if (a == null || b == null)
			return false;
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
		if (a == null)
			return 0;
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
		if (a == null)
			return 0;
		return a.getUniqueId().hashCode();
	}

}
