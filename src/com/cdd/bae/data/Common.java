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

package com.cdd.bae.data;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import javax.servlet.*;

import org.apache.commons.lang3.*;
import org.apache.log4j.*;
import org.json.*;
import org.slf4j.*;
import org.slf4j.Logger;

import com.cdd.bae.config.*;
import com.cdd.bae.config.authentication.*;
import com.cdd.bae.tasks.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import opennlp.tools.chunker.*;
import opennlp.tools.namefind.*;
import opennlp.tools.parser.*;
import opennlp.tools.postag.*;
import opennlp.tools.sentdetect.*;
import opennlp.tools.tokenize.*;

/*
	Common objects: access to resources that are load once/read many. 
*/

public class Common implements DataStore.Notifier
{
	private static final Logger logger = LoggerFactory.getLogger(Common.class);

	// ------------ initiation ------------

	public static final Object mutex = new Object(); // sometimes used by other objects to protect a compound operation
	public static boolean beenActivated = false; // set to true once the loading mechanism has begun

	private static Common main = null;
	private static ServletContext initContext = null;
	private static long initTime = 0;

	private static DataStore store = null;

	private static String configFN = null;
	private static Configuration configuration = null;
	
	private static OntologyTree ontoProps = null, ontoValues = null;
	private static ProvisionalCache provCache = null;

	// performs initialisation: only performs actions the first time it is called
	public static void bootstrap(ServletContext context)
	{
		//org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.OFF);
		
		synchronized (mutex)
		{
			if (initContext != null) return;

			beenActivated = true;

			initContext = context;

			// configuration file: the default location is specified in the web.xml file, but this can be
			// overridden as a system parameter (command line "-Dbae.cfg=/wherever")
			configFN = initContext.getInitParameter("config-file");
			String syscfg = System.getProperty("bae.cfg");
			if (Util.notBlank(syscfg)) configFN = syscfg;
			
			if (configFN.startsWith("$BUNDLE/"))
			{
				try 
				{
					configFN = new File(context.getRealPath("/") + configFN.substring(7)).getCanonicalPath();
				}
				catch (IOException ex) {throw new RuntimeException(ex);}
				Util.writeln("Configuration file mapped to: " + configFN);
			}
			// structure of BAE configuration directory changed for deployment. Try the new location if configFN is missing
			var configFile = new File(configFN);
			if (!configFile.exists())
			{
				configFile = new File(configFile.getParent() + "/cfg/" + configFile.getName());
				configFN = configFile.getAbsolutePath();
			}
			
			Util.writeln("Configuration file: " + configFN);
			if (!configFile.exists())
			{
				Util.writeln("ERROR: configuration file not found.");
				return;
			}

			var logFile = new File(new File(configFN).getParent() + "/log4j.properties").getAbsoluteFile();
			if (!logFile.exists())
			{
				// trigger service failure if log4j.properties not in the right place
				Util.writeln("ERROR: log4j.properties not found; log4j logging unavailable");
				return;
			}
			PropertyConfigurator.configureAndWatch(logFile.getPath(), 10000);

			logger.info("Bootstrap: initializing");
			logger.info("Bootstrap: Configuration file: {}", configFN);

			configuration = null;
			try
			{
				configuration = new Configuration(configFN);
			}
			catch (ConfigurationException ex)
			{
				logger.error("Configuration failed: [" + String.join(", ", ex.getDetails()) + "]", ex);
				return;
			}

			Util.writeln("==== Parameters ====\n\n" + configuration.getParams() + "\n");
			bootstrap(configuration.getParams());
			
			Util.writeln("Loading ontology...");
			try (var istr = context.getResourceAsStream("/WEB-INF/data/ontology.gz"))
			{
				var gzip = new GZIPInputStream(istr);
				ontoProps = OntologyTree.deserialise(gzip);
				ontoValues = OntologyTree.deserialise(gzip);
				gzip.close();
				
				Util.writeln("    ontology properties: " + ontoProps.countURI());
				Util.writeln("    ontology values:     " + ontoValues.countURI());
			}
			catch (IOException ex)
			{
				logger.info("Extreme failure: unable to read ontologies.");
				Util.errmsg("Ontology load failure", ex);
			}
			
			Util.writeln("Caching provisional terms...");
			provCache = new ProvisionalCache();
			provCache.update();
			Util.writeln("    # provisional terms: " + provCache.numTerms());
		}
	}

	// alternate entrypoint for bootstrapping, which does not involve a servlet context; this is the way
	// to go for command line invocation
	public static void bootstrap(Configuration configuration)
	{
		Common.configuration = configuration;
		bootstrap(configuration.getParams());
	}

	public static void bootstrap(InitParams params)
	{
		synchronized (mutex)
		{
			try
			{
				beenActivated = true;
			
				main = new Common();

				initTime = new Date().getTime();

				store = new DataStore(params.database);
				store.setNotifier(main);
			}
			catch (Exception ex)
			{
				logger.error("Bootstrap failure", ex);
			}
		}
	}

	// the 'stamp' is used to create arbitrary URL appendages that change each time the server is
	// restarted: this is a way
	// to force the browser to reload resources like JavaScript whenever the service is restarted
	// NOTE! if we're adding the ability to do things like refreshing the schema templates without
	// restarting the server, then
	// there has to be a way to update the stamp - otherwise browsers may stash older versions
	public static long stamp()
	{
		return initTime;
	}

	// returns true if the bootstrapping has been initiated; if bootstrapping is in progress, will wait
	// until it is complete
	public static boolean isBootstrapped()
	{
		synchronized (mutex)
		{
			return initTime > 0;
		}
	}
	
	// returns true if running in stateless mode, i.e. all database operations are unavailable
	public static boolean isStateless()
	{
		return !store.isDBAvailable();
	}

	// ------------ access to commons ------------

	// returns the filename that is known to contain the configuration, or null if not defined
	public static String getConfigFN()
	{
		synchronized (mutex)
		{
			return configuration.getConfigFN();
		}
	}

	// get the directory where normal web files reside (i.e. the stuff that the server normally passes
	// through as-is)
	public static String getWebDir()
	{
		if (initContext == null) return null;
		return initContext.getRealPath(".") + "/resource";
	}

	public static DataStore getDataStore()
	{
		synchronized (mutex)
		{
			return store;
		}
	}

	// ------------ keeping in sync ------------

	// sent when assay text has been added, changed or deleted, meaning that a fingerprint refresh is
	// necessary
	public void datastoreTextChanged()
	{
		if (FingerprintCalculator.main() != null) FingerprintCalculator.main().bump();
	}

	// sent when fingerprints for an assay have been modified
	public void datastoreFingerprintsChanged()
	{
		store.model().nextWatermarkNLP();
		if (ModelBuilder.main() != null) ModelBuilder.main().bump();
	}

	// sent when an assay's annotations have been modified
	public void datastoreAnnotationsChanged()
	{
		store.model().nextWatermarkCorr();
		if (CorrelationBuilder.main() != null) CorrelationBuilder.main().bump();
	}

	// sent when measurements may be in need of updating
	public void datastoreMeasurementsChanged()
	{
		store.measure().nextWatermarkMeasure();
		if (PubChemAssays.main() != null) PubChemAssays.main().bumpMeasurements();
	}

	// sent when compounds have been amended such that action is required (e.g. downloading structures)
	public void datastoreCompoundsChanged()
	{
		store.compound().nextWatermarkCompound();
		if (PubChemAssays.main() != null) PubChemAssays.main().bumpCompounds();
	}

	// sent when structures have been filled in
	public void datastoreStructuresChanged()
	{
		// nop (for now)
	}

	// ------------- access to configuration data ----------

	public static void setConfiguration(Configuration newConfiguration)
	{
		synchronized (mutex)
		{
			configuration = newConfiguration;
		}
	}

	public static Configuration getConfiguration()
	{
		synchronized (mutex)
		{
			return configuration;
		}
	}
	
	public static boolean isProduction()
	{
		synchronized (mutex)
		{
			return configuration.isProduction();
		}
	}

	public static boolean isVerboseDebug()
	{
		synchronized (mutex)
		{
			return configuration.isVerboseDebug();
		}
	}

	public static InitParams getParams()
	{
		synchronized (mutex)
		{
			return configuration.getParams();
		}
	}

	// module information
	public static InitParams.ModulePubChem getModulePubChem()
	{
		return getParams().modulePubChem;
	}

	public static InitParams.ModuleVault getModuleVault()
	{
		return getParams().moduleVault;
	}

	public static Authentication getAuthentication()
	{
		synchronized (mutex)
		{
			return configuration.getAuthentication();
		}
	}

	public static Identifier getIdentifier()
	{
		synchronized (mutex)
		{
			return configuration.getIdentifier();
		}
	}

	public static Schema getSchemaCAT()
	{
		return configuration.getSchemaCAT();
	}

	public static Schema[] getAllSchemata()
	{
		return configuration.getAllSchemata();
	}

	public static Schema[] getBranchSchemata()
	{
		return configuration.getBranchSchemata();
	}

	public static Schema getSchema(String schemaURI)
	{
		return configuration.getSchema(schemaURI);
	}
	
	public static Transliteration getTransliteration() 
	{
		return configuration.getTransliteration();
	}
	
	public static EntryForms getForms() 
	{
		return configuration.getEntryForms();
	}
	
	public static SchemaVocab getSchemaVocab()
	{
		synchronized (mutex)
		{
			return configuration.getSchemaVocab();
		}
	}

	public static SchemaVocabFile getSchemaVocabFile()
	{
		synchronized (mutex)
		{
			return configuration.getSchemaVocabFile();
		}
	}

	public static AxiomVocab getAxioms()
	{
		AxiomFiles axiomFiles = configuration.getAxiomFiles();
		if (axiomFiles == null) return new AxiomVocab();
		return axiomFiles.getAxioms();
	}

	public static NLPModels getNLPModels()
	{
		synchronized (mutex)
		{
			return configuration.getNLPModels();
		}
	}

	public static SentenceModel getSentenceModel()
	{
		synchronized (mutex)
		{
			return configuration.getSentenceModel();
		}
	}

	public static TokenizerModel getTokenModel()
	{
		synchronized (mutex)
		{
			return configuration.getTokenModel();
		}
	}

	public static POSModel getPosModel()
	{
		synchronized (mutex)
		{
			return configuration.getPosModel();
		}
	}

	public static ChunkerModel getChunkModel()
	{
		synchronized (mutex)
		{
			return configuration.getChunkModel();
		}
	}

	public static ParserModel getParserModel()
	{
		synchronized (mutex)
		{
			return configuration.getParserModel();
		}
	}

	public static TokenNameFinderModel getLocationModel()
	{
		synchronized (mutex)
		{
			return configuration.getLocationModel();
		}
	}

	public static TokenNameFinderModel getPersonModel()
	{
		synchronized (mutex)
		{
			return configuration.getPersonModel();
		}
	}

	public static TokenNameFinderModel getOrganizationModel()
	{
		synchronized (mutex)
		{
			return configuration.getOrganizationModel();
		}
	}

	public static String getCustomWebDir()
	{
		synchronized (mutex)
		{
			return configuration.getCustomWebDir();
		}
	}

	public static InitParams.PageToggle getPageToggle()
	{
		return configuration.getPageToggle();
	}
	
	public static InitParams.URIPatternMap[] getURIPatternMaps()
	{
		return configuration.getURIPatternMaps();
	}

	public static InitParams.BuildData getBuildData()
	{
		return configuration.getBuildData();
	}

	// ------------ access to the baseline ontology ------------
	
	// returns the static global baseline ontology tree for properties
	public static OntologyTree getOntoProps() 
	{
		return ontoProps;
	}
	
	// returns the static global baseline ontology tree for properties
	public static OntologyTree getOntoValues()
	{
		return ontoValues;
	}
	
	public static ProvisionalCache getProvCache()
	{
		return provCache;
	}

	// ------------ schema trees: quick fetching from cache ------------

	// obtain a tree instance for the given assignment
	public static SchemaTree obtainTree(Schema schema, Schema.Assignment assn)
	{
		//return configuration.obtainTree(schema, assn.propURI, assn.groupNest());
		return new CompositeTree(getOntoValues(), assn).compose();
	}

	// convenience method for grabbing a schema tree, by supplying only the property URI
	public static SchemaTree obtainTree(Schema schema, String propURI, String[] groupNest)
	{
		//return configuration.obtainTree(schema, propURI, groupNest);
		var assn = schema.findAssignmentByProperty(propURI, groupNest);
		if (assn.length == 0) return null;
		return obtainTree(schema, assn[0]);
	}

	// return template-specific name for the specified value
	public static String getCustomName(Schema schema, String propURI, String[] groupNest, String valueURI)
	{
		Schema.Assignment[] assn = schema.findAssignmentByProperty(propURI, groupNest);
		if (assn.length > 0) for (Schema.Value v : assn[0].values)
		{
			if (v.uri.equals(valueURI) && !StringUtils.isEmpty(v.name)) return v.name;
		}
		return null;
	}

	// return template-specific description for the specified value
	public static String getCustomDescr(Schema schema, String propURI, String[] groupNest, String valueURI)
	{
		Schema.Assignment[] assn = schema.findAssignmentByProperty(propURI, groupNest);
		if (assn.length > 0) for (Schema.Value v : assn[0].values)
		{
			if (v.uri.equals(valueURI) && !StringUtils.isEmpty(v.descr)) return v.descr;
		}
		return null;
	}

	// ------------ private methods ------------

	// ----- methods required for testing ------

	public static void setDataStore(DataStore store)
	{
		Common.store = store;
	}

	public static void setProvCache(ProvisionalCache provCache)
	{
		Common.provCache = provCache;
	}
	
	public static void setOntologies(OntologyTree ontoProps, OntologyTree ontoValues)
	{
		Common.ontoProps = ontoProps;
		Common.ontoValues = ontoValues;
	}
	
	public static void makeBootstrapped()
	{
		initTime = 1;
	}
}
