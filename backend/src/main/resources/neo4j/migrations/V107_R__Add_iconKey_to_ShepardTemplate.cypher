// TEMPLATE-ICONS-1 rollback — remove iconKey from the 10 MFFD templates.
//
// Scoped to the specific template names set by V107. Templates that an
// admin has since edited (and thereby copy-on-write-versioned) carry a
// different node; REMOVE on the v1 row does not affect those later versions.
//
// OPTIONAL MATCH means missing nodes are silently skipped.

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDStepRoot'})
WHERE t IS NOT NULL
REMOVE t.iconKey;

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDLayer'})
WHERE t IS NOT NULL
REMOVE t.iconKey;

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDPlyGroup'})
WHERE t IS NOT NULL
REMOVE t.iconKey;

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDTrack'})
WHERE t IS NOT NULL
REMOVE t.iconKey;

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDExecution'})
WHERE t IS NOT NULL
REMOVE t.iconKey;

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDBridgeWeldExecution'})
WHERE t IS NOT NULL
REMOVE t.iconKey;

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDSpotWeld'})
WHERE t IS NOT NULL
REMOVE t.iconKey;

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDNDTScan'})
WHERE t IS NOT NULL
REMOVE t.iconKey;

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDCell'})
WHERE t IS NOT NULL
REMOVE t.iconKey;

OPTIONAL MATCH (t:ShepardTemplate {name: 'MFFDLayerOverview'})
WHERE t IS NOT NULL
REMOVE t.iconKey;
