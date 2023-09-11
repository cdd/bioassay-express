<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.config.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response); %>
<!DOCTYPE html>
<html>
<head>
	<%=MiscInserts.includeGoogleAnalytics()%>
	<title>BioAssay Express</title>
	<%=MiscInserts.includeCommonHead(0)%>
	<%=MiscInserts.includeJSLibraries(0, cspPolicy.nonce)%>
</head>

<body>
<jsp:include page="inc/uimessage.jsp">
	<jsp:param name="show" value="<%= Common.getParams().uiMessage.show %>"/>
	<jsp:param name="style" value="<%= Common.getParams().uiMessage.style %>"/>
	<jsp:param name="message" value="<%= Common.getParams().uiMessage.message %>"/>
</jsp:include>

<div class="navigation">
	<div><a class="logo" href="."></a></div>
	<div>
		<div class="search">
			<div class="search-box"><input type="text" id="searchValue" value="" placeholder="find by ID"></div>
		</div>
	</div>
	<div id="spanLogin"></div>
</div>

<div class="content columns">
	<div>
		<h2>Main Pages</h2>
		<ul>
			<li class="nowrap"><a href="assign.jsp?edit=true">Assign Assay</a></li>
			<li class="nowrap"><a href="search.jsp">Search Assays</a></li>
			<li class="nowrap"><a href="explore.jsp">Explore Assays</a></li>
			<li class="nowrap"><a href="curated.jsp">Curated Assays</a></li>
			<li class="nowrap <%=com.cdd.bae.util.MiscInserts.showHoldingBay() ? "" : "hide"%>" >
				<a href="holding.jsp">Holding Bay</a><%=com.cdd.bae.util.MiscInserts.holdingBayCount()%>
			</li>
		</ul>

		<h2 class="nowrap">Diagnostic Pages</h2>
		<ul>
			<li class="nowrap <%=Common.getPageToggle().progressReport ? "loginRequired" : "hide"%>">
				<a href="diagnostics/progress.jsp">Progress Report</a>
			</li>

			<li class="nowrap <%=Common.getPageToggle().schemaReport ? "" : "hide"%>">
				<a href="diagnostics/schema.jsp">Schema Report</a>
			</li>

			<li class="nowrap <%=Common.getPageToggle().schemaTemplates ? "" : "hide"%>">
				<a href="diagnostics/template.jsp">Schema Templates</a>
			</li>

			<li class="nowrap <%=Common.getPageToggle().validationReport ? "" : "hide"%>">
				<a href="diagnostics/validation.jsp">Validation Report</a>
			</li>

			<li class="nowrap adminRequired <%=Common.getPageToggle().contentSummary ? "" : "hide"%>">
				<a href="admin/summary.jsp">Content Summary</a>
			</li>

			<li class="nowrap <%=Common.getPageToggle().randomAssay ? "" : "hide"%>">
				<a href="diagnostics/random.jsp">Random Assay Pages</a>
			</li>
		</ul>

		<h2 class="nowrap adminRequired">Admin Pages</h2>
		<ul class="adminRequired">
			<li class="nowrap">
				<a href="admin/import.jsp">Import Data</a>
			</li>
		<%
			boolean canRequestProvisional = Common.getConfiguration().getProvisional().baseURI != null;
			if (canRequestProvisional)
			{
		%>
				<li class="nowrap">
					<a href="provisional.jsp">Term Requests</a><%=com.cdd.bae.util.MiscInserts.provisionalCount()%>
				</li>
		<%
			}
		%>
			<li class="nowrap">
				<a href="admin/userManagement.jsp">User Management</a>
			</li>
			<li class="nowrap">
				<a href="admin/templateEditor.jsp">Template Editor</a>
			</li>
			<li class="nowrap">
				<a href="admin/checkTransliteration.jsp">Check Transliteration</a>
			</li>
			<li class="nowrap">
				<a href="admin/experimental.jsp">Experimental</a>
			</li>
		</ul>

		<h2 class="nowrap">Data Sharing</h2>
		<ul>
			<li class="nowrap"><a href="REST/RDF/all" target="_blank">Semantic Web/REST</a></li>
		</ul>

		<h2 class="nowrap">Documentation</h2>
		<ul>
			<li class="nowrap"><a href="docs/">Overview</a></li>
			<li class="nowrap"><a href="docs/license.jsp">License</a></li>
			<li class="nowrap"><a href="docs/fair.jsp">FAIR</a></li>
		</ul>

		<h2 class="nowrap">Contact</h2>
		For more information about the <i>BioAssay Express</i>, contact us at
		<a href="http://info.collaborativedrug.com/contact-us-collabortive-drug-discovery" target="_blank">
		<b>Collaborative Drug Discovery</b></a>.</div>

	<div>
		<div id="frontload" style="border: 1px solid #96CAFF; box-shadow: 0 0 5px rgba(66,77,88,0.1); padding: 0.5em; margin: 0.5em 0 1em 0;"></div>
		
		<%
			InitParams.ModulePubChem pubchem = Common.getParams().modulePubChem;
			if (pubchem != null && pubchem.userRequests)
			{
		%>
		<div id="requestPubChem" class="adminRequired" style="border: 1px solid #96CAFF; box-shadow: 0 0 5px rgba(66,77,88,0.1); padding: 0.5em; margin: 0.5em 0 1em 0;"></div>
		<%
			}
		%>

		<%=com.cdd.bae.web.CustomResource.embed("index.snippet")%>
	</div>

	<div>
		<!-- <h2>Recently&nbsp;Curated</h2> -->
		<div id="curation_recent"></div>
		<p align="right"><a href="curated.jsp">See more</a>...</p>
	</div>
</div>

<jsp:include page="inc/footer.jsp" />

<%
	String jsonRecent = MiscInserts.recentCuration(8, request).toString();
%>

<!--  Javascript -->

<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	bae.initREST(bae.getBaseURL(), '<%=Common.stamp()%>')

	// setup authentication (requires call to initREST first)
	<jsp:include page="inc/authentication.jsp" />

	bae.Popover.installHiders();

	// fill in appropriate links for the single-shot randoms
	var randomCurated = $('#random_curated'), randomUncurated = $('#random_uncurated');
	if (randomCurated.length > 0) bae.callREST('REST/PickRandomAssay', {'curated': true},
		function(assays)
		{
			if (assays.length > 0) randomCurated.attr('href', 'assign.jsp?assayID=' + assays[0].assayID);
		});
	if (randomUncurated.length > 0) bae.callREST('REST/PickRandomAssay', {'curated': false},
		function(assays)
		{
			if (assays.length > 0) randomUncurated.attr('href', 'assign.jsp?assayID=' + assays[0].assayID);
		});

	const CURATION_RECENT = <%=jsonRecent%>;
	new bae.RecentCuration().populateRecentlyCurated($('#curation_recent'), CURATION_RECENT);

	// quick search for assays
	var findIdent = new bae.FindIdentifier(function(match)
	{
		var url = 'assign.jsp?';
		if (match.uniqueID) url += 'uniqueID=' + encodeURIComponent(match.uniqueID); else url += 'assayID=' + match.assayID;
		document.location.href = url;
	});
	findIdent.install($('#searchValue'));

	new bae.PageFrontLoad($('#frontload'), CURATION_RECENT.all);

	<%
		if (pubchem != null && pubchem.userRequests)
		{
	%>
	new bae.PageRequestPubChem($('#requestPubChem'));
	<%
		}
	%>

	$('#bannerAlert span.glyphicon-remove').click(function()
	{
		$('#bannerAlert').css('display', 'none');
	});
});
</script>
</body>
</html>
