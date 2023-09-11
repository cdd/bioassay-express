<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response); %>
<!DOCTYPE html>
<html>
<head>
	<%=MiscInserts.includeGoogleAnalytics()%>
	<title>BioAssay Express: Curated</title>
	<%=MiscInserts.includeCommonHead(0)%>
</head>	
	
<body>

<jsp:include page="/inc/header.jsp">
	<jsp:param name="navtitle" value="Curated Assays" />
	<jsp:param name="depth" value="0" />
</jsp:include>

</head>

<body>

<div id="content"></div>

<jsp:include page="/inc/footer.jsp" />

<%=MiscInserts.includeJSLibraries(0, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	// setup authentication
	<jsp:include page="inc/authentication.jsp" />

	bae.initREST(bae.getBaseURL(), '<%=Common.stamp()%>')
	bae.Popover.installHiders();
	
	var page = new bae.PageCurated();
	page.populateContent($('#content'));
});
</script>


</body>
</html>