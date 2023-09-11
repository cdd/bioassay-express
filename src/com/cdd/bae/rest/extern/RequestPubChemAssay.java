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

package com.cdd.bae.rest.extern;

import com.cdd.bae.config.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.*;
import com.cdd.bae.tasks.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.lang3.*;
import org.apache.http.*;
import org.json.*;

/*
	RequestPubChemAssay: adds a list of PubChem assay IDs to the whitelist for PubChem downloading

	Parameters:
		assayAIDList: list of AID numbers that should correspond to PubChem identifiers
		
	Return:
		addedAIDList
		ignoredAIDList
*/

public class RequestPubChemAssay extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;
		
	// ------------ public methods ------------

	@Override
	protected boolean requireSession()
	{
		return true;
	}

	@Override
	protected boolean hasPermission(Session session)
	{
		return session != null && session.isAdministrator();
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		int[] assayIDList = input.optJSONArrayEmpty("assayAIDList").toIntArray();
		
		DataStore store = Common.getDataStore();
		
		List<Integer> addedAIDList = new ArrayList<>(), ignoredAIDList = new ArrayList<>();
		for (int aid : assayIDList)
		{
			String uniqueID = Identifier.PUBCHEM_PREFIX + aid;
			if (store.assay().getAssayFromUniqueID(uniqueID) == null)
				addedAIDList.add(aid);
			else
				ignoredAIDList.add(aid);
		}

		PubChemAssays.main().requestPubChemID(ArrayUtils.toPrimitive(addedAIDList.toArray(new Integer[0])));
			
		JSONObject result = new JSONObject();
		result.put("addedAIDList", addedAIDList);
		result.put("ignoredAIDList", ignoredAIDList);
		return result;
	}
	
	@Override
	protected String[] getRequiredParameter()
	{
		return new String[]{"assayAIDList"};
	}
	
	// ------------ private methods ------------
	
}
