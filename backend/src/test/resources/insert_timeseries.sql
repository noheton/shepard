insert into timeseries (container_id, measurement, field, symbolic_name, device, location, value_type)
values (:container_id,:measurement,:field,:symbolic_name,:device,:location,:value_type) on conflict do nothing;
with ts as (select id from timeseries where container_id = :container_id and measurement = :measurement and field = :field and symbolic_name = :symbolic_name and location = :location)
insert into timeseries_data_points (timeseries_id,time,string_value) select ts.id,:timestamp,:value from ts;
