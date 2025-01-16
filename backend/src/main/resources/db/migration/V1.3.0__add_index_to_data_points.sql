CREATE INDEX timeseries_id_index ON
    timeseries_data_points USING btree ("timeseries_id");
