insert into timeseries (container_id, measurement, field, symbolic_name, device, location, value_type)
values (?,?,?,?,?,?,?) on conflict do nothing returning id
