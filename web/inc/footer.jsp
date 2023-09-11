<%@page import="com.cdd.bae.data.*"%>
<%@page import="com.cdd.bae.config.*"%>
<%@ page session="false" %>
<%
InitParams.BuildData buildData = Common.getBuildData();
String branch = buildData.branch;
if (!branch.equals("")) branch = "[" + branch + "]";
%>
<div class="footer">
	<div>&copy; <a href="https://www.collaborativedrug.com/">Collaborative Drug Discovery, Inc.</a></div>
	<% if (!buildData.date.equals("")) { %>
		<div>Build: <%= buildData.date %> <%= branch %></div>
	<% } %>
</div>