package de.dlr.shepard.data.timeseries.model.enums;

/**
 * Possible format of CSV files. Currently, {@link CsvFormat#ROW} and
 * {@link CsvFormat#COLUMN} are supported
 */
public enum CsvFormat {
  /**
   * Row based CSV format. Each row consists of exactly one data point. The first 6 columns
   * describe the timestamp and the timeseries 5-tuple, the last column contains the data value.
   * Multiple timeseries are written completely one after each other, i.e. timestamps of
   * data points from different timeseries are not sorted
   */
  ROW,
  /**
   * Column based CSV format. Each row contains all data points from all timeseries for one certain
   * timestamp. If one timeseries does not contain data for a given timestamp, the column will be empty.
   * This can lead to very sparse files.
   * The timestamps are guaranteed to being ordered, but no assumptions can be made about the
   * time-distance of two rows.
   */
  COLUMN,
}
