/*
* This migration is done under the umbrella of https://gitlab.com/dlr-shepard/shepard/-/issues/688
*
* If a timeseries is references by multiple references, that lie in different containers:
* Create a new timeseries for each and set a relation from each respective reference to it.
* Also reference the respective container.
*/
match(tsc1:TimeseriesContainer)<-[ic1:is_in_container]-(tsr1:TimeseriesReference)-[:has_payload]->(ts:Timeseries)
  <-[:has_payload]-(tsr2:TimeseriesReference)-[ic2:is_in_container]->(tsc2:TimeseriesContainer)
  where tsc1 <> tsc2
create (ts1:Timeseries) set ts1 = ts
merge (tsr1)-[:has_payload]->(ts1)
merge (ts1)-[:is_in_container]->(tsc1)
detach delete ts;
