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

import java.util.*;

import org.json.*;

/*
	SummariseAssays: lookup some number of assays and return pertinent information about each. Can provide either assay IDs or unique IDs, or
	an array of both, either of which may be used.
	
	Parameters:
	
		assayIDList: list of assay ID codes [mutually optional]
		uniqueIDList: list of unique ID code [mutually optional]
		withCompounds: (optional) if true, looks up and returns compound IDs for actives & inactive
		onlyCompounds: (optional) list of compoundIDs to which the return values will be restricted (reduces HTTP traffic)
		
	Return:
	
		array of:
			assayID: internal database number
			uniqueID: globally unique identifer (string)
			schemaURI: associated schema
			shortText: abbreviated version of the full text, suitable for use as a title (usually)
			annotations: array of objects
			isCurated: curation flag
			actives: compoundIDs for actives (if requested)
			inactives: ditto for inactives
			probes: ditto for probes
*/

public class SummariseAssays extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------
	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();
		JSONArray listAssayID = input.optJSONArray("assayIDList");
		JSONArray listUniqueID = input.optJSONArray("uniqueIDList");
		boolean withCompounds = input.optBoolean("withCompounds", false);
		Set<Long> onlyCompounds = new HashSet<>();
		if (input.has("onlyCompounds"))
		{
			JSONArray list = input.getJSONArray("onlyCompounds");
			for (int n = 0; n < list.length(); n++) onlyCompounds.add(list.getLong(n));
		}

		JSONArray results = new JSONArray();
		int sz1 = listAssayID == null ? 0 : listAssayID.length();
		int sz2 = listUniqueID == null ? 0 : listUniqueID.length();
		for (int n = 0; n < Math.max(sz1, sz2); n++)
		{
			long assayID = n < sz1 ? listAssayID.getLong(n) : 0;
			if (assayID == 0 && n < sz2)
			{
				long[] ids = store.assay().assayIDFromUniqueID(new String[]{listUniqueID.getString(n)})[0];
				if (ids != null) assayID = ids[0];
			}
			results.put(formulateAssay(assayID, withCompounds, onlyCompounds));
		}
		return new JSONObject().put(RETURN_JSONARRAY, results);
	}

	// chop up an assay's text to make it a short line-sized summary (usually works quite well)
	public static String truncateText(String text)
	{
		if (Util.isBlank(text)) return "";

		StringBuilder snippet = new StringBuilder();
		for (String line : text.split("\n"))
		{
			if (Util.isBlank(line)) continue;
			if (snippet.length() > 0) snippet.append(" ");
			if (line.length() < 80)
			{
				snippet.append(line);
				if (snippet.length() > 100) return snippet.toString();
			}
			else
			{
				int cutpos = line.lastIndexOf(' ', 100);
				if (cutpos == -1) cutpos = Math.min(80, line.length());
				snippet.append(line.substring(0, cutpos) + "...");
				return snippet.toString();
			}
		}
		return snippet.toString();
	}

	// ------------ private methods ------------

	private JSONObject formulateAssay(long assayID, boolean withCompounds, Set<Long> onlyCompounds)
	{
		DataStore store = Common.getDataStore();
		if (assayID == 0) return new JSONObject();

		DataObject.Assay assay = store.assay().getAssay(assayID);
		if (assay == null) return new JSONObject();

		// start with the conventional serialisation, then add other stuff later
		JSONObject result = AssayJSON.serialiseAssay(assay);
		result.put("shortText", truncateText(assay.text));
		
		if (withCompounds)
		{
			JSONArray actives = new JSONArray(), inactives = new JSONArray(), probes = new JSONArray();
			String[] types = new String[]{DataMeasure.TYPE_ACTIVITY, DataMeasure.TYPE_PROBE};
			for (DataObject.Measurement measure : store.measure().getMeasurements(assay.assayID, types))
			{
				if (measure.type.equals(DataMeasure.TYPE_ACTIVITY))
				{
					for (int n = 0; n < measure.compoundID.length; n++)
					{
						long compoundID = measure.compoundID[n];
						if (!onlyCompounds.isEmpty() && !onlyCompounds.contains(compoundID)) continue;
						if (measure.value[n] > 0)
							actives.put(compoundID);
						else
							inactives.put(compoundID);
					}
				}
				else if (measure.type.equals(DataMeasure.TYPE_PROBE))
				{
					for (int n = 0; n < measure.compoundID.length; n++)
					{
						long compoundID = measure.compoundID[n];
						if (!onlyCompounds.isEmpty() && !onlyCompounds.contains(compoundID)) continue;
						probes.put(compoundID);
					}
				}
			}
			result.put("actives", actives);
			result.put("inactives", inactives);
			result.put("probes", probes);
		}

		return result;
	}
}
