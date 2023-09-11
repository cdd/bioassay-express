<%@page import="com.cdd.bae.util.BoilerplateAnalysis.*"%>
<%@page import="java.util.*"%>
<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.config.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page import="com.cdd.bao.template.*"%>
<%@ page session="false"%>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response, ".."); %>
<!DOCTYPE html>
<html>
<head>
<title>BioAssay Express: Check transliteration</title>
<%=MiscInserts.includeCommonHead(1)%>
</head>

<body>
<jsp:include page="/inc/header.jsp">
	<jsp:param name="navtitle" value="Check transliteration" />
	<jsp:param name="depth" value="1" />
</jsp:include>

<% 
Transliteration transliteration = Common.getTransliteration();
for (Schema schema : Common.getAllSchemata()) { 
	String schemaURI = schema.getSchemaPrefix();
}
%>

<textarea id="editor" rows="10" style="width:100%"></textarea>
<div id="cursorposition"></div>

<div id="translations"></div>

<jsp:include page="/inc/footer.jsp" />

<%=MiscInserts.includeJSLibraries(1, cspPolicy.nonce)%>
<%=MiscInserts.embedIdentifiers(cspPolicy.nonce)%>
<%=MiscInserts.embedTemplates(cspPolicy.nonce)%>

<script nonce="<%=cspPolicy.nonce%>">
$(document).ready(function()
{
	bae.initREST(bae.getBaseURL(1), '<%=Common.stamp()%>');
	
	let lastValue;
	$('#editor').on('change keyup paste', (e) => 
	{
		let newValue = $('#editor').val();
		if (newValue == lastValue) return;
		lastValue = newValue;
		let div = $("#translations");
		div.empty();
		
		bae.callREST('REST/admin/EnumerateTransliteration', {'template': newValue}, (data) =>
		{
			for (let result of data.results)
			{
				div.append($('<hr/>'));
				for (let combination of result.combinations)
				{
					$('<div/>').appendTo(div).append(combination).css('color', 'grey');
				}
				$('<div/>').appendTo(div).append(result.text);
			}
		},
		(data) => 
		{
			div.text(data.responseJSON.developerMessage);
		});
	});
	$('#editor').on('keyup', (e) => 
	{
		let textarea = $('#editor')[0];
		let cursor = textarea.selectionStart;
		let linesToCursor = textarea.value.substr(0, textarea.selectionStart).split("\n");
		let nrow = 'row: ' + linesToCursor.length;
		let ncol = ' col: ' + linesToCursor.slice(-1)[0].length;
		console.log({cursor, linesToCursor, nrow, ncol});
		console.log(`row: ${nrow.toString()}, col: ${ncol.toString()}`);
		$('#cursorposition').empty().append(nrow + ncol);
	});
	
});
</script>
</body>
</html>
