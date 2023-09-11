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
import com.cdd.bao.template.*;

import java.util.*;

import org.json.*;
import org.apache.commons.lang3.*;

/*
	SubmitBulkMap: submits a list of reassignments that should be made to a selection of assays. The changes will be added
	to the holding bay.
	
	Parameters:
		assayIDList: assays to apply to
		mappingList:
			.propURI
			.groupNest
			.[oldValueURI | oldValueLabel]
			.newValueURI
		
	Return:
		success: true/false (it worked or it didn't)
		status: one of STATUS_* below
		holdingIDList: an ID for each successful result (blank if nothing happened)
*/

public class SubmitBulkMap extends RESTBaseServlet 
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
		return session != null && session.canSubmitBulk();
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		long[] assayIDList = input.getJSONArray("assayIDList").toLongArray();
		JSONObject[] mappingList = input.getJSONArray("mappingList").toObjectArray();
		
		DataStore store = Common.getDataStore();

		// perform all of the translations (make sure they all stick, before persisting anything)		
		Set<DataObject.Holding> outcomes = new LinkedHashSet<DataObject.Holding>();
		for (long assayID : assayIDList)
		{
			DataObject.Assay assay = store.assay().getAssay(assayID);
			if (assay == null) continue;
			DataObject.Holding holding = applyMapping(assay, mappingList, session);
			if (holding != null) outcomes.add(holding);
		}
		
		// all successful: write to database
		List<Long> holdingIDList = new ArrayList<>();
		for (DataObject.Holding holding : outcomes)
		{
			store.holding().depositHolding(holding);
			holdingIDList.add(holding.holdingID);
		}
		
		JSONObject result = new JSONObject();
		result.put("success", true);
		result.put("status", Status.HOLDING);
		result.put("holdingIDList", holdingIDList);
		return result;
	}

	// ------------ private methods ------------

	// creates a holding bay entry that contains the assay as modified by the mapping list parameters; if nothing changed,
	// returns null
	protected DataObject.Holding applyMapping(DataObject.Assay assay, JSONObject[] mappingList, Session session)
	{
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) return null;
		
		DataObject.Holding holding = new DataObject.Holding();
		holding.assayID = assay.assayID;
		holding.submissionTime = new Date();
		holding.curatorID = session.curatorID;
		holding.schemaURI = schema.getSchemaPrefix();
		
		boolean modified = false;
		
		for (JSONObject mapping : mappingList)
		{
			String propURI = mapping.getString("propURI");
			String[] groupNest = mapping.getJSONArray("groupNest").toStringArray();
			String oldValueURI = mapping.optString("oldValueURI"), oldValueLabel = mapping.optString("oldValueLabel");
			String newValueURI = mapping.getString("newValueURI");
			
			if (Util.isBlank(oldValueURI) && Util.isBlank(oldValueLabel)) continue;
		
			// note: may want to check that newValueURI is legitimately in the schema; because it's only going in the holding bay,
			// it's not necessarily critical
		
			boolean hasOldValue = false, hasOldLabel = false, hasNew = false;
			if (Util.notBlank(oldValueURI)) for (DataObject.Annotation annot : assay.annotations)
			{
				if (!Schema.compatiblePropGroupNest(propURI, groupNest, annot.propURI, annot.groupNest)) continue;
				if (oldValueURI.equals(annot.valueURI)) hasOldValue = true;
				if (newValueURI.equals(annot.valueURI)) hasNew = true;
			}
			if (Util.notBlank(oldValueLabel)) for (DataObject.TextLabel label : assay.textLabels)
			{
				if (!Schema.compatiblePropGroupNest(propURI, groupNest, label.propURI, label.groupNest)) continue;
				if (oldValueLabel.equals(label.text)) hasOldLabel = true;
			}
			if (!hasOldValue && !hasOldLabel) continue; // not eligible
			
			if (hasOldValue)
			{
				modified = true;
				DataObject.Annotation annot = new DataObject.Annotation(propURI, oldValueURI, groupNest);
				holding.annotsRemoved = ArrayUtils.add(holding.annotsRemoved, annot);
			}
			if (hasOldLabel)
			{
				modified = true;
				DataObject.TextLabel label = new DataObject.TextLabel(propURI, oldValueLabel, groupNest);
				holding.labelsRemoved = ArrayUtils.add(holding.labelsRemoved, label);
			}
			if (!hasNew)
			{
				modified = true;
				DataObject.Annotation annot = new DataObject.Annotation(propURI, newValueURI, groupNest);
				holding.annotsAdded = ArrayUtils.add(holding.annotsAdded, annot);
			}
		}
		
		if (!modified) return null;
		
		return holding;
	}
}
