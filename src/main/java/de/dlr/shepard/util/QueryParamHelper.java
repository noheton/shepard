package de.dlr.shepard.util;

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

}
