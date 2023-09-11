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
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.slf4j.*;
import org.slf4j.Logger;

/*
	Command line/interactive entrypoint for the BioAssay Express, as opposed to via the web server.
	
	Invocation is based on a number of pluggable modules. Some of these are experimental and/or temporary, while others
	are meant for routine use.
*/

public class Main
{
	private static Logger logger = null;

	static
	{
		long time = new Date().getTime();
		logger = LoggerFactory.getLogger(Main.class);
		time = new Date().getTime() - time;
		if (time > 1000) logger.error("Time taken to load logging file: {} seconds.", time * 0.001f);
	}

	public interface ExecuteBase
	{
		public void execute(String[] args) throws IOException;
		public void printHelp();
		public default boolean needsNLP() {return false;}
		public default boolean needsConfig() {return true;}
	}

	protected static final String[][] COMMANDS = new String[][]
	{
		{"vocab", "Vocabulary: Schema/Ontology", "com.cdd.bae.main.VocabCommands"},
		{"holding", "Holding Bay", "com.cdd.bae.main.HoldingCommands"},
		{"user", "User Accounts", "com.cdd.bae.main.UserCommands"},
		{"maintenance", "Maintenance Operations", "com.cdd.bae.main.MaintenanceCommands"},
		{"bulk", "Bulk Data Operations", "com.cdd.bae.main.BulkCommands"},
		{"transfer", "Import/Export Assays", "com.cdd.bae.main.TransferCommands"},
		{"search", "Search Features", "com.cdd.bae.main.SearchCommands"},
		{"metrics", "Metrics Generation", "com.cdd.bae.main.MetricsCommands"},
		{"axioms", "Axiom Analysis", "com.cdd.bae.main.AxiomCommands"},
		{"geneid", "GeneID Fauxtology", "com.cdd.bae.main.GeneIDCommands"},
		{"protein", "Protein Fauxtology", "com.cdd.bae.main.ProteinCommands"},
		{"method", "Method Validation", "com.cdd.bae.main.MethodCommands"},
		{"nlpstats", "Tally NLP stats for named assay", "com.cdd.bae.main.NLPStatsCommands"},
		{"forms", "Verify Forms", "com.cdd.bae.main.FormVerification"},
		{"deploy", "Deployment Bundle", "com.cdd.bae.main.DeploymentBundle"},
		{"ontology", "Ontology Basis", "com.cdd.bae.main.OntologyCommands"},
	};

	// ------------ static methods ------------

	public static void main(String[] args)
	{
		// special deals for help: overall, and command-specific
		if (args.length == 0 || args[0].equals("-h") || args[0].equals("--help")) {printHelp(); return;}
		if (args[0].equals("help"))
		{
			if (args.length == 1) {printHelp(); return;}
			ExecuteBase base = obtainBase(args[1]);
			if (base == null) {Util.writeln("Unknown command: " + args[1]); return;}
			base.printHelp();
			return;
		}
		
		// find the command
		ExecuteBase base = obtainBase(args[0]);
		if (base == null) {Util.writeln("Unknown command: " + args[0]); return;}

		// strip out first argument and generic parameters
		List<String> params = new ArrayList<>();
		String initFN = "/opt/bae/config.json";
		for (int n = 1; n < args.length; n++)
		{
			if (args[n].equals("--init") && n + 1 < args.length)
			{
				File f = new File(args[++n]);
				if (!f.exists())
				{
					Util.writeln("Init parameter invalid: [" + initFN + "]");
					return;
				}
				initFN = f.isDirectory() ? f.getAbsolutePath() + "/config.json" : f.getAbsolutePath();
			}
			else params.add(args[n]);
		}

		final String cfgFN = initFN;
		final boolean wantNLP = base.needsNLP(), wantConfig = base.needsConfig();
		
		if (wantConfig)
		{
			new Thread(() -> loadConfiguration(cfgFN, wantNLP)).start();
	
			// give the bootstrapping a chance to catch
			while (!Common.beenActivated)
			{
				try {Thread.sleep(50);} 
				catch (InterruptedException ex) {}
			}
		}

		// let it rip
		try {base.execute(params.toArray(new String[params.size()]));}
		catch (Exception ex)
		{
			Util.writeln("Execution failed:");
			ex.printStackTrace();
		}
	}

	protected static ExecuteBase obtainBase(String cmd)
	{
		for (String[] blk : COMMANDS) if (blk[0].equals(cmd))
		{
			try
			{
				Class<?> cls = Class.forName(blk[2]);
				return (ExecuteBase)cls.getDeclaredConstructor().newInstance();
			}
			catch (Exception ex) // (not really a thing)
			{
				logger.error("Unknown command " + cmd, ex);
				return null;
			}
		}
		return null;
	}

	private static void printHelp()
	{
		Util.writeln("BioAssay Express (BAE)");
		Util.writeln("    (c) 2016-2019 Collaborative Drug Discovery Inc.");
		Util.writeln("    Command line entrypoint\n");
		
		Util.writeln("Command line options:");
		
		Util.writeln("    bae {command} [options]");
		Util.writeln("    bae help {command}");
		
		Util.writeln("\nCommand is one of:");
		for (String[] blk : COMMANDS) Util.writeln("    " + blk[0] + ": " + blk[1]);
		
		Util.writeln("\nUniversal options:");
		Util.writeln("    --init {path}    specify reference files (if needed)");
	}
	
	private static void loadConfiguration(String cfgFN, boolean wantNLP)
	{
		// structure of BAE configuration directory changed for deployment. Try the new location if configFN is missing
		File configFile = new File(cfgFN);
		if (!configFile.exists())
		{
			configFile = new File(configFile.getParent() + "/cfg/" + configFile.getName());
			cfgFN = configFile.getAbsolutePath();
		}

		Util.writeln("Bootstrapping from [" + cfgFN + "]");
		try 
		{
			// none of the command line functions need NLP; deal with this if ever this is not so
			org.apache.log4j.Level oldLevel = org.apache.log4j.LogManager.getLogger(Configuration.class).getLevel();
			org.apache.log4j.LogManager.getLogger(Configuration.class).setLevel(org.apache.log4j.Level.FATAL);
			Configuration configuration = new Configuration(cfgFN, wantNLP);
			org.apache.log4j.LogManager.getLogger(Configuration.class).setLevel(oldLevel);
			Common.bootstrap(configuration);
			
		}
		catch (ConfigurationException ex)
		{
			Util.writeln("Configuration contains errors:");
			for (String detail : ex.getDetails()) Util.writeln("    " + detail);
			System.exit(1);
		}
		catch (Exception ex)
		{
			ex.printStackTrace();
		}
	}
}



