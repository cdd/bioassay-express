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

package com.cdd.bae.main;

import com.cdd.bae.config.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.io.*;

/*
	Application for validation of configuration files.
*/

public class ValidateConfiguration
{
	public static void main(String[] args)
	{
		// special deals for help: overall, and command-specific
		if (args.length != 1 || args[0].equals("-h") || args[0].equals("--help"))
		{
			printHelp(); 
			return;
		}

		File configFile = new File(args[0]);
		if (!configFile.isFile())
		{
			printHelp();
			Util.writeln("\nNot a file: " + configFile);
			System.exit(1);
		}

		try
		{
			org.apache.log4j.LogManager.getLogger(Configuration.class).setLevel(org.apache.log4j.Level.FATAL);
			Util.writeln("Validating configuration file " + configFile);
			Configuration configuration = new Configuration(configFile.getAbsolutePath());
			Util.writeln();
			Util.writeln("Configuration is valid\n");
			Util.writeln(configuration.getParams().toString());
			Util.writeln();
		}
		catch (ConfigurationException e)
		{
			Util.writeln();
			Util.writeln(e.getMessage());
			Util.writeln();
			for (String d: e.getDetails()) Util.writeln(d);
			Util.writeln();
			System.exit(1);
		}
	}

	private static void printHelp()
	{
		Util.writeln("BioAssay Express (BAE)");
		Util.writeln("    (c) 2016-2018 Collaborative Drug Discovery Inc.");
		Util.writeln("    Configuration validation\n");
		Util.writeln("validateConfiguration [--help] configFile\n");
		Util.writeln("Command line options:");
		Util.writeln("    configFile  : path to configuration file for validation");
		Util.writeln("    --help, -h  : print help");
	}
}
