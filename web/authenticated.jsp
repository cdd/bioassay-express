<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response); %>
<!DOCTYPE HTML>
<html>
<head>
	<title>BioAssay Express</title>
	<%=MiscInserts.includeCommonHead(0)%>
</head>

<body>

<p>Authentication in progress</p>

<!--  Javascript -->

<%=MiscInserts.includeJSLibraries(0, cspPolicy.nonce)%>

<% com.cdd.bae.util.LoginSupport login = new LoginSupport(request); %>
<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	var details = <%=login.obtainToken()%>;
	parent.localStorage.setItem('oauth', JSON.stringify(details));
	window.close();
});
</script>
</body>
</html>
