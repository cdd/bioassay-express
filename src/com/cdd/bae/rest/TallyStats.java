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

import org.json.*;

/*
	TallyStats: requests one or more tokens which represent a "stat" that is to be computed and returned. The result is a dictionary
	that contains a response for each request.
	
	Parameters:
	
		tokens: array containing any valid token (see below)
		
	Return:
	
		dictionary of {token: value} corresponding to the inputs
*/

public class TallyStats extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();
		
		String[] tokens = input.getJSONArray("tokens").toStringArray();
		
		JSONObject result = new JSONObject();
		for (String token : tokens)
		{
			if (token.equals("numAssays")) result.put(token, store.assay().fetchAllAssayID().length);
			else if (token.equals("curatedAssays")) result.put(token, store.assay().fetchAllCuratedAssayID().length);
			else if (token.equals("assaysWithoutFP")) result.put(token, store.assay().fetchAssayIDWithoutFP().length);
			else if (token.equals("nlpFingerprints")) result.put(token, store.nlp().countFingerprints());
			else if (token.equals("nlpModels")) result.put(token, store.model().countModelNLP());
			else if (token.equals("corrModels")) result.put(token, store.model().countModelCorr());
			else if (token.equals("assaysWithMeasurements")) result.put(token, store.measure().countUniqueAssays());
			else if (token.equals("numMeasurements")) result.put(token, store.measure().countMeasurements());
			else if (token.equals("numCompounds")) result.put(token, store.compound().countTotal());
			else if (token.equals("compoundsWithStructures")) result.put(token, store.compound().countWithStructures());
			else throw new RESTException("Unexpected token: '" + token + "'", RESTException.HTTPStatus.BAD_REQUEST);
		}
		return result;
	}
	
	// ------------ private methods ------------
	
	
}
