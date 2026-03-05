package de.dlr.shepard.data.timeseries.utilities;

import java.io.IOException;

public interface CsvLineProvider {
  public String readCsvLine() throws IOException;
}
