import {
  type BasicEntity,
  ContainerType,
  QueryType,
  type ResultTriple,
  SearchApi,
  type SearchParams,
  type SearchScope,
} from "@dlr-shepard/backend-client";
import type { SearchResult } from "~/components/context/Search/context/SearchResultList.vue";
import { useShepardApi } from "~/composables/common/api/useShepardApi";

export function search(
  request: SearchRequest,
  loading: Ref<boolean>,
  searchResults: Ref<SearchResult[]>,
) {
  loading.value = true;
  request
    .execute()
    .then(results => (searchResults.value = results))
    .catch(error => handleError(error, "while searching data"))
    .finally(() => (loading.value = false));
}

export interface SearchRequest {
  execute(): Promise<SearchResult[]>;
}

abstract class SearchBasicEntityRequest implements SearchRequest {
  readonly query: string;
  readonly scope: SearchScope;
  readonly queryType: QueryType;

  protected constructor(
    query: string,
    scope: SearchScope,
    queryType: QueryType,
  ) {
    this.query = query;
    this.scope = scope;
    this.queryType = queryType;
  }

  async execute(): Promise<SearchResult[]> {
    const results = await searchBasicEntity(
      {
        query: this.query,
        queryType: this.queryType,
      },
      this.scope,
    );
    return results.map(([result, triple]) => ({
      id: result.id,
      name: result.name,
      url: this.triple2Url(triple),
    }));
  }

  protected abstract triple2Url(triple: ResultTriple): string;
}

async function searchBasicEntity(
  searchParams: SearchParams,
  scope: SearchScope,
): Promise<[BasicEntity, ResultTriple][]> {
  const response = await useShepardApi(SearchApi).value.search({
    searchBody: {
      searchParams: searchParams,
      scopes: [scope],
    },
  });
  const basicEntities = response.results ?? [];
  const resultTriples = response.resultSet ?? [];
  return zip(basicEntities, resultTriples);
}

export class SearchCollectionRequest extends SearchBasicEntityRequest {
  constructor(query: string) {
    super(query, { traversalRules: [] }, QueryType.Collection);
  }

  protected triple2Url(triple: ResultTriple): string {
    return "collections/" + triple.collectionId!;
  }
}

export class SearchDataObjectRequest extends SearchBasicEntityRequest {
  constructor(query: string, scope: SearchScope) {
    super(query, scope, QueryType.DataObject);
  }

  protected triple2Url(triple: ResultTriple): string {
    return (
      "collections/" +
      triple.collectionId! +
      "/dataobjects/" +
      triple.dataObjectId!
    );
  }
}

export class SearchReferenceRequest extends SearchBasicEntityRequest {
  constructor(query: string, scope: SearchScope) {
    super(query, scope, QueryType.Reference);
  }

  protected triple2Url(triple: ResultTriple): string {
    return (
      "collections/" +
      triple.collectionId! +
      "/dataobjects/" +
      triple.dataObjectId!
    );
  }
}

export class SearchStructuredRequest extends SearchBasicEntityRequest {
  constructor(query: string, scope: SearchScope) {
    super(query, scope, QueryType.StructuredData);
  }

  protected triple2Url(triple: ResultTriple): string {
    return (
      "collections/" +
      triple.collectionId! +
      "/dataobjects/" +
      triple.dataObjectId! +
      "/structureddatareferences/" +
      triple.referenceId!
    );
  }
}

abstract class SearchContainerRequest implements SearchRequest {
  readonly query: string;
  readonly containerType: ContainerType;

  protected constructor(query: string, containerType: ContainerType) {
    this.query = query;
    this.containerType = containerType;
  }

  protected abstract get containerUrlRepr(): string;

  async execute(): Promise<SearchResult[]> {
    const results = await useShepardApi(SearchApi).value.searchContainers({
      containerSearchBody: {
        searchParams: { query: this.query, queryType: this.containerType },
      },
    });
    return results.results.map(con => ({
      id: con.id,
      name: con.name,
      url: "containers/" + this.containerUrlRepr + "/" + con.id,
    }));
  }
}

export class SearchFileContainerRequest extends SearchContainerRequest {
  readonly containerUrlRepr = "files";

  constructor(query: string) {
    super(query, ContainerType.File);
  }
}

export class SearchStructuredContainerRequest extends SearchContainerRequest {
  readonly containerUrlRepr = "strcutureddata";

  constructor(query: string) {
    super(query, ContainerType.Structureddata);
  }
}

export class SearchTimeseriesContainerRequest extends SearchContainerRequest {
  readonly containerUrlRepr = "timeseries";

  constructor(query: string) {
    super(query, ContainerType.Timeseries);
  }
}

function zip<T, U>(a: T[], b: U[]): [T, U][] {
  if (a.length !== b.length) {
    throw new Error(
      "Error in search: Basic entities and result triples diverge! This is likely a server bug.",
    );
  }
  return a.map((e, i) => [e, b[i]!]);
}
