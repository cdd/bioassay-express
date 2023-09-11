<%@ page isErrorPage="true" import="java.io.*" contentType="text/html"%>
<%@ page import="org.bson.*" %>
<%@ page import="org.slf4j.*" %>
<%@ page import="com.cdd.bae.util.*" %>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% 
	MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response);
	Logger logger = LoggerFactory.getLogger("jsp.error");
	logger.error(exception.getMessage(), exception);
%>
<!DOCTYPE html>
<html>
<head>
	<%=MiscInserts.includeGoogleAnalytics()%>
	<title>BioAssay Express</title>
	<%=MiscInserts.includeCommonHead(0)%>
</head>

<body>
<h1>Exception occurred</h1>
<% if (!Common.getDataStore().isDBAvailable()) { %><div class="alert alert-danger" role="alert">Connecting to database failed</div><% } %>
<p>Further details are available in the log files.</p>

<a class="btn btn-info" href="index.jsp">Back to BioAssay Express</a>

<jsp:include page="/inc/footer.jsp" />

</body>
</html>
