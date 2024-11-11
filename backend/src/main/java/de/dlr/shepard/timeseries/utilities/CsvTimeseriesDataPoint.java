package de.dlr.shepard.timeseries.utilities;

import com.opencsv.bean.CsvBindByName;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
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

  public CsvTimeseriesDataPoint(
    long timestamp,
    String measurement,
    String device,
    String location,
    String symbolicName,
    String field,
    Object value
  ) {
    this.timestamp = timestamp;
    this.measurement = measurement;
    this.device = device;
    this.location = location;
    this.symbolicName = symbolicName;
    this.field = field;
    this.value = value;
  }
}
