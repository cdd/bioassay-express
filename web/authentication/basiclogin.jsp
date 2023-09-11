<%@page import="com.cdd.bae.config.authentication.Access.AccessType"%>
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

	final String RESET_PASSWORD = "If you need to reset your password, contact a BioAssay Express administrator.";
	final String[] modes = new String[]{"login", "change password", "register", "reset"};
	//final String[] modes = new String[]{"login", "change password", "reset"};
	String mode = request.getParameter("mode");
	if (mode == null) mode = request.getParameter("resetkey") == null ? modes[0] : "reset";

	LoginSupport loginSupport = null;
	Authentication.Session session = null;
	Authentication authentication = Common.getAuthentication();
	String error = "";

	String username = Util.safeString(request.getParameter("username")).trim();
	String password = Util.safeString(request.getParameter("password")).trim();
	String newpassword1 = Util.safeString(request.getParameter("newpassword1")).trim();
	String newpassword2 = Util.safeString(request.getParameter("newpassword2")).trim();
	String resetkey = Util.safeString(request.getParameter("resetkey")).trim();
	String resetsent = Util.safeString(request.getParameter("resetsent")).trim();
	String email = Util.safeString(request.getParameter("email")).trim();
	boolean showResetKeyInput = false;
	boolean validNewpassword = newpassword1.length() >= 10 && newpassword1.equals(newpassword2);
	
	if (username.isEmpty() && !mode.equals("reset")) { /* initial page load */ }
	else if (mode.equals("login"))
	{
		session = BasicAuthentication.attemptBasicAuthentication(username, password);
		if (session == null)
		{
			if (BasicAuthentication.findUser(username) == null)
				error = "Username not known. Do you want to register?";
				//error = "Username not known. Contact bha@collaborativedrug.com for registration or use Google/Orcid instead";
			else
				error = "Incorrect password. " + RESET_PASSWORD;
		}
	}
	else if (mode.equals("change password"))
	{
		if (validNewpassword)
		{
			if (BasicAuthentication.attemptChangePassword(username, password, newpassword1))
				session = BasicAuthentication.attemptBasicAuthentication(username, newpassword1);
			else
				error = "Incorrect user credentials. " + RESET_PASSWORD;

		}
		else
		{
			error = "Password invalid";
		}
	}
	else if (mode.equals("register"))
	{
		DataStore.User user = BasicAuthentication.findUser(username);
		if (user == null && validNewpassword)
		{
			BasicAuthentication auth = (BasicAuthentication)authentication.getAccessList(AccessType.BASIC)[0];
			auth.registerUser(username, newpassword1, email);
			session = BasicAuthentication.attemptBasicAuthentication(username, newpassword1);
		}
		else if (user != null)
		{
			error = "Username already in use. " + RESET_PASSWORD;
		}
		else
		{
			error = "Password invalid";
		}
	}
	else if (mode.equals("reset"))
	{
		showResetKeyInput = !resetkey.isEmpty() || !resetsent.isEmpty();
		if (!resetsent.isEmpty())  // reset key requested or change submitted
		{
			if (validNewpassword)
			{
				try
				{
					String curatorID = BasicAuthentication.attemptResetPasswordEmail(resetkey, newpassword1);
					session = BasicAuthentication.attemptBasicAuthentication(curatorID, newpassword1);
				}
				catch (Exception e)
				{
					System.out.println(e.toString());
					error = "Reset key no longer valid. " + RESET_PASSWORD;
					showResetKeyInput = false;
				}
			}
			else
			{
				DataStore.User user = BasicAuthentication.findUser(username);
				BasicAuthentication auth = (BasicAuthentication)authentication.getAccessList(AccessType.BASIC)[0];
				auth.sendResetPasswordEmail(user);
				
				error = "Reset email sent, please add together with new passwords";
			}
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
	<form action='./basiclogin.jsp' onsubmit='preSubmitHook()' accept-charset='UTF-8' method='post'>
		<input type='hidden' name='state' value='<%=request.getParameter("state")%>' />
		<input type='hidden' name='resetsent' id='resetsent' value='<%=request.getParameter("resetsent")%>' />
		<div class='login_container'>
			<div id='loginheader' class='center'><img src='../images/logo-blue.svg'/></div>
			<div id='modeselector' class='center'>
				<% for (String option : modes) { %>
					<input type='radio' name='mode' id='<%=option%>' value='<%=option%>' <% if (option.equals(mode)) { %>checked<% } %>>
					<label for='<%=option%>'><%=option%></label>
				<% } %>
			</div>
			<input class='input_text center' type='text' name='username' id='username' value='<%=username%>' placeholder='Username' />
			<input class='input_text center' type='text' name='email' id='email' value='<%=email%>' placeholder='Email' />
			<input class='input_text center' type='password' name='password' id='password' placeholder='Password' autocomplete='off' />
			<input class='input_text center' type='text' name='resetkey' id='resetkey' value='<%=resetkey%>' placeholder='Enter reset key from email' autocomplete='off' />
			<input class='input_text center' type='password' name='newpassword1' id='newpassword1' placeholder='New password' autocomplete='off' />
			<input class='input_text center' type='password' name='newpassword2' id='newpassword2' placeholder='Repeat new password' autocomplete='off' />
			<input class='buttony center' type='submit' name='commit' value='Log in' id='login_submit' />

			<div class='error' id='errorMain'><%=error%></div>
			<div class='error right' id='error1'></div>
			<div class='error right' id='error2'></div>
		</div>
	</form>

	<script nonce='<%=cspPolicy.nonce%>'>
		// ui elements
		const username = document.getElementById('username');
		const password = document.getElementById('password');
		const newpassword1 = document.getElementById('newpassword1');
		const newpassword2 = document.getElementById('newpassword2');
		const error1 = document.getElementById('error1');
		const error2 = document.getElementById('error2');
		const submit = document.getElementById('login_submit');
		const email = document.getElementById('email');
		
		const resetPasswordMode = <%=showResetKeyInput ? "true" : "false"%>;

		function setInputDisplay(pwDisplay, newDisplay, rkDisplay)
		{
			password.style.display = pwDisplay;
			newpassword1.style.display = newDisplay;
			newpassword2.style.display = newDisplay;
			error1.style.display = newDisplay;
			error2.style.display = newDisplay;
			resetkey.style.display = rkDisplay || 'none';
			email.style.display = 'none';
		}
		
		function preSubmitHook() 
		{
			const mode = document.querySelector('input[name="mode"]:checked').value;
			if (mode == 'reset') document.getElementById('resetsent').value = 'sent';
		}

		// validators
		const invalidUsername = () => username.value.trim() == '';
		const invalidCredentials = () => invalidUsername() || password.value.trim() == '';
		const validCredentials = () => username.value.trim() != '' && password.value.trim() == '';
		const invalidNewPasswordString = () => newpassword1.value.trim().length < 10;
		const invalidNewPassword = () => invalidNewPasswordString() || newpassword1.value.trim() != newpassword2.value.trim();
		const invalidForm = () => invalidCredentials() || (wantChangePassword() && invalidNewPassword());
		const invalidResetForm = () => invalidNewPassword || resetkey.value.trim() == '';

		function updateForm()
		{
			const mode = document.querySelector('input[name="mode"]:checked').value;
			let disableSubmit = true;
			if (mode == 'login')
			{
				setInputDisplay('block', 'none');
				password.placeholder = 'Password';
				submit.value = 'Login';
				disableSubmit = invalidCredentials();
			}
			else if (mode == 'change password')
			{
				setInputDisplay('block', 'block');
				password.placeholder = 'Old password';
				newpassword1.placeholder = 'New password';
				newpassword2.placeholder = 'Confirm new password';
				submit.value = 'Change password and login';
				disableSubmit = invalidCredentials() || invalidNewPassword();
			}
			else if (mode == 'register')
			{
				setInputDisplay('none', 'block');
				email.style.display = 'block';
				newpassword1.placeholder = 'Password';
				newpassword2.placeholder = 'Confirm password';
				submit.value = 'Register and login';
				disableSubmit = invalidUsername() || invalidNewPassword();
			}
			else if (mode == 'reset')
			{
				if (resetPasswordMode)
				{
					username.style.display = 'none';
					setInputDisplay('none', 'block', 'none');
					submit.value = 'Change password';
					disableSubmit = invalidNewPassword();
				}
				else
				{
					username.style.display = 'block';
					setInputDisplay('none', 'none', 'none');
					submit.value = 'Send reset email';
					disableSubmit = invalidUsername();
					
				}
			}

			let msg = invalidNewPasswordString() ? 'password must have 10 characters or more' : '';
			if (newpassword1.value.trim() == '') msg = '';
			error1.innerHTML = msg;

			msg = 'repeated password must be identical';
			if (invalidNewPasswordString() || newpassword2.value.trim() == '' || !invalidNewPassword()) msg = '';
			error2.innerHTML = msg;

			login_submit.disabled = disableSubmit;
		}

		// initialization
		updateForm();
		username.focus();

		// events that will update the form
		document.querySelectorAll('input[name="mode"]').forEach((o) => o.addEventListener('change', updateForm));
		username.addEventListener('keyup', updateForm);
		password.addEventListener('keyup', updateForm);
		newpassword1.addEventListener('keyup', updateForm);
		newpassword2.addEventListener('keyup', updateForm);
	</script>
<% } %>
</body>
</html>
