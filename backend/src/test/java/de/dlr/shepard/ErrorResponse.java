package de.dlr.shepard;

import lombok.Data;

@Data
public class ErrorResponse {

  private Integer status;
  private String exception;
  private String message;
}
