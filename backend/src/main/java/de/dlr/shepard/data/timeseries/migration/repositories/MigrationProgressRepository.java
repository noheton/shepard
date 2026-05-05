package de.dlr.shepard.data.timeseries.migration.repositories;

import de.dlr.shepard.data.timeseries.migration.model.MigrationProgress;
import de.dlr.shepard.data.timeseries.migration.model.MigrationProgressStatus;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class MigrationProgressRepository implements PanacheRepositoryBase<MigrationProgress, Long> {

  public Optional<MigrationProgress> find(long containerId) {
    return Optional.ofNullable(findById(containerId));
  }

  public List<MigrationProgress> listAll() {
    return findAll().list();
  }

  public List<MigrationProgress> listByStatus(MigrationProgressStatus status) {
    return list("status", status);
  }
}
