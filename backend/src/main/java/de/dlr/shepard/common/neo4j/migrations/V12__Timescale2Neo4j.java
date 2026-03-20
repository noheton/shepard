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
import org.jspecify.annotations.NonNull;
import org.neo4j.cypherdsl.core.Cypher;
import org.neo4j.cypherdsl.core.Node;

public class V12__Timescale2Neo4j implements JavaBasedMigration {

  private static final Node TSC_NODE = node("TimeseriesContainer").named("tsc");

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
      var tsList = getTimeseriesListFromTimescale(connection);
      migrateTimeseriesMetadataToNeo4(context, tsList);
      deleteMetadataFromTimescale(connection);
    } catch (ClassNotFoundException | SQLException e) {
      throw new MigrationFailureException(e);
    }
  }

  private static void migrateTimeseriesMetadataToNeo4(MigrationContext context, ArrayList<Timeseries> tsList) {
    var session = context.getSession();
    var tx = session.beginTransaction();
    for (var ts : tsList) {
      var updateQuery = ts2UpdateQuery(ts);
      var insertQuery = ts2InsertQuery(ts);

      // If an existing timeseries is altered the update query will return a value.
      var r = tx.run(updateQuery);
      // If this value is not returned that means the timeseries does not yet exist and needs to be created.
      if (!r.hasNext()) tx.run(insertQuery);
      // Remove the foreign key association between timeseries_data_points and timeseries so we can remove table timeseries
    }
    tx.commit();
  }

  private static void deleteMetadataFromTimescale(Connection connection) throws SQLException {
    try (
      var dropConstraint = connection.prepareStatement(
        "alter table timeseries_data_points drop constraint FKog3jr0iowrx3wkun79k0ihs6o"
      );
      var dropTimeseries = connection.prepareStatement("drop table timeseries")
    ) {
      connection.setAutoCommit(false);
      dropConstraint.executeUpdate();
      dropTimeseries.executeUpdate();
      connection.commit();
    }
  }

  private static String ts2InsertQuery(Timeseries ts) {
    var tsNodeToCreate = node("Timeseries").withProperties(
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
    var insertQuery = Cypher.match(TSC_NODE.where(TSC_NODE.internalId().eq(Cypher.literalOf(ts.containerId()))))
      .with(TSC_NODE)
      .create(tsNodeToCreate.relationshipTo(TSC_NODE, "is_in_container"))
      .build()
      .getCypher();
    return insertQuery;
  }

  private static String ts2UpdateQuery(Timeseries ts) {
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
    var updateQuery = Cypher.match(tsNodeToUpdate.relationshipTo(TSC_NODE, "is_in_container"))
      .where(TSC_NODE.internalId().eq(Cypher.literalOf(ts.containerId())))
      .set(tsNodeToUpdate.property("timeseriesId").to(Cypher.literalOf(ts.id())))
      .set(tsNodeToUpdate.property("valueType").to(Cypher.literalOf(ts.valueType().toString())))
      .returning(Cypher.literalTrue())
      .build()
      .getCypher();
    return updateQuery;
  }

  private @NonNull ArrayList<Timeseries> getTimeseriesListFromTimescale(Connection connection) throws SQLException {
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
    return resList;
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
