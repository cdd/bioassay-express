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

//import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.tasks.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

/*
	Test for GetPropertyTree REST API.
*/

public class GetPropertyTreeTest extends EndpointEmulator
{
	private static final String[] TREE_INFO = 
			{"depth", "parent", "name", "abbrev", "inSchema", "provisional", "schemaCount",
			"childCount", "inModel", "altLabels", "externalURLs", "containers", "prefixMap"}; 
	private static final String[] NODE_DATA = 
			{"depth", "parent", "name", "abbrev", "inSchema", "provisional", "schemaCount",
			"childCount", "inModel", "altLabels", "externalURLs"};

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		TestUtilities.ensureOntologies();
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
		Common.setProvCache(ProvisionalCache.loaded());

		setRestService(new GetPropertyTree());
	}

	// ------------ mockito-based tests ------------

	@Test
	public void testLocator() throws IOException
	{
		JSONObject json = doPost(getParameters("0", null)).getContentAsJSON();
		assertTreeInfoPresent(json);
	}

	@Test
	public void testPropURI() throws IOException
	{
		JSONObject json = doPost(getParameters(null, ModelSchema.PFX_BAO + "BAO_0000205")).getContentAsJSON();
		assertTreeInfoPresent(json);
	}

	@Test
	public void testTreeWithContainerTermIndices() throws IOException
	{
		JSONObject json = doPost(getParameters(null, PubChemAssays.ASSAYTYPE_URI)).getContentAsJSON();
		assertTreeInfoPresent(json);
	}
	
	@Test
	public void testExceptions() throws IOException
	{
		JSONObject json = doPost(getParameters(null, "unknownPropURI")).getContentAsJSON();
		assertThat(json.has("status"), is(true));
		assertThat(json.getInt("status"), is(RESTException.HTTPStatus.BAD_REQUEST.code()));

		json = doPost(getParameters("123456789", null)).getContentAsJSON();
		assertThat(json.has("status"), is(true));
		assertThat(json.getInt("status"), is(RESTException.HTTPStatus.BAD_REQUEST.code()));
		
		json = doPost(getParameters(null, null)).getContentAsJSON();
		assertThat(json.has("status"), is(true));
		assertThat(json.getInt("status"), is(RESTException.HTTPStatus.BAD_REQUEST.code()));
	}

	// ------------ private methods ------------
	
	private static void assertTreeInfoPresent(JSONObject json)
	{	
		assertThat(json.has("tree"), is(true));
		JSONObject tree = json.getJSONObject("tree");
		
		assertThat(tree.keySet(), containsInAnyOrder(TREE_INFO));
		
		// check consistency of a few fields
		int nnodes = tree.getJSONArray(NODE_DATA[0]).length();
		for (String key : NODE_DATA) 
			assertThat(tree.getJSONArray(key).length(), is(nnodes));
		
		// containers contains the indices of nodes with parent -1 or depth 0
		List<Integer> containers = new ArrayList<>();
		JSONArray parents = tree.getJSONArray("parent");
		JSONArray depths = tree.getJSONArray("depth");
		for (int i = 0; i < nnodes; i++)
		{
			int depth = depths.getInt(i);
			int parent = parents.getInt(i);
			if (depth > 0) assertThat(parent, is(greaterThan(-1)));
			if (parent > -1) assertThat(depth, is(greaterThan(0)));
			if (depth == 0) containers.add(i);
		}
		int[] found = tree.getJSONArray("containers").toIntArray();
		assertThat(found, is(Util.primInt(containers)));
	}

	private static Map<String, String> getParameters(String locator, String propURI)
	{
		Map<String, String> parameters = new HashMap<>();
		parameters.put("schemaURI", "http://www.bioassayontology.org/bas#"); // get the test schema
		if (locator != null) parameters.put("locator", locator);
		if (propURI != null) parameters.put("propURI", propURI);
		return parameters;
	}
}
