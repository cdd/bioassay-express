<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*" %>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response); %>
<!DOCTYPE HTML>
<html>
<head>
	<%=MiscInserts.includeGoogleAnalytics()%>
	<title>BioAssay Express: Term Requests</title>
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
<!-- we later will override the message here as needs be -->
<jsp:include page="inc/uimessage.jsp">
	<jsp:param name="show" value="false"/>
	<jsp:param name="style" value="warning"/>
	<jsp:param name="message" value="Notice"/>
</jsp:include>

<jsp:include page="/inc/header.jsp">
	<jsp:param name="navtitle" value="Term Requests" />
	<jsp:param name="depth" value="0" />
</jsp:include>

<div class="content">
	<p id="termRequests"></p>
</div>

<jsp:include page="/inc/footer.jsp" />

<!-- Javascript -->

<%=MiscInserts.includeJSLibraries(0, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>
<%=MiscInserts.embedOntoloBridges(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	$('#bannerAlert span.glyphicon-remove').click(function()
	{
		$('#bannerAlert').css('display', 'none');		
	});

	bae.initREST(bae.getBaseURL(), '<%=Common.stamp()%>');
	bae.Popover.installHiders();

	var page = new bae.PageTermRequests($('#termRequests'));
	page.build();
});
</script>

</body>
</html>