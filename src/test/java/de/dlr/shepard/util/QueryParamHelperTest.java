package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import de.dlr.shepard.BaseTestCase;

public class QueryParamHelperTest extends BaseTestCase {

	@Test
	public void withName() {
		var params = new QueryParamHelper().withName("test");
		assertTrue(params.hasName());
		assertEquals("test", params.getName());
	}

	@Test
	public void withoutName() {
		var params = new QueryParamHelper();
		assertFalse(params.hasName());
		assertNull(params.getName());
	}

	@Test
	public void withParentId() {
		var params = new QueryParamHelper().withParentId(123L);
		assertTrue(params.hasParentId());
		assertEquals(123L, params.getParentId());
	}

	@Test
	public void withoutParentId() {
		var params = new QueryParamHelper();
		assertFalse(params.hasParentId());
		assertNull(params.getParentId());
	}

	@Test
	public void withPagination() {
		var params = new QueryParamHelper().withPageAndSize(1, 2);
		assertTrue(params.hasPagination());
		assertEquals(new PaginationHelper(1, 2), params.getPagination());
	}

	@Test
	public void withoutPagination() {
		var params = new QueryParamHelper();
		assertFalse(params.hasPagination());
		assertNull(params.getPagination());
	}

}
