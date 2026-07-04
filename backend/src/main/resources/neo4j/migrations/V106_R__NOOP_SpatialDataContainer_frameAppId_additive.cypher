// Rollback for V106 (additive nullable property — no schema change to undo).
// The property is removed by simply not setting it; if an operator wants
// to forcibly drop the property from every existing node:
//   MATCH (c:SpatialDataContainer) WHERE c.frameAppId IS NOT NULL
//   REMOVE c.frameAppId;
// (only run as part of an intentional rollback of MFFD-SPATIAL-FRAME-HANDSHAKE.)
RETURN 1;
