<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@page import="com.cdd.bae.data.*"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page session="false"%>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response); %>
<!DOCTYPE html>
<html>
<head>
<%=MiscInserts.includeGoogleAnalytics()%>
<title>BioAssay Express</title>
<%=MiscInserts.includeCommonHead(1)%>
<link href='../css/login.css' rel='stylesheet'>
<style>
	h1 { margin-top: 1em; font-size: 1.7em; color: #1362B3; }
	b { color: #134293; }
</style>
</head>

<body>
<jsp:include page="/inc/header.jsp">
	<jsp:param name="navtitle" value="Template Editor" />
	<jsp:param name="depth" value="1" />
</jsp:include>

<div id='main' style="margin-bottom: 1em;"><b>Loading</b></div>

<jsp:include page="/inc/footer.jsp" />

<%=MiscInserts.includeJSLibraries(1, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>

<script nonce='<%=cspPolicy.nonce%>'>
	$(document).ready(function()
	{
		bae.initREST(bae.getBaseURL(1), '<%=Common.stamp()%>')
		bae.Popover.installHiders();

		var page = new bae.PageTemplateEditor();
	});
</script>
</body>
</html>
