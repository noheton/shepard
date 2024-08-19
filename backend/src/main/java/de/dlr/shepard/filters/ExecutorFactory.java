package de.dlr.shepard.filters;

import jakarta.enterprise.context.RequestScoped;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RequestScoped
public class ExecutorFactory {

  public ExecutorService getInstance() {
    return Executors.newCachedThreadPool();
  }
}
