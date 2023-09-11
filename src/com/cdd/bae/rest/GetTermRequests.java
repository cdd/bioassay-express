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
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.util.*;
import java.util.stream.*;

import org.json.*;

/*
	GetTermRequests: get list of term requests.

	Parameters:
		status: (optional) retrieve requests whose status matches this string; null means grab everything.

	Return:
		prefix: string containing prefix abbreviation for term requests.
		uri: string containing URI stem for term requests. 
		list: array of
			provisionalID
			parentURI
			label
			uri
			description
			explanation
			status
			proposerID
			createdTime
			modifiedTime
			remappedTo
			bridge: (null if not active)
				token
				url
				name
				description
*/

public class GetTermRequests extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected boolean requireSession()
	{
		return false;
	}

	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();
		String status = input.optString("status");

		DataObject.Provisional[] provisionals = store.provisional().fetchAllTerms();
		Map<String, String> labelMap = new HashMap<>();
		for (DataObject.Provisional prov : provisionals) labelMap.put(prov.uri, prov.label);
		
		Map<String, Integer> usage1 = usageAssays(provisionals, store);
		Map<String, Integer> usage2 = usageHoldings(provisionals, store);

		JSONArray list = new JSONArray();
		for (DataObject.Provisional prov : provisionals) 
		{
			if (Util.notBlank(status) && !status.equals(prov.bridgeStatus)) continue;
			list.put(formulateProvisional(store, prov, labelMap, usage1.get(prov.uri), usage2.get(prov.uri)));
		}

		JSONObject result = new JSONObject();
		result.put("list", list);
		return result;
	}
	
	public static Map<String, Integer> usageAssays(Provisional[] provisionals, DataStore store)
	{
		Set<String> provURIs = Arrays.stream(provisionals).map(p -> p.uri).collect(Collectors.toSet());
		
		Map<String, Integer> counts = new HashMap<>();
		for (String provURI : provURIs) 
		{
			long[] assayIDs = store.assay().fetchCuratedAssayIDWithAnnotation(provURI);
			counts.put(provURI, assayIDs.length);
		}
		return counts;
	}
	
	public static Map<String, Integer> usageHoldings(Provisional[] provisionals, DataStore store)
	{
		Set<String> provURIs = Arrays.stream(provisionals).map(p -> p.uri).collect(Collectors.toSet());
		
		Map<String, Integer> counts = new HashMap<>();
		for (String provURI : provURIs) 
		{
			long[] assayIDs = store.holding().fetchHoldingsByAnnotation(provURI);
			counts.put(provURI, assayIDs.length);
		}
		return counts;
	}
	
	// packages up a provisional object as JSON
	public static JSONObject formulateProvisional(DataStore store, DataObject.Provisional prov, Map<String, String> labelMap)
	{
		return formulateProvisional(store, prov, labelMap, null, null);
	}
	
	public static JSONObject formulateProvisional(DataStore store, DataObject.Provisional prov, Map<String, String> labelMap, Integer countAssays, Integer countHoldings)
	{
		JSONObject json = new JSONObject();
		
		json.put("provisionalID", prov.provisionalID);
		json.put("parentURI", prov.parentURI);
		
		String parentLabel = labelMap.get(prov.parentURI);
		if (parentLabel == null) parentLabel = Common.getOntoProps().getLabel(prov.parentURI);
		json.put("parentLabel", parentLabel);
		json.put("label", prov.label);
		json.put("uri", prov.uri);
		json.put("description", prov.description);
		json.put("explanation", prov.explanation);
		json.put("proposerID", prov.proposerID);
		DataObject.User user = store.user().getUser(prov.proposerID);
		if (user != null) json.put("proposerName", user.name);
		if (prov.role != null) json.put("role", prov.role.toString());
		json.put("createdTime", prov.createdTime.getTime());
		json.put("modifiedTime", prov.modifiedTime.getTime());
		json.put("remappedTo", prov.remappedTo);
		json.put("bridgeStatus", prov.bridgeStatus);
		if (countAssays != null) json.put("countAssays", countAssays);
		if (countHoldings != null) json.put("countHoldings", countHoldings);
		
		JSONObject jsonBridge = formulateOntolobridgeDetails(prov);
		if (jsonBridge != null) json.put("bridge", jsonBridge);	
		
		return json;
	}
	
	// wraps up the details about the ontolobridge connection, if any: involves cross referencing with configuration settings
	public static JSONObject formulateOntolobridgeDetails(DataObject.Provisional prov)
	{
		if (prov.bridgeURL == null || prov.bridgeToken == null) return null;

		InitParams.OntoloBridge[] bridges = Common.getConfiguration().getOntoloBridges();
		if (bridges == null) return null;
		InitParams.OntoloBridge bridge = null;
		for (InitParams.OntoloBridge look : bridges) if (prov.bridgeURL.equals(look.baseURL)) {bridge = look; break;}

		JSONObject json = new JSONObject();
		json.put("token", prov.bridgeToken);
		json.put("url", prov.bridgeURL);
		if (bridge != null)
		{
			json.put("name", bridge.name);
			json.put("description", bridge.description);
		}
		else // error state
		{
			json.put("name", "?");
			json.put("description", "?");
		}
		return json;
	}
}
