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
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import java.util.Map.*;

import org.json.*;
import org.junit.jupiter.api.*;

public class InitParamsTest extends TestBaseClass
{
	private static Map<String, String> prefixes;
	
	@BeforeAll
	public static void prepare()
	{
		prefixes = ModelSchema.getPrefixes();
	}
	
	@AfterEach
	public void restore()
	{
		if (!ModelSchema.getPrefixes().equals(prefixes))
		{
			for (Entry<String, String> prefix : prefixes.entrySet()) 
				ModelSchema.addPrefix(prefix.getKey(), prefix.getValue());
			Set<String> newPrefixes = ModelSchema.getPrefixes().keySet();
			newPrefixes.removeAll(prefixes.keySet());
			for (String key : newPrefixes) ModelSchema.removePrefix(key);
		}
	}

	@Test
	public void testInitParams() throws IOException
	{
		// this will fail due to missing files
		TestResourceFile res = new TestResourceFile("/testData/config/validConfig.json");
		final File file = res.getAsFile(folder, "config.json");
		JSONSchemaValidatorException e = assertThrows(JSONSchemaValidatorException.class,
				() -> new InitParams(file.toString()));
		assertThat(e.getDetails(), hasSize(7));

		// this will fail due to schema violations
		res = new TestResourceFile("/testData/config/invalidConfig.json");
		final File file2 = res.getAsFile(folder, "config.json");
		e = assertThrows(JSONSchemaValidatorException.class, () -> new InitParams(file2.toString()));
		assertThat(e.getDetails(), hasSize(6));

		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			InitParams result = new InitParams();
			result.buildData = new BuildData();
			result.pageToggle = new PageToggle();
			result.baseURL = "baseURL";
			result.database = new Database();
			result.modulePubChem = new ModulePubChem();
			result.moduleVault = new ModuleVault();
			result.authenticationFile = "authenticationFile";
			result.identifierFile = "identifierFile";
			result.schemaVocabFile = "schemaVocabFile";
			result.schemaVocabUpdate = "schemaVocabUpdate";
			result.template = new DirectoryFilesParameter();
			result.template.directory = "directory";
			result.translit = new DirectoryFilesParameter();
			result.translit.directory = "directory";
			result.forms = new DirectoryFilesParameter();
			result.forms.directory = "directory";
			result.axiomsDir = "axiomsDir";
			result.nlpDir = "nlpDir";
			result.customWebDir = "customWebDir";
			result.bridges = new OntoloBridge[]{};
			result.absenceTerms = new String[]{};
			result.prefixes = new CustomPrefix[]{};
			result.uriPatternMaps = new URIPatternMap[0];
			return result;
		});
	}
	
	@Test
	public void testURIPatternMap()
	{
		assertThat(URIPatternMap.fromJSON(null).length, is(0));
		
		Map<String, String> map = new HashMap<>();
		map.put("matchPrefix", "prefix");
		map.put("externalURL", "external");
		map.put("label", "label");
		JSONArray json = new JSONArray().put(new JSONObject(map));
		URIPatternMap[] patterns = URIPatternMap.fromJSON(json);
		assertThat(patterns.length, is(1));
		
		json.put(new JSONObject(map));
		patterns = URIPatternMap.fromJSON(json);
		assertThat(patterns.length, is(2));

		TestUtilities.assertEquality(() ->
		{
			URIPatternMap result = new URIPatternMap();
			result.matchPrefix = "matchPrefix";
			result.externalURL = "externalURL";
			result.label = "label";
			return result;
		});
	}

	@Test
	public void testDatabase()
	{
		// check default values
		Map<String, String> map = new HashMap<>();
		Map<String, String> env = new HashMap<>();

		Database d = Database.fromJSON(new JSONObject(map), env);
		assertThat(d.host, is("127.0.0.1"));
		assertThat(d.port, is(0));
		assertThat(d.name, is("bae"));
		assertThat(d.user, nullValue());
		assertThat(d.password, nullValue());

		// reading from env takes precedence
		map.put("host", "json_host");
		map.put("port", "5678");
		map.put("name", "json_name");
		map.put("user", "json_user");
		map.put("password", "json_pw");
		d = Database.fromJSON(new JSONObject(map), env);
		assertThat(d.host, is("json_host"));
		assertThat(d.port, is(5678));
		assertThat(d.name, is("json_name"));
		assertThat(d.user, is("json_user"));
		assertThat(d.password, is("json_pw"));

		// check that it takes information from environment
		env.put("MONGO_HOST", "mongo_host");
		env.put("MONGO_PORT", "1234");
		env.put("MONGO_NAME", "mongo_name");
		env.put("MONGO_USER", "mongo_user");
		env.put("MONGO_PASSWORD", "mongo_pw");
		d = Database.fromJSON(new JSONObject(map), env);
		assertThat(d.host, is("mongo_host"));
		assertThat(d.port, is(1234));
		assertThat(d.name, is("mongo_name"));
		assertThat(d.user, is("mongo_user"));
		assertThat(d.password, is("mongo_pw"));

		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			Database result = new Database();
			result.host = "host";
			result.port = 1234;
			result.name = "name";
			result.user = "user";
			result.password = "password";
			return result;
		});
		
		// check that description doesn't include password
		List<String> lines = new ArrayList<>();
		d.addDescription(lines);
		assertThat(lines.toString(), not(containsString("json_pw")));
	}


	@Test
	public void testBuildData()
	{
		BuildInformation info = new BuildInformation();
		// check default values
		Map<String, String> map = new HashMap<>();

		BuildData d = BuildData.fromJSON(new JSONObject(map));
		assertThat(d.date, is(info.get("buildtime").replace('/', '-')));
		assertThat(d.branch, is(info.get("gitbranch")));

		map.put("date", "2019-12-24");
		map.put("branch", "customer");
		d = BuildData.fromJSON(new JSONObject(map));
		assertThat(d.date, is("2019-12-24"));
		assertThat(d.branch, is("customer"));
		
		// check that description doesn't include password
		List<String> lines = new ArrayList<>();
		d.addDescription(lines);
		assertThat(lines.toString(), containsString("customer"));
		assertThat(lines.toString(), containsString("2019-12-24"));

		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			BuildData result = new BuildData();
			result.date = "2019-12-24";
			result.branch = "branch";
			return result;
		});
	}

	@Test
	public void testModulePubChem()
	{
		File baseDir = createFolder("cfg");
		(new File(baseDir, "assays")).mkdir();

		JSONObject json = new JSONObject("{'assays': true, 'compounds': false, 'directory': 'assays'}");

		ModulePubChem pubchem = ModulePubChem.fromJSON(json, baseDir.toString());
		// baseDir is the cfg directory
		// case 1: no assays directory next to cfg directory expect pubchem.directory as
		// subdirectory of cfg
		assertThat((new File(baseDir, "assays")).getAbsolutePath(), is(pubchem.directory));

		// case 2: assays directory next to cfg directory expect pubchem.directory point
		// to that assays directory
		File assaysDir = createFolder("assays");
		pubchem = ModulePubChem.fromJSON(json, baseDir.toString());
		assertThat(assaysDir.getAbsolutePath(), is(pubchem.directory));

		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			ModulePubChem result = new ModulePubChem();
			result.assays = true;
			result.compounds = true;
			result.directory = "directory";
			return result;
		});
	}

	@Test
	public void testModuleVault()
	{
		JSONObject json = getVaultConfiguration("[1, 2]");
		ModuleVault vault = ModuleVault.fromJSON(json);
		assertThat("ABCDE", is(vault.apiKey));
		assertThat(vault.unitsMap.keySet(), empty());
		assertThat(vault.propertyMap.keySet(), hasSize(1));

		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			ModuleVault result = new ModuleVault();
			result.vaultIDList = new long[]{1L, 2L};
			result.apiKey = "apiKey";
			result.propertyMap = new HashMap<>();
			result.propertyMap.put("key", "value");
			result.unitsMap = new HashMap<>();
			result.unitsMap.put("key", "value");
			result.operatorMap = new HashMap<>();
			result.operatorMap.put("key", "value");
			return result;
		});
	}

	@Test
	public void testGoogleAnalytics()
	{
		GoogleAnalytics ga = GoogleAnalytics.fromJSON(new JSONObject("{}"));
		assertThat(ga.show(), is(false));
		assertThat(ga.trackingID, is(nullValue()));
		
		JSONObject json = new JSONObject("{'googleAnalytics': {'trackingID': 'ABCDE'}}");
		ga = GoogleAnalytics.fromJSON(json);
		assertThat(ga.show(), is(true));
		assertThat(ga.trackingID, is("ABCDE"));

		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			GoogleAnalytics result = new GoogleAnalytics();
			result.trackingID = "trackingID";
			return result;
		});
	}

	@Test
	public void testPageToggle()
	{
		PageToggle toggle = PageToggle.fromJSON(null);
		assertThat(toggle.progressReport, is(true));

		Map<String, String> map = new HashMap<>();
		toggle = PageToggle.fromJSON(new JSONObject(map));
		assertThat(toggle.progressReport, is(true));
		assertThat(toggle, is(PageToggle.fromJSON(null)));

		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			return new PageToggle();
		});
	}

	@Test
	public void testUIMessage()
	{
		UIMessage message = UIMessage.fromJSON(null);
		assertThat(message.message, is(""));
		assertThat(message.show, is(false));

		Map<String, String> map = new HashMap<>();
		assertEquality(message, UIMessage.fromJSON(new JSONObject(map)));
		message = UIMessage.fromJSON(new JSONObject(map));
		assertThat(message.message, is(""));
		assertThat(message.show, is(false));

		map.put("show", "true");
		map.put("message", "message");
		assertInequality(message, UIMessage.fromJSON(new JSONObject(map)));
		message = UIMessage.fromJSON(new JSONObject(map));
		assertEquality(message, UIMessage.fromJSON(new JSONObject(map)));
		assertThat(message.message, is("message"));
		assertThat(message.show, is(true));

		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			UIMessage result = new UIMessage();
			result.message = "message";
			return result;
		});
	}

	@Test
	public void testProvisional()
	{
		String baseDir = "baseDir";
		Provisional oNull = new Provisional();
		Map<String, String> map = new HashMap<>();
		Provisional o1 = Provisional.fromJSON(new JSONObject(map), baseDir);
		assertThat(o1.baseURI, nullValue());
		assertThat(o1.abbreviation, is("user:"));
		assertThat(o1.directory, nullValue());
		assertThat(o1, is(not("abc")));
		assertEquality(o1, oNull);

		Provisional o2 = Provisional.fromJSON(new JSONObject(map), baseDir);
		assertThat(o1, is(o2));

		map.put("baseURI", "a");
		o1 = Provisional.fromJSON(new JSONObject(map), baseDir);
		assertThat(o1.baseURI, is("a"));

		map.put("directory", "b");
		o1 = Provisional.fromJSON(new JSONObject(map), baseDir);
		assertThat(o1.directory, is("baseDir/b"));

		JSONObject json = new JSONObject(map);
		json.put("abbreviation", "abc:");
		o1 = Provisional.fromJSON(json, baseDir);
		assertThat(o1.abbreviation, is("abc:"));

		List<String> lines = new ArrayList<>();
		o1.addDescription(lines);
		assertThat(lines, hasItem("Provisionals:"));
		assertThat(lines, hasItem("    a"));
		assertThat(lines, hasItem("Extra provisionals directory: baseDir/b"));

		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			Provisional result = new Provisional();
			result.baseURI = "baseURI";
			result.abbreviation = "abc:";
			result.directory = "directory";
			return result;
		});
	}

	@Test
	public void testOntoloBridge()
	{
		OntoloBridge oNull = new OntoloBridge();
		Map<String, String> map = new HashMap<>();
		OntoloBridge o1 = OntoloBridge.fromJSON(new JSONObject(map));
		assertThat(o1.name, is(""));
		assertThat(o1.description, is(""));
		assertThat(o1.baseURL, is(""));
		assertThat(o1.authToken, is(""));
		assertThat(o1, is(not("abc")));
		assertInequality(o1, oNull);

		OntoloBridge o2 = OntoloBridge.fromJSON(new JSONObject(map));
		assertEquality(o1, o2);

		map.put("name", "a");
		map.put("description", "b");
		map.put("baseURL", "c");
		map.put("authToken", "d");
		o1 = OntoloBridge.fromJSON(new JSONObject(map));
		assertThat(o1.name, is("a"));
		assertThat(o1.description, is("b"));
		assertThat(o1.baseURL, is("c"));
		assertThat(o1.authToken, is("d"));

		JSONObject json = new JSONObject();
		OntoloBridge[] bridges = OntoloBridge.fromJSON(json.optJSONArrayEmpty("ontolobridge"));
		assertThat(bridges, nullValue());

		json.put("ontolobridge", new JSONArray());
		bridges = OntoloBridge.fromJSON(json.optJSONArrayEmpty("ontolobridge"));
		assertThat(bridges, nullValue());

		json.getJSONArray("ontolobridge").put(new JSONObject(map));
		bridges = OntoloBridge.fromJSON(json.optJSONArrayEmpty("ontolobridge"));
		assertThat(bridges.length, is(1));
		assertThat(bridges[0].name, is("a"));

		map.put("name", "another");
		json.getJSONArray("ontolobridge").put(new JSONObject(map));
		bridges = OntoloBridge.fromJSON(json.optJSONArrayEmpty("ontolobridge"));
		assertThat(bridges.length, is(2));
		assertThat(bridges[0].name, is("a"));
		assertThat(bridges[1].name, is("another"));
		
		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			OntoloBridge result = new OntoloBridge();
			result.name = "baseURI";
			result.description = "description";
			result.baseURL = "baseURL";
			result.authToken = "authToken";
			return result;
		});
	}

	@Test
	public void testDirectoryParameter()
	{
		String baseDir = "abcde";
		DirectoryFilesParameter p = DirectoryFilesParameter.fromJSON(null, baseDir);
		assertThat(p.directory, nullValue());
		assertThat(p.files, nullValue());
		assertThat(p.updates, nullValue());

		Map<String, String> map = new HashMap<>();
		p = DirectoryFilesParameter.fromJSON(new JSONObject(map), baseDir);
		assertThat(p.directory, is(baseDir + "/"));
		assertArray(p.files);
		assertArray(p.updates);

		map.put("directory", "template");
		JSONObject json = new JSONObject(map);
		p = DirectoryFilesParameter.fromJSON(json, baseDir);
		assertThat(p.directory, is(baseDir + "/template"));

		List<String> lines = new ArrayList<>();
		p.addDescription(lines, "template");
		assertThat(lines, hasSize(3));

		json = new JSONObject(map);
		json.put("files", new JSONArray(new String[]{"a", "b"}));
		json.put("updateURLs", new JSONArray(new String[]{"c", "d"}));
		p = DirectoryFilesParameter.fromJSON(json, baseDir);
		assertThat(p.directory, is(baseDir + "/template"));
		assertArray(p.files, "a", "b");
		assertArray(p.updates, "c", "d");

		lines.clear();
		p.addDescription(lines, "template");
		assertThat(lines, hasSize(4));
		
		// comprehensive equality test
		TestUtilities.assertEquality(() ->
		{
			DirectoryFilesParameter result = new DirectoryFilesParameter();
			result.directory = "directory";
			result.files = new String[]{"a", "b"};
			result.updates = new String[]{"a", "b"};
			return result;
		});
	}

	@Test
	public void testCustomPrefix()
	{
		// check that equals is defined

		// parse a list of prefixes and add to ModelSchema
		assertThat(ModelSchema.getPrefixes().keySet(), not(hasItem("uri1:")));
		assertThat(ModelSchema.getPrefixes().keySet(), not(hasItem("uri2:")));
		Map<String, String> map = new HashMap<>();
		JSONArray json = new JSONArray();
		map.put("baseURI", "http://uri1.com/uri1#");
		map.put("abbreviation", "uri1:");
		json.put(new JSONObject(map));
		map.put("baseURI", "http://uri2.com/uri2#");
		map.put("abbreviation", "uri2:");
		json.put(new JSONObject(map));
		
		CustomPrefix[] customPrefixes = CustomPrefix.fromJSON(json);
		assertThat(customPrefixes.length, is(2));
		assertThat(ModelSchema.getPrefixes().keySet(), hasItem("uri1:"));
		assertThat(ModelSchema.getPrefixes().keySet(), hasItem("uri2:"));
	}
	
	@Test
	public void testSaveEquals()
	{
		assertThat(InitParams.saveEquals(null, null), is(true));
		assertThat(InitParams.saveEquals("a", null), is(false));
		assertThat(InitParams.saveEquals(null, "a"), is(false));
		assertThat(InitParams.saveEquals("b", "a"), is(false));
		assertThat(InitParams.saveEquals("b", "b"), is(true));

		String[] a1 = {"a", "b"};
		String[] a2 = {"c", "d"};
		String[] b = {"a", "b"};
		assertThat(InitParams.saveEqualsArray(null, null), is(true));
		assertThat(InitParams.saveEqualsArray(a1, null), is(false));
		assertThat(InitParams.saveEqualsArray(null, a1), is(false));
		assertThat(InitParams.saveEqualsArray(a2, a1), is(false));
		assertThat(InitParams.saveEqualsArray(a1, b), is(true));
	}

	@Test
	public void testGetURIPrefix()
	{
		String prefix = "http://www.bioassayexpress.org/user#";
		assertThat(InitParams.getURIPrefix(prefix), is(prefix));
		assertThat(InitParams.getURIPrefix(prefix + "PROV_000123"), is(prefix));
	}
	

	private void assertArray(String[] observed, String... expected)
	{
		assertThat(Arrays.asList(observed), is(Arrays.asList(expected)));
	}

	private void assertEquality(Object o1, Object o2)
	{
		assertThat(o1, is(o2));
		assertThat(o2, is(o1));
		assertThat(o1.hashCode(), is(o2.hashCode()));
	}

	private void assertInequality(Object o1, Object o2)
	{
		assertThat(o1, is(not(o2)));
		assertThat(o2, is(not(o1)));
		assertThat(o1.hashCode(), is(not(o2.hashCode())));
	}

	private JSONObject getVaultConfiguration(String vaultIDList)
	{
		Map<String, Object> map = new HashMap<>();
		map.put("vaultIDList", new JSONArray(vaultIDList));
		map.put("apiKey", "ABCDE");
		map.put("propertyMap", new JSONObject().put("field", "bao:BAX_0000015"));
		map.put("operatorMap", new JSONObject().put("=", "obo:GENEPIO_0001004"));
		return new JSONObject(map);
	}
}
