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

package com.cdd.bae.rest;

import com.cdd.bae.config.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.RESTBaseServlet.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;

import org.apache.http.*;
import org.json.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

/*
	Test for UpdateUserProfile REST API.
*/

public class UpdateUserProfileTest extends EndpointEmulator
{
	//	private Configuration configuration;
	private DataUser dataUser;
	private Session session1;
	private Session session2;
	private Session sessionOAuth;
	private Session sessionOAuthAdmin;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
		dataUser = new DataUser(mongo.getDataStore());

		prepareDatabase();

		setRestService(new UpdateUserProfile());
		restService = Mockito.spy(restService);
	}

	// ------------ mockito-based tests ------------

	// user logged in, test required parameters
	@Test
	public void testRequiredParameters() throws IOException
	{
		Map<String, String> parameter = new HashMap<>();
		doReturn(session1).when(restService).getSession(any());
		assertThat(dataUser.getUser(session1.curatorID).name, is("Thomas Mann"));
		assertThat(dataUser.getUser(session2.curatorID).name, is("Klaus Mann"));
		assertThat(dataUser.getUser(session1.curatorID).email, is("Thomas.Mann@b.com"));
		assertThat(dataUser.getUser(session2.curatorID).email, is("Klaus.Mann@b.com"));

		// missing curatorID
		parameter.put("userName", session1.userName);
		parameter.put("email", session1.email);
		JSONObject json = doPost(parameter).getContentAsJSON();
		TestUtilities.assertErrorResponse(json, RESTException.HTTPStatus.BAD_REQUEST);
		assertThat(dataUser.getUser(session1.curatorID).name, is("Thomas Mann"));

		// missing userName
		parameter.clear();
		parameter.put("email", session1.email);
		parameter.put("curatorID", session1.curatorID);
		json = doPost(parameter).getContentAsJSON();
		TestUtilities.assertErrorResponse(json, RESTException.HTTPStatus.BAD_REQUEST);
		assertThat(dataUser.getUser(session1.curatorID).name, is("Thomas Mann"));

		// missing email
		parameter.clear();
		parameter.put("userName", session1.userName);
		parameter.put("curatorID", session1.curatorID);
		json = doPost(parameter).getContentAsJSON();
		TestUtilities.assertErrorResponse(json, RESTException.HTTPStatus.BAD_REQUEST);
		assertThat(dataUser.getUser(session1.curatorID).email, is("Thomas.Mann@b.com"));

		// neither change email nor userName
		parameter.clear();
		parameter.put("userName", session1.userName);
		parameter.put("email", session1.email);
		parameter.put("curatorID", session1.curatorID);
		json = doPost(parameter).getContentAsJSON();
		TestUtilities.assertErrorResponse(json, RESTException.HTTPStatus.BAD_REQUEST);
		assertThat(dataUser.getUser(session1.curatorID).name, is("Thomas Mann"));
		assertThat(dataUser.getUser(session1.curatorID).email, is("Thomas.Mann@b.com"));

		// attempt to modify other user's profile fails because currently logged in user not an admin
		parameter.clear();
		parameter.put("userName", "Golo Mann");
		parameter.put("email", "golo.mann@b.com");
		parameter.put("curatorID", session2.curatorID);
		json = doPost(parameter).getContentAsJSON();
		TestUtilities.assertErrorResponse(json, RESTException.HTTPStatus.BAD_REQUEST);
		assertThat(dataUser.getUser(session2.curatorID).name, is("Klaus Mann"));

		// attempt to modify own profile succeeds
		parameter.put("userName", "Paul Thomas Mann");
		parameter.put("email", "tm712348@b.com");
		parameter.put("curatorID", session1.curatorID);
		json = doPost(parameter).getContentAsJSON();
		assertStatus(json, true, HttpStatus.SC_OK);
		assertThat(dataUser.getUser(session1.curatorID).name, is("Paul Thomas Mann"));
	}
	
	@Test
	public void testNotChanging() throws IOException
	{
		Map<String, String> parameter = new HashMap<>();
		doReturn(session1).when(restService).getSession(any());
		
	}

	// user not logged in
	@Test
	public void testNotLoggedIn() throws IOException
	{
		// no session since we are not logged in
		doReturn(null).when(restService).getSession(any());
		assertThat(dataUser.getUser(session1.curatorID).name, is("Thomas Mann"));

		Map<String, String> parameter = new HashMap<>();
		parameter.put("curatorID", session1.curatorID);
		parameter.put("userName", "Paul Thomas Mann");
		parameter.put("email", "paul.mann@b.com");
		JSONObject json = doPost(parameter).getContentAsJSON();
		assertStatus(json, false, Status.NOLOGIN);
		assertThat(dataUser.getUser(session1.curatorID).name, is("Thomas Mann"));
	}

	// trusted user logged in but disallowed from updating any profile, including its own
	@Test
	public void testTrustedUser() throws IOException
	{
		Session session = TestUtilities.mockSessionTrusted();
		doReturn(session).when(restService).getSession(any());
		assertThat(dataUser.getUser(session1.curatorID).name, is("Thomas Mann"));
		assertThat(dataUser.getUser(session2.curatorID).name, is("Klaus Mann"));

		// Trusted attempts to update own profile and fails
		Map<String, String> parameter = new HashMap<>();
		parameter.put("curatorID", session.curatorID);
		parameter.put("userName", "Paul Thomas Mann");
		parameter.put("email", "paul.mann@b.com");
		JSONObject json = doPost(parameter).getContentAsJSON();
		TestUtilities.assertErrorResponse(json, RESTException.HTTPStatus.BAD_REQUEST);

		parameter.clear();
		parameter.put("curatorID", session2.curatorID);
		parameter.put("userName", "Paul Thomas Mann");
		parameter.put("email", "paul.mann@b.com");
		json = doPost(parameter).getContentAsJSON();
		TestUtilities.assertErrorResponse(json, RESTException.HTTPStatus.BAD_REQUEST);
		assertThat(dataUser.getUser(session1.curatorID).name, is("Thomas Mann"));
		assertThat(dataUser.getUser(session2.curatorID).name, is("Klaus Mann"));
	}

	// oauth user logged in, updates his own profile
	@Test
	public void testOAuthUser() throws IOException
	{
		doReturn(sessionOAuth).when(restService).getSession(any());
		assertThat(dataUser.getUser(session1.curatorID).name, is("Thomas Mann"));
		assertThat(dataUser.getUser(sessionOAuth.curatorID).name, is("lloyd"));

		// oauth user attempts to update own profile and succeeds
		Map<String, String> parameter = new HashMap<>();
		parameter.put("curatorID", sessionOAuth.curatorID);
		parameter.put("userName", "Golo Mann");
		parameter.put("email", "golo.mann@b.com");
		JSONObject json = doPost(parameter).getContentAsJSON();
		assertStatus(json, true, HttpStatus.SC_OK);
		assertThat(dataUser.getUser(sessionOAuth.curatorID).name, is("Golo Mann"));

		// oauth user attempt to update other user's profile and fails
		parameter.clear();
		parameter.put("userName", "Golo Mann");
		parameter.put("curatorID", session1.curatorID);
		parameter.put("email", "golo.mann@b.com");
		json = doPost(parameter).getContentAsJSON();
		TestUtilities.assertErrorResponse(json, RESTException.HTTPStatus.BAD_REQUEST);
		assertThat(dataUser.getUser(session1.curatorID).name, is("Thomas Mann"));
	}

	// oauth admin user logged in, updates his own profile and other user's profile, too
	@Test
	public void testOAuthAdmin() throws IOException
	{
		doReturn(sessionOAuthAdmin).when(restService).getSession(any());
		assertThat(dataUser.getUser(session1.curatorID).name, is("Thomas Mann"));
		assertThat(dataUser.getUser(sessionOAuthAdmin.curatorID).name, is("lloyd"));

		// oauth admin user attempts to update own profile and succeeds
		Map<String, String> parameter = new HashMap<>();
		parameter.put("curatorID", sessionOAuthAdmin.curatorID);
		parameter.put("userName", "Golo Mann");
		parameter.put("email", "golo.mann@b.com");
		JSONObject json = doPost(parameter).getContentAsJSON();
		assertStatus(json, true, HttpStatus.SC_OK);
		assertThat(dataUser.getUser(sessionOAuthAdmin.curatorID).name, is("Golo Mann"));

		// oauth admin user attempts to update other user's profile and succeeds
		parameter.clear();
		parameter.put("userName", "Golo Mann");
		parameter.put("curatorID", session1.curatorID);
		parameter.put("email", "golo.mann@b.com");
		json = doPost(parameter).getContentAsJSON();
		assertStatus(json, true, HttpStatus.SC_OK);
		assertThat(dataUser.getUser(session1.curatorID).name, is("Golo Mann"));
	}

	// ------------ private methods ------------

	private void prepareDatabase() throws IOException
	{
		session1 = makeUserSession("curator1", "Thomas Mann");
		dataUser.submitSession(session1);

		session2 = makeUserSession("curator2", "Klaus Mann");
		dataUser.submitSession(session2);

		sessionOAuth = TestUtilities.mockSessionOAuth();
		sessionOAuthAdmin = TestUtilities.mockSessionOAuthAdmin();
		dataUser.submitSession(sessionOAuth);
		dataUser.submitSession(sessionOAuthAdmin);
	}

	private Session makeUserSession(String curatorID, String curatorName)
	{
		Session session = new Session();
		session.curatorID = curatorID;
		session.userID = curatorID + "-1";
		session.userName = curatorName;
		session.email = curatorName.replace(" ", ".") + "@b.com";
		return session;
	}

}
