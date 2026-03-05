// This migration is done under the umbrella of https://gitlab.com/dlr-shepard/shepard/-/issues/688
match(tsr1:TimeseriesReference)-[:has_payload]-(ts1:Timeseries)-[:is_in_container]->(tsc:TimeseriesContainer)<-[:is_in_container]-(ts2:Timeseries)
  where ts1.device = ts2.device and ts1.field = ts2.field and ts1.location = ts2.location and ts1.measurement = ts2.measurement and ts1.symbolicName = ts2.symbolicName
  and id(ts1) < id(ts2)
merge (tsr1)-[:has_payload]-(ts2)
detach delete ts1;
