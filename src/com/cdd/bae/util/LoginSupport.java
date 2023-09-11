/*
	BioAssay Express (BAE)

	Copyright 2016-2023 Collaborative Drug Discovery, Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package com.cdd.bae.util;

import com.cdd.bae.config.authentication.*;
import com.cdd.bae.config.authentication.Access.*;
import com.cdd.bae.data.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.net.*;

import javax.servlet.http.*;

import org.slf4j.*;
import org.json.*;

/*
	Assistance for JSPs to present the "login" vs. "logged in" status.
*/

public class LoginSupport
{
	private static final Logger logger = LoggerFactory.getLogger(LoginSupport.class);
	private HttpServletRequest request;

	// ------------ public methods ------------

	public LoginSupport(HttpServletRequest request)
	{
		this.request = request;
	}
	
	public JSONArray serializeServices()
	{
		JSONArray json = new JSONArray();
		for (Access access : Common.getAuthentication().getAccessList())
			json.put(access.serializeFrontend());
		return json;
	}

	public boolean isLoggedIn()
	{
		return currentSession() != null;
	}

	public boolean isAdministrator()
	{
		return isLoggedIn() && currentSession().isAdministrator();
	}

	// digs through the cookies and tries to reconcile them with a current login; returns null if anything is out of place
	public Authentication.Session currentSession()
	{
		// poll the cookies, and see if there's a positive match to a session: the token must also match
		String curatorID = null, serviceName = null, accessToken = null;
		if (request.getCookies() != null) for (Cookie c : request.getCookies())
		{
			try
			{
				String k = c.getName(), v = URLDecoder.decode(c.getValue(), Util.UTF8);
				if (k.equals("curatorID")) curatorID = v;
				else if (k.equals("serviceName")) serviceName = v;
				else if (k.equals("accessToken")) accessToken = v;
			}
			catch (UnsupportedEncodingException ex) { /* ignore */ }
		}
		
		// special deal: if indicating one of the trusted types, there's no need to identify an existing session: make one up
		Access trusted = Common.getAuthentication().getAccess(serviceName);
		if (AccessType.TRUSTED.implementedBy(trusted))
		{
			try {return Common.getAuthentication().authenticateSession(serviceName, null, null);}
			catch (Exception ex) {ex.printStackTrace(); return null;}
		}
		
		// a previously prepared session must exist, and the access token must match
		if (curatorID == null) return null;
		Authentication.Session session = Common.getAuthentication().getSession(curatorID);
		if (session == null) return null;
		if (!session.serviceName.equals(serviceName) || !session.accessToken.equals(accessToken)) session = null;
		return session;
	}

	// given that the request data is taken from a callback from an OAuth side, matches the {name:state} to an authentication record,
	// and uses the code to fetch more information about the service; the result is an access token & username, which can be passed
	// back to the client
	public String obtainToken() throws IOException
	{
		String state = request.getParameter("state");
		String name = state == null ? null : state.split(":")[0];
		String code = request.getParameter("code");
		if (name == null || code == null) 
		{
			logger.info("Authentication URL: {}", request.getRequestURI());
			throw new IOException("Did not receive both name & code");
		}
		
		JSONObject answer = new JSONObject();
		try
		{
			Authentication authentication = Common.getAuthentication();
			String baseURL = Common.getParams().baseURL;
			String returnURI = baseURL == null ? request.getRequestURL().toString() : baseURL + "authenticated.jsp";
			Authentication.Session session = authentication.authenticateSession(name, code, returnURI);
			
			answer = obtainTokenJSON(session, state);
		}
		catch (Exception ex)
		{
			logger.error("Authentication error: {}", ex);
			answer.put("error", ex.getMessage());
		}

		return answer.toString();
	}

	public JSONObject obtainTokenJSON(Authentication.Session session, String state)
	{
		JSONObject json = new JSONObject();
		json.put("type", session.type);
		json.put("curatorID", session.curatorID);
		json.put("serviceName", session.serviceName);
		json.put("accessToken", session.accessToken);
		json.put("userID", session.userID);
		json.put("userName", session.userName);
		json.put("email", session.email);
		json.put("status", session.status);
		json.put("state", state);
		return json;
	}
}
