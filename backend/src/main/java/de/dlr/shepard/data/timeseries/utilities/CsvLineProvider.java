package de.dlr.shepard.data.timeseries.utilities;

import java.io.IOException;

/**
 * Interface for a provider that generates a CSV file row by row
 */
public interface CsvLineProvider {
  /**
   * Query the line provider for the next row of CSV data, if available
   * @return The next row of CSV data, terminated with \n, or an empty string, if no more data is available
   * @throws IOException If any exception during the creation of a row occurs
   */
  String readCsvLine() throws IOException;
}
