with ts as (select id from timeseries where container_id = ? and measurement = ? and field = ? and symbolic_name = ? and device = ? and location = ?)
insert into timeseries_data_points (timeseries_id,time,:column) (select ts.id,?,? from ts)
