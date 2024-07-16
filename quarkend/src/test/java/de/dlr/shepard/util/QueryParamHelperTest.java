package de.dlr.shepard.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.dlr.shepard.BaseTestCase;
import de.dlr.shepard.neo4Core.orderBy.DataObjectAttributes;
import org.junit.jupiter.api.Test;

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
  public void withPredecessorId() {
    var params = new QueryParamHelper().withPredecessorId(123L);
    assertTrue(params.hasPredecessorId());
    assertEquals(123L, params.getPredecessorId());
  }

  @Test
  public void withoutPredecessorId() {
    var params = new QueryParamHelper();
    assertFalse(params.hasPredecessorId());
    assertNull(params.getPredecessorId());
  }

  @Test
  public void withSuccessorId() {
    var params = new QueryParamHelper().withSuccessorId(123L);
    assertTrue(params.hasSuccessorId());
    assertEquals(123L, params.getSuccessorId());
  }

  @Test
  public void withoutSuccessorId() {
    var params = new QueryParamHelper();
    assertFalse(params.hasSuccessorId());
    assertNull(params.getSuccessorId());
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

  @Test
  public void withOrderBy() {
    var params = new QueryParamHelper().withOrderByAttribute(DataObjectAttributes.name, true);
    assertTrue(params.hasOrderByAttribute());
    assertEquals(DataObjectAttributes.name, params.getOrderByAttribute());
    assertTrue(params.getOrderDesc());
  }

  @Test
  public void withoutOrderBy() {
    var params = new QueryParamHelper();
    assertFalse(params.hasOrderByAttribute());
    assertNull(params.getOrderByAttribute());
  }
}
