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

/*
	Security Assertion Markup Language (SAML, pronounced SAM-el) is an open
	standard for exchanging authentication and authorization data between parties,
	in particular, between an identity provider and a service provider.

	This is only a stub. The associated test file contains information about
	the setup of a developer machine. Actual implementation is postponed until
	require one.

	Watch https://www.youtube.com/watch?v=S9BpeOmuEz4 for a useful introduction.
	CDD Vault implements SAML too. There is also a test development server.
	Best contact is Peter Nyberg.
*/

public class SAMLAuthentication extends Access
{
	public static final AccessType TYPE = AccessType.SAML;

	public String entityID;
	public String assertionConsumerService;
	public String singleLogoutService;

	public String metadataService;

	@Override
	public boolean equals(Object o)
	{
		if (o == null || getClass() != o.getClass())
			return false;
		SAMLAuthentication other = (SAMLAuthentication)o;
		return name.equals(other.name) && prefix.equals(other.prefix);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, prefix);
	}

	@Override
	public String getType()
	{
		return TYPE.toString();
	}

	@Override
	public void parseJSON(JSONObject obj)
	{
		super.parseJSON(obj);
		entityID = obj.getString("entityID");
		assertionConsumerService = obj.getString("assertionConsumerService");
		singleLogoutService = obj.getString("singleLogoutService");
	}

	@Override
	public JSONObject serializeFrontend()
	{
		JSONObject obj = new JSONObject();
		obj.put("type", getType());
		obj.put("name", name);
		obj.put("prefix", prefix);
		return obj;
	}

	@Override
	public Session authenticate(String... parameter)
	{
		return null;
	}

	// ------------ private and protected methods ------------

}
