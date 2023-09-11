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
import com.cdd.bae.config.authentication.Access.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

/*
	Test for UpdateUserProfile REST API.
*/

public class AdminChangeStatusTest extends EndpointEmulator
{
	//	private Configuration configuration;
	private DataUser dataUser;
	private Session session1;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
		dataUser = new DataUser(mongo.getDataStore());

		prepareDatabase();

		setRestService(new AdminChangeStatus());
		restService = Mockito.spy(restService);
	}

	// ------------ mockito-based tests ------------

	@Test
	public void testPermission() throws IOException
	{
		assertThat(restService.hasPermission(null), is(false));
		assertThat(restService.hasPermission(TestUtilities.mockSessionBlocked()), is(false));
		assertThat(restService.hasPermission(TestUtilities.mockSession()), is(false));
		assertThat(restService.hasPermission(TestUtilities.mockSessionCurator()), is(false));
		assertThat(restService.hasPermission(TestUtilities.mockSessionAdmin()), is(true));
		assertThat(restService.hasPermission(TestUtilities.mockSessionTrusted()), is(true));
	}

	// user logged in, test required parameters
	@Test
	public void testRequiredParameters() throws IOException
	{
		Map<String, String> parameter = new HashMap<>();
		doReturn(TestUtilities.mockSessionAdmin()).when(restService).getSession(any());

		// missing curatorID
		JSONObject json = doPost(parameter).getContentAsJSON();
		TestUtilities.assertErrorResponse(json, RESTException.HTTPStatus.BAD_REQUEST);
	}
	
	@Test
	public void testBadRequest() throws IOException
	{
		Map<String, String> parameter = new HashMap<>();
		doReturn(TestUtilities.mockSessionAdmin()).when(restService).getSession(any());

		parameter.put("curatorID", "unknown");
		parameter.put("newStatus", "blocked");
		JSONObject json = doPost(parameter).getContentAsJSON();
		TestUtilities.assertErrorResponse(json, RESTException.HTTPStatus.BAD_REQUEST);
		assertThat(json.get("userMessage"), is("Unknown curator ID"));

		parameter.put("curatorID", session1.curatorID);
		parameter.put("newStatus", "unknown");
		json = doPost(parameter).getContentAsJSON();
		TestUtilities.assertErrorResponse(json, RESTException.HTTPStatus.BAD_REQUEST);
		assertThat(json.get("userMessage"), is("Unknown status requested"));
	}

	@Test
	public void testChangeStatus() throws IOException
	{
		// make sure that our user is properly registered
		assertThat(dataUser.getUser(session1.curatorID).status, nullValue());
		
		Map<String, String> parameter = new HashMap<>();
		parameter.put("curatorID", session1.curatorID);
		doReturn(TestUtilities.mockSessionAdmin()).when(restService).getSession(any());

		parameter.put("newStatus", "blocked");
		JSONObject json = doPost(parameter).getContentAsJSON();
		assertThat(json.getBoolean("success"), is(true));
		assertThat(dataUser.getUser(session1.curatorID).status, is(DataUser.STATUS_BLOCKED));

		parameter.put("newStatus", "default");
		json = doPost(parameter).getContentAsJSON();
		assertThat(json.getBoolean("success"), is(true));
		assertThat(dataUser.getUser(session1.curatorID).status, is(DataUser.STATUS_DEFAULT));

		parameter.put("newStatus", "curator");
		json = doPost(parameter).getContentAsJSON();
		assertThat(json.getBoolean("success"), is(true));
		assertThat(dataUser.getUser(session1.curatorID).status, is(DataUser.STATUS_CURATOR));

		parameter.put("newStatus", "admin");
		json = doPost(parameter).getContentAsJSON();
		assertThat(json.getBoolean("success"), is(true));
		assertThat(dataUser.getUser(session1.curatorID).status, is(DataUser.STATUS_ADMIN));
	}

	// ------------ private methods ------------

	private void prepareDatabase() throws IOException
	{
		session1 = makeUserSession("basic:user1", "Thomas Mann");
		dataUser.submitSession(session1);
		
	}

	private Session makeUserSession(String curatorID, String curatorName)
	{
		Session session = new Session();
		session.curatorID = curatorID;
		session.userID = curatorID + "-1";
		session.userName = curatorName;
		session.email = curatorName.replace(" ", ".") + "@b.com";
		session.serviceName = AccessType.BASIC.toString();
		return session;
	}

}
