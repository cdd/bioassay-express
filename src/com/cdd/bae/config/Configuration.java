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

import com.cdd.bae.config.authentication.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;

import org.slf4j.*;

import opennlp.tools.chunker.*;
import opennlp.tools.namefind.*;
import opennlp.tools.parser.*;
import opennlp.tools.postag.*;
import opennlp.tools.sentdetect.*;
import opennlp.tools.tokenize.*;

/*
	Configuration contains the full configuration
	
	InitParams contains the information required to initialize the other parts of the configuration.
	The configuration file for initParams is provided in the constructor. 
*/

public class Configuration
{
	private static Logger logger = LoggerFactory.getLogger(Configuration.class);

	private InitParams params;
	private Authentication authentication = null;
	private Identifier identifier = null;
	private NLPModels nlpModels = null;
	private SchemaVocabFile schemaVocabFile = null; // schema-vocab parsed
	private Templates templates = null;
	private Transliteration transliteration = null;
	private EntryForms forms = null;
	private AxiomFiles axioms = null;
	
	private String configFN = null;

	// ------------ public methods ------------

	public Configuration(String configFN) throws ConfigurationException
	{
		this(configFN, null, null, true);
	}
	public Configuration(String configFN, Configuration oldConfiguration) throws ConfigurationException
	{
		this(configFN, null, oldConfiguration, true);
	}
	public Configuration(String configFN, String baseDir) throws ConfigurationException
	{
		this(configFN, baseDir, null, true);
	}
	public Configuration(String configFN, String baseDir, Configuration oldConfiguration) throws ConfigurationException
	{
		this(configFN, baseDir, oldConfiguration, true);
	}
	public Configuration(String configFN, boolean loadNLPModels) throws ConfigurationException
	{
		this(configFN, null, null, loadNLPModels);
	}

	/**
	 * Most basic constructor for Configuration
	 * <ul>
	 * <li>oldConfiguration: If oldConfiguration is present, existing information for NLPmodels and
	 * SchemaVocabFile will be reused if it hasn't changed.
	 * <li>loadNLPModels: Set this to false to skip the loading of NLP models
	 * </ul>
	 */
	public Configuration(String configFN, String baseDir, Configuration oldConfiguration, boolean loadNLPModels) throws ConfigurationException
	{
		boolean reuseOld;
		try
		{
			this.configFN = configFN;

			LogTimer timer = new LogTimer(logger, "Load configuration");
			params = new InitParams(configFN, baseDir);
			timer.report("InitParams information loaded (if available), time required: {}");

			// skip loading models, if we can reuse old NLP models (models are loaded in background
			reuseOld = oldConfiguration != null && oldConfiguration.params.nlpDir.equals(params.nlpDir) && !oldConfiguration.nlpModels.hasChanged();
			Thread loadNLPthread = null;
			if (loadNLPModels && !reuseOld)
			{
				loadNLPthread = new Thread(this::loadNLPModels);
				loadNLPthread.start();
				logger.debug("Load NLP models in background");
			}
			else if (loadNLPModels && reuseOld)
			{
				nlpModels = oldConfiguration.nlpModels;
				logger.debug("NLP models from old configuration used");
			}
			else
			{
				nlpModels = null;
				logger.debug("NLP models not loaded");
			}

			LogTimer block = new LogTimer(logger);
			authentication = new Authentication(params.authenticationFile);
			block.report("Authentication information loaded (if available), time required: {}");

			block.reset();
			identifier = new Identifier(params.identifierFile);
			block.report("Identifier information loaded (if available), time required: {}");

			block.reset();
			templates = new Templates(params.template.directory, params.template.files, params.template.updates);
			block.report("Schema templates loaded: {} templates, time required: {}", Integer.toString(templates.getSchemaList().length));
			
			block.reset();
			transliteration = new Transliteration(params.translit.directory, params.translit.files);
			block.report("Transliteration loaded, time required: {}");

			block.reset();
			forms = new EntryForms(params.forms.directory, params.forms.files);
			block.report("Forms loaded, time required: {}");
			
			if (Util.notBlank(params.axiomsDir))
			{
				block.reset();
				axioms = new AxiomFiles(params.axiomsDir);
				block.report("Axioms loaded, time required: {}");
			}

			reuseOld = oldConfiguration != null &&
					   oldConfiguration.params.schemaVocabFile.equals(params.schemaVocabFile) &&
					   oldConfiguration.params.schemaVocabUpdate.equals(params.schemaVocabUpdate) &&
					   !oldConfiguration.schemaVocabFile.hasChanged();
			if (reuseOld)
			{
				// reuse information from old configuration and update schema list from latest templates
				schemaVocabFile = oldConfiguration.schemaVocabFile;
				schemaVocabFile.setSchemaList(templates.getSchemaList());
				logger.debug("SchemaVocab file information from old configuration reused.");
			}
			else
			{
				block.reset();
				schemaVocabFile = new SchemaVocabFile(params, templates);
				block.report("SchemaVocab file loaded, time required: {}");
			}

			if (loadNLPthread != null)
			{
				block.reset();
				loadNLPthread.join();
				block.report("Waiting for NLP models to finish loading (if required), time required: {}");
			}
			timer.report("Configuration loaded successfully, time required: {}");
		}
		catch (JSONSchemaValidatorException | ConfigurationException ex)
		{
			logger.error("Error loading configuration ({})", ex.getMessage());
			for (String detail : ex.getDetails()) logger.error("  " + detail);
			throw new ConfigurationException("Failure to load configuration: " + ex.getMessage(), ex.getDetails(), ex);
		}
		catch (InterruptedException ex)
		{
			throw new ConfigurationException("Failure to load NLP models: " + ex.getMessage(), ex);
		}
	}

	public boolean hasChanged()
	{
		if (params.hasChanged()) return true;
		if (authentication != null && authentication.hasChanged()) return true;
		if (identifier != null && identifier.hasChanged()) return true;
		if (forms != null && forms.hasChanged()) return true;
		if (nlpModels != null && nlpModels.hasChanged()) return true;
		if (templates.hasChanged() || schemaVocabFile.hasChanged()) return true;
		if (transliteration != null && transliteration.hasChanged()) return true;
		return false;
	}

	// ------------ access to commons ------------

	// returns the filename that is known to contain the configuration, or null if not defined
	public String getConfigFN()
	{
		return configFN;
	}

	public InitParams getParams()
	{
		return params;
	}
	
	public boolean isProduction()
	{
		return params.production;
	}

	public boolean isVerboseDebug()
	{
		return params.verboseDebug;
	}

	// module information
	public InitParams.ModulePubChem getModulePubChem()
	{
		return params.modulePubChem;
	}

	public InitParams.ModuleVault getModuleVault()
	{
		return params.moduleVault;
	}

	public String getCustomWebDir()
	{
		return params.customWebDir;
	}

	public InitParams.PageToggle getPageToggle()
	{
		return params.pageToggle;
	}

	public InitParams.Provisional getProvisional()
	{
		return params.provisional;
	}
	
	public InitParams.OntoloBridge[] getOntoloBridges()
	{
		return params.bridges;
	}

	public InitParams.URIPatternMap[] getURIPatternMaps()
	{
		return params.uriPatternMaps;
	}
	
	public Authentication getAuthentication()
	{
		return authentication;
	}
	
	public Identifier getIdentifier()
	{
		return identifier;
	}
	
	public Schema getSchemaCAT()
	{
		return templates.getSchemaCAT();
	}

	public Schema[] getAllSchemata()
	{
		return templates.getSchemaList();
	}
	
	public Schema[] getBranchSchemata()
	{
		return templates.getSchemaBranches();
	}

	public Templates getTemplateFiles() 
	{
		return templates;
	}

	public Schema getSchema(String schemaURI)
	{
		return templates.getSchema(schemaURI);
	}

	public SchemaVocab getSchemaVocab()
	{
		return schemaVocabFile.getSchemaVocab();
	}

	public SchemaVocabFile getSchemaVocabFile()
	{
		return schemaVocabFile;
	}	
	
	public NLPModels getNLPModels()
	{
		return nlpModels;
	}

	public void loadNLPModels()
	{
		// create NLP models only once
		if (nlpModels != null || params.nlpDir == null) return;

		try
		{
			LogTimer timer = new LogTimer(logger);
			nlpModels = new NLPModels(params.nlpDir);
			timer.report("Completed loading NLP models, time required: {}");
		}
		catch (IOException e)
		{
			nlpModels = null;
			logger.error("Failed to load NLP models", e);
		}
	}

	public SentenceModel getSentenceModel()
	{
		return nlpModels.sentenceModel;
	}

	public TokenizerModel getTokenModel()
	{
		return nlpModels.tokenModel;
	}

	public POSModel getPosModel()
	{
		return nlpModels.posModel;
	}

	public ChunkerModel getChunkModel()
	{
		return nlpModels.chunkModel;
	}

	public ParserModel getParserModel()
	{
		return nlpModels.parserModel;
	}

	public TokenNameFinderModel getLocationModel()
	{
		return nlpModels.locationModel;
	}

	public TokenNameFinderModel getPersonModel()
	{
		return nlpModels.personModel;
	}

	public TokenNameFinderModel getOrganizationModel()
	{
		return nlpModels.organizationModel;
	}
	
	public Transliteration getTransliteration()
	{
		return transliteration;
	}
	
	public AxiomFiles getAxiomFiles()
	{
		return axioms;
	}

	public String[] getAbsenceTerms()
	{
		return params.absenceTerms;
	}

	public EntryForms getEntryForms()
	{
		return forms;
	}
	
	public InitParams.BuildData getBuildData()
	{
		return params.buildData;
	}

	// ------------ schema trees: quick fetching from cache ------------

	// convenience method for grabbing a schema tree, by supplying only the property URI
	public SchemaTree obtainTree(Schema schema, Schema.Assignment assn)
	{
		return schemaVocabFile.obtainTree(schema, assn.propURI, assn.groupNest());
	}

	// convenience method for grabbing a schema tree, by supplying only the property URI
	public SchemaTree obtainTree(Schema schema, String propURI, String[] groupNest)
	{
		return schemaVocabFile.obtainTree(schema, propURI, groupNest);
	}
	
	// required for testing
	
	protected void setSchemaVocab(SchemaVocab schemaVocab)
	{
		schemaVocabFile.setSchemaVocab(schemaVocab);
	}

	protected void setProvisionals(InitParams.Provisional provisionals)
	{
		params.provisional = provisionals;
	}

	protected void setAuthentication(Authentication authentication)
	{
		this.authentication = authentication;
	}
}
