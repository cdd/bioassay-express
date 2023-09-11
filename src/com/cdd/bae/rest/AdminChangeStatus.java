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

import org.apache.http.*;
import org.json.*;

/*
	AdminChangeStatus: edit a user profile

	Parameters:
		curatorID: curator ID for user 
		newStatus: new status for user
*/

public class AdminChangeStatus extends RESTBaseServlet
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
		return session != null && session.isAdministrator();
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String curatorID = input.getString("curatorID");
		String newStatus = input.getString("newStatus");
		DataObject.User user = Common.getDataStore().user().getUser(curatorID);
		if (user == null)
			throw new RESTException("Unknown curator ID", RESTException.HTTPStatus.BAD_REQUEST);
		if (!Arrays.asList(DataUser.ALL_STATUS).contains(newStatus))
			throw new RESTException("Unknown status requested", RESTException.HTTPStatus.BAD_REQUEST);

		Common.getDataStore().user().changeStatus(curatorID, newStatus);

		JSONObject result = new JSONObject();
		result.put("success", true);
		result.put("status", HttpStatus.SC_OK);
		return result;
	}

	@Override
	protected String[] getRequiredParameter()
	{
		return new String[]{"curatorID", "newStatus"};
	}
}
