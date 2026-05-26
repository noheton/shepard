// Rollback for V87__hdf_vocabulary.cypher
//
// Removes the HDF5 Vocabulary node seeded by V87.
// Safe to run on an install that never ran V87 (MATCH finds nothing).

MATCH (v:Vocabulary { uri: "https://shepard.dlr.de/ontology/hdf#" })
DETACH DELETE v;
