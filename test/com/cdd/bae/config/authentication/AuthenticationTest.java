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

import com.cdd.bae.config.authentication.Access.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

public class AuthenticationTest extends TestBaseClass
{
	private static final String TRUSTED_NAME = "Trusted";
	private static final String ORCID_NAME = "ORCID";
	private static final String BASIC_NAME = "Basic";
	private static final String TRUSTED_PREFIX = "trusted:";
	private static final String ORCID_PREFIX = "orcid:";
	private static final String BASIC_PREFIX = "basic:";

	private DataUser dataUser;

	public TestResourceFile validConfig = new TestResourceFile("/testData/config/authenticationValid.json");
	public TestResourceFile invalidConfig = new TestResourceFile("/testData/config/authenticationInvalid.json");

	@BeforeEach
	public void prepare()
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Common.setDataStore(mongo.getDataStore());
		
		dataUser = new DataUser(mongo.getDataStore());
	}

	@Test
	public void testEmptyAuthentication() throws JSONSchemaValidatorException
	{
		Authentication authentication = new Authentication();
		assertNoAuthenticationInformation(authentication);
		authentication.reload();

		authentication = new Authentication((String)null);
		assertNoAuthenticationInformation(authentication);
		authentication.reload();

		authentication = new Authentication((String)"");
		assertNoAuthenticationInformation(authentication);
		authentication.reload();
	}

	@Test
	public void testValidAuthentication() throws IOException, JSONSchemaValidatorException
	{
		Authentication authentication = loadAuthentication(validConfig);

		assertThat(authentication.getAccessList(), arrayWithSize(6));
		assertTrue(authentication.hasAnyAccess());
		for (Access access : authentication.getAccessList())
		{
			assertThat(access.name, notNullValue());
			assertThat(access.prefix, notNullValue());
		}


		assertAuthenticationList(authentication, AccessType.TRUSTED, 1);
		assertAuthenticationList(authentication, AccessType.BASIC, 1);
		assertAuthenticationList(authentication, AccessType.OAUTH, 3);
		assertAuthenticationList(authentication, AccessType.LDAP, 1);

		// getAccess by name
		assertThat(authentication.getAccess(TRUSTED_NAME).prefix, is(TRUSTED_PREFIX));
		assertThat(authentication.getAccess(ORCID_NAME).prefix, is(ORCID_PREFIX));
		assertThat(authentication.getAccess(BASIC_NAME).prefix, is(BASIC_PREFIX));
	}
	
	private void assertAuthenticationList(Authentication authentication, AccessType type, int size)
	{
		assertTrue(authentication.hasAnyAccess(type));
		
		Access[] accessList = authentication.getAccessList(type);
		assertThat(accessList, arrayWithSize(size));
		for (Access access : accessList)
		{
			assertThat(type.implementedBy(access), is(true));
		}
	}

	@Test
	public void testAuthenticateSession() throws IOException, JSONSchemaValidatorException
	{
		Authentication authentication = loadAuthentication(validConfig);

		assertThat(dataUser.listUsers(), emptyArray());
		assertThat(Authentication.currentSessions, equalTo(Collections.emptyMap()));

		Session session = authentication.authenticateSession(TRUSTED_NAME, null, null);
		assertEquals(DataUser.STATUS_ADMIN, session.status);
		String[] users = dataUser.listUsers();
		assertThat("user added to store", users, arrayWithSize(1));
		assertThat(users, arrayContaining("trusted:anyone"));
		assertThat(Authentication.currentSessions, aMapWithSize(1));

		session = authentication.authenticateSession(TRUSTED_NAME, null, null);
		assertEquals(DataUser.STATUS_ADMIN, session.status);
		users = dataUser.listUsers();
		assertThat("user added to store", users, arrayWithSize(1));
		assertThat(users, arrayContaining("trusted:anyone"));
		assertThat(Authentication.currentSessions, aMapWithSize(1));

		assertThrows(IOException.class,
				() -> authentication.authenticateSession("Incorrect", null, null),
				"Exception expected for unknown authentication name");

		assertThat(dataUser.listUsers(), arrayWithSize(1));
		assertThat(Authentication.currentSessions, aMapWithSize(1));
	}

	@Test
	public void testAuthenticationHasChanged() throws JSONSchemaValidatorException, IOException
	{
		File file = validConfig.getAsFile(folder, "authentication.json");
		Authentication authentication = new Authentication(file.getAbsolutePath());
		assertFalse(authentication.hasChanged());

		authentication.reload();
		assertFalse(authentication.hasChanged());

		authentication.setFile(file);
		assertFalse(authentication.hasChanged(), "Replace with same file");

		authentication.setFile(new File(folder.toFile(), "abc"));
		assertTrue(authentication.hasChanged(), "Replace with new file");

		authentication.loader.getWatcher().reset();
		assertFalse(authentication.hasChanged(), "Reset");

		authentication.setFile(null);
		assertTrue(authentication.hasChanged(), "Remove referenced file");
	}

	@Test
	public void testInvalidAuthentication()
	{
		JSONSchemaValidatorException ex = assertThrows(JSONSchemaValidatorException.class,
				() -> loadAuthentication(invalidConfig),
				"Invalid authentication configuration");

		assertThat(ex.getMessage(), startsWith("Error loading configuration:"));
		assertEquals(3, ex.getDetails().size());
	}

	@Test
	public void testEquals()
	{
		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			return loadValidConfiguration();
		}, "currentSessions", "schemaDefinition", "file", "loader");

		// equality test for Access instances
		assertThat(loadValidConfiguration().listAccess, hasSize(6));
		String[] ignore = {"TYPE", "redirectURI", "userID", "userName"};
		TestUtilities.assertEquality(() -> loadValidConfiguration().listAccess.get(0), ignore);
		TestUtilities.assertEquality(() -> loadValidConfiguration().listAccess.get(1), ignore);
		TestUtilities.assertEquality(() -> loadValidConfiguration().listAccess.get(2), ignore);
		TestUtilities.assertEquality(() -> loadValidConfiguration().listAccess.get(3), ignore);
		TestUtilities.assertEquality(() -> loadValidConfiguration().listAccess.get(4), ignore);
		TestUtilities.assertEquality(() -> loadValidConfiguration().listAccess.get(5), ignore);
}

	@Test
	public void testSession()
	{
		Session session = new Authentication.Session();
		// DataUser.STATUS_DEFAULT
		assertEquals(DataUser.STATUS_DEFAULT, session.status);
		assertTrue(session.canSubmitHoldingBay());
		assertFalse(session.canSubmitDirectly());
		assertFalse(session.canSubmitBulk());
		assertFalse(session.canApplyHolding());
		assertFalse(session.isAdministrator());
		assertFalse(session.canRequestProvisionalTerm());

		// DataUser.STATUS_BLOCKED
		session.status = DataUser.STATUS_BLOCKED;
		assertFalse(session.canSubmitHoldingBay());
		assertFalse(session.canSubmitDirectly());
		assertFalse(session.canSubmitBulk());
		assertFalse(session.canApplyHolding());
		assertFalse(session.isAdministrator());
		assertFalse(session.canRequestProvisionalTerm());

		// DataUser.STATUS_CURATOR
		session.status = DataUser.STATUS_CURATOR;
		assertTrue(session.canSubmitHoldingBay());
		assertTrue(session.canSubmitDirectly());
		assertTrue(session.canSubmitBulk());
		assertTrue(session.canApplyHolding());
		assertFalse(session.isAdministrator());
		assertTrue(session.canRequestProvisionalTerm());

		// DataUser.STATUS_ADMIN
		session.status = DataUser.STATUS_ADMIN;
		assertTrue(session.canSubmitHoldingBay());
		assertTrue(session.canSubmitDirectly());
		assertTrue(session.canSubmitBulk());
		assertTrue(session.canApplyHolding());
		assertTrue(session.isAdministrator());
		assertTrue(session.canRequestProvisionalTerm());
	}

	// ------------ private methods ------------

	private void assertNoAuthenticationInformation(Authentication authentication)
	{
		assertThat(authentication.hasAnyAccess(), is(false));
		assertThat(authentication.getAccessList(), emptyArray());

		assertThat(authentication.hasAnyAccess(AccessType.TRUSTED), is(false));
		assertThat(authentication.hasAnyAccess(AccessType.BASIC), is(false));
		assertThat(authentication.hasAnyAccess(AccessType.OAUTH), is(false));
		assertThat(authentication.hasAnyAccess(AccessType.LDAP), is(false));

		assertThat(authentication.getAccessList(AccessType.TRUSTED), emptyArray());
		assertThat(authentication.getAccessList(AccessType.BASIC), emptyArray());
		assertThat(authentication.getAccessList(AccessType.OAUTH), emptyArray());
		assertThat(authentication.getAccessList(AccessType.LDAP), emptyArray());
	}

	private Authentication loadAuthentication(TestResourceFile resourceFile) throws JSONSchemaValidatorException, IOException
	{
		File file = resourceFile.getAsFile(folder, "authentication.json");
		return new Authentication(file.getAbsolutePath());
	}
	
	private Authentication loadValidConfiguration()
	{
		try
		{
			return loadAuthentication(validConfig);
		}
		catch (JSONSchemaValidatorException | IOException e)
		{
			assertThat("Shouldn't be here", true, is(false));
			return null;
		}
	}
}
