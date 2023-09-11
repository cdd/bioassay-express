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
	SubmitAssay: submits details about an annotated assay, with the expectation that it will be either applied to the data, placed
	into the holding bay, or rejected. The annotation list is presented as a delta (annotations to add/delete from whatever is present);
	if the assay is new, then the removal list will be empty. Besides annotations, other properties like text, schema and uniqueID will
	be modified only if provided and non-null.
	
	Parameters:
		assayID: (optional) if known, ID of existing assay
		uniqueID: (optional) globally unique identifier (e.g. from PubChem)
		text: (optional) assay text; null means no change
		added: array of annotation objects to add
		removed: array of annotation objects to remove
		schemaURI: (optional) which schema (default: CAT)
		schemaBranches: (optional) array of grafted branches
		schemaDuplication: (optional) array of duplicated branches
		holdingBay: (optional) default: will submit directly if access permits, holding bay if not;
							   setting to true will make it always use the holding bay preferentially
		
	Return:
		success: true/false (it worked or it didn't)
		status: one of STATUS_* below
		assayID: if applicable
		holdingID: if applicable
*/

public class SubmitAssay extends RESTBaseServlet 
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
		return session != null && session.canSubmitHoldingBay();
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		long assayID = input.optLong("assayID", 0);
		String uniqueID = input.optString("uniqueID", null);
		String text = input.optString("text", null);
		JSONArray annotsAdded = input.getJSONArray("added"), annotsRemoved = input.getJSONArray("removed");
		String schemaURI = input.optString("schemaURI", null);
		JSONArray schemaBranches = input.optJSONArray("schemaBranches");
		JSONArray schemaDuplication = input.optJSONArray("schemaDuplication");
		
		boolean holdingBay = input.optBoolean("holdingBay", false);
		
		DataObject.Holding holding = new DataObject.Holding();
		holding.assayID = assayID;
		holding.uniqueID = uniqueID;
		holding.submissionTime = new Date();
		holding.curatorID = session.curatorID;
		holding.schemaURI = schemaURI;
		holding.schemaBranches = unpackBranches(schemaBranches);
		holding.schemaDuplication = unpackDuplication(schemaDuplication);
		holding.text = text;

		addAnnotationsLabels(holding, true, annotsAdded);
		addAnnotationsLabels(holding, false, annotsRemoved);
		
		DataStore store = Common.getDataStore();
		JSONObject result = new JSONObject();
		if (holdingBay || !session.canSubmitDirectly())
		{
			store.holding().depositHolding(holding);
			
			result.put("success", true);
			result.put("status", Status.HOLDING);
			result.put("holdingID", holding.holdingID);
		}
		else
		{
			DataObject.Assay assay = holding.assayID > 0 ? store.assay().getAssay(holding.assayID) : null;
			if (assay == null && holding.uniqueID != null) assay = store.assay().getAssayFromUniqueID(holding.uniqueID);
			assay = DataHolding.createAssayDelta(assay, holding);
			store.assay().submitAssay(assay);
		
			/*DataObject.Assay assay = DataHolding.createAssayFromHolding(holding);
			if (assay.assayID == 0)
			{
				long[] lookID = store.assay().assayIDFromUniqueID(new String[]{uniqueID})[0];
				if (Util.length(lookID) == 1) assay.assayID = lookID[0];
			}
			store.assay().submitAssay(assay);*/

			result.put("success", true);
			result.put("status", Status.APPLIED);
			result.put("assayID", assay.assayID);
		}
		return result;
	}

	// ------------ private methods ------------

	protected static void addAnnotationsLabels(DataObject.Holding holding, boolean adding, JSONArray annots)
	{
		List<DataObject.Annotation> listAnnot = new ArrayList<>();
		List<DataObject.TextLabel> listText = new ArrayList<>();
		if (annots != null) for (int n = 0; n < annots.length(); n++)
		{
			JSONObject obj = annots.getJSONObject(n);
			String propURI = obj.optString("propURI");
			String valueURI = obj.optString("valueURI", null);
			String label = obj.optString("valueLabel", null);
			String[] groupNest = obj.optJSONArrayEmpty("groupNest").toStringArray();

			if (valueURI != null && valueURI.length() > 0) listAnnot.add(new DataObject.Annotation(propURI, valueURI, groupNest));
			else if (label != null && label.length() > 0) listText.add(new DataObject.TextLabel(propURI, label, groupNest));
		}
		if (adding)
		{
			holding.annotsAdded = listAnnot.toArray(new DataObject.Annotation[listAnnot.size()]);
			holding.labelsAdded = listText.toArray(new DataObject.TextLabel[listText.size()]);
		}
		else
		{
			holding.annotsRemoved = listAnnot.toArray(new DataObject.Annotation[listAnnot.size()]);
			holding.labelsRemoved = listText.toArray(new DataObject.TextLabel[listText.size()]);
		}
	}
	
	protected static DataObject.SchemaBranch[] unpackBranches(JSONArray json)
	{
		int sz = json == null ? 0 : json.length();
		if (sz == 0) return null;
		DataObject.SchemaBranch[] branches = new DataObject.SchemaBranch[sz];
		for (int n = 0; n < sz; n++) 
		{
			JSONObject obj = json.getJSONObject(n);
			branches[n] = new DataObject.SchemaBranch();
			branches[n].schemaURI = obj.getString("schemaURI");
			branches[n].groupNest = obj.optJSONArrayEmpty("groupNest").toStringArray();
		}
		return branches;
	}
	protected static DataObject.SchemaDuplication[] unpackDuplication(JSONArray json)
	{
		int sz = json == null ? 0 : json.length();
		if (sz == 0) return null;
		DataObject.SchemaDuplication[] duplication = new DataObject.SchemaDuplication[sz];
		for (int n = 0; n < sz; n++) 
		{
			JSONObject obj = json.getJSONObject(n);
			duplication[n] = new DataObject.SchemaDuplication();
			duplication[n].multiplicity = obj.getInt("multiplicity");
			duplication[n].groupNest = obj.optJSONArrayEmpty("groupNest").toStringArray();
		}
		return duplication;
	}
}
