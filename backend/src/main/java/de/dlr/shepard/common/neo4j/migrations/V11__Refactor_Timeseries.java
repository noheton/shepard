package de.dlr.shepard.common.neo4j.migrations;

import ac.simons.neo4j.migrations.core.JavaBasedMigration;
import ac.simons.neo4j.migrations.core.MigrationContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.eclipse.microprofile.config.ConfigProvider;

public class V11__Refactor_Timeseries implements JavaBasedMigration {

  @Override
  public void apply(MigrationContext context) {
    try {
      Class.forName("org.postgresql.Driver");
      Connection connection = DriverManager.getConnection(
        ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class),
        ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class),
        ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class)
      );
      assert isTimescaleOld(connection);
    } catch (ClassNotFoundException e) {
      throw new RuntimeException(e);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private boolean isTimescaleFresh(Connection connection) throws SQLException {
    return !isTimescaleOld(connection);
  }

  private boolean isTimescaleOld(Connection connection) throws SQLException {
    Statement statement = connection.createStatement();
    ResultSet res = statement.executeQuery("select * from pg_tables");
    while (res.next()) {
      String tablename = res.getString("tablename");
      if (tablename.equals("timeseries") || tablename.equals("timeseries_data_points")) return true;
    }
    return false;
  }
}
