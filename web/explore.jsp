<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*" %>
<%@ page import="org.json.*" %>
<%@ page import="com.cdd.bae.data.*" %>
<%@ page import="com.cdd.bao.template.*" %>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response); %>
<!DOCTYPE html>
<html>
<head>
	<%=MiscInserts.includeGoogleAnalytics()%>
	<title>BioAssay Express: Explore Assays</title>
	<%=MiscInserts.includeCommonHead(0)%>
</head>

<body>
<jsp:include page="/inc/header.jsp">
	<jsp:param name="navtitle" value="Explore Assays" />
	<jsp:param name="depth" value="0" />
</jsp:include>


<p><table width="100%"><tr style="display: flex; align-items: stretch;">
	<td id="template_choice"></td>
	<td style="flex: 1;">
		<div class="keyword-box">
			<input type="text" id="keywordentry" value="" placeholder="find term to search for">
		</div>
	</td>
	<td style="padding: 0.25em 0 0 0.5em; vertical-align: middle;">
		<input type="checkbox" id="chk_uncurated"></input>
		<label for="chk_uncurated" style="font-weight: normal;">Include uncurated assays</label>
	</td>
</tr></table></p>

<p id="info">
	Assay exploration is done by adding layers of filters. Each layer indicates an assignment category and some number of values. The assays that
	have at least one of these values are passed through to the next layer, and so on. The final result selection consists of the assays that
	remain after all the layers of filtering have been applied.
</p>

<p><table id="select" class="data"></table></p>

<p id="results"></p>
<p id="measurement_table"></p>
<p id="group_selection" margin="0.5em 0 0 0"></p>
<p id="property_grid"></p>

<style>
@media screen and (min-width: 768px) 
{
	.modal-width
	{
		width: 60%;
	}
}
</style>

<jsp:include page="/inc/footer.jsp" />

<%=MiscInserts.includeJSLibraries(0, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>
<%=MiscInserts.embedTemplateDescriptions(cspPolicy.nonce)%>
<%=MiscInserts.embedCompoundStatus(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	// defines the template and the initial assay, if any
	<jsp:include page="/servlet/SchemaEntry" />
	
	// setup authentication
	<jsp:include page="inc/authentication.jsp" />

	bae.initREST(bae.getBaseURL(), '<%=Common.stamp()%>')
	bae.Popover.installHiders();
	
	var page = new bae.PageExplore(SCHEMA_DESCRIPTIONS, COMPOUNDS_EXIST);
	page.initializeSelection(searchQuery, template, identifier, keyword, fullText);
	page.populateKeywords($('#keywordentry'));
	
	if (globalKeyword.length > 0) {
		$('#keywordentry').val(globalKeyword[0]);
		page.keyword.changeText(globalKeyword[0]);
	}
});
</script>

</body>
</html>