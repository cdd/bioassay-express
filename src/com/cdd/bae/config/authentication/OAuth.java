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

package com.cdd.bae.config.authentication;

import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.net.*;
import java.util.*;

import org.json.*;
import org.slf4j.*;

public class OAuth extends Access
{
	public static final AccessType TYPE = AccessType.OAUTH;
	
	private static final Logger logger = LoggerFactory.getLogger(Authentication.class);

	public String authURL; // URL that the client uses to bring up the authentication page
	public String tokenURL; // URL that the server uses to map the code into a token
	public String scope; // the extent of permission being requested (set it to the lowest possible)
	public String responseType; // desired response from authentication
	public String redirectURI; // optional redirect URI to be sent to the authURL
	public String clientID; // pre-registered client identifier for the app (public)
	public String clientSecret; // the private part of the client identifier
	public String userID; // access response field that contains the unique user identifier
	public String userName; // access response field that contains the human readable user name

	@Override
	public boolean equals(Object o)
	{
		if (o == null || getClass() != o.getClass()) return false;
		OAuth other = (OAuth) o;
		return super.equals(other) && authURL.equals(other.authURL) && tokenURL.equals(other.tokenURL)
				&& scope.equals(other.scope) && responseType.equals(other.responseType)
				&& clientID.equals(other.clientID) && clientSecret.equals(other.clientSecret)
				&& userID.equals(other.userID) && userName.equals(other.userName) 
				&& redirectURI.equals(other.redirectURI);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, prefix, authURL, tokenURL, scope, responseType, redirectURI, clientID, clientSecret, userID, userName);
	}

	@Override
	public String getType()
	{
		return TYPE.toString();
	}

	@Override
	public JSONObject serializeFrontend()
	{
		JSONObject obj = new JSONObject();
		obj.put("type", getType());
		obj.put("name", name);
		obj.put("url", authURL);
		obj.put("scope", scope);
		obj.put("responseType", responseType);
		obj.put("clientID", clientID);
		if (redirectURI != null) obj.put("redirectURI", redirectURI);
		return obj;
	}

	@Override
	public void parseJSON(JSONObject obj)
	{
		super.parseJSON(obj);
		authURL = obj.getString("authURL");
		tokenURL = obj.getString("tokenURL");
		scope = obj.getString("scope");
		responseType = obj.getString("responseType");
		redirectURI = obj.optString("redirectURI");
		clientID = obj.getString("clientID");
		clientSecret = obj.optString("clientSecret");
		userID = obj.optString("userID");
		userName = obj.optString("userName");
	}

	// connect to an OAuth server and figure out what's going on
	@Override
	public Session authenticate(String... parameter) throws IOException
	{
		String code = parameter[0];
		String currentURL = parameter[1];

		// make the request
		String data = "client_id=" + URLEncoder.encode(clientID, Util.UTF8);
		data += "&client_secret=" + URLEncoder.encode(clientSecret, Util.UTF8);
		data += "&grant_type=authorization_code";
		data += "&code=" + URLEncoder.encode(code, Util.UTF8);
		data += "&redirect_uri=" + URLEncoder.encode(currentURL, Util.UTF8);
		logger.debug(tokenURL);
		logger.debug(data);
		String tokenRaw = makeRequest(tokenURL, data);
		logger.debug(tokenRaw);

		JSONObject result = new JSONObject(new JSONTokener(tokenRaw));
		logger.debug(result.toString(3));

		/*
		 * Example of fields returned in the token result: "access_token":"{big long GUID}",
		 * "token_type":"bearer", "refresh_token":"{different GUID}", "expires_in":631138518,
		 * "scope":"/read-limited", "name":"Alex Clark", "orcid":"0000-0002-7376-6870"
		 */

		Session session = new Session();
		session.serviceName = name;
		session.accessToken = result.getString("access_token");
		session.userID = result.optString(userID, "");
		session.userName = result.optString(userName, "");
		session.email = "";
		session.status = DataUser.STATUS_DEFAULT;

		// grab user data from the "ID token", which is JWT-encoded (if provided)
		String idToken = result.optString("id_token", null);
		unpackIDToken(session, idToken);

		if (Util.isBlank(session.userID)) throw new IOException("UserID unavailable.");

		session.curatorID = prefix + session.userID;

		// if the user has been seen before, override content from the hardcoded database
		DataStore.User user = Common.getDataStore().user().getUser(session.curatorID);
		if (user != null)
		{
			if (Util.notBlank(user.status)) session.status = user.status;
			if (Util.isBlank(session.userName) && Util.notBlank(user.name)) session.userName = user.name;
			if (Util.isBlank(session.email) && Util.notBlank(user.email)) session.email = user.email;
		}

//		// and, update the database: make sure that the curatorID exists, and that the rest is current
//		Common.getDataStore().user().submitSession((Authentication.Session)session);

		return session;
	}

	// unpacks the JWT-encoded ID token, if any, and pulls out relevant information
	private void unpackIDToken(Session session, String idToken)
	{
		if (idToken == null) return;
		String[] bits = idToken.split("\\.");
		if (bits.length < 2) return;
		String raw = new String(Base64.getDecoder().decode(bits[1]));
		JSONObject json = new JSONObject(new JSONTokener(raw));

		/*
		 * Content example: { "iss": "accounts.google.com", "iat": 1487360113, "exp": 1487363713, 
		 * "at_hash": "qjjYsQGj4jz_hoEdwVBBYA", 
		 * "aud": "1078599405819-mq1bdr3n0u6km88jfeqqvptrj4obvkac.apps.googleusercontent.com", 
		 * "sub": "108375455890260223170", 
		 * "email_verified": true, 
		 * "azp": "1078599405819-mq1bdr3n0u6km88jfeqqvptrj4obvkac.apps.googleusercontent.com", 
		 * "email": "aclark.xyz@gmail.com" }
		 */
		session.userID = json.optString("sub", session.userID);
		session.userName = json.optString("name", session.userName);
		session.email = json.optString("email", session.email);
	}

	/**
	* Issues an HTTP request, with an optional URL-encoded form post. A return value of null implies a relatively graceful
	* not found error (usually 404).
	*/
	public static String makeRequest(String url, String post) throws IOException
	{
		HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
		conn.setDoOutput(true);
		if (post == null)
			conn.setRequestMethod("GET");
		else
		{
			conn.setRequestMethod("POST");
			conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
		}
		int cutoff = 300000; // 5 minutes
		conn.setConnectTimeout(cutoff);
		conn.setReadTimeout(cutoff);
		conn.connect();

		if (post != null)
		{
			BufferedWriter send = new BufferedWriter(new OutputStreamWriter(conn.getOutputStream(), Util.UTF8));
			send.append(post);
			send.flush();
			send.close();
		}

		int respCode = conn.getResponseCode();
		logger.debug("Response code {}", respCode);
		// if (respCode >= 400) return null; // this is relatively graceful
		if (respCode != 200) throw new IOException("HTTP response code " + respCode + " for URL [" + url + "]");

		// read the raw bytes into memory; abort if it's too long or too slow
		BufferedInputStream istr = new BufferedInputStream(conn.getInputStream());
		ByteArrayOutputStream buff = new ByteArrayOutputStream();
		final int DOWNLOAD_LIMIT = 100 * 1024 * 1024; // within reason
		while (true)
		{
			int b = -1;
			try
			{
				b = istr.read();
			}
			catch (SocketTimeoutException ex)
			{
				throw new IOException(ex);
			}
			if (b < 0) break;
			if (buff.size() >= DOWNLOAD_LIMIT)
				throw new IOException("Download size limit exceeded (max=" + DOWNLOAD_LIMIT + " bytes) for URL: " + url);
			buff.write(b);
		}
		istr.close();

		return new String(buff.toByteArray(), Util.UTF8);
	}
}
