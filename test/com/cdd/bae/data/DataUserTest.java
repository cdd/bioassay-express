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

package com.cdd.bae.data;

import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.security.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.data.DataUser
*/

public class DataUserTest
{
	private static final Random random = new SecureRandom();
	private static final String NAME_1 = "name1";
	private static final String USER_1 = "user1";
	private static final String CURATOR_2 = "curator-2";
	private static final String CURATOR_1 = "curator-1";
	DataUser dataUser;

	@BeforeEach
	public void initialize()
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		dataUser = new DataUser(mongo.getDataStore());
	}

	@Test
	public void testUserHandling() throws IOException
	{
		assertThat(dataUser.countUsers(), is(0));

		addUserWithSession(CURATOR_1, USER_1);
		addUserWithSession(CURATOR_2, "user2");
		assertThat(dataUser.countUsers(), is(2));
		assertThat(toList(dataUser.listUsers()), containsInAnyOrder(CURATOR_1, CURATOR_2));
		assertThat(dataUser.getUser(CURATOR_1).name, is(USER_1));

		addUserWithSession(CURATOR_1, NAME_1);
		addUserWithSession(CURATOR_2, "name2");
		assertThat(dataUser.countUsers(), is(2));
		assertThat(toList(dataUser.listUsers()), containsInAnyOrder(CURATOR_1, CURATOR_2));
		assertThat(dataUser.getUser(CURATOR_1).name, is(NAME_1));
	}

	@Test
	public void testGetUser() throws IOException
	{
		assertThat(dataUser.countUsers(), is(0));

		assertThat(dataUser.getUser(null), is(nullValue()));
		assertThat(dataUser.getUser("  "), is(nullValue()));
		assertThat(dataUser.getUser(CURATOR_1), is(nullValue()));

		addUserWithSession(CURATOR_1, USER_1);
		assertThat(dataUser.getUser(CURATOR_1).name, is(USER_1));
	}

	@Test
	public void testChangeUserName() throws IOException
	{
		assertThat(dataUser.countUsers(), is(0));
		addUserWithSession(CURATOR_1, USER_1);
		assertThat(dataUser.getUser(CURATOR_1).name, is(USER_1));

		dataUser.changeUserName(CURATOR_1, NAME_1);
		assertThat(dataUser.getUser(CURATOR_1).name, is(NAME_1));
	}

	@Test
	public void testChangeEmail() throws IOException
	{
		assertThat(dataUser.countUsers(), is(0));
		addUserWithSession(CURATOR_1, USER_1);
		assertThat(dataUser.getUser(CURATOR_1).name, is(USER_1));
		assertThat(dataUser.getUser(CURATOR_1).email, is(USER_1 + "@b.com"));

		dataUser.changeEmail(CURATOR_1, "user@another.com");
		assertThat(dataUser.getUser(CURATOR_1).email, is("user@another.com"));
	}

	@Test
	public void testChangeStatus() throws IOException
	{
		assertThat(dataUser.countUsers(), is(0));
		addUserWithSession(CURATOR_1, USER_1);
		assertThat(dataUser.getUser(CURATOR_1).status, is(nullValue()));

		dataUser.changeStatus(CURATOR_1, DataUser.STATUS_DEFAULT);
		assertThat(dataUser.getUser(CURATOR_1).status, is(DataUser.STATUS_DEFAULT));

		dataUser.changeStatus(CURATOR_1, DataUser.STATUS_ADMIN);
		assertThat(dataUser.getUser(CURATOR_1).status, is(DataUser.STATUS_ADMIN));

		// status isn't changed when user logs in again
		addUserWithSession(CURATOR_1, USER_1);
		assertThat(dataUser.getUser(CURATOR_1).status, is(DataUser.STATUS_ADMIN));
	}

	@Test
	public void testChangeCredentials() throws IOException
	{
		assertThat(dataUser.countUsers(), is(0));
		addUserWithSession(CURATOR_1, USER_1);

		DataObject.User user = dataUser.getUser(CURATOR_1);
		assertThat(user.passwordHash, is(nullValue()));
		assertThat(user.passwordSalt, is(nullValue()));

		byte[] pwHash = getRandomByteArray();
		byte[] pwSalt = getRandomByteArray();
		dataUser.changeCredentials(CURATOR_1, pwHash, pwSalt);

		user = dataUser.getUser(CURATOR_1);
		assertThat(user.passwordHash, is(pwHash));
		assertThat(user.passwordSalt, is(pwSalt));
	}

	// ------------ private methods ------------

	private void addUserWithSession(String curatorID, String curatorName)
	{
		dataUser.submitSession(DataStoreSupport.makeUserSession(curatorID, curatorName));
	}

	private <T> List<T> toList(T[] array)
	{
		return Arrays.asList(array);
	}
	
	protected static byte[] getRandomByteArray()
	{
		byte[] salt = new byte[16];
		random.nextBytes(salt);
		return salt;
	}
}
