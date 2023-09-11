<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*" %>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response); %>
<!DOCTYPE HTML>
<html>
<head>
	<%=MiscInserts.includeGoogleAnalytics()%>
	<title>BioAssay Express: Holding Bay</title>
	<%=MiscInserts.includeCommonHead(0)%>
	
	<style>
	@media screen and (min-width: 768px) 
	{
		.modal-width
		{
			width: 60%;
		}
	}
	</style>
</head>

<body>
<jsp:include page="/inc/header.jsp">
	<jsp:param name="navtitle" value="Holding Bay" />
	<jsp:param name="depth" value="0" />
</jsp:include>

<div class="content">
	<p id="holdingbay"></p>
</div>

<jsp:include page="/inc/footer.jsp" />

<!-- Javascript -->

<%=MiscInserts.includeJSLibraries(0, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	<jsp:include page="inc/authentication.jsp" />
	
	bae.initREST(bae.getBaseURL(), '<%=Common.stamp()%>')
	bae.Popover.installHiders();
	
	<%=com.cdd.bae.rest.GetHoldingBay.manufactureList(request)%>
	
//	var page = new bae.PageHolding($('#holdingbay'), LIST_HOLDING, LIST_HOLDINGID, LIST_ASSAYID, LIST_UNIQUEID, LIST_SUBMISSIONTIME, LIST_CURATORID, LIST_CURATORNAME, LIST_CURATOREMAIL);
	var page = new bae.PageHolding($('#holdingbay'), LIST_HOLDING);
	page.build();
});
</script>

</body>
</html>