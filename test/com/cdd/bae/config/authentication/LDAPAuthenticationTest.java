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

import java.io.*;
import java.util.*;

import org.apache.directory.api.ldap.model.exception.*;
import org.apache.directory.api.ldap.model.name.*;
import org.json.*;
import org.junit.jupiter.api.*;

/*
	For testing, setup a local LDAP server.

	1.) Download and install ApacheDirectory Studio
	2.) Start the LDAP server
	3.) Add test user by importing the file `LDAP_testuser.ldif` into ApacheDirectory Studio

*/

public class LDAPAuthenticationTest extends TestBaseClass
{
	@BeforeEach
	public void setUp() throws ConfigurationException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
	}

	@Disabled("Run only with a local LDAP server")
	@Test
	public void testLocalServer() throws IOException
	{
		LDAPAuthentication ldap = new LDAPAuthentication();
		ldap.server = "localhost";
		ldap.port = 10636;
		ldap.userOU = "ou=user,ou=system";

		Session session = ldap.authenticate("testuser", "secretpassword");
		assertThat(session, not(nullValue()));
		assertThat(session.userID, is("testuser"));
		assertThat(session.userName, is("Test User"));
		assertThat(session.email, is("test.user@collaborativedrug.com"));
	}

	@Test
	public void testEncodeForLDAP()
	{
		assertThat(LDAPAuthentication.encodeForLDAP("abcde"), is("abcde"));
		assertThat(LDAPAuthentication.encodeForLDAP(""), is(""));
		assertThat(LDAPAuthentication.encodeForLDAP(" "), is(" "));
		assertThat(LDAPAuthentication.encodeForLDAP(null), is(nullValue()));

		assertThat(LDAPAuthentication.encodeForLDAP("a\\b"), is("a\\5cb"));
		assertThat(LDAPAuthentication.encodeForLDAP("a*b"), is("a\\2ab"));
		assertThat(LDAPAuthentication.encodeForLDAP("a(b"), is("a\\28b"));
		assertThat(LDAPAuthentication.encodeForLDAP("a)b"), is("a\\29b"));
		assertThat(LDAPAuthentication.encodeForLDAP("a\0b"), is("a\\00b"));
	}

	@Test
	public void parseJSON()
	{
		Map<String, String> map = new HashMap<>();
		map.put("name", "joe");
		map.put("prefix", "pfx:");
		map.put("server", "localhost");
		map.put("userOU", "ou=root");
		LDAPAuthentication ldap = new LDAPAuthentication();
		ldap.parseJSON(new JSONObject(map));
		assertThat(ldap.name, is("joe"));
		assertThat(ldap.prefix, is("pfx:"));
		assertThat(ldap.server, is("localhost"));
		assertThat(ldap.userOU, is("ou=root"));
		assertThat(ldap.useSSL, is(true));
		assertThat(ldap.port, is(10636));

		map.put("useSSL", "false");
		map.put("port", "12345");
		ldap = new LDAPAuthentication();
		ldap.parseJSON(new JSONObject(map));
		assertThat(ldap.useSSL, is(false));
		assertThat(ldap.port, is(12345));
	}

	@Test
	public void testEncodeForDN()
	{
		assertThat(LDAPAuthentication.encodeForDN("abcde"), is("abcde"));
		assertThat(LDAPAuthentication.encodeForDN(null), is(nullValue()));
		assertThat(LDAPAuthentication.encodeForDN(""), is(""));
		assertThat(LDAPAuthentication.encodeForDN(" "), is("\\ "));
		assertThat(LDAPAuthentication.encodeForDN("  "), is("\\ \\ "));
		assertThat(LDAPAuthentication.encodeForDN(" a "), is("\\ a\\ "));
		assertThat(LDAPAuthentication.encodeForDN(" a b "), is("\\ a b\\ "));
		assertThat(LDAPAuthentication.encodeForDN("# a b "), is("\\# a b\\ "));

		assertThat(LDAPAuthentication.encodeForDN("a\\b"), is("a\\\\b"));
		assertThat(LDAPAuthentication.encodeForDN("a,b"), is("a\\,b"));
		assertThat(LDAPAuthentication.encodeForDN("a+b"), is("a\\+b"));
		assertThat(LDAPAuthentication.encodeForDN("a\"b"), is("a\\\"b"));
		assertThat(LDAPAuthentication.encodeForDN("a<b"), is("a\\<b"));
		assertThat(LDAPAuthentication.encodeForDN("a>b"), is("a\\>b"));
		assertThat(LDAPAuthentication.encodeForDN("a;b"), is("a\\;b"));
	}

	@Test
	public void testToDistinguishedName() throws LdapException
	{
		LDAPAuthentication ldap = new LDAPAuthentication();
		ldap.userOU = "ou=user,ou=system";

		Dn dn = ldap.toDistinguishedName("abc");
		assertThat(dn.toString(), is("uid=abc,ou=user,ou=system"));

		dn = ldap.toDistinguishedName("a,b,c");
		assertThat(dn.toString(), is("uid=a\\,b\\,c,ou=user,ou=system"));
	}

	@Test
	public void testSanitizePassword()
	{
		assertThat(LDAPAuthentication.sanitizePassword("abc"), is("abc"));
		String s = "\u4E66";
		assertThat(LDAPAuthentication.sanitizePassword(s), is("xn--1jq"));
		s = "∫\u4E66";
		assertThat(LDAPAuthentication.sanitizePassword(s), is("xn--jbh045o"));
		s = "\u4E66∫";
		assertThat(LDAPAuthentication.sanitizePassword(s), is("xn--jbhz45o"));
	}
}
