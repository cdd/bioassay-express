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
import com.cdd.bao.util.*;
import com.cdd.bae.util.*;

import org.json.*;

/*
	GetAssay: lookup an assay in the collection and return all the details
	
	Parameters:
	
		assayID: assayID using internal key sequence
		uniqueID: unique identifier
			NOTE: either assayID or uniqueID must be provided; assayID takes precedence; uniqueID can refer to more than one assay
			      in certain edge cases, and if this happens, an arbitrary instance will be returned
		countCompounds: (optional) if true, the # compounds will be counted and returned
		translitPreviews: (optional) if true, any available preview-type transliterations will be included
		blankAbsent: (optional) if true, failing to locate the assay will return blank rather than an error condition
*/

public class GetAssay extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;
	private static final String REQUEST_SCHEMA = "/com/cdd/bae/rest/schema/GetAssayRequest.json";

	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		long assayID = input.optLong("assayID", 0);
		String uniqueID = input.optString("uniqueID");
		if (assayID <= 0 && Util.isBlank(uniqueID)) 
			throw new RESTException("Bad parameters: either 'assayID' or 'uniqueID' must be specified.", RESTException.HTTPStatus.BAD_REQUEST);
		
		boolean countCompounds = input.optBoolean("countCompounds", false);
		boolean translitPreviews = input.optBoolean("translitPreviews", false);
		boolean blankAbsent = input.optBoolean("blankAbsent", false);

		DataStore store = Common.getDataStore();
		DataObject.Assay assay = null;
		if (assayID > 0) assay = store.assay().getAssay(assayID);
		else assay = store.assay().getAssayFromUniqueID(uniqueID);

		if (assay == null)
		{
			if (blankAbsent) return new JSONObject();
			String msgSuffix = (assayID > 0) ? "assayID " + assayID : "uniqueID " + uniqueID;
			throw new RESTException("Unable to find " + msgSuffix, RESTException.HTTPStatus.BAD_REQUEST);
		}

		JSONObject result = AssayJSON.serialiseAssay(assay);
		if (countCompounds) result.put("countCompounds", store.measure().countCompounds(assay.assayID));
		if (translitPreviews) result.put("translitPreviews", AssayUtil.transliteratedPreviews(assay));
		
		return result;
	}

	@Override
	protected JSONSchemaValidator getSchemaValidator()
	{
		try
		{
			return JSONSchemaValidator.fromResource(REQUEST_SCHEMA);
		}
		catch (JSONSchemaValidatorException e)
		{
			logger.error("Cannot load request schema: {}", REQUEST_SCHEMA);
			return null;
		}
	}

	// ------------ private methods ------------

}
