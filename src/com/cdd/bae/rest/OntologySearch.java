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

import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;

import com.cdd.bae.config.InitParams.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

/*
	OntologySearch: performs a keyword search on the whole ontology, without having to download the whole thing
	
	Parameters:
		type: either "property" or "value"
		query: string
		caseSensitive: (default = false)
		maxResults: (default = 100)
*/

public class OntologySearch extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;
	
	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		var type = input.optString("type", "");
		OntologyTree onto = null;
		if (type.equals("property")) onto = Common.getOntoProps();
		else if (type.equals("value")) onto = Common.getOntoValues();
		else throw RESTException.bad("Must provide valid 'type' parameter.");
		
		String query = input.getString("query");
		boolean caseSensitive = input.optBoolean("caseSensitive", false);
		int maxResults = input.optInt("maxResults", 100);
		
		if (!caseSensitive) query = query.toLowerCase();
		
		List<OntologyTree.Branch> matchList = new ArrayList<>();
		for (var root : onto.getRoots()) collectMatches(matchList, root, query, caseSensitive, maxResults, onto);
		
		var jsonMatches = new JSONArray();
		for (var branch : matchList)
		{
			List<String> hierarchy = new ArrayList<>();
			for (var look = branch.parent; look != null; look = look.parent) hierarchy.add(look.uri);
		
			var json = new JSONObject();
			json.put("uri", branch.uri);
			json.put("label", branch.label);
			json.put("hierarchy", hierarchy);
			jsonMatches.put(json);
		}

		return new JSONObject().put("matches", jsonMatches);
	}

	// ------------ private methods ------------
	
	private void collectMatches(List<OntologyTree.Branch> matchList, OntologyTree.Branch branch, String query, boolean caseSensitive, int maxResults, OntologyTree onto)
	{
		if (matchList.size() >= maxResults) return;
		
		boolean matched = false;
		matched = (caseSensitive ? branch.label : branch.label.toLowerCase()).contains(query);
		matched = matched || (caseSensitive ? branch.uri : branch.uri.toLowerCase()).contains(query);
		
		if (!matched)
		{
			var altLabels = onto.getAltLabels(query);
			if (altLabels != null) for (String label : altLabels)
				matched = matched || (caseSensitive ? label : label.toLowerCase()).contains(query);
		}
			
		if (matched) matchList.add(branch);
		
		for (var child : branch.children) collectMatches(matchList, child, query, caseSensitive, maxResults, onto);
	}
}


