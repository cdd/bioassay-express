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

import org.json.*;

/*
	QueryAssays: pass all assays through the query string and return IDs for all that match.
	
	Parameters:
		query: line notation (see QueryAssay for details on format)
		schemaURI: optional schema restriction
		
*/

public class ListQueryAssays extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		QueryAssay query = parseQuery(input.getString("query"));
		String schemaURI = input.optString("schemaURI", null);

		DataStore store = Common.getDataStore();

		long[] assayIDList = null;
		if (schemaURI == null)
			assayIDList = store.assay().fetchAssayIDCurated();
		else
			assayIDList = store.assay().fetchAssayIDWithAnnotationsSchema(schemaURI);

		JSONArray jsonResults = new JSONArray();
		for (long assayID : assayIDList)
		{
			DataObject.Assay assay = store.assay().getAssay(assayID);
			if (query.matchesAssay(assay)) jsonResults.put(assayID);
		}

		return new JSONObject().put("assayIDList", jsonResults);
	}
	
	// ------------ private methods ------------
	
	private QueryAssay parseQuery(String queryString) throws RESTException
	{
		try
		{
			return QueryAssay.parse(queryString);
		}
		catch (QueryAssay.Fail e)
		{
			throw new RESTException(e, "Query is incorrect: " + e.getMessage(), HTTPStatus.BAD_REQUEST);
		}
	}
}
