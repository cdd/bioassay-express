<%
	String rootHref = ".";
	if (request.getParameter("depth").equals("1")) rootHref = "..";
%>
<div class="navigation">
	<div><a class="logo" href="<%=rootHref%>"></a></div>
	<div class="navtitle">${param.navtitle}</div>
	<div id="spanLogin">&nbsp;</div>
</div>
