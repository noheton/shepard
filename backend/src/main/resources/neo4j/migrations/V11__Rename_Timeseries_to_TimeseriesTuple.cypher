// Rename Timeseries to TimeseriesTuple so the node it reflects its role better.
// It only consists of the 5-tuple and is only used in timeseries references.
// This node is needed separately since a timeseries reference may reference a timeseries by its 5-tuple that does not
// exist yet. The actual timeseries node should only get populated with data (such as the value type and timeseries id)
// once it gets filled with data.

match(n:Timeseries)
remove n:Timeseries
set n:TimeseriesTuple
