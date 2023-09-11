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

package com.cdd.bae.config.authentication;

import com.cdd.bae.config.authentication.Authentication.*;

import java.util.*;

import org.json.*;

public class Trusted extends Access
{
	public static final AccessType TYPE = AccessType.TRUSTED;

	public String status; // the level of access granted; see DataUser.STATUS_*

	@Override
	public boolean equals(Object o)
	{
		if (o == null || getClass() != o.getClass()) return false;
		Trusted other = (Trusted)o;
		return name.equals(other.name) && prefix.equals(other.prefix) 
				&& status.equals(other.status);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, prefix, status);
	}

	@Override
	public String getType()
	{
		return TYPE.toString();
	}

	@Override
	public JSONObject serializeFrontend()
	{
		JSONObject obj = new JSONObject();
		obj.put("type", getType());
		obj.put("name", name);
		obj.put("prefix", prefix);
		obj.put("status", status);
		return obj;
	}

	// pass through a "trusted" authentication session, which is guaranteed to succeed
	@Override
	public Session authenticate(String... parameter)
	{
		Session session = new Session();
		session.curatorID = prefix + ANYONE;
		session.serviceName = name;
		session.userID = ANYONE;
		session.userName = name;
		session.email = "";
		session.status = status; // just one kind
		return session;
	}

	@Override
	public void parseJSON(JSONObject obj)
	{
		super.parseJSON(obj);
		status = obj.getString("status");
	}
}
