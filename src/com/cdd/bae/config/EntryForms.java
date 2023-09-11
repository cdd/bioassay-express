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

import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.json.*;

/*
	Container class for "forms", which are portals onto a template that can be used to customise the UI, generally
	for data entry.
	
*/

public class EntryForms extends DirectoryWatcher
{
	public static class Entry
	{
		public String name; // top level name
		public int priority; // ordering; lowerest is first, and the default "template" form is 0
		public String[] schemaURIList; // the templates to which this applies
		public JSONArray sections;
	}

	public static final String SCHEMA_DEFINITION = "/com/cdd/bae/config/EntryFormsSchema.json";

	protected Map<File, FileLoaderJSONArray> loader;
	private List<Entry> entries;

	// ------------ public methods ------------

	public EntryForms(String formsDir, String[] formFiles) throws ConfigurationException
	{
		super(formsDir, formFiles, true);
	}

	// there are no default files and we only accept required files
	@Override
	public boolean isValidFile(File f)
	{
		return false;
	}

	// it is not necessary to have files
	@Override
	public boolean requireFiles()
	{
		return false;
	}

	@Override
	public void postLoad() throws ConfigurationException
	{
		synchronized (this)
		{
			if (entries == null) entries = new ArrayList<>();
			entries.clear();
			for (File file : getFilesOrdered()) loadFile(file);
		}
	}

	// access to content, by schema
	public Entry[] getEntries() {return entries.toArray(new Entry[entries.size()]);}

	// ------------ private methods ------------

	// loads up a single forms file, which consists of any number of entries
	protected void loadFile(File file) throws ConfigurationException
	{
		JSONObject[] items;
		try
		{
			if (loader == null) loader = new HashMap<>();
			if (!loader.containsKey(file)) loader.put(file, new FileLoaderJSONArray(file, SCHEMA_DEFINITION));
			JSONArray list = loader.get(file).load();
			items = list.toObjectArray();
		}
		catch (JSONSchemaValidatorException ex) {throw new ConfigurationException(ex.getMessage(), ex.getDetails(), ex);}

		for (JSONObject item : items)
		{
			Entry entry = new Entry();
			try
			{
				entry.name = item.optString("name");
				entry.priority = item.optInt("priority", 0);
				entry.schemaURIList = item.getJSONArray("schemaURI").toStringArray();
				entry.sections = item.getJSONArray("sections");
			}
			catch (JSONException ex) {throw new ConfigurationException("Invalid content for " + file, ex);}
			entries.add(entry);
		}
	}
}
