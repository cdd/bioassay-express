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
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for GetPropertyList REST API.
*/

public class GetPropertyListTest
{
	private GetPropertyList getPropertyList;

	@BeforeEach
	public void prepare() throws ConfigurationException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		getPropertyList = new GetPropertyList();
		getPropertyList.logger = TestUtilities.mockLogger();
	}

	// ------------ mockito-based tests ------------

	@Test
	public void testLocator() throws IOException
	{
		// examine CAT so we can make assumptions about what's meant to come back
		Schema schema = Common.getSchemaCAT();
		Schema.Assignment[] assnList = schema.findAssignmentByProperty(ModelSchema.expandPrefix("bao:BAO_0002854")); // bioassay type
		assertEquals(1, assnList.length);
		String locator = schema.locatorID(assnList[0]);
		Set<String> containers = new HashSet<>();
		for (Schema.Value value : assnList[0].values) if (value.spec == Schema.Specify.CONTAINER) containers.add(value.uri);
		int reqSize = 0;
		for (SchemaTree.Node node : Common.obtainTree(schema, assnList[0]).getFlat())
			if (!containers.contains(node.uri)) reqSize++;
	
		// fetch list items for this one branch, using locator
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters(locator, null));
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		HttpServletResponse response = mockResponse.getResponse();
		getPropertyList.doPost(request, response);

		JSONObject json = mockResponse.getContentAsJSON();
		assertTrue(json.has("list"));
		JSONArray list = json.getJSONArray("list");
		assertEquals(reqSize, list.length());
	}

	@Test
	public void testPropURI() throws IOException
	{
		// we get information using GET request without parameter
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters(null, "http://www.bioassayontology.org/bao#BAO_0000205"));
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		HttpServletResponse response = mockResponse.getResponse();
		getPropertyList.doPost(request, response);

		JSONObject json = mockResponse.getContentAsJSON();
		assertTrue(json.has("list"));
		JSONArray list = json.getJSONArray("list");
		assertEquals(23, list.length()); // adjust number if CAT schema changes
	}

	@Test
	public void testListFilterContainerTerms() throws IOException
	{
		// we get information using GET request without parameter
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters(null, "http://www.bioassayontology.org/bao#BAO_0002854"));
		MockRESTUtilities.MockJSONResponse mockResponse = new MockRESTUtilities.MockJSONResponse();
		HttpServletResponse response = mockResponse.getResponse();
		getPropertyList.doPost(request, response);

		JSONObject json = mockResponse.getContentAsJSON();
		assertTrue(json.has("list"));

		JSONArray arr = json.getJSONArray("list");
		List<String> names = new ArrayList<>();
		for (int n = 0; n < arr.length(); n++)
			names.add(arr.getJSONObject(n).getString("name"));
		assertThat(names, not(hasItem("bioassay type")));
	}

	// ------------ private methods ------------

	private static Map<String, String> getParameters(String locator, String propURI)
	{
		Map<String, String> parameters = new HashMap<>();
		parameters.put("schemaURI", "http://www.bioassayontology.org/bas#"); // get the test schema
		if (locator != null) parameters.put("locator", locator);
		if (propURI != null) parameters.put("propURI", propURI);
		return parameters;
	}
}
