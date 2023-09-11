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
import com.cdd.bae.data.DataObject.*;

import org.apache.commons.lang3.*;
import org.apache.http.*;
import org.json.*;

/*
	UpdateUserProfile: edit a user profile

	Parameters:
		curatorID: curator ID for authenticated user 
		userName: new name to be persisted for user with given curatorID
*/

public class UpdateUserProfile extends RESTBaseServlet
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
		return session != null && !StringUtils.isEmpty(session.curatorID);
	}

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		String curatorID = input.getString("curatorID");
		String userName = input.getString("userName");
		String email = input.getString("email");

		// do not allow edits by Trusted parties
		if (session.curatorID.startsWith("trusted"))
			throw new RESTException("Trusted parties cannot edit user-profiles.", RESTException.HTTPStatus.BAD_REQUEST);

		// only allow edits from owner of the session or admins
		if (!curatorID.equals(session.curatorID) && !session.isAdministrator())
			throw new RESTException("Only administrators may edit any user profile.", RESTException.HTTPStatus.BAD_REQUEST);

		// fail if curatorID does not reference an existing user record
		DataStore store = Common.getDataStore();
		User existing = store.user().getUser(curatorID);
		if (existing == null)
			throw new RESTException("User with curatorID " + curatorID + " does not exist.", RESTException.HTTPStatus.BAD_REQUEST);

		// fail if users details were not changed
		if (StringUtils.equals(existing.name, userName) && StringUtils.equals(existing.email, email))
			throw new RESTException("Disallow write operation if user attributes are not changed.", RESTException.HTTPStatus.BAD_REQUEST);

		// write updated profile to database
		store.user().changeUserName(curatorID, userName);
		store.user().changeEmail(curatorID, email);

		JSONObject result = new JSONObject();
		result.put("success", true);
		result.put("status", HttpStatus.SC_OK);

		return result;
	}

	@Override
	protected String[] getRequiredParameter()
	{
		return new String[]{"curatorID", "userName", "email"};
	}
}
