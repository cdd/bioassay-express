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
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

/*
	Manage loading and reloading of templates.
*/

public class Templates extends DirectoryWatcher
{
	public static final String DEFAULT_TEMPLATE = "schema.json";

	private File[] templateExplicitFiles;
	private String[] templateUpdates;
	private Map<String, Schema> schemaMap;
	private List<Schema> schemaList, schemaBranches;

	// ------------ public methods ------------

	public Templates(String templateDir, String[] templateFiles, String[] templateUpdates) throws ConfigurationException
	{
		super(templateDir, templateFiles, true);
		this.templateUpdates = templateUpdates;
		
		templateExplicitFiles = new File[0];
	}

	@Override
	public List<String> getDefaultFiles()
	{
		return Arrays.asList(DEFAULT_TEMPLATE);
	}

	@Override
	public boolean isValidFile(File f)
	{
		return f.getName().endsWith(".json");
	}

	@Override
	public void postLoad() throws ConfigurationException
	{
		List<File> files = getFilesOrdered();
		synchronized (this)
		{
			if (schemaMap == null) schemaMap = new HashMap<>();
			if (schemaList == null) schemaList = new ArrayList<>();
			if (schemaBranches == null) schemaBranches = new ArrayList<>();
	
			schemaList.clear();
			schemaMap.clear();
			schemaBranches.clear();
			
			for (File file : files)
			{
				Schema schema = ((FileLoaderSchema)getFileWatcher(file)).load();
				if (schema.getBranchGroups() == null) 
					schemaList.add(schema);
				else
					schemaBranches.add(schema);
				schemaMap.put(schema.getSchemaPrefix(), schema);
			}
		}
	}

	@Override
	protected List<File> getFilesOrdered() throws ConfigurationException
	{
		List<File> files = super.getFilesOrdered();
		if (templateExplicitFiles == null) return files;
		for (File file : templateExplicitFiles)
		{
			file = file.getAbsoluteFile();
			if (!files.contains(file))
				files.add(file);
		}
		return files;
	}
	
	// override for custom file watcher
	@Override
	public FileWatcher newFileWatcher(File file)
	{
		return new FileLoaderSchema(file);
	}

	// get the main schema (first in list)
	public Schema getSchemaCAT()
	{
		synchronized (this)
		{
			return schemaList.get(0);
		}
	}

	public Schema[] getSchemaList()
	{
		synchronized (this)
		{
			return schemaList == null ? new Schema[0] : schemaList.toArray(new Schema[schemaList.size()]);
		}
	}
	
	public Schema[] getSchemaBranches()
	{
		synchronized (this)
		{
			return schemaBranches == null ? new Schema[0] : schemaBranches.toArray(new Schema[schemaBranches.size()]);
		}
	}
	
	public File[] getExplicitFiles()
	{
		synchronized (this)
		{
			return Arrays.copyOf(templateExplicitFiles, templateExplicitFiles.length);
		}
	}
	
	public void setExplicitFiles(File[] files) throws ConfigurationException
	{
		synchronized (this)
		{
			templateExplicitFiles = Arrays.copyOf(files, files.length);
			load();
		}
	}
	
	public String[] getUpdateURIs()
	{
		synchronized (this)
		{
			return templateUpdates == null ? new String[0] : Arrays.copyOf(templateUpdates, templateUpdates.length);
		}
	}

	public Schema getSchema(String schemaURI)
	{
		Schema schemaCAT = getSchemaCAT();
		synchronized (this)
		{
			if (Util.isBlank(schemaURI) || schemaURI.equals(schemaCAT.getSchemaPrefix())) return schemaCAT;
			return schemaMap.get(schemaURI);
		}
	}
}
