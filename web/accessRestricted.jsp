<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response); %>
<!DOCTYPE html>
<html>
<head>
	<%=MiscInserts.includeGoogleAnalytics()%>
	<title>BioAssay Express: Access restricted</title>
	<%=MiscInserts.includeCommonHead(0)%>
</head>
<body>
<div class="navigation">
<div><a class="logo" href="."></a></div>
<div class="navtitle">Access restricted</div>
<div class="right"></div>
</div>

<div class="content">
	<h1>Access to restricted page</h1>
	<p>If you require access to the restricted page, contact your administrator.</p>

	<p><a class="btn btn-action" style="color: white;" href="index.jsp">Back to BioAssay Express</a></p>
</div>
<jsp:include page="/inc/footer.jsp" />
</body>
</html>