<%@ page import="com.cdd.bae.util.*" %>
<%@ page import="org.apache.commons.text.* "%>
<%
	MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response, "..");
	String message = request.getParameter("message");
	if (message == null) message = "";
	message = StringEscapeUtils.escapeHtml4(message);
	String dpyStyle = Boolean.parseBoolean(request.getParameter("show")) ? "block" : "none";
%>
<div id="bannerAlert" class="alert alert-<%= request.getParameter("style") %>" style="display:<%=dpyStyle %>">
	<table style="width:100%">
		<tr>
			<td align="left">
				<span id="bannerMessage">
					<%= message %>
				</span>
			</td>
			<td align="right">
				<span class="glyphicon glyphicon-remove" style="cursor: pointer;"></span>
			</td>
		</tr>
	</table>
</div>

