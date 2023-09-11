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
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import org.apache.http.*;
import org.json.*;

/*
	RequestProvisional: request that a provisional term be added to the schema.

	Parameters:
		parentURI: uri of parent value term
		label: label of new provisional
		description: description of new provisional
		explanation: explanation of new provisional
		role: (optional) requested role, see DataObject.ProvisionalRole.{lowercase}
		
		provisionalID: (optional) triggers an update of existing term rather than addition
		
	Return:
		success
		errmsg: if not successful, some indication of why
		provisionalID
		uri
*/

public class RequestProvisional extends RESTBaseServlet
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
		return session != null && session.canRequestProvisionalTerm();
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		if (Util.isBlank(Common.getConfiguration().getProvisional().baseURI))
			throw new RESTException("Provisional term requests have not been enabled", RESTException.HTTPStatus.BAD_REQUEST);

		long provisionalID = input.optLong("provisionalID", 0);
		
		if (provisionalID == 0)
			return createNewTerm(input, session);
		else
			return updateExistingTerm(input, session, provisionalID);
	}
	
	@Override
	protected String[] getRequiredParameter()
	{
		return new String[]{/*"parentURI",*/ "label", "description", "explanation"};
	}

	// if baseURL for provisional terms, return null; otherwise, return a unique URI for the next provisional term
	public static String nextProvisionalURI()
	{
		Configuration config = Common.getConfiguration();
		String baseURL = config.getProvisional().baseURI;

		DataStore store = Common.getDataStore();
		long uriSeqNo = store.provisional().getNextURISequence();
		return String.format("%s%07d", baseURL, uriSeqNo);
	}
	
	// ------------ private methods ------------
	
	private JSONObject createNewTerm(JSONObject input, Session session) throws RESTException
	{
		DataObject.Provisional prov = new DataObject.Provisional();
		prov.label = input.getString("label");
		prov.description = input.getString("description");
		prov.explanation = input.getString("explanation");
		prov.parentURI = ModelSchema.expandPrefix(input.getString("parentURI"));
		prov.proposerID = session.curatorID;
		
		String roleStr = input.optString("role", null);
		try
		{
			if (roleStr != null) prov.role = DataObject.ProvisionalRole.fromString(roleStr);
		}
		catch (IllegalArgumentException ex) {} // silent fail if no match: leave as null
		
		prov.bridgeStatus = DataProvisional.BRIDGESTATUS_UNSUBMITTED;

		var onto = Common.getOntoValues();
		var cache = Common.getProvCache();
		if (onto.getBranch(prov.parentURI) == null && cache.getTerm(cache.remapMaybe(prov.parentURI)) == null)
			return failMessage("Invalid parentURI: " + prov.parentURI);

		prov.uri = nextProvisionalURI();

		// check to see if there is already a {parent/label} match, and if so fail
		var store = Common.getDataStore();
		for (DataObject.Provisional look : store.provisional().fetchAllTerms())
		{
			if (look.parentURI.equals(prov.parentURI) && look.label.equals(prov.label)) 
				return failMessage("Could not add provisional with URI " + prov.uri);
		}
		store.provisional().updateProvisional(prov);
		Common.getProvCache().update();
		
		JSONObject result = new JSONObject();

		// return sequence ID of new provisional term, as well as uri of said term
		result.put("provisionalID", prov.provisionalID);
		result.put("uri", prov.uri);
		result.put("success", true);
		result.put("status", HttpStatus.SC_OK);
		return result;
	}
	
	private JSONObject updateExistingTerm(JSONObject input, Session session, long provisionalID) throws RESTException
	{
		DataStore store = Common.getDataStore();
		DataObject.Provisional prov = store.provisional().getProvisional(provisionalID);
		if (prov == null)
			throw new RESTException("Provisional ID not found: " + provisionalID, RESTException.HTTPStatus.BAD_REQUEST);

		if (!session.isAdministrator() && !prov.proposerID.equals(session.curatorID))
			throw new RESTException("Non-administrators can only edit their own term requests", RESTException.HTTPStatus.BAD_REQUEST);
	
		JSONObject result = new JSONObject();
	
		prov.label = input.getString("label");
		prov.description = input.getString("description");
		prov.explanation = input.getString("explanation");
		
		String role = input.optString("role", null);
		try
		{
			if (role != null) prov.role = DataObject.ProvisionalRole.valueOf(role.toUpperCase());
		}
		catch (IllegalArgumentException ex) {} // silent fail if no match: leave as null

		store.provisional().updateProvisional(prov);
		Common.getProvCache().update();
		
		result.put("provisionalID", prov.provisionalID);
		result.put("uri", prov.uri);
		result.put("success", true);
		result.put("status", HttpStatus.SC_OK);
		return result;
	}
	
	private JSONObject failMessage(String msg)
	{
		JSONObject result = new JSONObject();
		result.put("success", false);
		result.put("errmsg", msg);
		return result;
	}
}
