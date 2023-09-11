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

import com.cdd.bao.template.*;

import java.io.*;
import java.util.*;

import org.slf4j.*;

/*
	...
*/

public class FileLoaderSchema extends FileWatcher
{
	private static final Logger logger = LoggerFactory.getLogger(FileLoaderSchema.class);

	private static final String ERROR_LOADING_CONFIGURATION = "Error loading configuration";
	protected int retryDelay = 500;

	// ------------ public methods ------------

	public FileLoaderSchema(File file) 
	{
		super(file);
	}

	public Schema load() throws ConfigurationException
	{
		try
		{
			Schema result = SchemaUtil.deserialise(getFile()).schema;
			reset();
			return result;
		}
		catch (IOException ex)
		{
			throw new ConfigurationException(ERROR_LOADING_CONFIGURATION, Arrays.asList("Cannot read configuration file", ex.getMessage()), ex);
		}
	}

	// in contrast to load(), reload fails silently and returns null
	public Schema reload()
	{
		try {return load();}
		catch (Exception ex) {logger.debug("Loading file {} failed", getFile());}

		try {Thread.sleep(retryDelay);}
		catch (InterruptedException ex) {return null;}

		try {return load();}
		catch (Exception ex) {logger.info("Loading file {} failed twice", getFile());}

		return null;
	}
}

