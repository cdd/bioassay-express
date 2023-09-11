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

package com.cdd.bae.config;

import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.slf4j.*;
import org.json.*;


/*
	Class that loads up the list unique identifiers that are used to distinguish assays from one another -
	typically by reference to an invariant location (e.g. PubChem Assay ID).
*/

public class Identifier
{
	private static final Logger logger = LoggerFactory.getLogger(Identifier.class);

	// assay identifiers that have fixed meaning to certain bespoke parts of the codebase
	public static final String PUBCHEM_PREFIX = "pubchemAID:";
	public static final String VAULT_PREFIX = "vaultPID:";
	public static final String[] defaultSummary = {ModelSchema.expandPrefix(AssayUtil.URI_ASSAYTITLE), null, "autotext:"};

	private String schemaDefinition = "/com/cdd/bae/config/IdentifierSchema.json";

	private File file = null;
	protected FileLoaderJSONArray loader;
	private List<Source> listSources = new ArrayList<>();

	public static final class Source
	{
		public String name; // concise descriptive name for the identifier
		public String shortName; // very short mnemonic prefix for the identifier, for when space is at a premium
		public String prefix; // prefix that must uniquely disambiguate
		public String baseURL; // meaningful URL for official source; catenating the ID (post-prefix) should be meaningful
		public String baseRegex; // regular expression to help with validation & extracting key payload
		public String recogRegex; // optional recogniser regular expression for base payload (e.g. ^\d+$ when expecting a plain number)
		public String defaultSchema; // optional schemaURI for default schema, if any
		public String[] summary;

		public static Source fromJSON(JSONObject json)
		{
			Source source = new Source();
			source.name = json.getString("name");
			source.shortName = json.getString("shortName");
			source.prefix = json.getString("prefix");
			source.baseURL = json.optString("baseURL", "");
			source.baseRegex = json.optString("baseRegex", "");
			source.recogRegex = json.optString("recogRegex", "");
			source.defaultSchema = json.optString("defaultSchema", "");
			JSONArray jarr = json.optJSONArray("summary");
			if (jarr == null)
				source.summary = defaultSummary;
			else
			{
				source.summary = new String[jarr.length()];
				for (int i = 0; i < jarr.length(); i++)
					source.summary[i] = ModelSchema.expandPrefix(jarr.optString(i, null));
			}
			return source;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;

			Source other = (Source) o;
			return name.equals(other.name) && shortName.equals(other.shortName) && prefix.equals(other.prefix) && 
					baseURL.equals(other.baseURL) && baseRegex.equals(other.baseRegex) && recogRegex.equals(other.recogRegex) &&
					defaultSchema.equals(other.defaultSchema) && Arrays.equals(summary, other.summary);
		}
		
		@Override
		public int hashCode()
		{
			return Objects.hash(name, shortName, prefix, baseURL, baseRegex, recogRegex, defaultSchema, Arrays.hashCode(summary));
		}
	}

	public static final class UID
	{
		public Source source;
		public String id;

		public UID(Source source, String id)
		{
			this.source = source;
			this.id = id;
		}
		
		// provide a way to sort identifiers; first pass: if different sources, sort by source name; second pass: try to convert the identifier
		// string into a number (which is possible within most schemes) and use that preferentially; non-value strings fall back to raw string
		// comparisons
		public int compareTo(UID other)
		{
			int cmp = source.name.compareTo(other.source.name);
			if (cmp != 0) return cmp;

			long val1 = toValue(id), val2 = toValue(other.id);
			if (val1 != 0 || val2 != 0) return Long.compare(val1, val2);
			return id.compareTo(other.id);
		}
		
		// converts an identifier to a number, if possible; will strip away prefixes; if it's not a number, will return 0
		private long toValue(String str)
		{
			for (int n = 0; n < str.length(); n++)
			{
				char ch = str.charAt(n);
				if (ch >= '0' && ch <= '9') return Util.safeLong(n == 0 ? str : str.substring(n));
			}
			return 0;
		}
	}

	// ------------ public methods ------------

	// empty constructor: can be used as a placeholder for nothing available
	public Identifier() throws JSONSchemaValidatorException
	{
		this(null);
	}

	// instantiation: parses out the configuration file, which is failable
	public Identifier(String filename) throws JSONSchemaValidatorException
	{
		super();
		listSources.clear();
		if (Util.notBlank(filename)) file = new File(filename);
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
		Identifier other = (Identifier) o;
		return listSources.equals(other.listSources);
	}

	@Override
	public int hashCode()
	{
		return listSources.hashCode();
	}

	// access to content
	public Source[] getSources()
	{
		return listSources.toArray(new Source[listSources.size()]);
	}

	public Source getSource(String prefix)
	{
		for (Source source : listSources) if (source.prefix.equals(prefix)) return source;
		return null;
	}

	// interconversion between a single string that can be used to refer to the assay indefinitely
	public static String makeKey(UID uid)
	{
		return uid.source.prefix + uid.id;
	}

	public static String makeKey(Source source, String id)
	{
		return source.prefix + id;
	}

	// splits a key into its constituents; returns null if the prefix is not recognised
	public UID parseKey(String key)
	{
		if (key == null) return null;
		for (Source source : listSources) if (key.startsWith(source.prefix))
		{
			String id = key.substring(source.prefix.length());
			return new UID(source, id);
		}
		return null;
	}

	// returns a URL that refers to the original thing
	public String composeRefURL(String key)
	{
		return composeRefURL(parseKey(key));
	}

	public String composeRefURL(UID uid)
	{
		if (uid == null) return null;
		return uid.source.baseURL + uid.id;
	}

	// ------------ private methods ------------

	void parseJSON(JSONArray json)
	{
		// json is valid, load the data
		listSources.clear();
		for (int n = 0; n < json.length(); n++)
		{
			JSONObject obj = json.getJSONObject(n);
			Source source = Source.fromJSON(obj);
			listSources.add(source);
		}
	}
}
