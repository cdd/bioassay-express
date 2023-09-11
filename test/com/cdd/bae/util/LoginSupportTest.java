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

import com.cdd.bae.config.*;
import com.cdd.bae.config.authentication.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

public class LoginSupportTest extends TestBaseClass
{
	private static Configuration configuration;
	private static Authentication authentication;

	@BeforeAll
	public static void setUp() throws ConfigurationException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Common.setDataStore(mongo.getDataStore());
		configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		authentication = Common.getAuthentication();
	}

	@AfterEach
	public void restore()
	{
		TestConfiguration.setAuthentication(authentication);
	}

	@Test
	public void testObtainTokenJSON()
	{
		Access access = new Trusted();
		Session session = access.createDefaultSession("name");
		String state = "client-provided-state";
		JSONObject json = new LoginSupport(null).obtainTokenJSON(session, state);

		assertThat(json.length(), is(8));
		assertThat(json.getString("type"), is(access.getType()));
		assertThat(json.getString("state"), is(state));
	}

	@Test
	public void testCurrentSessionNoLogin() throws IOException
	{
		Map<String, String> cookies = new HashMap<>();
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(null, cookies);

		LoginSupport login = new LoginSupport(request);
		Authentication.Session session = login.currentSession();
		assertThat(session, is(nullValue()));

		assertThat(login.isLoggedIn(), is(false));
		assertThat(login.isAdministrator(), is(false));
	}

	@Test
	public void testCurrentSessionTrusted() throws IOException, JSONSchemaValidatorException
	{
		Authentication authentication = new Authentication();
		Trusted trusted = new Trusted();
		trusted.name = "Trusted";
		trusted.status = DataUser.STATUS_ADMIN;
		authentication.listAccess.add(trusted);
		TestConfiguration.setAuthentication(authentication);

		Map<String, String> cookies = new HashMap<>();
		cookies.put("serviceName", "Trusted");
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(null, cookies);

		LoginSupport login = new LoginSupport(request);
		Authentication.Session session = login.currentSession();
		assertThat(session, not(nullValue()));

		assertThat(login.isLoggedIn(), is(true));
		assertThat(login.isAdministrator(), is(true));
	}

	@Test
	public void testCurrentSessionOtherMethod() throws IOException
	{
		Session storedSession = new Session();
		storedSession.serviceName = "Basic";
		storedSession.accessToken = "1234567";
		storedSession.curatorID = "basic:username";
		storedSession.status = DataUser.STATUS_DEFAULT;
		Common.getAuthentication().putSession(storedSession);

		// user is successfully logged in
		Map<String, String> cookies = new HashMap<>();
		cookies.put("serviceName", storedSession.serviceName);
		cookies.put("curatorID", storedSession.curatorID);
		cookies.put("accessToken", storedSession.accessToken);
		cookies.put("other-cookies", "ignored");
		LoginSupport login = new LoginSupport(MockRESTUtilities.mockedPOSTRequest(null, cookies));
		Authentication.Session session = login.currentSession();
		assertThat(session, not(nullValue()));
		assertThat(login.isLoggedIn(), is(true));
		assertThat(login.isAdministrator(), is(false));

		// user has an invalid access token
		cookies.put("accessToken", "invalid");
		login = new LoginSupport(MockRESTUtilities.mockedPOSTRequest(null, cookies));
		session = login.currentSession();
		assertThat(session, is(nullValue()));
		assertThat(login.isLoggedIn(), is(false));
		assertThat(login.isAdministrator(), is(false));

		// user that is not logged in tries
		cookies.put("curatorID", "basic:not-logged-in");
		login = new LoginSupport(MockRESTUtilities.mockedPOSTRequest(null, cookies));
		session = login.currentSession();
		assertThat(session, is(nullValue()));
		assertThat(login.isLoggedIn(), is(false));
		assertThat(login.isAdministrator(), is(false));

		// user that is logged in with a different service
		cookies.put("serviceName", "other-service");
		cookies.put("curatorID", storedSession.curatorID);
		cookies.put("accessToken", storedSession.accessToken);
		login = new LoginSupport(MockRESTUtilities.mockedPOSTRequest(null, cookies));
		session = login.currentSession();
		assertThat(session, is(nullValue()));
		assertThat(login.isLoggedIn(), is(false));
		assertThat(login.isAdministrator(), is(false));
	}

	@Test
	public void testSerializeServices()
	{
		Set<String> accessNames = Arrays.stream(Common.getAuthentication().getAccessList())
				.map(a -> a.name).collect(Collectors.toSet());
		assertThat("Test configuration should contain authentication methods", accessNames, not(empty()));

		JSONArray serialization = new LoginSupport(null).serializeServices();
		Set<String> serialized = new HashSet<>();
		for (int i = 0; i < serialization.length(); i++)
			serialized.add(serialization.getJSONObject(i).getString("name"));
		assertThat(serialized, equalTo(accessNames));
	}

	@Test
	public void testObtainToken() throws IOException
	{
		Map<String, String> payload = new HashMap<>();
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(payload);

		// check that we error on missing parameter
		assertThrows(IOException.class, () -> new LoginSupport(request).obtainToken());

		payload.put("state", "authname:123456");
		assertThrows(IOException.class, () -> new LoginSupport(request).obtainToken());

		payload.clear();
		payload.put("code", "123456789");
		assertThrows(IOException.class, () -> new LoginSupport(request).obtainToken());

		// testing additional functionality would require mocking the OAuth authentication
	}
}
