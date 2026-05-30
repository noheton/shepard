export function buildDataObjectSparqlUrl(dataObjectAppId: string): string {
  const query = `SELECT ?p ?o WHERE {\n  <urn:shepard:${dataObjectAppId}> ?p ?o .\n}\nLIMIT 50`;
  return `/semantic/sparql?query=${encodeURIComponent(query)}`;
}
