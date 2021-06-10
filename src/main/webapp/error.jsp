<%@ page language="java" isErrorPage="true" import="java.io.*" contentType="application/json"%>{
  "Status": <%=response.getStatus() %>,
  "Exception": "<%if(exception != null) {%><%=exception.getCause()%><%}%>",
  "Message": "<%if(exception != null) {%><%=exception.getMessage()%><%}%>"
}
