alter table if exists timeseries
    add constraint timeseries_unique_b4a836fabc25
    unique (container_id, measurement, field, symbolic_name, device, location);
