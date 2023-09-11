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

import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.RESTException.*;
import com.cdd.bao.util.*;
import com.cdd.bao.template.*;

import org.json.*;

/*
	AutomapKeywords: given context information for some number of keywords, returns any previously established
	association to an existing term or assignment.
	
	Parameters:
		schemaURI
		propURI, groupNest (if not given, will match assignments instead of values)
		keywordList (array of strings)
		
	Return:
		either:
			assignments: [{propURI, groupNest}] for assignments
		or
			values: [{valueURI, valueLabel}] for values

		(where an entry in the array is null if nothing was found)
*/

public class AutomapKeywords extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------


	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String schemaURI = input.getString("schemaURI");
		String propURI = input.optString("propURI", null);
		String[] groupNest = input.optJSONArrayEmpty("groupNest").toStringArray();
		String[] keywordList = input.getJSONArray("keywordList").toStringArray();
		
		String[] valueURIList = new String[keywordList.length], valueLabelList = new String[keywordList.length];
		
		Schema schema = Common.getSchema(schemaURI);
		if (schema == null) throw new RESTException("Schema not found: " + schemaURI, HTTPStatus.BAD_REQUEST);
		
		JSONObject result = new JSONObject();
		if (propURI == null)
			mapAssignments(result, schema, keywordList);
		else
			mapValues(result, schema, propURI, groupNest, keywordList);
		return result;
	}

	// ------------ private methods ------------

	private void mapAssignments(JSONObject result, Schema schema, String[] keywordList) throws RESTException
	{
		DataStore store = Common.getDataStore();
		
		JSONObject[] assignments = new JSONObject[keywordList.length];
		
		for (int n = 0; n < assignments.length; n++)
		{
			DataObject.KeywordMap map = store.keywordMap().getKeywordMapAssignment(schema.getSchemaPrefix(), keywordList[n]);
			if (map == null) continue; // leave return entries blank
			
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(map.propURI, map.groupNest);
			if (assnList.length == 0)
			{
				// leave both array entries blank, and purge the entry from the database because it's no longer valid
				store.keywordMap().deleteKeywordMap(map.keywordmapID);
				continue;
			}
			
			assignments[n] = new JSONObject();
			assignments[n].put("propURI", assnList[0].propURI);
			assignments[n].put("groupNest", assnList[0].groupNest());
		}
		
		result.put("assignments", assignments);
	}

	private void mapValues(JSONObject result, Schema schema, 
						   String propURI, String[] groupNest, String[] keywordList) throws RESTException
	{			
		Schema.Assignment[] assnList = schema.findAssignmentByProperty(propURI, groupNest);
		if (assnList.length == 0) 
			throw new RESTException("Assignment not found: " + propURI + "/" + Util.arrayStr(groupNest), HTTPStatus.BAD_REQUEST);
		SchemaTree tree = Common.obtainTree(schema, assnList[0]); // could be >1 if under-specified groupNest, but should be OK
		
		DataStore store = Common.getDataStore();

		JSONObject[] values = new JSONObject[keywordList.length];

		for (int n = 0; n < keywordList.length; n++)
		{
			DataObject.KeywordMap map = store.keywordMap().getKeywordMapValue(schema.getSchemaPrefix(), propURI, groupNest, keywordList[n]);
			if (map == null) continue; // leave return entries blank
			
			SchemaTree.Node node = tree.getNode(map.valueURI);
			if (node == null)
			{
				// leave both array entries blank, and purge the entry from the database because it's no longer valid
				store.keywordMap().deleteKeywordMap(map.keywordmapID);
				continue;
			}
			
			values[n] = new JSONObject();
			values[n].put("valueURI", node.uri);
			values[n].put("valueLabel", node.label);
		}
		
		result.put("values", values);
	}
}
