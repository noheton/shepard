package de.dlr.shepard.timeseries.migration.repositories;

import de.dlr.shepard.timeseries.migration.model.MigrationTaskEntity;
import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.RequestScoped;
import jakarta.transaction.Transactional;

@RequestScoped
@Transactional
public class MigrationTaskRepository implements PanacheRepositoryBase<MigrationTaskEntity, Integer> {}
