<%@ page import="com.cdd.bao.template.Schema.Assignment"%>
<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="java.util.*"%>
<%@ page import="java.util.stream.*"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.*"%>
<%@ page import="com.cdd.bao.template.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page import="com.cdd.bae.model.*"%>
<%@ page import="com.cdd.bae.model.PredictAnnotations.*"%>
<%@ page import="com.cdd.bae.model.dictionary.*"%>
<%@ page session="false" %>
<%
	DataObject.Assay assay;
	PredictAnnotations.Predictions predictions; 
	Schema schema = Common.getSchemaCAT();
	SchemaVocab schvoc = Common.getSchemaVocab();
	MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response, "..");
	String uniqueID = request.getParameter("uniqueID");
	
	if (uniqueID != null)
	{
		if (uniqueID.equals("random"))
		{
			long[] uniqueIDs = Common.getDataStore().assay().fetchAllCuratedAssayID();
			long assayID = uniqueIDs[(int)(System.currentTimeMillis() % uniqueIDs.length)];
			assay = Common.getDataStore().assay().getAssay(assayID);
		}
		else
		{
			assay = Common.getDataStore().assay().getAssayFromUniqueID(uniqueID);
		}
		schema = Common.getSchema(assay.schemaURI);
		predictions = PredictAnnotations.getPredictions(assay);
	}
	else
	{
		assay = new DataObject.Assay();
		assay.schemaURI = schema.getSchemaPrefix();
		assay.text = request.getParameter("text");
		if (assay.text == null) assay.text = "";
		assay.annotations = new DataObject.Annotation[]{};
		predictions = PredictAnnotations.getPredictions(assay);
	}
%>
<!DOCTYPE html>
<html>
<head>
	<title>BioAssay Express: Models</title>
	<%=MiscInserts.includeCommonHead(1)%>
	<style>
	.match {text-decoration:underline; background-color: coral;}
	.container {height: 800px; width: 800px; display: flex; flex-direction: column;}
	.container textarea {box-sizing: border-box; height: 100%;}
	</style>
</head>
<body>
<table class="navigation"><tr>
	<td align="left" class="navigation"><a class="logo" href=".."></a></td>
	<td align="center" class="navigation navtitle">Models</td>
	<td align="right" class="navigation">
		<div class="search"><div class="search-box"><input type="text" id="searchValue" value="" placeholder="find by ID"></div></div>
	</td>
</tr></table>
<% if (assay.text.trim().isEmpty()) { %>
	<p>No assay information provided. Either search for an assay ID in the search box or paste assay text into the followinig text box.</p>
<% } else { %>
<h1>Assay <%= assay.uniqueID %></h1>
<% for (Assignment assignment : predictions.assignments) {
	Set<String> annotValueURIs = predictions.annotations.get(assignment.propURI);
	StringJoiner joiner = new StringJoiner(", ");
	for (String valueURI : annotValueURIs) joiner.add(schvoc.getTerm(valueURI) == null ? valueURI : schvoc.getTerm(valueURI).label);
	List<String> l;
%>
<p><b><%= assignment.name %>: <%= joiner.toString() %></b> </p>
<ul>
<li>Dictionary: <% l = predictions.get(Method.DICTIONARY).getOrDefault(assignment.propURI, null); if (l != null) for (String s : l) { %>
<span class='<%= annotValueURIs.contains(s) ? "match" : "" %>'><%= schvoc.getTerm(s).label %></span>, 
<% } %></li>
<li>AssociationRules: <% l = predictions.get(Method.ASSOCIATION).getOrDefault(assignment.propURI, null); if (l != null) for (String s : l) { %>
<span class='<%= annotValueURIs.contains(s) ? "match" : "" %>'><%= schvoc.getTerm(s).label %></span>, 
<% } %></li>
<li>NLP: <% l = predictions.get(Method.NLP).getOrDefault(assignment.propURI, null); if (l != null) for (String s : l.subList(0, Math.min(l.size(), 6))) { %>
<span class='<%= annotValueURIs.contains(s) ? "match" : "" %>'><%= schvoc.getTerm(s).label %></span>, 
<% } %></li>
<li>Correlation: <% l = predictions.get(Method.CORRELATION).getOrDefault(assignment.propURI, null); if (l != null) for (String s : l.subList(0, Math.min(l.size(), 6))) { %>
<span class='<%= annotValueURIs.contains(s) ? "match" : "" %>'><%= schvoc.getTerm(s).label %></span>, 
<% } %></li>
</ul>

<% } %>

<% } // end if assay.isEmpty %>
<div class='container'>
<button id='submitAssayText'>Submit</button>
<textarea id='assayText'><%= assay.text %></textarea>
</div>

<jsp:include page="/inc/footer.jsp" />

<%=MiscInserts.includeJSLibraries(1, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	bae.initREST(bae.getBaseURL(1), '<%=Common.stamp()%>');
	// quick search for assays
	var findIdent = new bae.FindIdentifier(function(match)
	{
		var url = 'models.jsp?';
		if (match.uniqueID) url += 'uniqueID=' + encodeURIComponent(match.uniqueID); else url += 'assayID=' + match.assayID;
		document.location.href = url;
	});
	findIdent.install($('#searchValue'));
	
	$('#submitAssayText').click(function() 
	{
		var url = 'models.jsp?text=' + encodeURIComponent($('#assayText').val());
		document.location.href = url;
	});
});
</script>

</body>
</html>