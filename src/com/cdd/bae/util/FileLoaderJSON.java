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

package com.cdd.bae.util;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

import org.slf4j.*;
import org.json.*;

/*
	Use FileLoaderJsonArray or FileLoaderJsonObject.
*/

class FileLoaderJSON<T>
{
	private static final Logger logger = LoggerFactory.getLogger(FileLoaderJSON.class);

	private static final String ERROR_LOADING_CONFIGURATION = "Error loading configuration";
	protected int retryDelay = 500;

	private FileWatcher watcher;
	private JSONSchemaValidator validator;

	// ------------ public methods ------------

	public FileLoaderJSON(File file, String schemaDefinition) throws JSONSchemaValidatorException
	{
		super();
		this.watcher = new FileWatcher(file);
		this.validator = JSONSchemaValidator.fromResource(schemaDefinition);
	}
	
	public FileWatcher getWatcher()
	{
		return this.watcher;
	}
	
	public void setFile(File newFile)
	{
		this.watcher.watchFile(newFile);
	}

	public boolean hasChanged()
	{
		return watcher.hasChanged();
	}

	public T load() throws JSONSchemaValidatorException
	{
		File file = watcher.getFile();
		if (!file.isFile())
			throw new JSONSchemaValidatorException(ERROR_LOADING_CONFIGURATION,
					Arrays.asList("Configuration file does not exist: " + file));
		T result;
		try
		{
			String content = new String(Files.readAllBytes(file.toPath()));
			if (getGenericType().endsWith("JSONObject"))
				result = (T)loadJSONObject(content);
			else if (getGenericType().endsWith("JSONArray"))
				result = (T)loadJSONArray(content);
			else
				throw new UnsupportedOperationException("Unsupported type " + getGenericType());
			watcher.reset();
			return result;
		}
		catch (IOException ex)
		{
			throw new JSONSchemaValidatorException(ERROR_LOADING_CONFIGURATION,
					Arrays.asList("Cannot read configuration file: " + file, ex.getMessage()), ex);
		}
		catch (JSONException ex)
		{
			throw new JSONSchemaValidatorException(ERROR_LOADING_CONFIGURATION,
					Arrays.asList("Cannot parse JSON from configuration file: " + file, ex.getMessage()), ex);
		}
	}

	// in contrast to load(), reload fails silently and returns null
	public T reload()
	{
		try
		{
			return load();
		}
		catch (Exception ex)
		{
			logger.debug("Loading file {} failed", watcher.getFile());
		}

		try
		{
			Thread.sleep(retryDelay);
		}
		catch (InterruptedException ex)
		{
			return null;
		}

		try
		{
			return load();
		}
		catch (Exception ex)
		{
			logger.info("Loading file {} failed twice", watcher.getFile());
		}
		return null;
	}

	// ------------ private methods ------------

	private String getGenericType()
	{
		return ((ParameterizedType)getClass().getGenericSuperclass()).getActualTypeArguments()[0].getTypeName();
	}

	private JSONObject loadJSONObject(String content) throws JSONSchemaValidatorException
	{
		JSONObject json = new JSONObject(content);
		List<String> errors = validator.validate(json);
		if (!errors.isEmpty()) throw new JSONSchemaValidatorException(ERROR_LOADING_CONFIGURATION + ": " + watcher.getFile(), errors);
		return json;
	}

	private JSONArray loadJSONArray(String content) throws JSONSchemaValidatorException
	{
		JSONArray json = new JSONArray(new JSONTokener(content));
		List<String> errors = validator.validate(json);
		if (!errors.isEmpty()) throw new JSONSchemaValidatorException(ERROR_LOADING_CONFIGURATION + ": " + watcher.getFile(), errors);
		return json;
	}
}

