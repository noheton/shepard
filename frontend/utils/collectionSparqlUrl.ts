export function buildCollectionSparqlUrl(collectionAppId: string): string {
  const query = `SELECT ?s ?p ?o WHERE {
  ?s ?p ?o .
  FILTER(CONTAINS(STR(?s), "${collectionAppId}"))
}
LIMIT 50`;
  return `/semantic/sparql?query=${encodeURIComponent(query)}`;
}
