/*
* This migration is done under the umbrella of https://gitlab.com/dlr-shepard/shepard/-/issues/688.
*
* Its aim is to unify the Timeseries and AnnotatableTimeseries nodes with the annotations being transferred from
* AnnotatableTimeseries to Timeseries and subsequent Deletion of AnnotatableTimeseries.
*/

match(ats:AnnotatableTimeseries)-[:has_annotation]->(a:SemanticAnnotation)
match(ts:Timeseries {timeseriesId: ats.timeseriesId})
merge (ts)-[:has_annotation]->(a)
detach delete ats
