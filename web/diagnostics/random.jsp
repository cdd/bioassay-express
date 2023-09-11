<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response, ".."); %>
<!DOCTYPE html>
<html>
<head>
	<title>BioAssay Express: Random</title>
	<%=MiscInserts.includeCommonHead(1)%>
</head>

<body>

<p><table width="100%" class="navigation"><tr>
	<td align="left" class="navigation">
		<a class="logo" href=".."></a>
	</td>
	<td class="navigation navtitle">
		Random Assays
	</td>
	<td align="right" class="navigation">
		<button id="reloadAssays" class="btn btn-action"><span class="glyphicon glyphicon-refresh"></span> Reselect</button>
	</td>
</tr></table></p>

<p>
	Assays shown below are selected randomly from content that have yet to be curated. Find an assay that has a description that
	provides text with sufficient description to fill in most of the annotation categories.
</p>

<div id="content">
Loading...
</div>

<jsp:include page="/inc/footer.jsp" />

<%=MiscInserts.includeJSLibraries(1, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	bae.initREST(bae.getBaseURL(1), '<%=Common.stamp()%>')
	bae.Popover.installHiders();

	var page = new bae.PageRandom($('#content'));
	page.build();

	$('#reloadAssays').on('click', function() {page.reloadAssays();});
});
</script>

</body>
</html>