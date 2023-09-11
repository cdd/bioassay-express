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

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.apache.commons.lang3.*;
import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for AutomapKeyword and AutomapDefine REST API. 
	
	Unlike most tests, this one covers two API classes, because it writes first and reads second.
*/

public class AutomapKeywordsTest extends EndpointEmulator
{
	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
	}

	// ------------ faux-mongo tests ------------

	@Test
	public void testAutomapValues() throws Exception
	{
		AutomapDefine automapDefine = new AutomapDefine();
		AutomapKeywords automapKeywords = new AutomapKeywords();
	
		// check permissions first
		assertFalse(automapDefine.hasPermission(TestUtilities.mockSession()));
		assertTrue(automapDefine.hasPermission(TestUtilities.mockSessionCurator()));
		assertTrue(automapKeywords.hasPermission(TestUtilities.mockSession()));
	
		// find the first assignment with at least 3 values in it

		Schema schema = Common.getSchemaCAT();
		assertNotNull(schema);
		Schema.Assignment assn = null;
		SchemaTree.Node[] nodes = null;
		for (Schema.Assignment look : schema.getRoot().flattenedAssignments()) if (look.suggestions == Schema.Suggestions.FULL) 
		{
			SchemaTree tree = Common.obtainTree(schema, look);
			if (tree == null || tree.getTree().size() < 3) continue;
			assn = look;
			nodes = tree.getList();
			break;
		}
		assertNotNull(assn);
		assertNotNull(nodes);

		// define some mappings
		String[] keywords = new String[]{"ning", "nang", "nong"};
		JSONArray defMappings = new JSONArray();
		for (int n = 0; n < 3; n++)
		{
			JSONObject json = new JSONObject();
			json.put("schemaURI", schema.getSchemaPrefix());
			json.put("propURI", assn.propURI);
			json.put("groupNest", assn.groupNest());
			json.put("keyword", keywords[n]);
			json.put("valueURI", nodes[n].uri);
			defMappings.put(json);
		}
		JSONObject request = new JSONObject();
		request.put("mappings", defMappings);
		JSONObject result = automapDefine.processRequest(request, TestUtilities.mockSessionCurator());		
		assertTrue(result.getBoolean("success"));
	
		// 2 of 3 keywords are expected to match
		verifyQueryValues(schema, assn, new String[]{keywords[0], keywords[1], "fubar"}, new SchemaTree.Node[]{nodes[0], nodes[1], null});

		// try with a different assignment, which should find nothing
		Schema.Assignment altAssn = null;
		for (Schema.Assignment look : schema.getRoot().flattenedAssignments()) if (look != assn && look.suggestions == Schema.Suggestions.FULL)
		{
			altAssn = look;
			break;
		}
		verifyQueryValues(schema, altAssn, new String[]{keywords[0]}, new SchemaTree.Node[]{null});
		
		// un-define the first keyword
		JSONArray undefMappings = new JSONArray();
		for (int n = 0; n < 1; n++)
		{
			JSONObject json = new JSONObject();
			json.put("schemaURI", schema.getSchemaPrefix());
			json.put("propURI", assn.propURI);
			json.put("groupNest", assn.groupNest());
			json.put("keyword", keywords[n]);
			undefMappings.put(json);
		}
		request = new JSONObject();
		request.put("deletions", undefMappings);
		result = automapDefine.processRequest(request, TestUtilities.mockSessionCurator());		
		assertTrue(result.getBoolean("success"));
		
		// try again, expecting that the first keyword no longer maps		
		verifyQueryValues(schema, assn, new String[]{keywords[0], keywords[1]}, new SchemaTree.Node[]{null, nodes[1]});
	}

	@Test
	public void testAutomapAssignments() throws Exception
	{	
		AutomapDefine automapDefine = new AutomapDefine();
		AutomapKeywords automapKeywords = new AutomapKeywords();
	
		Schema schema = Common.getSchemaCAT();
		Schema.Assignment[] assnList = ArrayUtils.subarray(schema.getRoot().flattenedAssignments(), 0, 3);

		// define some mappings
		String[] keywords = new String[]{"once", "upon", "a time"};
		JSONArray defMappings = new JSONArray();
		for (int n = 0; n < 3; n++)
		{
			JSONObject json = new JSONObject();
			json.put("schemaURI", schema.getSchemaPrefix());
			json.put("keyword", keywords[n]);
			json.put("propURI", assnList[n].propURI);
			json.put("groupNest", assnList[n].groupNest());
			defMappings.put(json);
		}
		JSONObject request = new JSONObject();
		request.put("mappings", defMappings);
		JSONObject result = automapDefine.processRequest(request, TestUtilities.mockSessionCurator());		
		assertTrue(result.getBoolean("success"));
	
		// 2 of 3 keywords are expected to match
		verifyQueryAssignments(schema, new String[]{keywords[0], keywords[1], "fubar"}, 
							   new Schema.Assignment[]{assnList[0], assnList[1], null});

		// un-define the first keyword
		JSONArray undefMappings = new JSONArray();
		for (int n = 0; n < 1; n++)
		{
			JSONObject json = new JSONObject();
			json.put("schemaURI", schema.getSchemaPrefix());
			json.put("keyword", keywords[n]);
			undefMappings.put(json);
		}
		request = new JSONObject();
		request.put("deletions", undefMappings);
		result = automapDefine.processRequest(request, TestUtilities.mockSessionCurator());		
		assertTrue(result.getBoolean("success"));
		
		// try again, expecting that the first keyword no longer maps
		verifyQueryAssignments(schema, new String[]{keywords[0], keywords[1]}, 
							   new Schema.Assignment[]{null, assnList[1]});		
	}

	// ------------ private methods ------------
	
	private void verifyQueryValues(Schema schema, Schema.Assignment assn, String[] keywords, SchemaTree.Node[] nodes) throws RESTException
	{
		JSONObject request = new JSONObject();
		request.put("schemaURI", schema.getSchemaPrefix());
		request.put("propURI", assn.propURI);
		request.put("groupNest", assn.groupNest());
		request.put("keywordList", keywords);
		JSONObject results = new AutomapKeywords().processRequest(request, TestUtilities.mockSession());
		JSONObject[] values = results.getJSONArray("values").toObjectArray();
		
		assertEquals(keywords.length, values.length);
		for (int n = 0; n < keywords.length; n++)
		{
			if (nodes[n] == null)
			{
				assertNull(values[n]);
			}
			else
			{
				assertNotNull(values[n]);
				assertEquals(nodes[n].uri, values[n].getString("valueURI"));
				assertEquals(nodes[n].label, values[n].getString("valueLabel"));
			}
		}
	}

	private void verifyQueryAssignments(Schema schema, String[] keywords, Schema.Assignment[] assnLook) throws RESTException
	{
		JSONObject request = new JSONObject();
		request.put("schemaURI", schema.getSchemaPrefix());
		request.put("keywordList", keywords);
		JSONObject results = new AutomapKeywords().processRequest(request, TestUtilities.mockSession());
		JSONObject[] assnFind = results.getJSONArray("assignments").toObjectArray();
		
		assertEquals(keywords.length, assnFind.length);
		for (int n = 0; n < keywords.length; n++)
		{
			if (assnLook[n] == null)
			{
				assertNull(assnFind[n]);
			}
			else
			{
				assertNotNull(assnFind[n]);
				assertEquals(assnLook[n].propURI, assnFind[n].getString("propURI"));
				assertArrayEquals(assnLook[n].groupNest(), assnFind[n].getJSONArray("groupNest").toStringArray());
			}
		}
	}
}
