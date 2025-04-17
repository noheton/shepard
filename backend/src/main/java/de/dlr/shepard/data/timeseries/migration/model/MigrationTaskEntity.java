package de.dlr.shepard.data.timeseries.migration.model;

import de.dlr.shepard.common.util.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "migration_tasks")
public class MigrationTaskEntity {

  public static final String SPLIT_CHAR_REPLACEMENT = "_";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private int id;

  @Column(name = "container_id", nullable = false, unique = true)
  private long containerId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private MigrationTaskState state = MigrationTaskState.Planned;

  @Column(name = "created_at", nullable = false)
  private Date createdAt = new Date();

  @Column(name = "started_at")
  private Date startedAt;

  @Column(name = "finished_at")
  private Date finishedAt;

  @Column(nullable = false, columnDefinition = "TEXT")
  @Convert(converter = StringListConverter.class)
  private List<String> errors = new ArrayList<String>();

  // json representation of a timeseries
  private String timeseries;

  // influx database name
  @Column(name = "database_name")
  private String databaseName;

  public MigrationTaskEntity(long containerId) {
    this.containerId = containerId;
  }

  public void addError(String message) {
    if (message == null || message.isBlank()) {
      errors.add("UNKNOWN_ERROR");
      return;
    }
    message = message.replaceAll(Pattern.quote(StringListConverter.SPLIT_CHAR), SPLIT_CHAR_REPLACEMENT);
    errors.add(message);
  }
}
