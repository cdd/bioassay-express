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

import com.cdd.bae.config.authentication.*;
import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;

import java.util.*;

import org.apache.http.*;
import org.json.*;

/*
	DeleteProvisionalTerm: cancel list of term requests.
	
	Parameters:
		provisionalIDList: array of unique provisionalIDs that name the term requests to be deleted.

	Return:
		deleted: array of provisionalIDs that name deleted term requests.
		notDeleted: array of provisionalIDs that name term requests that could not be deleted.
*/

public class DeleteProvisionalTerm extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected boolean requireSession()
	{
		return true;
	}

	@Override
	protected boolean hasPermission(Authentication.Session session)
	{
		return session != null && session.canRequestProvisionalTerm();
	}

	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();
		JSONArray wasDeleted = new JSONArray();
		JSONArray notDeleted = new JSONArray();

		// go over the list of requested deletions: anything disallowed or in use gets excluded
		Set<DataObject.Provisional> roster = new LinkedHashSet<>();
		for (long provisionalID : input.getJSONArray("provisionalIDList").toLongArray())
		{
			DataObject.Provisional prov = store.provisional().getProvisional(provisionalID);
			
			boolean isCurator = session.canRequestProvisionalTerm() && session.curatorID.equals(prov.proposerID);
			if (!(session.isAdministrator() || isCurator))
			{
				notDeleted.put(new JSONObject().put("ID", provisionalID).put("reason", "insufficient permission"));
				continue;
			}
			if (isUsedByAssays(store, prov))
				notDeleted.put(new JSONObject().put("ID", provisionalID).put("reason", "term is used"));
			else if (isUsedByHolding(store, prov))
				notDeleted.put(new JSONObject().put("ID", provisionalID).put("reason", "term is used in holding bay"));
			else
				roster.add(prov);
		}
		
		// iterate over the candidate list looking for deletables; leaf nodes have to be deleted first, and can turn other nodes into leaves
		while (!roster.isEmpty())
		{
			boolean anything = false;
			for (Iterator<DataObject.Provisional> it = roster.iterator(); it.hasNext();)
			{
				DataObject.Provisional prov = it.next();
				if (hasDescendents(store, prov)) continue;
				
				if (!store.provisional().deleteProvisional(prov.provisionalID)) continue;
				
				wasDeleted.put(prov.provisionalID);
				it.remove();
				anything = true;
			}
			if (!anything) break;
		}

		Common.getProvCache().update();
		
		for (DataObject.Provisional prov : roster)
			notDeleted.put(new JSONObject().put("ID", prov.provisionalID).put("reason", "not leaf node"));

		JSONObject result = new JSONObject();
		result.put("success", true);
		result.put("status", HttpStatus.SC_OK);
		result.put("deleted", wasDeleted);
		result.put("notDeleted", notDeleted);
		return result;
	}

	@Override
	protected String[] getRequiredParameter()
	{
		return new String[]{"provisionalIDList"};
	}
	
	// ------------ private methods ------------
	
	// returns true if the term's URI is referenced by any assay
	protected static boolean isUsedByAssays(DataStore store, DataObject.Provisional prov)
	{
		// note: it's tempting to think about putting the query into the Mongo request, but the getAssay(..) call puts it through
		// conformAnnotations, which does remapping - and this is important
		for (long assayID : store.assay().fetchAllCuratedAssayID())
		{
			try 
			{
				DataObject.Assay assay = store.assay().getAssay(assayID);
				for (DataObject.Annotation annot : assay.annotations) if (annot.valueURI.equals(prov.uri)) return true;
			}
			catch (IllegalArgumentException e)
			{
				/* ignore */
			}
		}
		return false;
	}
	
	// returns true if the term's URI is referenced by any assay
	protected static boolean isUsedByHolding(DataStore store, DataObject.Provisional prov)
	{
		for (long holdingID : store.holding().fetchHoldings())
		{
			try 
			{
				DataObject.Holding holding = store.holding().getHolding(holdingID);
				for (DataObject.Annotation annot : holding.annotsAdded) if (annot.valueURI.equals(prov.uri)) return true;
				for (DataObject.Annotation annot : holding.annotsRemoved) if (annot.valueURI.equals(prov.uri)) return true;
			}
			catch (IllegalArgumentException e)
			{
				/* ignore */
			}
		}
		return false;
	}
	
	// returns true if there are any other provisional terms that have this one as a parent
	private boolean hasDescendents(DataStore store, DataObject.Provisional prov)
	{
		return store.provisional().fetchChildTerms(prov.uri).length > 0;
	}
}
