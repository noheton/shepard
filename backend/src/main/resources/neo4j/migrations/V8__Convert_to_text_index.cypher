// assume that version is ge 5.0
DROP INDEX idx_Timeseries_attr IF EXISTS;
CREATE TEXT INDEX idx_Timeseries_attr_measurement IF NOT EXISTS FOR (n:Timeseries) ON (n.measurement);
CREATE TEXT INDEX idx_Timeseries_attr_device IF NOT EXISTS FOR (n:Timeseries) ON (n.device);
CREATE TEXT INDEX idx_Timeseries_attr_location IF NOT EXISTS FOR (n:Timeseries) ON (n.location);
CREATE TEXT INDEX idx_Timeseries_attr_symbolic_name IF NOT EXISTS FOR (n:Timeseries) ON (n.symbolicName);
CREATE TEXT INDEX idx_Timeseries_attr_field IF NOT EXISTS FOR (n:Timeseries) ON (n.field);