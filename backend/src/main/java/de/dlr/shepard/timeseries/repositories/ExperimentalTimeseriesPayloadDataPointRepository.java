package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseriesPayloadDataPoint;
import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ExperimentalTimeseriesPayloadDataPointRepository {

  @Inject
  AgroalDataSource dataSource;

  public void insert(int timeseriesId, List<ExperimentalTimeseriesPayloadDataPoint> payload) {
    try (
      var connection = dataSource.getConnection();
      var statement = connection.prepareStatement(
        "INSERT INTO timeseries_payload(timeseries_id, time, double_value, int_value, string_value, boolean_value) VALUES(?, ?, ?, ?, ?, ?)"
      );
    ) {
      for (var dataPoint : payload) {
        statement.setInt(1, timeseriesId);
        statement.setTimestamp(2, new Timestamp(dataPoint.getTimestamp()));
        statement.setDouble(3, dataPoint.getDoubleValue());
        statement.setNull(4, Types.INTEGER);
        statement.setNull(5, Types.NVARCHAR);
        statement.setNull(6, Types.BOOLEAN);
        statement.execute();
      }
    } catch (SQLException exception) {
      // Todo: Exception handling missing
    }
  }

  public List<ExperimentalTimeseriesPayloadDataPoint> getPayload(int timeseriesId) {
    try (
      var connection = dataSource.getConnection();
      var statement = connection.prepareStatement("SELECT * FROM timeseries_payload WHERE timeseries_id = ?");
    ) {
      var payload = new ArrayList<ExperimentalTimeseriesPayloadDataPoint>();
      statement.setInt(1, timeseriesId);
      var rs = statement.executeQuery();
      while (rs.next()) {
        var dataPoint = new ExperimentalTimeseriesPayloadDataPoint();
        dataPoint.setBooleanValue(rs.getBoolean("boolean_value"));
        dataPoint.setDoubleValue(rs.getDouble("double_value"));
        dataPoint.setIntValue(rs.getInt("int_value"));
        dataPoint.setStringValue(rs.getString("string_value"));
        dataPoint.setTimeseriesId(rs.getInt("timeseries_id"));
        dataPoint.setTimestamp(rs.getTimestamp("time").getTime());
        payload.add(dataPoint);
      }
      return payload;
    } catch (SQLException exception) {
      // Todo: Exception handling missing
      return new ArrayList<>();
    }
  }
}
