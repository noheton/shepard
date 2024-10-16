package de.dlr.shepard.timeseries.repositories;

import de.dlr.shepard.timeseries.entities.ExperimentalTimeseriesDataPoint;
import io.agroal.api.AgroalDataSource;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ExperimentalTimeseriesDataPointRepositoryPlain {

  @Inject
  AgroalDataSource dataSource;

  public void insert(int timeseriesId, List<ExperimentalTimeseriesDataPoint> payload) {
    try (
      var connection = dataSource.getConnection();
      var statement = connection.prepareStatement(
        "INSERT INTO timeseries_payload(timeseries_id, time, double_value, int_value, boolean_value) VALUES(?, ?, ?, ?, ?)"
        //"INSERT INTO timeseries_payload(timeseries_id, time, double_value, int_value, string_value, boolean_value) VALUES(?, ?, ?, ?, ?, ?)"
      );
    ) {
      Log.warn("size is " + payload.size());
      for (var dataPoint : payload) {
        statement.setInt(1, timeseriesId);
        //statement.setTimestamp(2, new Timestamp(dataPoint.getTimestamp()));
        statement.setDouble(3, dataPoint.getDoubleValue());
        statement.setNull(4, Types.INTEGER);
        //statement.setNull(5, Types.NVARCHAR); // TEXT is not part of SQL standard
        statement.setNull(5, Types.BOOLEAN);
        var rowsAffected = statement.executeUpdate();
        if (rowsAffected != 1) throw new SQLException("executeUpdate returned " + rowsAffected);
        Log.info("insert data point returned " + rowsAffected);
      }
    } catch (SQLException exception) {
      Log.error(exception);
      // Todo: Exception handling missing
    }
  }

  public List<ExperimentalTimeseriesDataPoint> getByTimeseries(int timeseriesId) {
    try (
      var connection = dataSource.getConnection();
      var statement = connection.prepareStatement("SELECT * FROM timeseries_payload WHERE timeseries_id = ?");
    ) {
      var payload = new ArrayList<ExperimentalTimeseriesDataPoint>();
      statement.setInt(1, timeseriesId);
      var rs = statement.executeQuery();
      while (rs.next()) {
        var dataPoint = new ExperimentalTimeseriesDataPoint();
        dataPoint.setBooleanValue(rs.getBoolean("boolean_value"));
        dataPoint.setDoubleValue(rs.getDouble("double_value"));
        dataPoint.setIntValue(rs.getInt("int_value"));
        dataPoint.setStringValue(rs.getString("string_value"));
        dataPoint.setTimeseriesId(rs.getInt("timeseries_id"));
        //dataPoint.setTimestamp(rs.getTimestamp("time").getTime());
        payload.add(dataPoint);
      }
      rs.close();
      return payload;
    } catch (SQLException exception) {
      Log.error(exception);
      // Todo: Exception handling missing
      return new ArrayList<>();
    }
  }

  public void deleteByTimeseries(int timeseriesId) {
    try (
      var connection = dataSource.getConnection();
      var statement = connection.prepareStatement("DELETE FROM timeseries_payload WHERE timeseries_id = ?");
    ) {
      statement.setInt(1, timeseriesId);
      statement.execute();
      connection.commit();
    } catch (SQLException exception) {
      Log.error(exception);
      // Todo: Exception handling missing
    }
  }
}
