package de.dlr.shepard.data.timeseries.model;

import de.dlr.shepard.data.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.data.timeseries.model.enums.FillOption;
import java.util.Optional;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@EqualsAndHashCode
@RequiredArgsConstructor
@AllArgsConstructor
@Data
public class TimeseriesDataPointsQueryParams {

  /**
   * The start of the timeseries part to be fetched in nanoseconds since unix epoch
   */
  @NonNull
  private Long startTime;

  /**
   * The end of the timeseries part to be fetched in nanoseconds since unix epoch
   */
  @NonNull
  private Long endTime;

  private Long timeSliceNanoseconds;
  private FillOption fillOption;
  private AggregateFunction function;

  /**
   *
   * @return The time interval that measurements get grouped by to apply the aggregate function.
   */
  public Optional<Long> getTimeSliceNanoseconds() {
    return Optional.ofNullable(timeSliceNanoseconds);
  }

  /**
   *
   * @return The fill option for missing values when applying aggregate functions on possibly empty time slices.
   */
  public Optional<FillOption> getFillOption() {
    return Optional.ofNullable(fillOption);
  }

  /**
   *
   * @return The aggregate function.
   */
  public Optional<AggregateFunction> getFunction() {
    return Optional.ofNullable(function);
  }
}
