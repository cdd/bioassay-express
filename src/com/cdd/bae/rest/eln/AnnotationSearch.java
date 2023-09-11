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

package com.cdd.bae.rest.eln;

import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.model.dictionary.*;
import com.cdd.bae.model.dictionary.AnnotationSearchModel.*;
import com.cdd.bae.rest.*;
import com.cdd.bae.rest.RESTException.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import org.json.*;

/*
	AnnotationSearch (Common assay template supported)
	
	Parameters:
		assignment_uri: assignment URI (required)
		query: query string to search (optional, if missing returns the full list)
		
		offset: The index of the first object actually returned. Defaults to 0.
		page_size: The maximum number of objects to return in this call. Default is 10, maximum is 1000.
		
	Return:
		<input parameters>
		count: number of matching annotations
		objects: list of matching hits
			.label: main label 
			.alt_labels: alternative labels
			.value_uri: ontology URI
*/

public class AnnotationSearch extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	private static AnnotationSearchModel search;
	private static FuzzyAnnotationSearchModel fuzzySearch;

	// ------------ public methods ------------

	@Override
	protected String[] getRequiredParameter()
	{
		return new String[]{"assignment_uri"};
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		try
		{
			return autosuggest(input);
		}
		catch (IOException e)
		{
			throw new RESTException(e.getMessage(), HTTPStatus.INTERNAL_SERVER_ERROR);
		}
	}

	// ------------ private methods ------------

	private JSONObject autosuggest(JSONObject input) throws IOException
	{
		if (search == null) initSearchModel();
		
		String propURI = input.getString("assignment_uri").replace("%23", "#");
		String query = input.optString("query", "").toLowerCase();
		int offset = input.optInt("offset", 0);
		int pageSize = input.optInt("page_size", 10);
		
		List<Hit> result = search.search(query, propURI);
		List<Hit> fuzzyResult = new ArrayList<>();
		if (result.size() < 20) 
		{
			Set<String> exactURIs = result.stream().map(h -> h.node.uri).collect(Collectors.toSet()); 
			fuzzyResult = fuzzySearch.search(query, propURI);
			fuzzyResult = fuzzyResult.stream().filter(h -> !exactURIs.contains(h.node.uri)).collect(Collectors.toList());
		}

		JSONArray results = new JSONArray();
		
		if (offset <= result.size())
			serializeResults(result, offset, pageSize, "exact").forEach(results::put);
		if (!fuzzyResult.isEmpty()) 
		{
			int n = Math.min(pageSize - results.length(), fuzzyResult.size());
			serializeResults(fuzzyResult, 0, n, "fuzzy").forEach(results::put);
		}
		
		return new JSONObject(input, JSONObject.getNames(input))
				.put("count", result.size() + fuzzyResult.size())
				.put("objects", results);
	}
	
	private static JSONArray serializeResults(List<Hit> result, int offset, int pageSize, String type)
	{
		JSONArray results = new JSONArray();
		for (Hit hit : result.subList(offset, Math.min(offset + pageSize, result.size())))
			results.put(new JSONObject()
					.put("label", hit.node.label.trim())
					.put("altlabels", hit.node.altLabels == null ? null : String.join(", ", hit.node.altLabels))
					.put("value_uri", hit.node.uri)
					.put("type", type));
		return results;
	}

	private static synchronized void initSearchModel() throws IOException
	{
		if (search != null) return;
		search = new AnnotationSearchModel();
		fuzzySearch = new FuzzyAnnotationSearchModel(1.5);
	}
	
	protected static List<Hit> prioritizeNearExactMatches(List<Hit> hits, String query)
	{
		List<List<Hit>> subset = new ArrayList<>(3);
		for (int i = 0; i < subset.size(); i++) subset.add(new ArrayList<>());
		
		int nmin = query.length() + 2;
		query = query.toLowerCase();
		for (Hit hit : hits)
			if (hit.node.label.trim().length() < nmin)
				subset.get(0).add(hit);
			else if (hit.node.label.toLowerCase().startsWith(query.toLowerCase()))
				subset.get(1).add(hit);
			else 
				subset.get(2).add(hit);
		List<Hit> result = subset.get(0);
		result.addAll(subset.get(1));
		result.addAll(subset.get(2));
		return result;
	}

}
