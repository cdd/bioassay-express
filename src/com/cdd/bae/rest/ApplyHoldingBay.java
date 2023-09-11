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

import org.json.*;
import org.apache.commons.lang3.*;

/*
	ApplyHoldingBay: applies (or removes) holding bay entries, as long as permission is available. Order is sometimes important, and will be
	taken as the order supplied in the parameter arrays, with applies being effected first, followed by deletions. When an action is
	successful (either apply or delete), the holding bay entry is no longer present in the database.
	
	Parameters:
		applyList: array of holding bay IDs to apply
		deleteList: array of holding bay IDs to remove from the holding bay
		
	Return:
		success: true/false (it worked or it didn't)
		status: one of STATUS_* below
		holdingIDList: a holding bay ID for each entry affected (no longer valid in database)
		assayIDList: analogous assay IDs for each (for deletions, these will be no longer valid)
*/

public class ApplyHoldingBay extends RESTBaseServlet 
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
		return session != null && session.canApplyHolding();
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		long[] applyList = input.optJSONArrayEmpty("applyList").toLongArray();
		long[] deleteList = input.optJSONArrayEmpty("deleteList").toLongArray();
		
		long[] affectedHoldingID = new long[0];
		long[] affectedAssayID = new long[0];

		DataStore store = Common.getDataStore();

		// apply actions first
		for (long holdingID : applyList)
		{
			DataObject.Holding holding = store.holding().getHolding(holdingID);
			if (holding == null) continue;
			
			if (!holding.deleteFlag)
			{
				DataObject.Assay assay = holding.assayID > 0 ? store.assay().getAssay(holding.assayID) : null;
				if (assay == null && holding.uniqueID != null) assay = store.assay().getAssayFromUniqueID(holding.uniqueID);
				assay = DataHolding.createAssayDelta(assay, holding);
				store.assay().submitAssay(assay);
				affectedHoldingID = ArrayUtils.add(affectedHoldingID, holdingID);
				affectedAssayID = ArrayUtils.add(affectedAssayID, assay.assayID);
			}
			else // this is a deletion request (applying it removes the assay)
			{
				if (holding.assayID > 0)
				{
					store.measure().deleteMeasurementsForAssay(holding.assayID);
					if (store.assay().deleteAssay(holding.assayID)) 
					{
						affectedHoldingID = ArrayUtils.add(affectedHoldingID, holding.holdingID);
						affectedAssayID = ArrayUtils.add(affectedAssayID, holding.assayID);
					}
				}
			}

			store.holding().deleteHolding(holdingID);
		}

		// delete actions second
		for (long holdingID : deleteList)
		{
			DataObject.Holding holding = store.holding().getHolding(holdingID);
			if (holding == null) continue;
			
			store.holding().deleteHolding(holdingID);
		}
		
		JSONObject result = new JSONObject();
		result.put("success", true);
		result.put("status", Status.HOLDING);
		result.put("holdingIDList", affectedHoldingID);
		result.put("assayIDList", affectedAssayID);
		return result;
	}

	// ------------ private methods ------------

//	// creates a holding bay entry that contains the assay as modified by the mapping list parameters; if nothing changed,
//	// returns null
//	private DataObject.Holding applyMapping(DataObject.Assay assay, JSONObject[] mappingList, Session session)
//	{
//		Schema schema = Common.getSchema(assay.schemaURI);
//		if (schema == null) return null;
//		
//		DataObject.Holding holding = new DataObject.Holding();
//		holding.assayID = assay.assayID;
//		holding.submissionTime = new Date();
//		holding.curatorID = session.curatorID;
//		holding.schemaURI = schema.getSchemaPrefix();
//		
//		boolean modified = false;
//		
//		for (JSONObject mapping : mappingList)
//		{
//			String propURI = mapping.getString("propURI");
//			String[] groupNest = mapping.getJSONArray("groupNest").toStringArray();
//			String valueURI = mapping.getString("valueURI");
//			String newValueURI = mapping.getString("newValueURI");
//		
//			// note: may want to check that newValueURI is legitimately in the schema; because it's only going in the holding bay,
//			// it's not necessarily critical
//		
//			boolean hasOld = false, hasNew = false;
//			for (int n = 0; n < assay.annotations.length; n++)
//			{
//				DataObject.Annotation annot = assay.annotations[n];
//				if (!Schema.compatiblePropGroupNest(propURI, groupNest, annot.propURI, annot.groupNest)) continue;
//				if (valueURI.equals(annot.valueURI)) hasOld = true;
//				if (newValueURI.equals(annot.valueURI)) hasNew = true;
//			}
//			
//			if (hasOld)
//			{
//				modified = true;
//				DataObject.Annotation annot = new DataObject.Annotation(propURI, valueURI, groupNest);
//				holding.annotsRemoved = ArrayUtils.add(holding.annotsRemoved, annot);
//			}
//			if (!hasNew)
//			{
//				modified = true;
//				DataObject.Annotation annot = new DataObject.Annotation(propURI, newValueURI, groupNest);
//				holding.annotsAdded = ArrayUtils.add(holding.annotsAdded, annot);
//			}
//		}
//		
//		if (!modified) return null;
//		
//		return holding;
//	}
}
