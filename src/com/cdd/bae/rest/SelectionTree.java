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
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import org.json.*;

/*
	SelectionTree: given some number of properties that have been identified, for each of them returns a list of values that are 
	applicable (i.e. that prop/value pair must occur at least once). Beyond the first selection, the applicable values are restricted
	to just those that passed the previous test.
	
	Parameters:
		schemaURI: choice of schema
		select: an order-important list of:
			.propURI (special values: "FULLTEXT", "KEYWORD", "IDENTIFIER")
			.groupNest
			.valueURIList (array: empty = no filtering; special value: "EMPTY")
			.keywordSelector (optional)
		withUncurated: (default: false) optionally include uncurated assays in the search
*/

public class SelectionTree extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;
	
	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String schemaURI = input.getString("schemaURI");
		JSONArray select = input.getJSONArray("select");
		boolean withUncurated = input.optBoolean("withUncurated", false);
		
		return performSelection(Common.getSchema(schemaURI), select, withUncurated);
	}
		
	protected JSONObject performSelection(Schema schema, JSONArray select, boolean withUncurated)
	{
		WinnowTree winnow = new WinnowTree(schema, parseSelectionLayers(select), withUncurated);
		winnow.perform();

		JSONObject result = new JSONObject();
		
		JSONArray treeList = new JSONArray(), literalList = new JSONArray();
		for (WinnowTree.Result wr : winnow.getResults())
		{
			treeList.put(formatNodeResults(wr.nodes));
			literalList.put(formatLiteralResults(wr.literals));
		}
		
		result.put("treeList", treeList);
		result.put("literalList", literalList);
		
		long[] assayIDList = winnow.getMatched();
		result.put("matchesAssayID", assayIDList);
		result.put("matchesUniqueID", Common.getDataStore().assay().uniqueIDFromAssayID(assayIDList));
		return result;
	}
	
	// pull out the user-specified selection criteria
	public static WinnowTree.Layer[] parseSelectionLayers(JSONArray select) throws JSONException
	{
		if (select == null) return null;
		
		WinnowTree.Layer[] layers = new WinnowTree.Layer[select.length()];
		for (int n = 0; n < select.length(); n++)
		{
			layers[n] = new WinnowTree.Layer();
			JSONObject obj = select.getJSONObject(n);
			layers[n].propURI = obj.getString("propURI");
			layers[n].groupNest = obj.optJSONArrayEmpty("groupNest").toStringArray();
			layers[n].valueURIList = obj.optJSONArrayEmpty("valueURIList").toStringArray();
			layers[n].keywordSelector = obj.optString("keywordSelector");
		}
		return layers;
	}

	// ------------ private methods ------------

	private JSONArray formatNodeResults(WinnowTree.NodeResult[] nodes)
	{
		JSONArray list = new JSONArray();
		if (nodes == null) return list;
		
		for (WinnowTree.NodeResult node : nodes)
		{
			JSONObject obj = new JSONObject();
			obj.put("depth", node.depth);
			obj.put("parent", node.parent);
			obj.put("uri", node.uri);
			obj.put("name", node.name);
			obj.put("count", node.count);
			obj.put("childCount", node.childCount);
			obj.put("totalCount", node.totalCount);
			obj.put("curatedCount", node.curatedCount);
			list.put(obj);
		}
		
		return list;
	}
	
	private JSONArray formatLiteralResults(WinnowTree.LiteralResult[] literals)
	{
		JSONArray list = new JSONArray();
		if (literals == null) return list;

		for (WinnowTree.LiteralResult literal : literals)
		{
			JSONObject obj = new JSONObject();
			obj.put("label", literal.label);
			obj.put("count", literal.count);
			list.put(obj);
		}
		
		return list;
	}
}
