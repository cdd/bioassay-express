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
import com.cdd.bao.util.*;

import java.io.*;
import java.net.*;
import java.util.*;

import org.apache.directory.api.ldap.model.cursor.*;
import org.apache.directory.api.ldap.model.entry.*;
import org.apache.directory.api.ldap.model.exception.*;
import org.apache.directory.api.ldap.model.message.*;
import org.apache.directory.api.ldap.model.name.*;
import org.apache.directory.ldap.client.api.*;
import org.json.*;
import org.slf4j.*;

public class LDAPAuthentication extends Access
{
	public static final AccessType TYPE = AccessType.LDAP;

	private static Logger logger = LoggerFactory.getLogger(LDAPAuthentication.class);

	public String server;
	public int port = 10636;
	public boolean useSSL = true;
	public String userOU = null;

	@Override
	public boolean equals(Object o)
	{
		if (o == null || getClass() != o.getClass()) return false;
		LDAPAuthentication other = (LDAPAuthentication)o;
		return name.equals(other.name) && prefix.equals(other.prefix) &&
				saveEquals(server, other.server) && (port == other.port) &&
				(useSSL == other.useSSL) && saveEquals(userOU, other.userOU);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(name, prefix, server, port, useSSL, userOU);
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
		return obj;
	}

	@Override
	public Session authenticate(String... parameter) throws IOException
	{
		String username = parameter[0].trim();
		String password = parameter[1].trim();

		Session session = createDefaultSession(username);

		try (LdapConnection connection = new LdapNetworkConnection(server, port, useSSL))
		{
			Dn dnUser = toDistinguishedName(username);
			connection.bind(dnUser, sanitizePassword(password));
			EntryCursor result = connection.search(dnUser, "(uid=*)", SearchScope.OBJECT);
			result.next();
			extractLDAPInformation(session, result.get());
		}
		catch (LdapException | CursorException e)
		{
			logger.error(e.toString());
			return null;
		}

		// authentication successful
		String curatorID = prefix + username;
		DataObject.User user = Common.getDataStore().user().getUser(curatorID);

		if (user != null)
		{
			// the user has been seen before, override content from the hardcoded database
			if (Util.notBlank(user.status)) session.status = user.status;
			if (Util.isBlank(session.userName) && Util.notBlank(user.name)) session.userName = user.name;
			if (Util.isBlank(session.email) && Util.notBlank(user.email)) session.email = user.email;
		}
		return session;
	}

	@Override
	public void parseJSON(JSONObject obj)
	{
		super.parseJSON(obj);
		server = obj.getString("server");
		if (obj.has("port")) port = obj.getInt("port");
		if (obj.has("useSSL")) useSSL = obj.getBoolean("useSSL");
		userOU = obj.getString("userOU");
	}

	public static Session attemptLDAPAuthentication(String username, String pw) throws IOException
	{
		if (username.isEmpty() || pw.isEmpty()) return null;
		Authentication authentication = Common.getAuthentication();
		for (LDAPAuthentication auth : getLDAPAuthentication(authentication))
		{
			Session session = authentication.authenticateSession(auth.name, username, pw);
			if (session != null) return session;
		}
		return null;
	}

	// ------------ private and protected methods ------------

	private static LDAPAuthentication[] getLDAPAuthentication(Authentication authentication)
	{
		Access[] accessList = authentication.getAccessList(AccessType.LDAP);
		return Arrays.copyOf(accessList, accessList.length, LDAPAuthentication[].class);
	}

	protected Dn toDistinguishedName(String username) throws LdapException
	{
		return new Dn("uid=" + encodeForDN(username) + "," + userOU);
	}

	protected static String sanitizePassword(String password)
	{
		return IDN.toASCII(password);
	}

	private void extractLDAPInformation(Session session, Entry entry)
	{
		String value = getEntryKeyValue(entry, "mail");
		if (value != null) session.email = value;
		value = getEntryKeyValue(entry, "cn", "commonname");
		if (value != null) session.userName = value;
	}

	private String getEntryKeyValue(Entry entry, String... keys)
	{
		for (String key : keys)
		{
			Attribute attrib = entry.get(key);
			if (attrib == null) continue;
			try
			{
				return attrib.getString();
			}
			catch (LdapInvalidAttributeValueException e)
			{
				/* try more */
			}
		}
		return null;
	}

	private static boolean saveEquals(Object o1, Object o2)
	{
		if (o1 == null) return o1 == o2;
		return o1.equals(o2);
	}

	// taken and modified from https://github.com/ESAPI/esapi-java-legacy
	protected static String encodeForLDAP(String input)
	{
		if (input == null) return null;

		StringBuilder sb = new StringBuilder();
		for (char c : input.toCharArray())
		{
			if (c == '\\') sb.append("\\5c");
			else if (c == '*') sb.append("\\2a");
			else if (c == '(') sb.append("\\28");
			else if (c == ')') sb.append("\\29");
			else if (c == '\0') sb.append("\\00");
			else sb.append(c);
		}
		return sb.toString();
	}

	protected static String encodeForDN(String input)
	{
		if (input == null) return null;

		StringBuilder sb = new StringBuilder();
		if ((input.length() > 0) && ((input.charAt(0) == ' ') || (input.charAt(0) == '#')))
		{
			sb.append('\\'); // add the leading backslash if needed
		}
		for (char c : input.toCharArray())
		{
			if (c == '\\') sb.append("\\\\");
			else if (c == ',') sb.append("\\,");
			else if (c == '+') sb.append("\\+");
			else if (c == '"') sb.append("\\\"");
			else if (c == '<') sb.append("\\<");
			else if (c == '>') sb.append("\\>");
			else if (c == ';') sb.append("\\;");
			else sb.append(c);
		}
		// add the trailing backslash if needed
		if ((input.length() > 1) && (input.charAt(input.length() - 1) == ' '))
		{
			sb.insert(sb.length() - 1, '\\');
		}
		return sb.toString();
	}

}
