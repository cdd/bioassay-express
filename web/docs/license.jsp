<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response, ".."); %>
<!DOCTYPE html>
<html>
<head>
<title>BioAssay Express</title>
<%=MiscInserts.includeCommonHead(1)%>

<style>

h1
{
	margin-top: 1em;
	font-size: 1.7em;
	color: #1362B3;
}

h2
{
	font-size: 1.4em;
}

table.points
{
	border: 1px solid black;
	border-collapse: 5px;
	box-shadow: 0 0 5px rgba(66,88,77,0.5);
	max-width: 50em;
}

b
{
	color: #134293;
}

tr.even
{
	background-color: #F8F8F8;
}

tr.odd
{
	background-color: #E6EDF2;
}

td.point
{
	padding: 0.5em;
	text-align: left;
}

</style>

</head>

<body>
<jsp:include page="/inc/header.jsp">
	<jsp:param name="depth" value="1" />
</jsp:include>

<p id="headerContainer">
	The <b>BioAssay Express</b> is &copy; 
	<a href="http://www.collaborativedrug.com">Collaborative Drug Discovery, Inc.</a>
	and made available via the 
	<a href="http://www.apache.org/licenses/LICENSE-2.0">Apache 2</a> license.
</p>

<p>
	Other libraries are used with acknowledgment:
</p>

<ul>
	<li><a href="https://getbootstrap.com" target="_blank">Bootstrap</a></li>
	<li><a href="https://github.com/cdk" target="_blank">Chemical Development Kit</a> (CDK)</li>
	<li><a href="https://jena.apache.org" target="_blank">Jena</a></li>
	<li><a href="https://jquery.org" target="_blank">jQuery</a></li>
	<li><a href="https://mongodb.github.io/mongo-java-driver" target="_blank">MongoDB Client</a></li>
	<li><a href="https://commons.apache.org/" target="_blank">Apache Commons</a></li>
	<li><a href="https://xmlbeans.apache.org/" target="_blank">Apache XMLBeans</a></li>
	<li><a href="https://poi.apache.org" target="_blank">POI</a></li>
	<li><a href="https://opennlp.apache.org" target="_blank">OpenNLP</a></li>
	<li><a href="https://tomcat.apache.org" target="_blank">Tomcat</a></li>
	<li><a href="https://github.com/Microsoft/TypeScript" target="_blank">TypeScript</a></li>
	<li><a href="https://github.com/hankcs/AhoCorasickDoubleArrayTrie" target="_blank">AhoCorasickDoubleArrayTrie Project</a></li>
	<li><a href="https://github.com/google/diff-match-patch" target="_blank">Diff Match and Patch</a></li>
</ul>

<jsp:include page="/inc/footer.jsp" />

</body>
</html>
