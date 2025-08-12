CREATE OR REPLACE FUNCTION unix_now_immutable() returns BIGINT LANGUAGE SQL IMMUTABLE as $$  SELECT extract (epoch from now())::BIGINT * 1000000000 $$;

SELECT set_integer_now_func('timeseries_data_points', 'unix_now_immutable', true);
