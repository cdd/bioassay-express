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
import com.cdd.bao.util.*;

import java.util.*;
import java.util.regex.*;

import org.json.*;

/*
	FindIdentifier: looks for identifier(s) that have the id portion of the given parameter - this is the suffix of the unique ID,
	for example a search id of "101" would match both "pubchemAID:101" and "somethingElse:101". Note that it is possible for a uniqueID
	to have more than one assay corresponding to it (this is discouraged but not disallowed).
	
	Parameters:
		id: the identifier suffix
		permissive: (default false) if true, matches some variants on the provided ID
		partialMatching: (default true) if true, allows partial matching in permissive mode
		
	Response:
		matches[]:
			.assayID
			.uniqueID
*/

public class FindIdentifier extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;
	private static final String ASSAY_DELIMITERS = "\\t|,|;| |\\n|$";

	// ------------ public methods ------------

	@Override
	protected String[] getRequiredParameter()
	{
		return new String[]{"id"};
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String id = input.getString("id").trim().toUpperCase();
		boolean permissive = input.optBoolean("permissive");
		boolean partialMatching = input.optBoolean("partialMatching", true);

		// add support for matching multiple ids, but still need to stay backwards compatible for current clients
		String[] idList = splitID(id);
		JSONObject result = new JSONObject();

		JSONArray matches = findMatches(id, permissive, partialMatching);
		result.put("matches", matches);
		
		// multi-match breaks the string and attempts to identify several assay ids
		JSONArray multiMatches = new JSONArray();

		if (idList.length > 0)
		{
			for (String idEntry : idList)
			{
				if (Util.notBlank(idEntry)) 
				{
					matches = findMatches(idEntry, permissive, partialMatching);
					multiMatches.put(matches);
				}
			}
			result.put("multiMatches", multiMatches);
		}
		
		return result;
	}

	// ------------ private methods ------------

	private String[] splitID(String id)
	{
		String[] idList = id.split(ASSAY_DELIMITERS);
		return idList;
	}

	private JSONArray findMatches(String id, boolean permissive, boolean partialMatching)
	{
		if (partialMatching)
		{
			String shortID = id;
			for (Identifier.Source src : Common.getIdentifier().getSources())
				if (shortID.startsWith(src.shortName.toUpperCase()))
				{
					shortID = shortID.substring(src.shortName.length());
					break;
				}
			shortID = shortID.replaceAll("^[- ]*", "");
			partialMatching = shortID.length() > 2;
		}

		JSONArray matches = permissive ? matchRegex(id, partialMatching) : matchExact(id);
		return matches;
	}

	// matches identifiers for which the ID payload matches precisely
	private JSONArray matchExact(String id)
	{
		DataStore store = Common.getDataStore();

		String[] uniqueIDList = exactUniqueIDList(id);
		long[][] assayIDList = store.assay().assayIDFromUniqueID(uniqueIDList);

		JSONArray matches = new JSONArray();
		for (int n = 0; n < uniqueIDList.length; n++)
		{
			if (assayIDList[n] == null) continue;
			for (long assayID : assayIDList[n])
			{
				JSONObject obj = new JSONObject();
				obj.put("assayID", assayID);
				obj.put("uniqueID", uniqueIDList[n]);
				matches.put(obj);
			}
		}
		return matches;
	}

	protected static String[] exactUniqueIDList(String id)
	{
		Identifier.Source[] srclist = Common.getIdentifier().getSources();
		String[] uniqueIDList = new String[srclist.length];
		for (int n = 0; n < srclist.length; n++)
			uniqueIDList[n] = Identifier.makeKey(srclist[n], id);
		return uniqueIDList;
	}

	// matches variants on identifiers, which is somewhat specific, but allows some minor shortcuts/variation
	private JSONArray matchRegex(String id, boolean partialMatching)
	{
		DataStore store = Common.getDataStore();

		Set<Long> assayIDSet = new HashSet<>();

		// add literal full-spec matches, in case there are any
		for (long[] list : store.assay().assayIDFromUniqueID(new String[]{id}))
		{
			if (list == null) continue;
			for (long assayID : list) assayIDSet.add(assayID);
		}

		// form regexes for several possible variants
		for (Identifier.Source src : Common.getIdentifier().getSources())
		{
			// compose a bunch of variants: the full ID payload, or +/- the 
			// short name prefix in some way each of them gets its chance to match
			for (String variantID : createIDVariants(id, src.shortName))
			{
				String regex = "^" + Pattern.quote(src.prefix) + "[A-Za-z]*0*" + Pattern.quote(variantID);
				if (!partialMatching) regex += "$";
				for (long assayID : store.assay().assayIDFromUniqueIDRegex(regex)) assayIDSet.add(assayID);
			}
		}

		long[] assayIDList = Util.primLong(assayIDSet);
		String[] uniqueIDList = store.assay().uniqueIDFromAssayID(assayIDList);

		JSONArray matches = new JSONArray();
		for (int n = 0; n < assayIDList.length; n++)
		{
			JSONObject obj = new JSONObject();
			obj.put("assayID", assayIDList[n]);
			obj.put("uniqueID", uniqueIDList[n]);
			matches.put(obj);
		}
		return matches;
	}

	protected static Set<String> createIDVariants(String id, String shortName)
	{
		id = id.trim();
		if (id.startsWith(shortName))
			id = id.substring(shortName.length()).replaceAll("^[- ]*", "");
		Set<String> variants = new HashSet<>();
		variants.add(id);
		variants.add(shortName + id);
		variants.add(shortName + " " + id);
		variants.add(shortName + "-" + id);
		return variants;
	}
}

/*
{
    "$schema": "http://json-schema.org/draft-04/schema#",
    "description": "Looks for identifier(s) that have the id portion of the given parameter",
    "type": "object",
    "required": 
	[
		"id"
	],
	properties: 
	{
		id: 
		{
			description: "id portion of assay identifier",
			"type": "string"
		}
	}
}
*/
