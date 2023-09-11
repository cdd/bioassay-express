<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response); %>
<!DOCTYPE HTML>
<html>
<head>
	<%=MiscInserts.includeGoogleAnalytics()%>
	<title>Assign: CDD BioAssay Express</title>
	<%=MiscInserts.includeCommonHead(0)%>

	<style>
	@media screen and (min-width: 768px)
	{
		.modal-width
		{
			width: 80%;
		}
	}
	</style>
</head>

<body>
<jsp:include page="inc/uimessage.jsp">
	<jsp:param name="show" value="false"/>
	<jsp:param name="style" value="warning"/>
	<jsp:param name="message" value=""/>
</jsp:include>


<div class="navigation">
	<div><a class="logo" href="."></a></div>
	<div class="navtitle">Assign Assay</div>
	<div id="topButtons"></div>
	<div id="topSearch"></div>
	<div id="spanLogin"></div>
</div>

<div id="keywordBar"></div>

<div id="identityBar" class="flexbar" style="margin: 0.5em 0 0.5em 0;"></div>

<div id="mainEntry"></div>

<div id="bottomButtons" class="flexbuttons" style="margin-top: 0.5em;"></div>

<div id="variousXref" style="margin: 0.5em 0 0.5em 0;"></div>
<div id="annotationHistory" style="margin: 0.5em 0 0.5em 0;"></div>
<div id="pubchemWidget" style="margin: 0.5em 0 0.5em 0;"></div>
<div id="measurementTable" style="margin: 0.5em 0 0.5em 0;"></div>

<jsp:include page="/inc/footer.jsp" />

<!-- JavaScript -->

<%=MiscInserts.includeJSLibraries(0, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>
<%=MiscInserts.embedTemplates(cspPolicy.nonce)%>
<%=MiscInserts.embedCompoundStatus(cspPolicy.nonce)%>
<%=MiscInserts.embedOntoloBridges(cspPolicy.nonce)%>
<%=MiscInserts.embedURIPatternMaps(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	// defines the template and the initial assay, if any
	<jsp:include page="/servlet/SchemaEntry" />

	// setup authentication
	<jsp:include page="inc/authentication.jsp" />

	bae.initREST(bae.getBaseURL(), '<%=Common.stamp()%>')
	bae.Popover.installHiders();

	var pageAssignmentOptions =
	{
		'entryForms': entryForms,
		'availableTemplates': SCHEMA_TEMPLATES,
		'branchTemplates': BRANCH_TEMPLATES,
		'uriPatternMaps': new bae.URIPatternMaps(URI_PATTERN_MAPS),
		'editMode': forceEdit,
		'canRequestProvisionals': canRequestProvisionals,
		'holdingBaySubmit': holdingBaySubmit,
		'holdingBayDelete': holdingBayDelete,
		'absenceTerms': absenceTerms,
	};

	var page = new bae.PageAssignment(schema, assay, pageAssignmentOptions);
	if (COMPOUNDS_EXIST) page.setupMeasurementTable();
	page.finishSetup();
});
</script>

</body>
</html>
