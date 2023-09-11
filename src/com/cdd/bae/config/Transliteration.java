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
import org.apache.commons.lang3.*;

/*
	Container class for "transliteration" instructions.
	
	TODO: spec out the JSON syntax for the input file
*/

public class Transliteration extends DirectoryWatcher
{
	public static class Boilerplate
	{
		public String preview = null; // non-null to define a "preview category"
		public String block = null; // non-null to define a "block category"
		public JSONArray content;
	}
	protected Map<String, Boilerplate[]> boilers; // schemaURI-to-transliteration boilerplate

	// use e.g. https://www.jsonschemavalidator.net/ for validation of transliteration files using the schema
	protected static String schemaDefinition = "/com/cdd/bae/config/TransliterationSchema.json";

	// ------------ public methods ------------

	public Transliteration(String translitDir, String[] translitFiles) throws ConfigurationException
	{
		super(translitDir, translitFiles, true);
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
			if (boilers == null) boilers = new HashMap<>();
			boilers.clear();
			for (File file : getFilesOrdered()) loadTranslitFile(file);
		}
	}

	// get the names of the "preview"/"block" boilerplates corresponding to a particular schema, if any
	public String[] getPreviews(String schemaURI)
	{
		List<String> results = new ArrayList<>();
		synchronized (this) 
		{
			Boilerplate[] boilerList = boilers.get(schemaURI);
			if (boilerList != null) for (Boilerplate boiler : boilerList) if (Util.notBlank(boiler.preview)) results.add(boiler.preview);
		}
		return results.toArray(new String[results.size()]);
	}
	public String[] getBlocks(String schemaURI)
	{
		List<String> results = new ArrayList<>();
		synchronized (this) 
		{
			Boilerplate[] boilerList = boilers.get(schemaURI);
			if (boilerList != null) for (Boilerplate boiler : boilerList) if (Util.notBlank(boiler.block)) results.add(boiler.block);
		}
		return results.toArray(new String[results.size()]);
	}

	// access the boilerplate corresponding to a schema; by default the one with "null" preview is returned
	public Boilerplate getBoilerplate(String schemaURI) {return getBoilerplate(schemaURI, null, null);}
	public Boilerplate getBoilerplatePreview(String schemaURI, String preview) {return getBoilerplate(schemaURI, preview, null);}
	public Boilerplate getBoilerplateBlock(String schemaURI, String block) {return getBoilerplate(schemaURI, null, block);}
	public Boilerplate getBoilerplate(String schemaURI, String preview, String block)
	{
		synchronized (this) 
		{
			Boilerplate[] boilerList = boilers.get(schemaURI);
			if (boilerList != null) for (Boilerplate boiler : boilerList) 
				if (Util.equals(preview, boiler.preview) && Util.equals(block, boiler.block)) return boiler;
			return null;
		}
	}

	// ------------ private methods ------------

	// loads up a single transliteration file, which consists of any number of "boilerplate" instances
	protected void loadTranslitFile(File file) throws ConfigurationException
	{
		JSONObject[] items = loadTemplateFile(file).toObjectArray();
		
		// each section of the transliteration file should indicate one-or-more schemas, each of which is directed to use the
		// given boilerplate (i.e. can reuse for multiple)
		for (JSONObject item : items)
		{
			String[] schemaURIList;
			Boilerplate bp = new Boilerplate();
			try
			{
				schemaURIList = item.getJSONArray("schemaURI").toStringArray();
				bp.preview = item.optString("preview");
				bp.block = item.optString("block");
				bp.content = item.getJSONArray("boilerplate");
			}
			catch (JSONException ex) {throw new ConfigurationException("Invalid content for " + file, ex);}
			
			for (String schemaURI : schemaURIList)
			{
				Boilerplate[] list = boilers.get(schemaURI);
				boilers.put(schemaURI, ArrayUtils.add(list, bp));
			}
		}
	}

	
	protected static JSONArray loadTemplateFile(File file) throws ConfigurationException
	{
		JSONArray list;
		try (Reader rdr = new FileReader(file))
		{
			list = new JSONArray(new JSONTokener(rdr));
		}
		catch (IOException ex) {throw new ConfigurationException("Failed to load " + file, ex);}
		
		return loadBuildingBlocks(list, file);
	}
	
	private static JSONArray loadBuildingBlocks(JSONArray json, File file) throws ConfigurationException
	{
		JSONArray result = new JSONArray();
		for (int i = 0; i < json.length(); i++)
		{	
			Object e = json.get(i);
			if (e instanceof JSONArray) 
				result.put(loadBuildingBlocks((JSONArray)e, file));
			else if (e instanceof JSONObject)
			{
				JSONObject obj = (JSONObject)e;
				if (obj.length() == 1 && obj.has("include"))
				{
					String bbName = obj.getString("include");
					JSONArray bblock = loadTemplateFile(new File(file.getParentFile(), bbName));
					for (int k = 0; k < bblock.length(); k++)
						result.put(bblock.get(k));
				}
				else
					result.put(processJSONObject((JSONObject)e, file));
			}
			else
				result.put(e);
		}
		return result;
	}
	
	private static JSONObject processJSONObject(JSONObject json, File file) throws ConfigurationException
	{
		Iterator<?> keys = json.keys();
		while (keys.hasNext())
		{
			String key = (String) keys.next();
			Object e = json.get(key);
			if (e instanceof JSONObject)
				json.put(key, processJSONObject((JSONObject)e, file));
			else if (e instanceof JSONArray)
				json.put(key, loadBuildingBlocks((JSONArray)e, file));
		}
		return json;
	}

}
