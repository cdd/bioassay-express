<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response, ".."); %>
<!DOCTYPE html>
<html>
<head>
	<title>BioAssay Express: Experimental</title>
	<%=MiscInserts.includeCommonHead(1)%>
</head>

<body>
<jsp:include page="/inc/header.jsp">
	<jsp:param name="navtitle" value="Experimental" />
	<jsp:param name="depth" value="1" />
</jsp:include>

<h1>Export associative rules</h1>
<div>
<form id="dar" action="../REST/DownloadAssociationRules" method="get">
	<div>
		<label for="minSupport">Minimum support (default 5): </label>
		<input class="darInput" type="text" name="minSupport" id="minSupport">
	</div>
	<div>
		<label for="minConfidence">Minimum confidence (default 0.8): </label>
		<input class="darInput" type="text" name="minConfidence" id="minConfidence">
	</div>
	<div>
		<label for="addLabels">Add description: </label>
		<input class="darInput" type="checkbox" name="addLabels" id="addLabels" value="checked" checked="checked" >
	</div>
	<div>
		<input type="submit" value="Download">
	</div>
</form>
</div>

<jsp:include page="/inc/footer.jsp" />

<%=MiscInserts.includeJSLibraries(1, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>
<%=MiscInserts.embedTemplates(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	bae.initREST(bae.getBaseURL(1), '<%=Common.stamp()%>');
	
	$('#dar').submit(function()
		{
			$("input[type='submit']", this)
				.val("Please Wait...")
				.prop('disabled', true);
			return true;
		 }
	);
	
	$('.darInput').change(function() 
		{
			$("#dar input[type='submit']")
				.val("Download")
				.prop('disabled', false);
		}
	);
	
});
</script>
</body>
</html>
