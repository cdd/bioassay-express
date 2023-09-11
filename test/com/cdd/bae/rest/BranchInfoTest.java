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
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for BranchInfo REST API.
*/

public class BranchInfoTest
{
	// ------------ mockito-based tests ------------

	@Test
	public void testBranch() throws IOException, ConfigurationException, RESTException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);

		// find a suitable candidate by rummaging around in the default template
	
		Schema schema = Common.getSchemaCAT();
		assertNotNull(schema);
		
		Schema.Assignment assn = null;
		for (Schema.Assignment look : schema.getRoot().flattenedAssignments()) 
			if (look.suggestions == Schema.Suggestions.FULL) {assn = look; break;}
		assertNotNull(assn);
		
		SchemaTree tree = Common.obtainTree(schema, assn);
		assertNotNull(tree);
		
		SchemaTree.Node node1 = null, node2 = null;
		for (SchemaTree.Node look : tree.getFlat()) 
		{
			if (node1 == null) node1 = look; // node1 = first one (empty branch parent sequence)
			if (node2 == null || look.depth > node2.depth) node2 = look; // node2 = deepest one
		}
		assertNotNull(node1);
		assertNotNull(node2);
		assertNotEquals(node1, node2);
		
		// first test: fetch information about the assignment
		JSONObject request = new JSONObject();
		request.put("schemaURI", schema.getSchemaPrefix());
		request.put("propURI", assn.propURI);
		request.put("groupNest", assn.groupNest());
		JSONObject result = new BranchInfo().processRequest(request, TestUtilities.mockSession());
		JSONObject prop = result.getJSONObject("property");
		assertEquals(assn.name, prop.optString("name", ""));
		assertEquals(assn.descr, prop.optString("descr", ""));
		assertEquals(assn.propURI, prop.optString("propURI", ""));
		assertTrue(Arrays.equals(assn.groupNest(), prop.optJSONArrayEmpty("groupNest").toStringArray()));
		
		// second test: fetch information about both the node values
		request.put("valueURIList", new String[]{node1.uri, node2.uri}); // request is the same as before + values
		result = new BranchInfo().processRequest(request, TestUtilities.mockSession());
		JSONArray branches = result.getJSONArray("branches");
		assertEquals(2, branches.length());
		for (int n = 0; n < 2; n++)
		{
			SchemaTree.Node node = n == 0 ? node1 : node2;
			JSONObject branch = branches.getJSONObject(n);
			assertEquals(node.uri, branch.optString("valueURI", ""));
			assertEquals(node.label, branch.optString("valueLabel", ""));
			assertEquals(node.descr, branch.optString("valueDescr", ""));

			int idx = 0;
			String[] valueHier = branch.getJSONArray("valueHier").toStringArray();
			String[] labelHier = branch.getJSONArray("labelHier").toStringArray();
			for (SchemaTree.Node look = node.parent; look != null; look = look.parent)
			{
				assertEquals(look.uri, valueHier[idx]);
				assertEquals(look.label, labelHier[idx]);
				idx++;
			}
		}
	}

	// ------------ private methods ------------

}
