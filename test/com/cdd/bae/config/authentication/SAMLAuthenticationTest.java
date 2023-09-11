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

import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	This is only a stub. The associated test file contains information about
	the setup of a developer machine. Actual implementation is postponed until
	require one.

	SAML test server: https://hub.docker.com/r/kristophjunge/test-saml-idp/

	Run a local SAML server using the following docker command

	docker run --name=testsamlidp_idp -p 7080:8080 -p 8443:8443 \
		-e SIMPLESAMLPHP_SP_ENTITY_ID=saml-poc \
		-e SIMPLESAMLPHP_SP_ASSERTION_CONSUMER_SERVICE=http://localhost/simplesaml/module.php/saml/sp/saml2-acs.php/test-sp \
		-e SIMPLESAMLPHP_SP_SINGLE_LOGOUT_SERVICE=http://localhost/simplesaml/module.php/saml/sp/saml2-logout.php/test-sp \
		kristophjunge/test-saml-idp

	server defines two users:
	 - user1 / user1pass / user1@example.com
	 - user2 / user2pass / user2@example.com

	entityID is usually a globally unique name (often defined as a URL)

	https://medium.com/disney-streaming/setup-a-single-sign-on-saml-test-environment-with-docker-and-nodejs-c53fc1a984c9

	assertionConsumerService is the URL to redirect if login was successful
*/

public class SAMLAuthenticationTest extends TestBaseClass
{
	// @Disabled("Run only with a local SAML server")
	@Test
	public void testLocalServer() throws IOException
	{
		SAMLAuthentication saml = new SAMLAuthentication();
		saml.name = "saml";
		saml.prefix = "saml:";
		saml.entityID = "saml-poc";
		saml.assertionConsumerService = "http://localhost:7080/simplesaml/module.php/core/loginuserpass.php";
		saml.metadataService = "http://localhost:7080/simplesaml/saml2/idp/metadata.php";

		saml.authenticate("gedeck", "changeme");
	}

	@Test
	public void parseJSON()
	{
		Map<String, String> map = new HashMap<>();
		map.put("name", "saml");
		map.put("prefix", "saml:");
		map.put("entityID", "http://entity.com");
		map.put("assertionConsumerService", "http://acs.com/saml/consume");
		map.put("singleLogoutService", "http://acs.com/saml/logout");
		SAMLAuthentication saml = new SAMLAuthentication();
		saml.parseJSON(new JSONObject(map));
		assertThat(saml.name, is("saml"));
		assertThat(saml.prefix, is("saml:"));
		assertThat(saml.entityID, is("http://entity.com"));
		assertThat(saml.assertionConsumerService, is("http://acs.com/saml/consume"));
		assertThat(saml.singleLogoutService, is("http://acs.com/saml/logout"));
	}
}
