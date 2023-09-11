<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response); %>
<!DOCTYPE html>
<html>
<head>
	<%=MiscInserts.includeGoogleAnalytics()%>
	<title>BioAssay Express: Bulk Remapping</title>
	<%=MiscInserts.includeCommonHead(0)%>
</head>

<body>

<style>
@media screen and (min-width: 768px) 
{
    .modal-width
    {
        width: 80%;
    }
}
</style>

<div class="navigation">
	<div><a class="logo" href="."></a></div>
	<div class="navtitle">Bulk Remapping</div>
	<div id="spanLogin"></div>
</div>

<style>
@media screen and (min-width: 768px) 
{
    .modal-width
    {
        width: 60%;
    }
}
</style>

<p id="content"></p>

<jsp:include page="/inc/footer.jsp" />

<%
	String paramAssayList = MiscInserts.parseAssayParameter(request.getParameter("assays"));
	String paramQuery = MiscInserts.parseQueryParameter(request.getParameter("query"));
	String schemaDef = MiscInserts.parseSchemaParameter(request.getParameter("schema"));
%>

<%=MiscInserts.includeJSLibraries(0, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	// setup authentication
	<jsp:include page="inc/authentication.jsp" />
	
	bae.initREST(bae.getBaseURL(), '<%=Common.stamp()%>');
	bae.Popover.installHiders();
	
	var page = new bae.PageBulkMap(<%=paramAssayList%>, <%=paramQuery%>, <%=schemaDef%>);
	page.build();
});
</script>

</body>
</html>