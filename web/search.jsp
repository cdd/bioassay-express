<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="org.json.*" %>
<%@ page import="com.cdd.bae.data.*" %>
<%@ page import="com.cdd.bao.template.*" %>
<%@ page import="com.cdd.bae.util.*" %>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response, ".."); %>
<!DOCTYPE html>
<html>

<head>
	<%=MiscInserts.includeGoogleAnalytics()%>
	<title>BioAssay Express: Search</title>
	<%=MiscInserts.includeCommonHead(0)%>
</head>
	
<body>

<style>
@media screen and (min-width: 768px) 
{
	.modal-width
	{
		width: 80%;
	}
}
</style>
<jsp:include page="/inc/header.jsp">
	<jsp:param name="navtitle" value="Search Assays" />
	<jsp:param name="depth" value="0" />
</jsp:include>

<p id="actionLine"></p>

<p><table>
<tr>
	<th class="coltitle" style="text-align: left;">Available Terms</th>
	<td colspan="2"></td>
	<th class="coltitle" style="text-align: left;">Selected Terms</th>
</tr>
<tr>
	<td id="termsAvail" valign="top">
		Loading...
	</td>
	<td class="vborder"> </td>
	<td class="vpadding"> </td>
	<td id="termsUsed" valign="top">
		None selected.
	</td>
</tr>
</table></p>

<p id="searchResults"></p>
<p id="measurement_table"></p>
<p id="group_selection" margin="0.5em 0 0 0"></p>
<p id="property_grid"></p>

<jsp:include page="/inc/footer.jsp" />

<%
	JSONArray termList = new JSONArray();
	String schemaURI = request.getParameter("schema");
	Schema schema = schemaURI == null || schemaURI.length() == 0 ? Common.getSchemaCAT() : Common.getSchema(schemaURI);
	
	for (int n = 0; ; n++)
	{
		String propURI = request.getParameter("p" + n), valueURI = request.getParameter("v" + n);
		if (propURI == null || valueURI == null) break;
		String groupAbbrev = request.getParameter("g" + n);
		String[] groupNest = groupAbbrev != null ? groupAbbrev.split("::") : new String[0];
		for (int i = 0; i < groupNest.length; i++) groupNest[i] = ModelSchema.expandPrefix(groupNest[i]);
				
		// can be raw URI or use internal prefixes (saves space)
		propURI = ModelSchema.expandPrefix(propURI);
		valueURI = ModelSchema.expandPrefix(valueURI);
		
		Schema.Assignment[] assn = schema.findAssignmentByProperty(propURI, groupNest); // (note: rather assumes that it's unique)
		if (assn.length == 0) continue;
		
		JSONObject term = new JSONObject();
		
		term.put("propURI", propURI);
		term.put("propAbbrev", ModelSchema.collapsePrefix(propURI));
		term.put("propLabel", assn[0].name);
		term.put("propDescr", assn[0].descr);
		term.put("valueURI", valueURI);
		term.put("valueAbbrev", ModelSchema.collapsePrefix(valueURI));
		term.put("valueLabel", Common.getOntoValues().getLabel(valueURI));
		term.put("valueDescr", Common.getOntoValues().getDescr(valueURI));
		term.put("groupNest", assn[0].groupNest());
		term.put("groupLabel", assn[0].groupLabel());
		
		termList.put(term);
	}
	
	String content = "[]";
	if (termList.length() > 0) content = termList.toString();
%>

<%=MiscInserts.includeJSLibraries(0, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>
<%=MiscInserts.embedTemplateDescriptions(cspPolicy.nonce)%>
<%=MiscInserts.embedCompoundStatus(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	// setup authentication
	<jsp:include page="inc/authentication.jsp" />

	bae.initREST(bae.getBaseURL(), '<%=Common.stamp()%>')
	bae.Popover.installHiders();

	var page = new bae.PageSearch("<%=schema.getSchemaPrefix()%>", <%=content%>, SCHEMA_DESCRIPTIONS, COMPOUNDS_EXIST);
	page.render();
});
</script>

</body>
</html>