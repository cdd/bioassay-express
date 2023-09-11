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

import java.util.*;

import javax.servlet.http.*;

import org.json.*;

/*
	GetIdentities: get all of the "UniqueIdentifier" values, for all assays, packaging them up by category.
	
	Parameters:
		none
*/

public class GetIdentifiers extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;
       
	// ------------ public methods ------------
       
	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();
		
		Map<String, Set<String>> idGroups = new TreeMap<>();
	
		for (String uniqueID : store.assay().fetchAllUniqueID())
		{
			Identifier.UID uid = Common.getIdentifier().parseKey(uniqueID);
			if (uid == null) continue;
			
			Set<String> idset = idGroups.get(uid.source.prefix);
			if (idset == null) 
			{
				idset = new TreeSet<>();
				idGroups.put(uid.source.prefix, idset);
			}
			idset.add(uid.id);
		}
		
		JSONObject result = new JSONObject();
		for (Map.Entry<String, Set<String>> entry: idGroups.entrySet())
		{
			Set<String> idset = entry.getValue();
			result.put(entry.getKey(), new JSONArray(idset.toArray(new String[idset.size()])));
		}
		return result;
	}

	@Override
	protected void processResponse(HttpServletResponse response)
	{
		response.setHeader("Cache-Control", "public, max-age=31536000");
	}
}
