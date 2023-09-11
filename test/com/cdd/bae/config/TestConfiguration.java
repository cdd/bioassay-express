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

import com.cdd.bae.config.InitParams.*;
import com.cdd.bae.config.authentication.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;

import java.io.*;

import org.apache.commons.lang3.*;

public class TestConfiguration
{
	static Configuration configuration = null;

	static File[] templateFiles = null;

	static boolean notOnCI = isNotOnCI();
	static SchemaVocab schemaVocab = null;

	public static String getProductionConfiguration()
	{
		String productionConfiguration = "/opt/bae";
		if (new File(productionConfiguration + "/cfg").isDirectory())
			productionConfiguration = productionConfiguration + "/cfg";
		return productionConfiguration;
	}

	public static Configuration getConfiguration() throws ConfigurationException
	{
		return getConfiguration(true);
	}

	public static Configuration getConfiguration(boolean loadNLPmodels) throws ConfigurationException
	{
		if (configuration != null)
		{
			if (loadNLPmodels) configuration.loadNLPModels();
			return configuration;
		}

		String productionConfiguration = getProductionConfiguration();
		File file = new File(productionConfiguration + "/config.json");
		try
		{
			configuration = new Configuration(file.getAbsolutePath(), productionConfiguration, null, loadNLPmodels);

			// keep copies of original information
			//if (notOnCI) createSchemaVocabCopy();
			//templateFiles = configuration.getTemplateFiles().getExplicitFiles();
		}
		catch (ConfigurationException e)
		{
			System.out.println(e.getMessage());
			for (String s : e.getDetails())
				System.out.println("  " + s);
			throw e;
		}
		configuration.getParams().production = true;
		configuration.getParams().verboseDebug = true;
		return configuration;
	}

	/* !!
	public static void restoreSchemaVocab() throws IOException, ConfigurationException
	{
		if (configuration == null) return;
		if (notOnCI)
			configuration.getSchemaVocabFile().setSchemaVocab(createCopy(schemaVocab));
		else
			configuration.getSchemaVocabFile().reload();
	}*/

	/* !!
	public static void restoreTemplate() throws ConfigurationException
	{
		configuration.getTemplateFiles().setExplicitFiles(templateFiles);
	}*/

	public static void setProvisionals(Provisional provisional)
	{
		if (configuration != null) configuration.setProvisionals(provisional);
	}

	/* !!
	public static void setSchemaVocab(SchemaVocab schemaVocab)
	{
		if (configuration != null) configuration.setSchemaVocab(schemaVocab);
	}*/

	public static void setAuthentication(Authentication authentication)
	{
		if (configuration != null) configuration.setAuthentication(authentication);
	}

	//-- private methods ------

	private static boolean isNotOnCI()
	{
		return System.getenv("CIRCLE_BRANCH") == null;
	}

	/* !!
	private static void createSchemaVocabCopy() throws ConfigurationException
	{
		if (configuration == null) return;
		try
		{
			schemaVocab = createCopy(configuration.getSchemaVocab());
		}
		catch (IOException e)
		{
			throw new ConfigurationException(e.getMessage());
		}
	}*/

	/* !!
	private static SchemaVocab createCopy(SchemaVocab schemaVocab) throws IOException
	{
		try (ByteArrayOutputStream memoryStream = new ByteArrayOutputStream())
		{
			schemaVocab.serialise(memoryStream);
			try (ByteArrayInputStream inputStream = new ByteArrayInputStream(memoryStream.toByteArray()))
			{
				Schema[] templates = ArrayUtils.addAll(configuration.getAllSchemata(), configuration.getBranchSchemata());
				return SchemaVocab.deserialise(inputStream, templates);
			}
		}
	}*/
}
