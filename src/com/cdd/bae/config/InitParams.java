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

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;
import org.slf4j.*;

import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

/*
	Container class for the parameters that get loaded from various configuration files. An instance is required
	to boot the infrastructure.
*/

public class InitParams
{
	private static final Logger logger = LoggerFactory.getLogger(InitParams.class);
	private static final String indent = "    ";
	private static final String noInformation = indent + "<none>";

	public static final class BuildData
	{
		public String date = null;
		public String branch = null;
		
		static BuildData fromJSON(JSONObject json)
		{
			BuildInformation info = new BuildInformation();
			BuildData result = new BuildData();
			result.date = json.optString("date", info.get(BuildInformation.BUILDTIME)).replace('/', '-');
			result.branch = json.optString("branch", null); 

			if (result.branch == null) 
				result.branch = info.get(BuildInformation.GITBRANCH);
			return result;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			BuildData other = (BuildData)o;
			return saveEquals(date, other.date) && saveEquals(branch, other.branch);
		}
		
		@Override
		public int hashCode()
		{
			return Objects.hash(date, branch);
		}
		
		public void addDescription(List<String> lines)
		{
			lines.add("BuildData:");
			lines.add(indent + "date: " + date);
			lines.add(indent + "branch: " + (branch == null ? "unknown" : branch));
		}
	}

	public static final class Database
	{
		public String host = null;
		public int port = 0;
		public String name = null;
		public String user = null;
		public String password = null;
		
		static Database fromJSON(JSONObject json)
		{
			return fromJSON(json, System.getenv());
		}

		static Database fromJSON(JSONObject json, Map<String, String> env)
		{
			if (json == null) return null;
			
			Database database = new Database();

			// order: default value; overridden by JSON config file; overridden by environment variables
			database.host = env.getOrDefault("MONGO_HOST", json.optString("host", "127.0.0.1"));
			database.port = json.optInt("port", 0);
			if (env.containsKey("MONGO_PORT")) database.port = Integer.parseInt(env.get("MONGO_PORT"));
			database.name = env.getOrDefault("MONGO_NAME", json.optString("name", "bae"));
			database.user = env.getOrDefault("MONGO_USER", json.optString("user", null));
			database.password = env.getOrDefault("MONGO_PASSWORD", json.optString("password", null));
			
			return database;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			Database other = (Database)o;
			return saveEquals(host, other.host) && port == other.port && saveEquals(name, other.name) &&
					saveEquals(user, other.user) && saveEquals(password, other.password);
		}
		
		@Override
		public int hashCode()
		{
			return Objects.hash(host, port, name, user, password);
		}
		
		public void addDescription(List<String> lines)
		{
			lines.add("Database:");
			lines.add(indent + "host: " + host);
			lines.add(indent + "port: " + port);
			lines.add(indent + "name: " + name);
			lines.add(indent + "user: " + user);
		}
	}

	public static final class ModulePubChem
	{
		public boolean assays;
		public boolean compounds;
		public String directory;
		public boolean userRequests;

		static ModulePubChem fromJSON(JSONObject json, String baseDir)
		{
			ModulePubChem module = new ModulePubChem();
			module.assays = json.getBoolean("assays");
			module.compounds = json.getBoolean("compounds");
			File cacheDirectory = new File(combineFilename(json.getString("directory"), baseDir));
			File altCacheDirectory = new File(cacheDirectory.getParentFile().getParentFile(), json.getString("directory"));
			if (altCacheDirectory.exists())
				module.directory = altCacheDirectory.getAbsolutePath();
			else
				module.directory = cacheDirectory.getAbsolutePath();
			module.userRequests = json.optBoolean("userRequests", false);
			return module;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;

			ModulePubChem other = (ModulePubChem)o;
			return assays == other.assays && compounds == other.compounds && 
				saveEquals(directory, other.directory) && userRequests == other.userRequests;
		}
		
		@Override
		public int hashCode()
		{
			return Objects.hash(assays, compounds, directory, userRequests);
		}
	}

	public static final class ModuleVault
	{
		public long[] vaultIDList;
		public String apiKey;
		public Map<String, String> propertyMap;
		public Map<String, String> unitsMap;
		public Map<String, String> operatorMap;

		static ModuleVault fromJSON(JSONObject json)
		{
			ModuleVault module = new ModuleVault();
			module.vaultIDList = json.getJSONArray("vaultIDList").toLongArray();
			module.apiKey = json.getString("apiKey");
			module.propertyMap = jsonToMap(json.optJSONObject("propertyMap"));
			module.unitsMap = jsonToMap(json.optJSONObject("unitsMap"));
			module.operatorMap = jsonToMap(json.optJSONObject("operatorMap"));
			return module;
		}

		private static Map<String, String> jsonToMap(JSONObject obj)
		{
			Map<String, String> map = new HashMap<>();
			if (obj == null) return map;
			for (Iterator<String> it = obj.keys(); it.hasNext();)
			{
				String key = it.next();
				map.put(key, obj.getString(key));
			}
			return map;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;

			ModuleVault other = (ModuleVault)o;
			return Arrays.equals(vaultIDList, other.vaultIDList) && saveEquals(apiKey, other.apiKey) &&
				   saveEquals(propertyMap, other.propertyMap) && saveEquals(unitsMap, other.unitsMap) &&
				   saveEquals(operatorMap, other.operatorMap);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(Arrays.hashCode(vaultIDList), apiKey, propertyMap, unitsMap, operatorMap);
		}
	}
	
	public static final class GoogleAnalytics
	{
		public String trackingID = null;

		public static GoogleAnalytics fromJSON(JSONObject json)
		{
			GoogleAnalytics googleAnalytics = new GoogleAnalytics();
			JSONObject obj = json.optJSONObject("googleAnalytics");
			if (obj != null) googleAnalytics.trackingID = obj.optString("trackingID", null);
			return googleAnalytics;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;

			GoogleAnalytics other = (GoogleAnalytics)o;
			return saveEquals(trackingID, other.trackingID);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(trackingID);
		}

		public boolean show()
		{
			return trackingID != null;
		}
	}

	public static final class PageToggle
	{
		public boolean progressReport = true;
		public boolean schemaReport = true;
		public boolean schemaTemplates = true;
		public boolean validationReport = true;
		public boolean contentSummary = true;
		public boolean randomAssay = true;

		static PageToggle fromJSON(JSONObject json)
		{
			PageToggle pageToggle = new PageToggle();
			if (json != null)
			{
				pageToggle.progressReport = json.optBoolean("progressReport", pageToggle.progressReport);
				pageToggle.schemaReport = json.optBoolean("schemaReport", pageToggle.schemaReport);
				pageToggle.schemaTemplates = json.optBoolean("schemaTemplates", pageToggle.schemaTemplates);
				pageToggle.validationReport = json.optBoolean("validationReport", pageToggle.validationReport);
				pageToggle.contentSummary = json.optBoolean("contentSummary", pageToggle.contentSummary);
				pageToggle.randomAssay = json.optBoolean("randomAssay", pageToggle.randomAssay);
			}
			return pageToggle;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;

			PageToggle other = (PageToggle)o;
			return hashCode() == other.hashCode();
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(progressReport, schemaReport, schemaTemplates, validationReport, contentSummary, randomAssay);
		}
	}

	public static final class URIPatternMap
	{
		public String matchPrefix;
		public String externalURL;
		public String label;
		
		public static URIPatternMap[] fromJSON(JSONArray jsonParent)
		{
			List<URIPatternMap> result = new ArrayList<>();
			
			if (jsonParent != null) for (int n = 0; n < jsonParent.length(); n++)
			{
				JSONObject obj = jsonParent.getJSONObject(n);
				URIPatternMap patternMap = new URIPatternMap();
				patternMap.matchPrefix = obj.getString("matchPrefix");
				patternMap.externalURL = obj.getString("externalURL");
				patternMap.label = obj.getString("label");
				result.add(patternMap);
			}
			
			return result.toArray(new URIPatternMap[0]);
		}		
		
		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;

			URIPatternMap other = (URIPatternMap)o;
			return Util.equals(matchPrefix, other.matchPrefix) && Util.equals(externalURL, other.externalURL) && Util.equals(label, other.label);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(matchPrefix, externalURL, label);
		}
	}		
	
	public static final class UIMessage
	{
		public String message = "";
		public String style = "info";
		public boolean show = false;
		
		static UIMessage fromJSON(JSONObject json)
		{
			UIMessage uiMessage = new UIMessage();
			if (json == null) return uiMessage;
			uiMessage.message = json.optString("message", uiMessage.message);
			uiMessage.style = json.optString("style", uiMessage.style);
			uiMessage.show = json.optBoolean("show", uiMessage.show);
			return uiMessage;
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;

			UIMessage other = (UIMessage)o;
			return message.equals(other.message) && style.equals(other.style) && show == other.show;
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(message, style, show);
		}
	}

	public static final class Provisional
	{
		public String baseURI = null;
		public String abbreviation = "user:";
		public String directory = null;
		
		public static Provisional fromJSON(JSONObject json, String baseDir)
		{
			Provisional provisional = new Provisional();
			provisional.baseURI = json.optString("baseURI", null);
			if (json.has("directory")) provisional.directory = getPath(json, "directory", baseDir);
			provisional.abbreviation = json.optString("abbreviation", "user:");
			if (provisional.baseURI != null)
				 ModelSchema.addPrefix(provisional.abbreviation, getURIPrefix(provisional.baseURI));
			return provisional;
		}

		public int hashCode()
		{
			return Objects.hash(baseURI, directory, abbreviation);
		}

		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			Provisional other = (Provisional)o;
			return saveEquals(baseURI, other.baseURI) && saveEquals(directory, other.directory) && saveEquals(abbreviation, other.abbreviation);
		}

		public void addDescription(List<String> lines)
		{
			lines.add("Provisionals:");
			if (!StringUtils.isEmpty(baseURI))
			{
				lines.add(indent + baseURI);
				lines.add(indent + "abbreviation: " + abbreviation);
			}
			else
				lines.add(noInformation);
			if (directory != null)
				lines.add("Extra provisionals directory: " + directory);
		}
	}
	
	public static final class CustomPrefix
	{
		public String baseURI;
		public String abbreviation;
		
		CustomPrefix(String baseURI, String abbreviation)
		{
			this.baseURI = baseURI;
			this.abbreviation = abbreviation;
			ModelSchema.addPrefix(abbreviation, baseURI);
		}

		public int hashCode()
		{
			return Objects.hash(baseURI, abbreviation);
		}

		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			CustomPrefix other = (CustomPrefix)o;
			return saveEquals(baseURI, other.baseURI) && saveEquals(abbreviation, other.abbreviation);
		}

		public static CustomPrefix[] fromJSON(JSONArray json)
		{
			List<CustomPrefix> result = new ArrayList<>();
			for (int n = 0; n < json.length(); n++)
			{
				JSONObject obj = json.getJSONObject(n);
				result.add(new CustomPrefix(obj.getString("baseURI"), obj.getString("abbreviation")));
			}
			
			return result.toArray(new CustomPrefix[0]);
		}

		public static void addDescription(CustomPrefix[] customPrefixes, List<String> lines)
		{
			lines.add("Custom prefixes: " + Util.length(customPrefixes));
			if (customPrefixes == null) return;
			for (CustomPrefix c : customPrefixes) lines.add(indent + c.abbreviation + "  " + c.baseURI);
		}
	}

	public static final class OntoloBridge
	{
		public String name = null;
		public String description = null;
		public String baseURL = null;
		public String authToken = null;

		static OntoloBridge fromJSON(JSONObject json)
		{
			OntoloBridge onto = new OntoloBridge();
			onto.name = json.optString("name", "");
			onto.description = json.optString("description", "");
			onto.baseURL = json.optString("baseURL", "");
			onto.authToken = json.optString("authToken", "");
			return onto;
		}
		
		static OntoloBridge[] fromJSON(JSONArray json)
		{
			OntoloBridge[] bridges = null;
			for (JSONObject bobj : json.toObjectArray())
				bridges = ArrayUtils.add(bridges, OntoloBridge.fromJSON(bobj));
			return bridges;
		}
		
		@Override
		public int hashCode()
		{
			return ("!" + name + description + baseURL + authToken).hashCode();
		}

		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;

			OntoloBridge other = (OntoloBridge)o;
			return saveEquals(name, other.name) && saveEquals(description, other.description) && 
				   saveEquals(baseURL, other.baseURL) && saveEquals(authToken, other.authToken);
		}

		public static void addDescription(OntoloBridge[] bridges, List<String> lines)
		{
			lines.add("OntoloBridges: " + Util.length(bridges));
			if (bridges == null) return;
			for (OntoloBridge b : bridges) lines.add("    [" + b.name + "] <" + b.baseURL + ">");
		}
	}
	
	public static final class DirectoryFilesParameter
	{
		String directory = null;
		String[] files = null;
		String[] updates = null;
		
		DirectoryFilesParameter()
		{
		}
		
		static DirectoryFilesParameter fromJSON(JSONObject json, String baseDir)
		{
			DirectoryFilesParameter obj = new DirectoryFilesParameter();
			if (json == null) return obj;
			
			obj.directory = getPath(json, "directory", baseDir);
			obj.files = json.optJSONArrayEmpty("files").toStringArray();
			obj.updates = json.optJSONArrayEmpty("updateURLs").toStringArray();
			return obj;
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;

			DirectoryFilesParameter other = (DirectoryFilesParameter)o;
			return saveEquals(directory, other.directory) && 
					saveEqualsArray(files, other.files) &&
					saveEqualsArray(updates, other.updates);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(directory, Arrays.hashCode(files), Arrays.hashCode(updates));
		}
		
		public void addDescription(List<String> lines, String type)
		{
			lines.add(type + " Directory: " + directory);
			lines.add(type + " Files:");
			if (Util.length(files) == 0)
				lines.add(noInformation);
			else
				for (String fn : files) lines.add(indent + fn);
		}
	}

	private String schemaDefinition = "/com/cdd/bae/config/ConfigurationSchema.json";

	protected FileLoaderJSONObject loader;
	protected File file;
	protected String baseDir;

	public String baseURL = null;
	public boolean production = false;
	public boolean verboseDebug = false;
	public BuildData buildData = null;
	public Database database = null;
	public ModulePubChem modulePubChem = null;
	public ModuleVault moduleVault = null;
	public String authenticationFile = null;
	public String identifierFile = null;
	public String schemaVocabFile = null;
	public String schemaVocabUpdate = null;
	public DirectoryFilesParameter template = new DirectoryFilesParameter();
	public DirectoryFilesParameter translit = new DirectoryFilesParameter();
	public DirectoryFilesParameter forms = new DirectoryFilesParameter();
	public String axiomsDir = null;
	public String[] absenceTerms = null;
	public String nlpDir = null;
	public String customWebDir = null;
	public GoogleAnalytics googleAnalytics = new GoogleAnalytics();
	public PageToggle pageToggle = new PageToggle();
	public URIPatternMap[] uriPatternMaps = null;
	public UIMessage uiMessage = new UIMessage();
	public Provisional provisional = new Provisional();
	public OntoloBridge[] bridges = null;
	public CustomPrefix[] prefixes = null;
	
	protected InitParams()
	{
	}

	// initialize configuration from filename; baseDir is set to the directory of filename
	public InitParams(String filename) throws JSONSchemaValidatorException
	{
		this(filename, null);
	}

	public InitParams(String filename, String baseDir) throws JSONSchemaValidatorException
	{
		super();
		this.file = new File(filename);
		this.loader = new FileLoaderJSONObject(file, schemaDefinition);
		this.baseDir = baseDir == null ? file.getParent() : baseDir;
		
		// parse the validated JSON object returned from loader
		this.parseJSON(loader.load());
	}

	// try to reload the configuration. Errors will be suppressed here.
	public void reload()
	{
		try
		{
			parseJSON(loader.load());
		}
		catch (JSONSchemaValidatorException e)
		{
			logger.error("Couldn't reload configuration from configuration file");
		}
	}

	public boolean hasChanged()
	{
		return loader.hasChanged();
	}

	public void setFile(File newFile)
	{
		this.setFile(newFile, null);
	}

	public void setFile(File newFile, String newBaseDir)
	{
		loader.setFile(newFile);
		this.file = newFile;
		this.baseDir = newBaseDir == null ? newFile.getParent() : newBaseDir;
	}

	@Override
	public boolean equals(Object o)
	{
		if (o == null || getClass() != o.getClass()) return false;

		InitParams other = (InitParams)o;

		// compare the rest
		return pageToggle.equals(other.pageToggle) && saveEquals(authenticationFile, other.authenticationFile) &&
			   saveEquals(schemaVocabFile, other.schemaVocabFile) && saveEquals(schemaVocabUpdate, other.schemaVocabUpdate) &&
			   saveEquals(axiomsDir, other.axiomsDir) && saveEqualsArray(absenceTerms, other.absenceTerms) &&
			   saveEquals(nlpDir, other.nlpDir) && saveEquals(identifierFile, other.identifierFile) && 
			   saveEquals(customWebDir, other.customWebDir) && saveEquals(moduleVault, other.moduleVault) && 
			   saveEquals(modulePubChem, other.modulePubChem) && saveEquals(database, other.database) &&
			   saveEquals(template, other.template) && saveEquals(translit, other.translit) && 
			   saveEquals(forms, other.forms) && saveEquals(buildData, other.buildData) &&
			   saveEquals(baseURL, other.baseURL) && saveEquals(production, other.production) &&
			   saveEquals(uiMessage, other.uiMessage) &&
			   saveEquals(provisional, other.provisional) &&
			   saveEqualsArray(bridges, other.bridges) &&
			   saveEquals(googleAnalytics, other.googleAnalytics) &&
			   verboseDebug == other.verboseDebug &&
			   saveEqualsArray(prefixes, other.prefixes) &&
			   saveEqualsArray(uriPatternMaps, other.uriPatternMaps);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(authenticationFile, schemaVocabFile, schemaVocabUpdate, database,
							axiomsDir, Arrays.hashCode(absenceTerms), nlpDir, identifierFile, customWebDir,
							template, translit, forms, moduleVault, modulePubChem, buildData,
							pageToggle, baseURL, production, uiMessage, provisional,
							Arrays.hashCode(bridges), googleAnalytics, verboseDebug, 
							Arrays.hashCode(prefixes), Arrays.hashCode(uriPatternMaps));
	}

	// human-readable encapsulation of the content
	@Override
	public String toString()
	{
		List<String> lines = new ArrayList<>();

		lines.add(production ? "Production" : "Debug");
		buildData.addDescription(lines);
		lines.add("baseURL:");
		if (baseURL != null)
		{
			lines.add("    URL: " + baseURL);
		}
		else lines.add(noInformation);

		if (database != null) 
			database.addDescription(lines);
		else
		{
			lines.add("Database:");
			lines.add(noInformation);
		}

		lines.add("Module/PubChem:");
		if (modulePubChem != null)
		{
			lines.add("    assays: " + modulePubChem.assays);
			lines.add("    compounds: " + modulePubChem.compounds);
			lines.add("    directory: " + modulePubChem.directory);
		}
		else lines.add(noInformation);

		lines.add("Module/Vault:");
		if (moduleVault != null)
		{
			lines.add("    vault IDs: " + Util.arrayStr(moduleVault.vaultIDList));
			lines.add("    apiKey: <length=" + moduleVault.apiKey.length() + ">");
		}
		else
			lines.add(noInformation);

		lines.add("Authentication File: " + authenticationFile);
		lines.add("Identifier File: " + identifierFile);
		lines.add("Schema Vocab File: " + schemaVocabFile);

		template.addDescription(lines, "Template");
		translit.addDescription(lines, "Transliteration");
		forms.addDescription(lines, "Forms");
		
		lines.add("URI Pattern Maps: " + Util.length(uriPatternMaps));

		lines.add("NLP Directory: " + nlpDir);
		lines.add("Custom Web Directory: " + customWebDir);

		if (uiMessage.show) lines.add("UI message [" + uiMessage.style + "]: " + uiMessage.message);
		if (googleAnalytics.show()) lines.add("Google Analytics: " + googleAnalytics.trackingID);

		provisional.addDescription(lines);
		CustomPrefix.addDescription(prefixes, lines);
		OntoloBridge.addDescription(bridges, lines);

		return String.join("\n", lines);
	}
	
	// ------------ private methods ------------

	// parse a validated JSON object
	protected void parseJSON(JSONObject json) throws JSONSchemaValidatorException
	{
		// we know that the configuration is valid, so we can load it without further checking
		buildData = BuildData.fromJSON(json.optJSONObjectEmpty("buildinfo"));

		// set flag for production mode (default is false)
		production = json.optBoolean("production", false);
		verboseDebug = json.optBoolean("verboseDebug", false);

		// If baseURL is not set, possiblity of Redirect/Phishing via HTTP Host Injection (CDD-01-003 Web)
		baseURL = json.optString("baseURL", null);
		
		database = Database.fromJSON(json.optJSONObject("database"));

		JSONObject obj = json.optJSONObject("modules");
		if (obj != null)
		{
			modulePubChem = obj.has("pubchem") ? ModulePubChem.fromJSON(obj.getJSONObject("pubchem"), baseDir) : null;
			moduleVault = obj.has("vault") ? ModuleVault.fromJSON(obj.getJSONObject("vault")) : null;
		}

		authenticationFile = getPath(json, "authentication/filename", baseDir);
		identifierFile = getPath(json, "identifier/filename", baseDir);
		schemaVocabFile = getPath(json, "schema/filename", baseDir);
		schemaVocabUpdate = getOptPath(json, "schema/updateURL", baseDir);
		nlpDir = getPath(json, "nlp/directory", baseDir);
		customWebDir = getPath(json, "customWeb/directory", baseDir);

		pageToggle = PageToggle.fromJSON(json.optJSONObject("pages"));
		uriPatternMaps = URIPatternMap.fromJSON(json.optJSONArray("uriPatternMaps"));

		template = DirectoryFilesParameter.fromJSON(json.optJSONObject("templates"), baseDir);
		translit = DirectoryFilesParameter.fromJSON(json.optJSONObjectEmpty("transliterate"), baseDir);
		forms = DirectoryFilesParameter.fromJSON(json.optJSONObjectEmpty("forms"), baseDir);
		
		obj = json.optJSONObject("axioms");
		if (obj != null) axiomsDir = getPath(obj, "directory", baseDir);
		
		absenceTerms = json.optJSONArrayEmpty("absence").toStringArray();
		absenceTerms = ModelSchema.expandPrefixes(absenceTerms);
		
		uiMessage = UIMessage.fromJSON(json.optJSONObject("message"));
		googleAnalytics = GoogleAnalytics.fromJSON(json);

		provisional = Provisional.fromJSON(json.optJSONObjectEmpty("provisional"), baseDir);
		prefixes = CustomPrefix.fromJSON(json.optJSONArrayEmpty("prefixes"));
		
		bridges = OntoloBridge.fromJSON(json.optJSONArrayEmpty("ontolobridge"));

		// now that the data are all read in, we can check that the files and directories all exist
		List<String> errors = validateFilenames();
		if (!errors.isEmpty()) throw new JSONSchemaValidatorException("Configuration invalid: " + file, errors);
	}

	// check that all required files and directories exist
	protected List<String> validateFilenames()
	{
		List<String> errors = new ArrayList<>();
		errors.add(checkIsFile(authenticationFile, "/authentication/filename"));
		errors.add(checkIsFile(identifierFile, "/identifier/filename"));
		errors.add(checkIsFile(schemaVocabFile, "/schema/filename"));
		errors.add(checkIsDirectory(nlpDir, "/nlp/directory"));
		errors.add(checkIsDirectory(customWebDir, "/customWeb/directory"));
		errors.add(checkIsDirectory(template.directory, "/templates/directory"));

		// next we check optional entries
		if (template.files != null)
		{
			for (String name : template.files) errors.add(checkIsFile(template.directory + "/" + name, "/templates/files[]"));
		}
		if (modulePubChem != null) errors.add(checkIsDirectory(modulePubChem.directory, "/modules/pubchem/directory"));

		// remove all the empty entries
		Iterator<String> iter = errors.iterator();
		while (iter.hasNext())
		{
			String s = iter.next();
			if (s == null) iter.remove();
		}
		return errors;
	}

	// convenience: check that files or directories exist
	private String checkIsFile(String name, String level)
	{
		if (name == null) return null;
		File f = new File(name);
		if (f.exists() && !f.isDirectory()) return null;
		return level + ": file " + name + " does not exist";
	}

	private String checkIsDirectory(String name, String level)
	{
		if (name == null) return null;
		File f = new File(name);
		if (f.exists() && f.isDirectory()) return null;
		return level + ": directory " + name + " does not exist";
	}

	// convenience: prepends relative directory if appropriate
	private static String combineFilename(String filename, String baseDir)
	{
		if (filename.startsWith("file://") || filename.startsWith("ftp://") || 
			filename.startsWith("http://") || filename.startsWith("https://")) return filename;
		return filename.startsWith("/") ? filename : baseDir + "/" + filename;
	}

	// convenience: returns filename from json based on path
	private static String getPath(JSONObject json, String path, String baseDir)
	{
		JSONObject obj = json;
		String[] sections = path.split("/");
		for (int i = 0; i < sections.length - 1; i++) 
		{
			obj = obj.optJSONObject(sections[i]);
			if (obj == null) return null;
		}
		String fn = obj.optString(sections[sections.length - 1]);		
		if (fn == null) return null;
		return combineFilename(fn, baseDir);
	}

	// same as getPath, but returns null if not found
	private static String getOptPath(JSONObject json, String path, String baseDir)
	{
		try
		{
			return getPath(json, path, baseDir);
		}
		catch (JSONException ex)
		{
			return null;
		}
	}

	protected static boolean saveEquals(Object o1, Object o2)
	{
		if (o1 == null) return o1 == o2;
		return o1.equals(o2);
	}
	protected static boolean saveEqualsArray(Object[] a1, Object[] a2)
	{
		if (a1 == null) return a1 == a2;
		return Arrays.equals(a1, a2);		
	}
	
	// extract and return URI prefix
	// for URI like http://www.bioassayexpress.org/user#PROV_000123, return http://www.bioassayexpress.org/user#
	protected static String getURIPrefix(String uri)
	{
		int lastIdx = uri.lastIndexOf('#');
		return lastIdx < 0 ? uri : uri.substring(0, lastIdx + 1);
	}
}
