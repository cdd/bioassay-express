<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response, ".."); %>
<!DOCTYPE html>
<html>
<head>
	<title>BioAssay Express: Content Summary</title>
	<%=MiscInserts.includeCommonHead(1)%>
</head>

<body>

<jsp:include page="/inc/header.jsp">
	<jsp:param name="navtitle" value="Content Summary" />
	<jsp:param name="depth" value="1" />
</jsp:include>

<div id="main"><b>Loading...</b></div>

<jsp:include page="/inc/footer.jsp" />

<%=MiscInserts.includeJSLibraries(1, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>
<%=MiscInserts.embedTemplates(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	bae.initREST(bae.getBaseURL(1), '<%=Common.stamp()%>')
	
	var page = new bae.PageSummary(SCHEMA_TEMPLATES);
	page.buildContent();
});
</script>

</body>
</html>
