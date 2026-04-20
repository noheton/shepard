package de.dlr.shepard.common.util;

public class Neo4jLabels {

  public static final String TIMESERIES = "Timeseries";
  public static final String TIMESERIES_CONTAINER = "TimeseriesContainer";
  public static final String TIMESERIES_TUPLE = "TimeseriesTuple";
  // Relationships
  public static final String HAS_DATAOBJECT = "has_dataobject";
  public static final String HAS_REFERENCE = "has_reference";
  public static final String HAS_CHILD = "has_child";
  public static final String HAS_SUCCESSOR = "has_successor";
  public static final String HAS_PREDECESSOR = "has_predecessor";
  public static final String CREATED_BY = "created_by";
  public static final String UPDATED_BY = "updated_by";
  public static final String BELONGS_TO = "belongs_to";
  public static final String SUBSCRIBED_BY = "subscribed_by";
  public static final String POINTS_TO = "points_to";
  public static final String IS_IN_CONTAINER = "is_in_container";
  public static final String IS_IN_GROUP = "is_in_group";
  public static final String VALUE_REPOSITORY = "value_in_repository";
  public static final String PROPERTY_REPOSITORY = "property_in_repository";
  public static final String HAS_ANNOTATION = "has_annotation";
  public static final String FILE_IN_CONTAINER = "file_in_container";
  public static final String TIMESERIES_IN_CONTAINER = "timeseries_in_container";
  public static final String STRUCTUREDDATA_IN_CONTAINER = "structureddata_in_container";
  public static final String HAS_PAYLOAD = "has_payload";
  public static final String HAS_LABJOURNAL_ENTRY = "has_labjournalentry";
  public static final String HAS_DEFAULT_FILE_CONTAINER = "has_default_file_container";
  public static final String HAS_TIMESERIES_TUPLE = "has_timeseries_tuple";
}
