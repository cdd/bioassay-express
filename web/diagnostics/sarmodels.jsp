<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response, ".."); %>
<!DOCTYPE html>
<html>
<head>
	<title>BioAssay Express: SAR Models</title>
	<%=MiscInserts.includeCommonHead(1)%>
</head>

<body>

<p><table width="100%" class="navigation"><tr>
	<td align="left" class="navigation">
		<a class="logo" href=".."></a>
	</td>
	<td align="center" class="navigation navtitle">
		Structure-Activity Relationship Models
	</td>
</tr></table></p>

<p id="intro" style="display: none;">
	Structure-activity models are built by combining molecule &amp; assay fingerprints and training a Bayesian model against
	the activity status reported in the <i>PubChem</i> database. Chemical structures are converted into fingerprints using the
	ECFP6 scheme, and assays by using each of the annotations (present/absent) as a fingerprint. This method allows many different
	assay protocols to be combined into a single model. Making a prediction using one of these models requires the compound <i>and</i> 
	the assay to be provided. The influence of other assays within the model depends on the similarity of the assays as well as the
	structures.
</p>

<div id="main"><b>Loading...</b></div>

<jsp:include page="/inc/footer.jsp" />

<%=MiscInserts.includeJSLibraries(1, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	bae.initREST(bae.getBaseURL(1), '<%=Common.stamp()%>')
	bae.Popover.installHiders();

	var annotCode = <%=MiscInserts.parseModelCodeParameter(request.getParameter("annots"))%>;
	var predictCode = <%=MiscInserts.parseModelCodeParameter(request.getParameter("predict"))%>;

	var page = new bae.PageSARModels();
	if (annotCode != null) page.buildAnnotations(annotCode);
	else if (predictCode != null) page.buildPredictions(predictCode);
	else page.buildList();
});
</script>
</body>
</html>