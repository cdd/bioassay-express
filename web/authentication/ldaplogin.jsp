<%@ page language="java" contentType="text/html; charset=UTF-8"
	pageEncoding="UTF-8"%>
<%@ page import="java.util.Map"%>
<%@ page import="com.cdd.bae.config.authentication.*"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bao.util.*"%>
<%@ page import="com.cdd.bae.data.*"%>
<%@ page session="false"%>
<%
	MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response);
	Map<String, String[]> parameters = request.getParameterMap();
	
	final String[] modes = new String[]{"login"};
	String mode = "login"; 
	
	LoginSupport loginSupport = null;
	Authentication.Session session = null;
	Authentication authentication = Common.getAuthentication();
	String error = "";
	
	String username = Util.safeString(request.getParameter("username")).trim();
	String password = Util.safeString(request.getParameter("password")).trim();

	if (username.isEmpty()) { /* initial page load */ }
	else if (mode.equals("login"))
	{
		session = LDAPAuthentication.attemptLDAPAuthentication(username, password);
		if (session == null) 
		{
			if (BasicAuthentication.findUser(username) == null)
				error = "Username not known. Do you want to register?";
			else
				error = "Incorrect password.";
		}
	}
	if (session != null) loginSupport = new LoginSupport(request);
%>
<!DOCTYPE html>
<html>
<head>
<%=MiscInserts.includeGoogleAnalytics()%>
<title>BioAssay Express</title>
<%=MiscInserts.includeCommonHead(1)%>
<link href='../css/login.css' rel='stylesheet'>
<%=MiscInserts.includeJSLibraries(1, cspPolicy.nonce)%>
</head>

<body>
<% if (loginSupport != null) { %>
	<p>Successfully logged in</p>
	<script nonce='<%=cspPolicy.nonce%>'>
		var details = <%=loginSupport.obtainTokenJSON(session, request.getParameter("state")).toString()%>;
		parent.localStorage.setItem('oauth', JSON.stringify(details));
		window.close();
	</script>
<% } else { %>
	<form action='<%=request.getRequestURL()%>' accept-charset='UTF-8' method='post'>
		<input type='hidden' name='state' value='<%=request.getParameter("state")%>' />
		<div class='login_container'>
			<div id='loginheader' class='center'><img src='../images/logo-blue.svg'/></div>
			<input type='hidden' name='mode' value='<%=mode%>'\>
			<input class='input_text center' type='text' name='username' id='username' value='<%=username%>' placeholder='Username' />
			<input class='input_text center' type='password' name='password' id='password' placeholder='Password' autocomplete='off' />
			<input class='buttony center' type='submit' name='commit' value='Log in' id='login_submit' />
			
			<div class='error' id='errorMain'><%=error%></div>
		</div>
	</form>

	<script nonce='<%=cspPolicy.nonce%>'>
		// ui elements
		const username = document.getElementById('username');
		const password = document.getElementById('password');
		const submit = document.getElementById('login_submit');
		
		function setInputDisplay(pwDisplay, newDisplay)
		{
			password.style.display = pwDisplay;
		}

		// validators
		const invalidUsername = () => username.value.trim() == '';
		const invalidCredentials = () => invalidUsername() || password.value.trim() == '';
		const validCredentials = () => username.value.trim() != '' && password.value.trim() == '';
		const invalidForm = () => invalidCredentials();
		
		function updateForm()
		{
			const mode = document.querySelector('input[name="mode"]').value;
			let disableSubmit = true;
			if (mode == 'login') 
			{
				setInputDisplay('block', 'none');
				password.placeholder = 'Password';
				submit.value = 'Login';
				disableSubmit = invalidCredentials();
			}
			
			login_submit.disabled = disableSubmit;
		}
		
		// initialization
		updateForm();
		username.focus();

		// events that will update the form
		username.addEventListener('keyup', updateForm);
		password.addEventListener('keyup', updateForm);
	</script>
<% } %>
</body>
</html>
