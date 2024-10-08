package de.dlr.shepard.services;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonBackReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "timeseries_payload")
public class ExperimentalTimeseriesPayload {

  @Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

  @ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "timeseries_id", nullable = false)
	@JsonBackReference // Prevents recursion in JSON serialization
	private ExperimentalTimeseries timeseries;

  @Column(name = "time", nullable = false)
	private LocalDateTime time;

  @Column(name = "value")
	private Double value;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public ExperimentalTimeseries getTimeseries() {
    return timeseries;
  }

  public void setTimeseries(ExperimentalTimeseries timeseries) {
    this.timeseries = timeseries;
  }

    public LocalDateTime getTime() {
    return time;
  }

  public void setTime(LocalDateTime time) {
    this.time = time;
  }

    public Double getValue() {
    return value;
  }

  public void setValue(Double value) {
    this.value = value;
  }

}
