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
import com.cdd.bae.config.Identifier.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.SchemaVocab.*;
import com.cdd.bao.util.*;

import java.util.*;
import java.util.stream.*;

import org.apache.commons.lang3.*;
import org.json.*;

/*
	GetRecentCuration: get information on recently curated assays

	Parameters:

		maxNum: (default 8) maximum number of assays
		curatorID: (optional) return a second list for assays that the curator contributed
*/

public class GetRecentCuration extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	@Override
	protected boolean requireSession()
	{
		return false;
	}

	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		int maxNum = input.optInt("maxNum", 8);
		String curatorID = input.optString("curatorID", null);

		return getRecentCuration(maxNum, curatorID);
	}

	public static JSONObject getRecentCuration(int maxNum, String curatorID)
	{
		DataStore store = Common.getDataStore();

		JSONObject results = new JSONObject();
		results.put("all", new JSONArray());

		if (maxNum == 0)
			return results;
		for (long assayID : store.assay().fetchRecentlyCurated(maxNum))
			results.getJSONArray("all").put(serializeAssay(assayID, store));

		if (curatorID == null)
			return results;

		results.put("curator", new JSONArray());
		for (DataAssay.UserCuration userCuration : store.assay().fetchAssayIDRecentCuration(curatorID))
		{
			results.getJSONArray("curator").put(serializeAssay(userCuration.assayID, store));
			if (results.getJSONArray("curator").length() == maxNum) break;
		}

		results.put("holding", new JSONArray());
		for (long holdingID : store.holding().fetchHoldingsByCurator(curatorID))
		{
			results.getJSONArray("holding").put(serializeHolding(holdingID, store));
			if (results.getJSONArray("holding").length() == maxNum) break;
		}
		return results;
	}

	// ------------ private methods ------------

	private static JSONObject serializeAssay(long assayID, DataStore store)
	{
		DataObject.Assay assay = store.assay().getAssay(assayID);
		return serializeAssay(assay, store);
	}

	private static JSONObject serializeAssay(DataObject.Assay assay, DataStore store)
	{
		JSONObject obj = new JSONObject();
		obj.put("assayID", assay.assayID);
		obj.put("uniqueID", assay.uniqueID);
		obj.put("curationTime", assay.curationTime == null ? 0 : assay.curationTime.getTime());
		obj.put("shortText", summariseAssay(assay, getSummaryOrder(assay)));
		obj.put("numAnnots", ArrayUtils.getLength(assay.annotations));
		obj.put("curatorID", assay.curatorID);
		DataObject.User user = store.user().getUser(assay.curatorID);
		if (user != null) obj.put("userName", user.name);
		return obj;
	}

	private static JSONObject serializeHolding(long holdingID, DataStore store)
	{

		DataObject.Holding holding = store.holding().getHolding(holdingID);

		int changes = ArrayUtils.getLength(holding.annotsAdded) + ArrayUtils.getLength(holding.annotsRemoved);
		changes += ArrayUtils.getLength(holding.labelsAdded) + ArrayUtils.getLength(holding.labelsRemoved);

		JSONObject obj = new JSONObject();
		obj.put("curationTime", holding.submissionTime.getTime());
		obj.put("numAnnots", changes);
		obj.put("curatorID", holding.curatorID);
		DataObject.User user = store.user().getUser(holding.curatorID);
		if (user != null) obj.put("userName", user.name);

		DataObject.Assay assay = null;
		if (holding.assayID != 0)
		{
			assay = store.assay().getAssay(holding.assayID);
			obj.put("assayID", assay.assayID);
			obj.put("uniqueID", assay.uniqueID);
		}
		obj.put("shortText", summariseHolding(holding, assay, getSummaryOrder(assay)));
		return obj;
	}

	protected static String summariseAssay(DataObject.Assay assay, String[] summaryOrder)
	{
		List<String> fragments = new ArrayList<>();
		for (String order : summaryOrder)
		{
			if (order == null)
				fragments.add(assay.text);
			else if (order.startsWith("autotext:"))
				fragments.add(generateAutotext(assay, order));
			else
				addAssayFragments(fragments, assay, order);
		}
		return SummariseAssays.truncateText(combineFragments(fragments));
	}
	
	protected static String generateAutotext(DataObject.Assay assay, String order)
	{
		String block = null;
		if (!order.equals("autotext:")) block = order.split(":")[1];
		Schema schema = Common.getSchema(assay.schemaURI);
		Transliteration.Boilerplate boiler = Common.getTransliteration().getBoilerplateBlock(assay.schemaURI, block);
		try
		{
			String result = TransliterateAssay.transcribeAssay(boiler, schema, assay);
			result = result.replaceAll("[^\\.]</p>", ". "); // add space for paragraph ends
			result = result.replaceAll("\\<[^>]*>", ""); // strip tags
			result = String.join(". ", Arrays.stream(result.split("\\. ")).filter(s -> !s.startsWith("Auto Annotation")).collect(Collectors.toList()));
			return result;
		}
		catch (RESTException e)
		{
			return "";
		}
	}

	protected static String summariseHolding(Holding holding, DataObject.Assay assay, String[] summaryOrder)
	{
		List<String> fragments = new ArrayList<>();
		for (String order : summaryOrder)
		{
			if (order == null)
			{
				if (holding.text != null) fragments.add(holding.text);
				else if (assay != null) fragments.add(assay.text);
			}
			else
			{
				boolean inHolding = false;
				for (TextLabel label : holding.labelsAdded)
				{
					if (!label.propURI.equals(order)) continue;
					fragments.add(label.text);
					inHolding = true;
				}
				if (!inHolding && assay != null)
					addAssayFragments(fragments, assay, order);
			}
		}
		return SummariseAssays.truncateText(combineFragments(fragments));
	}

	private static void addAssayFragments(List<String> fragments, DataObject.Assay assay, String order)
	{
		for (TextLabel label : assay.getTextLabels(order, new String[0]))
			fragments.add(label.text.trim());
		for (Annotation label : assay.getAnnotations(order, new String[0]))
			fragments.add(Common.getOntoValues().getLabel(ModelSchema.expandPrefix(label.valueURI)));
	}

	protected static String combineFragments(List<String> fragments)
	{
		StringJoiner joiner = new StringJoiner(". ");
		String last = "";
		for (String fragment : fragments)
		{
			fragment = Util.safeString(fragment).trim();
			if (fragment.isEmpty()) continue;
			if (!fragment.startsWith(last)) joiner.add(last);
			last = fragment;
		}
		joiner.add(last);
		return joiner.toString();
	}

	protected static String[] getSummaryOrder(DataObject.Assay assay)
	{
		if (assay == null || assay.uniqueID == null || !assay.uniqueID.contains(":"))
			return Identifier.defaultSummary;
		String prefix = assay.uniqueID.split(":")[0] + ":";
		Source source = Common.getIdentifier().getSource(prefix);
		return source == null ? Identifier.defaultSummary : source.summary;
	}
}
