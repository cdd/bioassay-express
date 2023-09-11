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

<h1>FAIR Data Compliance</h1>

<a name="protocol"></a>
<h2>Access Protocol</h2>

<p>
	Assays can be browsed using RDF-style semantic web access, via <tt>{BASE}/REST/RDF/all</tt>, where <tt>BASE</tt> is the base URL, 
	e.g. <tt>http://www.bioassayexpress.com</tt>.
</p>

<p>
	Individual assays can be fetched via the RESTful API, using globally unique IDs, or database-specific assay identifiers:
</p>

<pre>
{BASE}/REST/GetAssay?assayID=101
{BASE}/REST/GetAssay?uniqueID=pubchemAID%3A651671
</pre>

<a name="metadata"></a>
<h2>Metadata Format</h2>

<p>
	Assays are represented using a straightforward JSON-based datastructure:
</p>

<pre>
{
  "assayID": 1,
  "uniqueID": "pubchemAID:346",
  "text: "HIV-1 nucleocapsid protein (HIV-1 NC) is a ... (etc)",
  "schemaURI": "http://www.bioassayontology.org/bas#",
  "annotations": 
  [
    {
      "propURI": "http://www.bioassayontology.org/bao#BAO_0002852",
      "propLabel": "assay sources",
      "propAbbrev": "bao:BAO_0002852",
      "valueURI": "http://www.bioassayexpress.org/sources#SRC_0000001",
      "valueLabel": "Broad Institute (Harvard-MIT)",
      "valueAbbrev": "src:SRC_0000001",
      "valueDescr": "Broad Institute.",
      "groupNest": [],
      "groupLabel": [],
      "externalURLs": ["http://www.broadinstitute.org"],
      "valueHier": 
	  [
        "http://www.bioassayexpress.org/sources#pubchem_sources",
        "http://www.bioassayontology.org/bao#BAO_0002934"
      ],
      "labelHier": 
	  [
        "PubChem Sources",
        "organization"
      ]
    },
	...
  ],
  "curationTime": 1492468125618,
  "curatorID": "admin",
  "history":
  [
    ...
  ]
}
</pre>

<p>Definitions:</p>

<ul>
	<li>assayID: local database identifier for the assay</li>
	<li>uniqueID: globally unique identifier (e.g. PubChem Assay ID)</li>
	<li>text: raw text description of the assay, if available</li>
	<li>schemaURI: unique reference to indicate the template schema for the assay</li>
	<li>annotations: a list of curated terms that describe the assay, including both URI-based values, and text labels</li>
	<li>curationTime/curatorID: most recent activity by a curator</li>
	<li>history: a complete log of all changes (who and what)</li>
</ul>

<a name="longevity"></a>
<h2>Longevity Plan</h2>

<p>
	Public data created via the BioAssay Express is retained on several servers, two of them public (www.bioassayexpress.com and 
	beta.bioassayexpress.com), both of them maintained by Collaborative Drug Discovery, Inc. Annotations that are added to assays
	sourced from PubChem are submitted back to PubChem, using their Classification Tree container. PubChem is committed to providing
	free and open access to their data content in perpetuity.
</p>


<script src="../js/jquery-2.2.4.min.js" type="text/javascript"></script>

<script nonce="<%=cspPolicy.nonce%>">

$('.points').each(function()
{
	var trList = $(this).find('tr'), sz = trList.length;
	for (var n = 0; n < sz; n++)
	{
		$(trList[n]).addClass(n % 2 == 0 ? 'even' : 'odd');
		$(trList[n]).find('td').addClass('point');
	}
});

</script>

</body>
</html>