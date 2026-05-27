package de.dlr.shepard.data.spatialdata.repositories;

public class NativeInsertStatementBuilder {

  private StringBuilder sb = new StringBuilder();
  private boolean valuesAlreadyAdded = false;

  public NativeInsertStatementBuilder insert(String tableName, String[] columns) {
    sb.append("INSERT INTO %s (%s)".formatted(tableName, String.join(", ", columns)));
    return this;
  }

  public NativeInsertStatementBuilder addValues(String values) {
    addValuesIfNecessary();
    sb.append("(%s),".formatted(values));
    return this;
  }

  public String build() {
    sb.setLength(sb.length() - 1); // remove last ,
    sb.append(";");
    return sb.toString();
  }

  private void addValuesIfNecessary() {
    if (valuesAlreadyAdded == false) {
      sb.append(" VALUES ");
      valuesAlreadyAdded = true;
    }
  }
}
