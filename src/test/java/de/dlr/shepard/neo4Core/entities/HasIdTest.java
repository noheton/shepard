package de.dlr.shepard.neo4Core.entities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class HasIdTest extends BaseTestCase {

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
		assertTrue(HasId.equalsHelper((List<HasId>) null, (List<HasId>) null));
	}

	@Test
	public void equalsHelpersTest_oneNull() {
		assertFalse(HasId.equalsHelper(null, List.of(aDiffers)));
		assertFalse(HasId.equalsHelper(List.of(a), null));
	}

	@Test
	public void equalsHelpersTest_equal() {
		List<HasId> nullList = new ArrayList<HasId>();
		nullList.add(null);
		assertTrue(HasId.equalsHelper(List.of(a), List.of(aEquals)));
		assertFalse(HasId.equalsHelper(List.of(a), List.of(aDiffers)));
		assertFalse(HasId.equalsHelper(nullList, List.of(aDiffers)));
		assertFalse(HasId.equalsHelper(List.of(a, aDiffers), List.of(aDiffers)));
		assertFalse(HasId.equalsHelper(List.of(a, aDiffers), List.of(aDiffers, aEquals)));
	}

	@Test
	public void hashCodeHelperTest() {
		assertEquals(0, HasId.hashcodeHelper((HasId) null));
		assertEquals("a".hashCode(), HasId.hashcodeHelper(a));
	}

	@Test
	public void hashCodeHelpersTest() {
		List<HasId> nullList = new ArrayList<HasId>();
		nullList.add(null);
		assertEquals(0, HasId.hashcodeHelper((List<HasId>) null));
		assertEquals(31, HasId.hashcodeHelper(nullList));
		assertEquals(HasId.hashcodeHelper(List.of(a)), HasId.hashcodeHelper(List.of(aEquals)));
	}

}
