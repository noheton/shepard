package de.dlr.shepard.data.timeseries.migrations;

import io.quarkus.logging.Log;
import jakarta.resource.cci.ResultSet;
import java.sql.Connection;
import java.time.Instant;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

public class V1_7_0__int_to_bigint extends BaseJavaMigration {

  @Override
  public void migrate(Context context) throws Exception {
    var connection = context.getConnection();

    Log.info("Start migration, determining earliest time, may take a while ...");

    alterCompressionJob(connection, false);

    connection.prepareStatement("ALTER TABLE timeseries_data_points ADD COLUMN bigint_value BIGINT;").executeUpdate();

    var numberOfIntTSRS = connection
      .prepareStatement("SELECT COUNT(*) FROM timeseries WHERE value_type = 'Integer';")
      .executeQuery();

    numberOfIntTSRS.next();
    var numberOfIntTS = numberOfIntTSRS.getLong(1);

    if (numberOfIntTS > 0) {
      var minTimeRS = connection
        .prepareStatement(
          "SELECT MIN(time) FROM timeseries_data_points WHERE timeseries_id IN (SELECT id FROM timeseries WHERE value_type = 'Integer');"
        )
        .executeQuery();

      minTimeRS.next();
      var minTime = minTimeRS.getLong(1);

      Log.infov("Earliest time: {0}", minTime);
      var chunkStmt = connection.prepareStatement(
        "SELECT * FROM timescaledb_information.chunks WHERE (range_start_integer >= ? OR (range_start_integer < ? AND range_end_integer > ?)) AND hypertable_name = 'timeseries_data_points' ORDER BY range_start_integer;",
        ResultSet.TYPE_SCROLL_INSENSITIVE,
        ResultSet.CONCUR_READ_ONLY
      );
      chunkStmt.setLong(1, minTime);
      chunkStmt.setLong(2, minTime);
      chunkStmt.setLong(3, minTime);
      var chunksRS = chunkStmt.executeQuery();

      chunksRS.last();
      int chunkCount = chunksRS.getRow();
      chunksRS.beforeFirst();

      Log.infov("There are {0} chunks to migrate", chunkCount);

      int currentChunk = 0;

      while (chunksRS.next()) {
        currentChunk++;
        var start = chunksRS.getLong("range_start_integer");
        var end = chunksRS.getLong("range_end_integer");

        var compressed = chunksRS.getBoolean("is_compressed");

        Log.infov(
          "Chunk from {0} to {1}, number {2}/{3}",
          Instant.ofEpochMilli(start / 1000000),
          Instant.ofEpochMilli(end / 1000000),
          currentChunk,
          chunkCount
        );

        // Uncompress

        if (compressed) {
          var decompressStmt = connection.prepareStatement("SELECT decompress_chunk(?);");
          decompressStmt.setString(1, chunksRS.getString("chunk_schema") + "." + chunksRS.getString("chunk_name"));
          decompressStmt.execute();
        }

        // Update
        var updateStmt = connection.prepareStatement(
          "UPDATE timeseries_data_points SET bigint_value = int_value WHERE time >= ? AND time < ? AND timeseries_id IN (SELECT id FROM timeseries WHERE value_type = 'Integer');"
        );
        updateStmt.setLong(1, start);
        updateStmt.setLong(2, end);
        var rowsUpdated = updateStmt.executeUpdate();

        // Compress
        if (compressed) {
          var compressStmt = connection.prepareStatement("SELECT compress_chunk(?);");
          compressStmt.setString(1, chunksRS.getString("chunk_schema") + "." + chunksRS.getString("chunk_name"));
          compressStmt.execute();
        }

        Log.infov("Updated chunk, changed {0} rows", rowsUpdated);
      }
    } else {
      Log.info("There are no integer timeseries, therefore no data migration necessary.");
    }

    Log.info("Finished migration of data, starting table changes");
    connection.prepareStatement("ALTER TABLE timeseries_data_points DROP COLUMN int_value;").executeUpdate();
    connection
      .prepareStatement("ALTER TABLE timeseries_data_points RENAME COLUMN bigint_value TO int_value;")
      .executeUpdate();

    alterCompressionJob(connection, true);

    Log.info("Finished migration");
  }

  private void alterCompressionJob(Connection connection, boolean enable) throws Exception {
    var jobIDRS = connection
      .prepareStatement(
        "SELECT job_id from timescaledb_information.jobs where hypertable_name = 'timeseries_data_points' and proc_name = 'policy_compression';"
      )
      .executeQuery();

    if (!jobIDRS.next()) {
      Log.info("Did not find a job id - not altering job");
    }

    var jobID = jobIDRS.getInt("job_id");

    var jobStmt = connection.prepareStatement("SELECT alter_job(?, scheduled => ?);");
    jobStmt.setInt(1, jobID);
    jobStmt.setBoolean(2, enable);
    jobStmt.execute();
  }
}
