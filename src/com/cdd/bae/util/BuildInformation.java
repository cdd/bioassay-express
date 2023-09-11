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
import java.util.*;

import org.slf4j.*;

/*
	 Fetches build information from the packaged JAR file and makes it available to the web pages.
*/

public class BuildInformation
{
	private static final Logger logger = LoggerFactory.getLogger(BuildInformation.class);

	public static final String BUILDTIME = "buildtime";
	public static final String GITREVISION = "gitrevision";
	public static final String GITBRANCH = "gitbranch";

	private static final String BUILDINFO_PROPERTIES = "com/cdd/bae/buildinfo.properties";

	private Properties prop = new Properties();

	public BuildInformation()
	{
		this(BUILDINFO_PROPERTIES);
	}

	public BuildInformation(String buildinfoProperties)
	{
		loadBuildInformation(buildinfoProperties);
	}

	public String get(String key)
	{
		String result = prop.getProperty(key, "");
		if (key.equals(BUILDTIME)) result = result.split(" ")[0];
		else if (key.equals(GITBRANCH))
		{
			if (result.equals("master") || result.equals("git failed"))
				result = "";
		}
		return result;
	}

	private void loadBuildInformation(String buildinfoProperties)
	{
		prop.setProperty(BUILDTIME, "");
		prop.setProperty(GITREVISION, "");
		prop.setProperty(GITBRANCH, "");
		try (InputStream input = BuildInformation.class.getClassLoader().getResourceAsStream(buildinfoProperties))
		{
			if (input != null) prop.load(input);
		}
		catch (IOException e)
		{
			logger.info("Buildinformation not found or cannot be loaded");
		}
	}
}
