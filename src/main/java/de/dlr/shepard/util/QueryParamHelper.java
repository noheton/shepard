package de.dlr.shepard.util;

import de.dlr.shepard.neo4Core.orderBy.OrderByAttribute;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@EqualsAndHashCode
@NoArgsConstructor
public class QueryParamHelper {

	private String name;
	private PaginationHelper pagination;
	private Long parentId;
	private OrderByAttribute orderByAttribute;
	private Boolean orderDesc;

	public QueryParamHelper withName(String name) {
		this.name = name;
		return this;
	}

	public boolean hasName() {
		return this.name != null;
	}

	public QueryParamHelper withPageAndSize(int page, int size) {
		this.pagination = new PaginationHelper(page, size);
		return this;
	}

	public boolean hasPagination() {
		return this.pagination != null;
	}

	public QueryParamHelper withParentId(long parentId) {
		this.parentId = parentId;
		return this;
	}

	public boolean hasParentId() {
		return this.parentId != null;
	}

	public QueryParamHelper withOrderByAttribute(OrderByAttribute orderBy, Boolean orderDesc) {
		this.orderByAttribute = orderBy;
		this.orderDesc = orderDesc;
		return this;
	}

	public boolean hasOrderByAttribute() {
		return this.orderByAttribute != null;
	}

}
