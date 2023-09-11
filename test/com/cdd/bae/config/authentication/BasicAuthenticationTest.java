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

import com.cdd.bae.config.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import javax.mail.internet.*;

import org.apache.commons.codec.binary.*;
import org.apache.commons.mail.*;
import org.junit.jupiter.api.*;

/*	
	To test the password reset by email functionality use for example FakeSMTP: 
	- Download FakeSMTP from http://nilhcem.com/FakeSMTP/index.html
	- Run the smtp server using: java -jar fakeSMTP-2.0.jar -s -p 2525 -a 127.0.0.1
	- Configure basic authentication in authentication.json using smtpHost=localhost, smtpPort=2525
*/

public class BasicAuthenticationTest extends TestBaseClass
{
	private static final String PWSTRING = "test";

	private static Configuration configuration;
	private static List<Access> oldAccessList;
	private DataUser dataUser;

	@BeforeAll
	public static void setUp() throws ConfigurationException
	{
		configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		oldAccessList = configuration.getAuthentication().listAccess;
	}

	@AfterAll
	public static void tearDown()
	{
		configuration.getAuthentication().listAccess = oldAccessList;
		Authentication.currentSessions.clear();
	}

	@BeforeEach
	public void prepare() throws ConfigurationException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Common.setDataStore(mongo.getDataStore());

		dataUser = new DataUser(mongo.getDataStore());
	}

	@Test
	public void testGetNextSalt()
	{
		byte[] salt1 = BasicAuthentication.getNextSalt();
		byte[] salt2 = BasicAuthentication.getNextSalt();
		assertThat(salt1, not(equalTo(salt2)));
		assertThat(salt1.length, is(16));
	}

	@Test
	public void testHashPassword()
	{
		byte[] salt = new byte[]{1, 1, 1, 1};
		byte[] pwhash1 = BasicAuthentication.hashPassword(PWSTRING.toCharArray(), salt);
		assertThat(pwhash1.length, is(32));
		assertThat(Hex.encodeHexString(pwhash1), is("8b2321b6881a67cdc659d35343bc8501f1d1b01dc6bb6a81ffe1cf5ea975e268"));

		byte[] pwhash2 = BasicAuthentication.hashPassword(PWSTRING.toCharArray(), salt);
		assertThat(pwhash1, equalTo(pwhash2));

		// password is cleared after call to hashPassword
		char[] pw = "test".toCharArray();
		assertThat(pw.length, is(4));
		assertThat(new String(pw), is(PWSTRING));
		BasicAuthentication.hashPassword(pw, salt);
		assertThat(pw.length, is(4));
		assertThat(new String(pw), is(not(PWSTRING)));
		assertThat(pw, is(new char[]{0, 0, 0, 0}));
	}

	@Test
	public void testValidPassword()
	{
		char[] pw = PWSTRING.toCharArray();
		byte[] salt = BasicAuthentication.getNextSalt();
		byte[] expectedHash = BasicAuthentication.hashPassword(pw, salt);
		assertThat(pw, is(new char[]{0, 0, 0, 0}));

		// password is invalid
		pw = PWSTRING.toCharArray();
		assertThat(BasicAuthentication.validPassword(pw, salt, expectedHash), is(true));
		assertThat("password is cleared after call", pw, is(new char[]{0, 0, 0, 0}));

		// password is invalid if
		assertThat("password is incorrect",
				BasicAuthentication.validPassword("incorrect".toCharArray(), salt, expectedHash), is(false));
		assertThat("hashes differ in length", BasicAuthentication.validPassword(pw, salt, new byte[]{1, 2, 3}), is(false));
		assertThat("password is incorrect", BasicAuthentication.validPassword("".toCharArray(), salt, expectedHash),
				is(false));
		assertThat("expectedHash is null", BasicAuthentication.validPassword(PWSTRING.toCharArray(), salt, null),
				is(false));
		assertThat("expectedHash is empty array",
				BasicAuthentication.validPassword(PWSTRING.toCharArray(), salt, new byte[0]), is(false));
	}

	@Test
	public void testGeneratePassword()
	{
		String pw1 = BasicAuthentication.generatePassword(10);
		assertThat(pw1.length(), is(10));
		for (int i = 0; i < 10; i++)
		{
			String pw2 = BasicAuthentication.generatePassword(10);
			assertThat(pw2.length(), is(10));
			assertThat(pw2, not(equalTo(pw1)));
			assertThat(pw2, matchesPattern("[a-zA-Z0-9]+"));
		}
	}

	@Test
	public void testAuthenticate() throws IOException
	{
		String username = "testuser";
		String pw = "sometest";
		BasicAuthentication auth = new BasicAuthentication();
		auth.prefix = "basic:";
		String curatorID = auth.prefix + username;

		// user isn't known - authenticate fails
		Session session = auth.authenticate(username, pw);
		assertThat(session, is(nullValue()));

		// user exists, but has not credentials - authenticate fails
		dataUser.submitSession(auth.createDefaultSession(username));
		assertThat(auth.authenticate(username, pw), is(nullValue()));

		// user has credentials - authentication works
		pw = auth.resetPassword(curatorID);
		assertThat(pw, is(not("sometest")));
		session = auth.authenticate(username, pw);
		assertThat(session, not(nullValue()));

		// user has credentials but provides the wrong password - authentication fails
		assertThat(pw, is(not("new_password")));
		pw = "new_password";
		session = auth.authenticate(username, pw);
		assertThat(session, is(nullValue()));

		// user sets the new password - authentication works
		auth.setPassword(curatorID, pw);
		session = auth.authenticate(username, pw);
		assertThat(session, not(nullValue()));
	}

	@Test
	public void testRegistration() throws IOException
	{
		String username = "testuser";
		String pw = "sometest";
		String email = "testuser@b.com";
		BasicAuthentication auth = new BasicAuthentication();
		auth.prefix = "basic:";
		String curatorID = auth.prefix + username;

		// initially user doesn't exist
		assertThat(dataUser.getUser(curatorID), is(nullValue()));
		assertThat(auth.authenticate(username, pw), is(nullValue()));

		// user exists after registration
		auth.registerUser(username, pw, email);
		DataObject.User user = dataUser.getUser(curatorID);
		assertThat(user, not(nullValue()));
		assertThat(user.curatorID, is(curatorID));
		assertThat(user.email, is(email));

		// check that we can authenticate user with credentials
		Session session = auth.authenticate(username, pw);
		assertThat(session, not(nullValue()));
	}

	@SuppressWarnings("unlikely-arg-type")
	@Test
	public void testAttemptMethods() throws IOException
	{
		Common.getAuthentication().listAccess = new ArrayList<>();
		BasicAuthentication auth = new BasicAuthentication();
		auth.prefix = "basic:";
		auth.name = "Basic";
		Common.getAuthentication().listAccess.add(auth);

		String username = "testuser";
		String pw = "sometest";
		auth.registerUser(username, pw, "email");

		// attempt authentication
		Session session = BasicAuthentication.attemptBasicAuthentication(username, pw);
		assertThat(session, not(nullValue()));

		// incorrect credentials
		assertThat(BasicAuthentication.attemptBasicAuthentication("", pw), is(nullValue()));
		assertThat(BasicAuthentication.attemptBasicAuthentication(username, ""), is(nullValue()));
		assertThat(BasicAuthentication.attemptBasicAuthentication(username, "a"), is(nullValue()));

		// attempt password reset
		String newpw = BasicAuthentication.attemptResetPassword(username);
		assertThat(newpw, not(equals(pw)));
		session = BasicAuthentication.attemptBasicAuthentication(username, newpw);
		assertThat(session, not(nullValue()));

		pw = newpw;
		newpw = BasicAuthentication.attemptResetPassword(auth.prefix + username);
		assertThat(newpw, not(equals(pw)));
		pw = newpw;

		assertThat(BasicAuthentication.attemptResetPassword("unknown"), is(nullValue()));

		// attempt change password
		assertThat(BasicAuthentication.attemptBasicAuthentication(username, pw), not(nullValue()));
		newpw = "mynewpassword";
		assertThat(BasicAuthentication.attemptChangePassword(username, pw, newpw), is(true));
		assertThat(BasicAuthentication.attemptBasicAuthentication(username, pw), is(nullValue()));
		assertThat(BasicAuthentication.attemptBasicAuthentication(username, newpw), not(nullValue()));

		// fails if old password is incorrect
		assertThat(BasicAuthentication.attemptChangePassword(username, pw, newpw), is(false));

		// or too short
		assertThat(BasicAuthentication.attemptChangePassword(username, newpw, "short"), is(false));
	}

	@Test
	public void testResetPasswordEmail() throws EmailException, AddressException
	{
		BasicAuthentication.resetKeys.clear();
		BasicAuthentication auth = new BasicAuthentication();
		auth.prefix = "basic:";
		auth.smtpHost = "localhost";
		auth.smtpPort = 2525;
		DataObject.User user = new DataObject.User();

		assertThrows(EmailException.class, () -> auth.prepareResetPasswordEmail(user, new HtmlEmail()));
		assertThat(BasicAuthentication.resetKeys.size(), is(0));

		user.curatorID = "basic:testuser";
		user.email = "testuser@b.com";
		MockHtmlEmail email = new MockHtmlEmail();
		auth.prepareResetPasswordEmail(user, email);

		assertThat(BasicAuthentication.resetKeys.size(), is(1));
		BasicAuthentication.ResetKey resetKey = BasicAuthentication.resetKeys.get(user.curatorID);
		assertThat(resetKey.key.length(), greaterThan(0));
		assertThat(resetKey.isValid(), is(true));
		assertThat(email.getMsg(), containsString(resetKey.key));
		assertThat(email.getMsg(), containsString("resetkey=basic:"));
		assertThat(email.getToAddresses(), hasItem(new InternetAddress(user.email)));

		// ensure that we always create a different reset key
		for (int i = 0; i < 10; i++)
		{
			auth.prepareResetPasswordEmail(user, email);
			assertThat(BasicAuthentication.resetKeys.size(), is(1));
			assertThat(BasicAuthentication.resetKeys.get(user.curatorID).key, is(not(resetKey.key)));
		}
	}

	private class MockHtmlEmail extends HtmlEmail
	{
		public String getMsg()
		{
			return (String)this.html;
		}
	}
}
