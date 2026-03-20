package de.dlr.shepard.common.neo4j.migrations;

import static org.neo4j.cypherdsl.core.Cypher.node;

import ac.simons.neo4j.migrations.core.JavaBasedMigration;
import ac.simons.neo4j.migrations.core.MigrationContext;
import de.dlr.shepard.data.timeseries.model.enums.DataPointValueType;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import org.eclipse.microprofile.config.ConfigProvider;
import org.neo4j.cypherdsl.core.Cypher;

public class V12__Timescale2Neo4j implements JavaBasedMigration {

  private record Timeseries(
    long id,
    long containerId,
    String measurement,
    String device,
    String location,
    String symbolicName,
    String field,
    DataPointValueType valueType
  ) {}

  public Connection createPostgresConnection() throws ClassNotFoundException, SQLException {
    Class.forName("org.postgresql.Driver");
    return DriverManager.getConnection(
      ConfigProvider.getConfig().getValue("quarkus.datasource.jdbc.url", String.class),
      ConfigProvider.getConfig().getValue("quarkus.datasource.username", String.class),
      ConfigProvider.getConfig().getValue("quarkus.datasource.password", String.class)
    );
  }

  @Override
  public void apply(MigrationContext context) {
    try (var connection = createPostgresConnection()) {
      assert isTimescaleOld(connection);
      var res = connection
        .prepareStatement(
          "select id, container_id, measurement, device, location, symbolic_name, field, value_type from timeseries"
        )
        .executeQuery();
      var resList = new ArrayList<Timeseries>();
      while (res.next()) {
        resList.add(
          new Timeseries(
            res.getLong(1),
            res.getLong(2),
            res.getString(3),
            res.getString(4),
            res.getString(5),
            res.getString(6),
            res.getString(7),
            dbValueType2Java(res.getString(8))
          )
        );
      }
      var session = context.getSession();
      for (var ts : resList) {
        var tsNodeToUpdate = node("Timeseries")
          .withProperties(
            "measurement",
            Cypher.literalOf(ts.measurement()),
            "device",
            Cypher.literalOf(ts.device()),
            "location",
            Cypher.literalOf(ts.location()),
            "symbolicName",
            Cypher.literalOf(ts.symbolicName()),
            "field",
            Cypher.literalOf(ts.field())
          )
          .named("ts");
        var tsc = node("TimeseriesContainer").named("tsc");
        var updateQuery = Cypher.match(tsNodeToUpdate.relationshipTo(tsc, "is_in_container"))
          .where(tsc.internalId().eq(Cypher.literalOf(ts.containerId())))
          .set(tsNodeToUpdate.property("timeseriesId").to(Cypher.literalOf(ts.id())))
          .set(tsNodeToUpdate.property("valueType").to(Cypher.literalOf(ts.valueType().toString())))
          .returning(Cypher.literalTrue())
          .build()
          .getCypher();

        var tsNodeToCreate = tsNodeToUpdate.withProperties(
          "timeseriesId",
          Cypher.literalOf(ts.id()),
          "measurement",
          Cypher.literalOf(ts.measurement()),
          "device",
          Cypher.literalOf(ts.device()),
          "location",
          Cypher.literalOf(ts.location()),
          "symbolicName",
          Cypher.literalOf(ts.symbolicName()),
          "field",
          Cypher.literalOf(ts.field()),
          "valueType",
          Cypher.literalOf(ts.valueType().toString())
        );
        var insertQuery = Cypher.match(tsc.where(tsc.internalId().eq(Cypher.literalOf(ts.containerId()))))
          .with(tsc)
          .create(tsNodeToCreate.relationshipTo(tsc, "is_in_container"))
          .build()
          .getCypher();

        var tx = session.beginTransaction();
        // If an existing timeseries is altered the update query will return a value.
        var r = tx.run(updateQuery);
        // If this value is not returned that means the timeseries does not yet exist and needs to be created.
        if (!r.hasNext()) tx.run(insertQuery);
        tx.commit();
      }
    } catch (ClassNotFoundException | SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private DataPointValueType dbValueType2Java(String valueType) {
    return switch (valueType) {
      case "Boolean" -> DataPointValueType.Boolean;
      case "Integer" -> DataPointValueType.Integer;
      case "String" -> DataPointValueType.String;
      case "Double" -> DataPointValueType.Double;
      default -> throw new RuntimeException("Value type from timescale not assignable!");
    };
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
