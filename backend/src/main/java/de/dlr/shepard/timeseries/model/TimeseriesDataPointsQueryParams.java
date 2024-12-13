package de.dlr.shepard.timeseries.model;

import de.dlr.shepard.timeseries.model.enums.AggregateFunction;
import de.dlr.shepard.timeseries.model.enums.FillOption;
import java.util.Optional;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode
public class TimeseriesDataPointsQueryParams {

  private long startTime;
  private long endTime;
  private Optional<Long> timeSliceNanoseconds;
  private Optional<FillOption> fillOption;
  private Optional<AggregateFunction> function;

  /**
   * @param startTime             The start of the timeseries part to be fetched in nanoseconds since unix epoch
   * @param endTime               The end of the timeseries part to be fetched in nanoseconds since unix epoch
   * @param timeSliceNanoseconds  The time interval that measurements get grouped by to apply the aggregate function.
   * @param fillOption            The fill option for missing values when applying aggregate functions on possibly empty time slices.
   * @param function              The aggregate function.
   */
  public TimeseriesDataPointsQueryParams(
    long startTime,
    long endTime,
    Long timeSliceNanoseconds,
    FillOption fillOption,
    AggregateFunction function
  ) {
    this.startTime = startTime;
    this.endTime = endTime;

    this.timeSliceNanoseconds = Optional.ofNullable(timeSliceNanoseconds);
    this.fillOption = Optional.ofNullable(fillOption);
    this.function = Optional.ofNullable(function);
  }

  /**
   *
   * @return The start of the timeseries part to be fetched in nanoseconds since unix epoch
   */
  public long getStartTime() {
    return startTime;
  }

  /**
   *
   * @return The end of the timeseries part to be fetched in nanoseconds since unix epoch
   */
  public long getEndTime() {
    return endTime;
  }

  /**
   *
   * @return The time interval that measurements get grouped by to apply the aggregate function.
   */
  public Optional<Long> getTimeSliceNanoseconds() {
    return timeSliceNanoseconds;
  }

  /**
   *
   * @return The fill option for missing values when applying aggregate functions on possibly empty time slices.
   */
  public Optional<FillOption> getFillOption() {
    return fillOption;
  }

  /**
   *
   * @return The aggregate function.
   */
  public Optional<AggregateFunction> getFunction() {
    return function;
  }
}
