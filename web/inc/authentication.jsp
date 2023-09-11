<%
	com.cdd.bae.util.LoginSupport login = new com.cdd.bae.util.LoginSupport(request);
	if (!login.isLoggedIn()) out.println("bae.Authentication.removeSession();"); // commonly happens when server is restarted
%>

var AUTHENTICATION_SERVICES = <%=login.serializeServices().toString(2)%>;
<% if (request.getParameter("resetkey") == null) { %>
	new bae.Authentication(AUTHENTICATION_SERVICES, $('#spanLogin'));
<% } else { %>
	new bae.Authentication(AUTHENTICATION_SERVICES, $('#spanLogin'), '<%=request.getParameter("resetkey")%>');
<% } %>

