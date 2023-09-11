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
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

public class AccessTest extends TestBaseClass
{
	@Test
	public void testFromJSON() throws JSONSchemaValidatorException
	{
		Map<String, String> map = new HashMap<>();
		map.put("type", "unknown-type");
		
		assertThrows(JSONSchemaValidatorException.class,
				() -> Access.fromJSON(new JSONObject(map)),
				"Exception expected for unknown authentication name");

		String[] common = {"name", "prefix", "status"};

		map.clear();
		for (String field : common) map.put(field, field);
		map.put("type", AccessType.TRUSTED.toString());
		Access access = Access.fromJSON(new JSONObject(map));
		assertThat(access, instanceOf(Trusted.class));

		String[] oauth = {"authURL", "tokenURL", "scope", "responseType", "clientID"};
		map.clear();
		for (String field : common) map.put(field, field);
		for (String field : oauth) map.put(field, field);
		map.put("type", AccessType.OAUTH.toString());
		access = Access.fromJSON(new JSONObject(map));
		assertThat(access, instanceOf(OAuth.class));

		String[] ldap = {"server", "userOU"};
		map.clear();
		for (String field : common) map.put(field, field);
		for (String field : ldap) map.put(field, field);
		map.put("type", AccessType.LDAP.toString());
		access = Access.fromJSON(new JSONObject(map));
		assertThat(access, instanceOf(LDAPAuthentication.class));

		String[] basic = {};
		map.clear();
		for (String field : common) map.put(field, field);
		for (String field : basic) map.put(field, field);
		map.put("type", AccessType.BASIC.toString());
		access = Access.fromJSON(new JSONObject(map));
		assertThat(access, instanceOf(BasicAuthentication.class));
	}
}
