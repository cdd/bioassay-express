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
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;

import java.io.*;
import java.util.*;

import org.json.*;

public abstract class Access
{
	public enum AccessType
	{
		TRUSTED("Trusted"), BASIC("Basic"), OAUTH("OAuth"), LDAP("LDAP"), SAML("SAML");

		private String type;

		AccessType(String type)
		{
			this.type = type;
		}

		public boolean implementedBy(Access access)
		{
			return access != null && type.equals(access.getType());
		}

		@Override
		public String toString()
		{
			return type;
		}

		public static AccessType get(String type)
		{
			for (AccessType obj : AccessType.values())
			{
				if (obj.type.equals(type)) return obj;
			}
			return null;
		}
	}

	public static final String ANYONE = "anyone";

	public String name; // short name to describe the service
	public String prefix; // prefix used to disambiguate user IDs

	@Override
	public boolean equals(Object o)
	{
		if (o == null || getClass() != o.getClass()) return false;
		Access other = (Access)o;
		return name.equals(other.name) && prefix.equals(other.prefix);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, prefix);
	}

	public abstract String getType();

	public abstract JSONObject serializeFrontend();

	public abstract Authentication.Session authenticate(String... parameter) throws IOException;

	public void parseJSON(JSONObject obj)
	{
		name = obj.getString("name");
		prefix = obj.getString("prefix");
	}

	public static Access fromJSON(JSONObject obj) throws JSONSchemaValidatorException
	{
		AccessType type = AccessType.get(obj.getString("type"));
		if (type == null)
			throw new JSONSchemaValidatorException("Authentication configuration: unknown type '" + obj.getString("type") + "'");
		Access result = null;
		if (type.equals(AccessType.TRUSTED))
			result = new Trusted();
		else if (type.equals(AccessType.OAUTH))
			result = new OAuth();
		else if (type.equals(AccessType.BASIC))
			result = new BasicAuthentication();
		else if (type.equals(AccessType.LDAP))
			result = new LDAPAuthentication();
		else
			throw new JSONSchemaValidatorException("Authentication configuration: '" + obj.getString("type") + "' not implemented");
		result.parseJSON(obj);
		return result;
	}

	public Session createDefaultSession(String username)
	{
		Session session = new Session();
		session.type = getType();
		session.serviceName = name;
		session.accessToken = UUID.randomUUID().toString();
		session.userID = username;
		session.userName = username;
		session.email = "";
		session.status = DataUser.STATUS_DEFAULT;
		session.curatorID = prefix + username;
		return session;
	}

}
