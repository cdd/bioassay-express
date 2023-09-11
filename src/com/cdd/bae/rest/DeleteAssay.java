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

import java.util.*;

import org.json.*;

/*
	Delete: submits details about an annotated assay, with the expectation that it will be either applied to the data, placed
	into the holding bay, or rejected.
	
	Parameters:
		assayID: what to delete
		
	Return:
		success: true/false (true = some action was taken; false = complete failure)
		status: one of Status.* defined in RESTBaseServlet
		holdingID: if applicable
*/

public class DeleteAssay extends RESTBaseServlet
{
	private static final long serialVersionUID = 1L;

	// ------------ public methods ------------

	@Override
	protected String[] getRequiredParameter()
	{
		return new String[]{"assayID"};
	}

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
		if (assayID < 1) throw new RESTException("Assay ID must be greater zero", RESTException.HTTPStatus.BAD_REQUEST);

		DataStore store = Common.getDataStore();

		if (store.assay().getAssay(assayID) == null) return statusResponse(false, Status.NONEXISTENT);

		DataObject.Holding holding = new DataObject.Holding();
		holding.assayID = assayID;
		holding.submissionTime = new Date();
		holding.curatorID = session.curatorID;
		holding.deleteFlag = true;

		JSONObject result = new JSONObject();
		if (true /* unavailable until later */ || !session.canSubmitDirectly())
		{
			store.holding().depositHolding(holding);

			result = statusResponse(true, Status.HOLDING);
			result.put("holdingID", holding.holdingID);
		}
		else
		{
			// TODO: currently not allowing direct deletion, but this should be added later
		}
		return result;
	}

	// ------------ private methods ------------

}
