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
import com.cdd.bae.model.assocrules.*;
import com.cdd.bae.model.dictionary.*;
import com.cdd.bae.rest.*;
import com.cdd.bae.rest.RESTException.*;

import java.io.*;
import java.util.*;
import java.util.Map.*;

import org.json.*;

/*
	AnnotationRules (Common assay template supported)
	
	Parameters:
		annotations: list of existing annotations as value_uri (required)
		
	Return:
		<input parameters>
		count: number of matching annotations
		objects: list of matching hits
			.assignment_uri: URI of assignment for which value is applicable
			.label: main label 
			.alt_labels: alternative labels
			.value_uri: ontology URI
*/

public class AnnotationRules extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	private static ARModel arModel;  

	// ------------ public methods ------------

	@Override
	protected String[] getRequiredParameter()
	{
		return new String[]{"annotations"};
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		try
		{
			return rulessuggestions(input);
		}
		catch (IOException e)
		{
			throw new RESTException(e.getMessage(), HTTPStatus.INTERNAL_SERVER_ERROR);
		}
	}

	// ------------ private methods ------------

	private JSONObject rulessuggestions(JSONObject input) throws IOException
	{
		initModels();
		
		Map<String, List<ScoredHit>> result = getARModelPredictions(input.getJSONArray("annotations"));

		JSONArray results = new JSONArray();
		for (Entry<String, List<ScoredHit>> assnResult : result.entrySet())
		{
			String assnURI = assnResult.getKey();
			for (ScoredHit hit : assnResult.getValue())
			{
				results.put(new JSONObject()
						.put("assignment_uri", assnURI)
						.put("label", hit.hit.label.trim())
						.put("altlabels", hit.hit.altLabels == null ? null : String.join(", ", hit.hit.altLabels))
						.put("value_uri", hit.hit.uri));
			}
		}
		return new JSONObject(input, JSONObject.getNames(input))
				.put("count", results.length())
				.put("objects", results);
	}

	private static synchronized void initModels() throws IOException
	{
		if (arModel == null) arModel = ARModel.loadDefaultModel();
	}
	
	protected Map<String, List<ScoredHit>> getARModelPredictions(JSONArray annotations)
	{
		String[] valueURIs = new String[annotations.length()];
		for (int i = 0; i < annotations.length(); i++) valueURIs[i] = annotations.getString(i);
		return arModel.predict(valueURIs);
	}
}
