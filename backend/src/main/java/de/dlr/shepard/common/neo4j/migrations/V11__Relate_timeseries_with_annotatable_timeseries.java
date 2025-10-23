package de.dlr.shepard.common.neo4j.migrations;

import ac.simons.neo4j.migrations.core.JavaBasedMigration;
import ac.simons.neo4j.migrations.core.MigrationContext;
import de.dlr.shepard.data.timeseries.model.TimeseriesContainer;
import de.dlr.shepard.data.timeseries.model.TimeseriesEntity;
import de.dlr.shepard.data.timeseries.repositories.TimeseriesRepository;
import de.dlr.shepard.data.timeseries.services.TimeseriesContainerService;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import java.util.List;
import org.neo4j.driver.Session;

@RequestScoped
public class V11__Relate_timeseries_with_annotatable_timeseries implements JavaBasedMigration {

  @Inject
  TimeseriesContainerService timeseriesContainerService;

  @Inject
  TimeseriesRepository timeseriesRepository;

  @Override
  public void apply(MigrationContext context) {
    Log.info("migrating (annotatable) timeseries");
    try (Session session = context.getSession()) {
      List<TimeseriesContainer> allContainers = timeseriesContainerService.getContainers();
      for (TimeseriesContainer container : allContainers) {
        List<TimeseriesEntity> timeseriesEntitiesInContainer = timeseriesRepository.list(
          "containerId",
          container.getId()
        );
        for (TimeseriesEntity tsEntityInContainer : timeseriesEntitiesInContainer) {
          String query =
            "MATCH (ats:AnnotatableTimeseries), (ts:Timeseries)<-[:has_payload]-(tsr:TimeseriesReference)-[:is_in_container]->(tsc:TimeseriesContainer) ";
          query = query + "WHERE ";
          query = query + "ats.timeseriesId = " + tsEntityInContainer.getId();
          query = query + " AND ";
          query = query + "ats.containerId = id(tsc) ";
          query = query + " AND ";
          query = query + "ts.device = \"" + tsEntityInContainer.getDevice() + "\" ";
          query = query + " CREATE (ats)-[:corresponds_to]->(ts)";
        }
      }
    } catch (Exception e) {
      Log.error("Error while running migration (relating timeseries and annotatable timeseries): ", e);
    }
  }
}
