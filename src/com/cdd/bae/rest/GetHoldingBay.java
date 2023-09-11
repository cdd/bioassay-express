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
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;
import javax.servlet.http.*;

import org.json.*;

/*
	GetHoldingBay: either grabs a list of everything in the holding bay, or specific entries.
	
	Parameters:
		holdingIDList: (optional) grabs a specific entry, or a list of IDs to grab
		
	Return (list form):
		holdingID: list of IDs for each entry
		assayID: list for existing assay ID references (0=new)
	
	Return (specific):
		array of:
			holdingID
			assayID (0=new)
			uniqueID (if changed or set)
			currentUniqueID (if available for existing assay)
			submissionTime
			curatorID
			curatorName
			curatorEmail
			
			schemaURI
			schemaBranches
			schemaDuplication
			deleteFlag
			text
			added
			removed
*/

public class GetHoldingBay extends RESTBaseServlet
{
	private static final String HOLDING_ID = "holdingID";
	private static final String ASSAY_ID = "assayID";
	private static final String CURRENT_UNIQUE_ID = "currentUniqueID";
	private static final long serialVersionUID = 1L;

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();
		JSONArray holdingIDList = input.optJSONArray("holdingIDList");
		JSONObject result = new JSONObject();
		if (holdingIDList != null && holdingIDList.length() > 0)
		{
			JSONArray list = new JSONArray();
			for (int n = 0; n < holdingIDList.length(); n++) list.put(composeSpecific(holdingIDList.getLong(n), store));
			result.put("list", list);
		}
		else composeList(result);
		return result;
	}

	// convenience function for JSP use: similar to invoking composeList(..) via REST; returns lines of JavaScript that
	// define the arrays
	public static String manufactureList(HttpServletRequest request)
	{
		DataStore store = Common.getDataStore();

		long[] holdingID = null;
		String strAssayID = request.getParameter(ASSAY_ID), strHoldingID = request.getParameter(HOLDING_ID);
		if (Util.notBlank(strAssayID)) holdingID = store.holding().fetchForAssayID(Long.parseLong(strAssayID));
		else if (Util.notBlank(strHoldingID))
		{
			String[] bits = strHoldingID.split(",");
			holdingID = new long[bits.length];
			for (int n = 0; n < bits.length; n++) holdingID[n] = Long.parseLong(bits[n]);
		}
		if (holdingID == null) holdingID = store.holding().fetchHoldings();

		JSONArray jsonHoldingList = new JSONArray();
		for (long hid : holdingID)
		{
			DataObject.Holding holding = store.holding().getHolding(hid);
			long assayID = getAssayID(holding);
			String currentUniqueID = null;
			if (assayID > 0) 
			{
				DataStore.Assay assay = store.assay().getAssay(assayID);
				if (assay != null) currentUniqueID = assay.uniqueID;
			}

			JSONObject obj = new JSONObject();
			obj.put("holdingID", hid);
			obj.put("assayID", assayID);
			obj.put("uniqueID", holding.uniqueID);
			if (currentUniqueID != null) obj.put(CURRENT_UNIQUE_ID, currentUniqueID);
			obj.put("submissionTime", holding.submissionTime.getTime());
			obj.put("curatorID", holding.curatorID);
			
			DataObject.User user = store.user().getUser(holding.curatorID);
			obj.put("curatorName", user == null ? "" : user.name);
			obj.put("curatorEmail", user == null ? "" : user.email);
			obj.put("deleteFlag", holding.deleteFlag);
			
			jsonHoldingList.put(obj);
		}

		return "var LIST_HOLDING = " + jsonHoldingList + ";\n";
	}

	// ------------ private methods ------------

	private static void composeList(JSONObject result)
	{
		DataStore store = Common.getDataStore();
		long[] holdingID = store.holding().fetchHoldings();
		long[] assayID = new long[holdingID.length];
		String[] uniqueID = new String[holdingID.length];
		for (int n = 0; n < holdingID.length; n++)
		{
			DataObject.Holding holding = store.holding().getHolding(holdingID[n]);
			assayID[n] = getAssayID(holding);
			uniqueID[n] = holding.uniqueID;
		}

		result.put(HOLDING_ID, holdingID);
		result.put(ASSAY_ID, assayID);
		result.put("uniqueID", uniqueID);
	}
	
	// if holding.assayID is unknown, try to get assayID from uniqueID in 
	// case the assay was registered in the meantime
	protected static long getAssayID(DataObject.Holding holding)
	{
		DataStore store = Common.getDataStore();
		if (holding.assayID == 0 && Util.notBlank(holding.uniqueID))
		{
			DataObject.Assay assay = store.assay().getAssayFromUniqueID(holding.uniqueID);
			if (assay != null) holding.assayID = assay.assayID;
		}
		return holding.assayID;
	}

	private static JSONObject composeSpecific(long holdingID, DataStore store)
	{
		DataObject.Holding holding = store.holding().getHolding(holdingID);

		JSONObject obj = new JSONObject();
		if (holding == null) return obj;
		
		// identify the post-apply schema (i.e. current one if no change)
		Schema schema = holding.schemaURI == null ? null : Common.getSchema(holding.schemaURI);
		if (schema == null && holding.assayID != 0)
		{
			DataStore.Assay assay = store.assay().getAssay(holding.assayID);
			schema = Common.getSchema(assay.schemaURI);
		}
		if (schema == null) schema = Common.getSchemaCAT();

		obj.put(HOLDING_ID, holding.holdingID);
		long assayID = getAssayID(holding);
		if (assayID > 0) 
		{
			obj.put(ASSAY_ID, assayID);
			DataStore.Assay assay = store.assay().getAssay(assayID);
			if (assay != null && assay.uniqueID != null) obj.put(CURRENT_UNIQUE_ID, assay.uniqueID);
		}
		if (holding.submissionTime != null) obj.put("submissionTime", holding.submissionTime.getTime());

		obj.put("curatorID", holding.curatorID);
		DataObject.User user = store.user().getUser(holding.curatorID);
		obj.put("curatorName", user == null ? "" : user.name);
		obj.put("curatorEmail", user == null ? "" : user.email);

		obj.put("uniqueID", holding.uniqueID);
		obj.put("schemaURI", holding.schemaURI);
		if (holding.schemaBranches != null)
		{
			JSONArray list = new JSONArray();
			for (DataObject.SchemaBranch branch : holding.schemaBranches)
			{
				JSONObject json = new JSONObject();
				json.put("schemaURI", branch.schemaURI);
				json.put("groupNest", branch.groupNest);
				list.put(json);
			}
			obj.put("schemaBranches", list);
		}
		if (holding.schemaDuplication != null)
		{
			JSONArray list = new JSONArray();
			for (DataObject.SchemaDuplication dupl : holding.schemaDuplication)
			{
				JSONObject json = new JSONObject();
				json.put("multiplicity", dupl.multiplicity);
				json.put("groupNest", dupl.groupNest);
				list.put(json);
			}
			obj.put("schemaDuplication", list);
		}
		obj.put("deleteFlag", holding.deleteFlag);
		obj.put("text", holding.text);
		
		SchemaDynamic schdyn = null;
		if (holding.schemaBranches != null || holding.schemaDuplication != null)
			schdyn = new SchemaDynamic(schema, holding.schemaBranches, holding.schemaDuplication);

		JSONArray jsonAdded = new JSONArray(), jsonRemoved = new JSONArray();
		if (holding.annotsAdded != null) for (DataObject.Annotation annot : holding.annotsAdded) 
			jsonAdded.put(AssayJSON.serialiseAnnotation(annot, schema, schdyn));
		if (holding.annotsRemoved != null) for (DataObject.Annotation annot : holding.annotsRemoved) 
			jsonRemoved.put(AssayJSON.serialiseAnnotation(annot, schema, schdyn));
		if (holding.labelsAdded != null) for (DataObject.TextLabel annot : holding.labelsAdded) 
			jsonAdded.put(AssayJSON.serialiseTextLabel(annot, schema));
		if (holding.labelsRemoved != null) for (DataObject.TextLabel annot : holding.labelsRemoved) 
			jsonRemoved.put(AssayJSON.serialiseTextLabel(annot, schema));

		Schema.Assignment[] assignments = schema.getRoot().flattenedAssignments();
		obj.put("added", sortBySchema(assignments, jsonAdded));
		obj.put("removed", sortBySchema(assignments, jsonRemoved));
		
		return obj;
	}
	
	// sorts the JSON array of annotations & labels based on occurrence order with the schema assignments
	private static JSONArray sortBySchema(Schema.Assignment[] assignments, JSONArray jsonList)
	{
		Map<Integer, List<JSONObject>> batches = new TreeMap<>();
		for (JSONObject json : jsonList.toObjectArray())
		{
			int idx = assignments.length; // goes at the end if not found
			String propURI = json.getString(AssayJSON.PROP_URI);
			String[] groupNest = json.optJSONArrayEmpty(AssayJSON.GROUP_NEST).toStringArray();
			for (int n = 0; n < assignments.length; n++)
				if (Schema.compatiblePropGroupNest(propURI, groupNest, assignments[n].propURI, assignments[n].groupNest())) {idx = n; break;}
				
			List<JSONObject> batch = batches.get(idx);
			if (batch == null) batches.put(idx, batch = new ArrayList<>());
			batch.add(json);
		}
		
		JSONArray result = new JSONArray();
		for (List<JSONObject> batch : batches.values()) for (JSONObject json : batch) result.put(json);
		return result;
	}
}
