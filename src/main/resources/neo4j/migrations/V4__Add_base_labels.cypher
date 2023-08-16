MATCH (n) WHERE NOT n:BasicContainer AND (n:FileContainer OR n:StructuredDataContainer OR n:TimeseriesContainer) SET n:BasicContainer;

MATCH (n) WHERE NOT n:BasicEntity AND (n:Collection OR n:DataObject OR n:BasicReference OR n:BasicContainer OR n:SemanticRepository OR n:UserGroup) SET n:BasicEntity;
