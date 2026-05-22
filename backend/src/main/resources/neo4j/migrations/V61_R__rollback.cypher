// V61_R — rollback for V61__v15_prov_predicates.cypher.
//
// Removes the 8 shepard: predicates + 2 role individuals registered
// by V61. Safe to run before or after re-application; identifies the
// nodes by the `shepard__addedBy` provenance tag (no chance of
// collateral damage to other :Resource nodes minted by n10s).
//
// Operator runbook:
//   cypher-shell -f V61_R__rollback.cypher
//
// Caveats:
//   - If a SemanticAnnotation has been created that references one of
//     these IRIs as its propertyIRI/valueIRI, the annotation will
//     dangle (n10s does not cascade). Run the dangling-annotation
//     probe afterwards:
//       MATCH (a:SemanticAnnotation)
//       WHERE a.propertyIRI STARTS WITH 'http://semantics.dlr.de/shepard-upper#'
//          AND a.propertyIRI IN [
//            'http://semantics.dlr.de/shepard-upper#targetCollection',
//            'http://semantics.dlr.de/shepard-upper#filesUploaded',
//            'http://semantics.dlr.de/shepard-upper#timeseriesImported',
//            'http://semantics.dlr.de/shepard-upper#structuredPayloads',
//            'http://semantics.dlr.de/shepard-upper#batchSequence',
//            'http://semantics.dlr.de/shepard-upper#throughputBytesPerSec',
//            'http://semantics.dlr.de/shepard-upper#retryCount',
//            'http://semantics.dlr.de/shepard-upper#sourceInstance'
//          ]
//       RETURN count(a) AS dangling;

MATCH (r:Resource)
WHERE r.uri STARTS WITH 'http://semantics.dlr.de/shepard-upper#'
  AND r.shepard__addedBy = 'V61__v15_prov_predicates'
WITH r, r.uri AS uri
DETACH DELETE r
RETURN uri, 'removed' AS status;
