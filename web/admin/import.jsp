<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response, "..");%>
<!DOCTYPE html>
<html>
<head>
	<title>BioAssay Express: Import Data</title>
	<%=MiscInserts.includeCommonHead(1)%>
</head>

<body>
<jsp:include page="/inc/header.jsp">
	<jsp:param name="navtitle" value="Import Data" />
	<jsp:param name="depth" value="1" />
</jsp:include>

<div id="main"><b>Loading...</b></div>

<jsp:include page="/inc/footer.jsp" />

<!--  Javascript -->

<%=MiscInserts.includeJSLibraries(1, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>
<%=MiscInserts.embedTemplates(cspPolicy.nonce)%>
<%=MiscInserts.embedURIPatternMaps(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	bae.initREST(bae.getBaseURL(1), '<%=Common.stamp()%>')

	var pageAssignmentOptions =
	{
		'availableTemplates': SCHEMA_TEMPLATES,
		'branchTemplates': BRANCH_TEMPLATES,		
		'uriPatternMaps': new bae.URIPatternMaps(URI_PATTERN_MAPS)
	};
	
	
	var page = new bae.PageImport(pageAssignmentOptions);
	page.buildContent();
});
</script>

</body>
</html>
