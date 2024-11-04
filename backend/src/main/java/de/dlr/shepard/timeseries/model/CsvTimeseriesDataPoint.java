package de.dlr.shepard.timeseries.model;

import com.opencsv.bean.CsvBindByName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CsvTimeseriesDataPoint {

  @CsvBindByName(required = true)
  private long timestamp;

  @CsvBindByName(required = true)
  private String measurement;

  @CsvBindByName(required = true)
  private String device;

  @CsvBindByName(required = true)
  private String location;

  @CsvBindByName(required = true)
  private String symbolicName;

  @CsvBindByName(required = true)
  private String field;

  @CsvBindByName(required = false)
  private Object value;
}
