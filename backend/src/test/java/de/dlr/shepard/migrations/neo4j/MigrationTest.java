package de.dlr.shepard.migrations.neo4j;

import de.dlr.shepard.common.neo4j.MigrationsRunner;

/**
 * Class to implement for all the test methods of a complex migration test.
 * This can be subclassed to avoid polluting the namespace of the main test runner.
 * All the respective helper methods and fields can be encapsulated here.
 */
abstract class MigrationTest {

  static final QueryHelper q = new QueryHelper();
  final SampleNodeCreatorFactory sample = new SampleNodeCreatorFactory(getTargetVersion());

  abstract void setupPreMigrationData() throws Exception;

  abstract String getTargetVersion();

  void runMigration() {
    new MigrationsRunner(getTargetVersion()).apply();
  }
}
