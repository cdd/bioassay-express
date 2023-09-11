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

import com.cdd.bae.config.authentication.Access.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.slf4j.*;

/*
	Singleton class that loads up the list of approved authentication services and provides access to them.
*/

public class Authentication
{
	private static final Logger logger = LoggerFactory.getLogger(Authentication.class);

	private String schemaDefinition = "/com/cdd/bae/config/AuthenticationSchema.json";

	private File file = null;
	protected FileLoaderJSONArray loader;
	public List<Access> listAccess = new ArrayList<>();
	public static Map<String, Session> currentSessions = new HashMap<>();

	public static class Session
	{
		public String type;
		public String curatorID; // composed from source prefix & user ID: unique identifier for the session
		public String serviceName; // matches name field in one of the authentication options
		public String accessToken; // unique string that can be taken as confirmation
		public String userID; // user ID from the service proper
		public String userName; // name of the user
		public String email; // if available
		// (should add an expiry date...)

		public String status = DataUser.STATUS_DEFAULT; // from existing records, if applicable

		// judgment calls about what a session is allowed to do
		public boolean canSubmitHoldingBay()
		{
			return status.equals(DataUser.STATUS_DEFAULT) || status.equals(DataUser.STATUS_CURATOR) || status.equals(DataUser.STATUS_ADMIN);
		}
		public boolean canSubmitDirectly()
		{
			return status.equals(DataUser.STATUS_CURATOR) || status.equals(DataUser.STATUS_ADMIN);
		}
		public boolean canSubmitBulk()
		{
			return status.equals(DataUser.STATUS_CURATOR) || status.equals(DataUser.STATUS_ADMIN);
		}
		public boolean canApplyHolding()
		{
			return status.equals(DataUser.STATUS_CURATOR) || status.equals(DataUser.STATUS_ADMIN);
		}
		public boolean canRequestProvisionalTerm()
		{
			return status.equals(DataUser.STATUS_CURATOR) || status.equals(DataUser.STATUS_ADMIN);
		}
		public boolean isAdministrator()
		{
			return status.equals(DataUser.STATUS_ADMIN);
		}
	}

	// ------------ public methods ------------

	// empty constructor: can be used as a placeholder for nothing available
	public Authentication() throws JSONSchemaValidatorException
	{
		this(null);
	}

	// instantiation: parses out the configuration file, which is failable
	public Authentication(String filename) throws JSONSchemaValidatorException
	{
		super();
		listAccess.clear();
		if (filename != null && Util.notBlank(filename.trim())) file = new File(filename);
		loader = new FileLoaderJSONArray(file, schemaDefinition);
		if (file != null) this.parseJSON(loader.load());
	}

	// try to reload the configuration. Errors will be suppressed here.
	public void reload()
	{
		if (loader.getWatcher().getFile() == null) return;

		try
		{
			this.parseJSON(loader.load());
		}
		catch (JSONSchemaValidatorException e)
		{
			logger.error("Couldn't reload configuration from configuration file");
		}
	}

	public boolean hasChanged()
	{
		return loader.hasChanged();
	}

	public void setFile(File newFile)
	{
		loader.setFile(newFile);
	}

	@Override
	public boolean equals(Object o)
	{
		if (o == null || getClass() != o.getClass()) return false;
		Authentication other = (Authentication)o;
		return Util.equals(listAccess, other.listAccess);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(listAccess);
	}

	// public Session authenticateSession(String name, String code, String currentURL) throws IOException
	public Session authenticateSession(String name, String... parameter) throws IOException
	{
		Access access = getAccess(name);
		if (access == null) throw new IOException("Authentication '" + name + "' invalid.");

		Session session = access.authenticate(parameter);
		if (session == null) return null; // not possible

		// and, update the database: make sure that the curatorID exists, and that the rest is current
		Common.getDataStore().user().submitSession(session);
		putSession(session);
		return session;
	}

	// access to content
	public boolean hasAnyAccess()
	{
		return !listAccess.isEmpty();
	}

	public boolean hasAnyAccess(AccessType type)
	{
		for (Access access : listAccess) if (type.implementedBy(access)) return true;
		return false;
	}

	public Access[] getAccessList()
	{
		return listAccess.toArray(new Access[listAccess.size()]);
	}

	public Access[] getAccessList(AccessType type)
	{
		return listAccess.stream().filter(type::implementedBy).toArray(Access[]::new);
	}

	public Access getAccess(String name)
	{
		for (Access access : listAccess) if (access.name.equals(name)) return access;
		return null;
	}

	public Session getSession(String curatorID)
	{
		synchronized (currentSessions)
		{
			return currentSessions.get(curatorID);
		}
	}

	public void putSession(Session session)
	{
		synchronized (currentSessions)
		{
			currentSessions.put(session.curatorID, session);
		}
	}

	// ------------ private methods ------------

	void parseJSON(JSONArray json) throws JSONSchemaValidatorException
	{
		listAccess.clear();
		for (int n = 0; n < json.length(); n++)
		{
			JSONObject obj = json.getJSONObject(n);
			listAccess.add(Access.fromJSON(obj));
		}
	}
}
