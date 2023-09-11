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

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.BoilerplateAnalysis.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.Schema.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Tests for BoilerplateAnalysis 
*/

public class BoilerplateAnalysisTest
{
	// assignments
	private String assn1 = "bao:BAO_0002854"; // bioassay type
	private String assn2 = "bao:BAO_0000205"; // assay format
	private String assn3 = "bao:BAO_0002853"; // assay title
	
	// values
	private String val1 = "bao:BAO_0000009";
	
	// branch
	private String branch1 = "bao:BAO_0000008"; // parent of val1

	// textlabels
	BoilerplateAnalysis analysis;
	Transliteration.Boilerplate boiler;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		boiler = new Transliteration.Boilerplate();
		analysis = new BoilerplateAnalysis(boiler, Common.getSchemaCAT());
	}
	
	@Test
	public void testCreateCombinations() throws DetailException
	{
		JSONObject json = new JSONObject();
		json.put("terms", new JSONArray().put(assn1).put(assn2).put(assn3));
		boiler.content = new JSONArray().put(json);
		
		assertThat(analysis.createCombinations().size(), is(2 * 2 * 2));
		assertThat(analysis.createCombinations(AssayUtil.URI_NOTAPPLICABLE).size(), is(3 * 3 * 3));
		
		Map<String, List<AnalysisCombination>> grouped = new HashMap<>();
		for (AnalysisCombination combination : analysis.createCombinations())
		{
			DataObject.Assay assay = mock(DataObject.Assay.class);
			assay.textLabels = combination.textLabels.toArray(new DataObject.TextLabel[0]);
			assay.annotations = combination.annotations.toArray(new DataObject.Annotation[0]);
			BoilerplateScript script = new BoilerplateScript(boiler, Common.getSchemaCAT(), assay);
			script.inscribe();
			
			grouped.computeIfAbsent(script.getHTML(), key -> new ArrayList<>()).add(combination);
		}
		
		List<String> generatedTexts = new ArrayList<>(grouped.keySet());
		generatedTexts.sort((s1, s2) -> s1.length() - s2.length());
		for (String text : generatedTexts)
		{
			List<AnalysisCombination> combinations = grouped.get(text);
			combinations.sort((c1, c2) -> c1.size() - c2.size());
			// for (AnalysisCombination combination : combinations) System.out.println(combination);
			// System.out.println(text);
			// System.out.println();
		}
	}
	
	@Test
	public void testProcessContent() throws DetailException
	{
		JSONArray arr = new JSONArray();
		arr.put("text");
		arr.put(" and text");
		assertThat(processContent(arr), is(Collections.emptySet()));
		
		arr.put(new JSONObject().put("term", assn1));
		assertAssignmentSet(processContent(arr), assn1);
		
		arr.put(new JSONArray());
		assertThrows(DetailException.class, () -> processContent(arr));
	}
	
	@Test
	public void testProcessObject()
	{
		assertThrows(DetailException.class, () -> processObject(new JSONObject()));
	}

	@Test
	public void testProcessTerm() throws DetailException
	{
		JSONObject json = new JSONObject();
		json.put("term", assn1);
		assertAssignmentSet(processObject(json), assn1);

		// text labels
		json.put("term", assn3);
		assertAssignmentSet(processObject(json), assn3);
	}

	@Test
	public void testProcessTerms() throws DetailException
	{
		JSONObject json = new JSONObject();
		
		json.put("terms", new JSONArray());
		assertAssignmentSet(processObject(json));
		
		json.put("terms", new JSONArray().put(assn1).put(assn2).put(assn3));
		assertAssignmentSet(processObject(json), assn1, assn2, assn3);
	}

	@Test
	public void testProcessField() throws DetailException
	{
		JSONObject json = new JSONObject();
		
		// uniqueID
		json.put("field", "uniqueID");
		assertAssignmentSet(processObject(json));
		
		// assayID
		json.put("field", "assayID");
		assertAssignmentSet(processObject(json));
		
		// incompatible field
		json.put("field", "incompatible");
		assertThrows(DetailException.class, () -> processObject(json));
	}

	@Test
	public void testProcessIfAny() throws DetailException
	{
		// only one branch
		JSONObject json = new JSONObject();
		json.put("ifany", assn1);
		json.put("then", "thenBranch");
		json.put("else", "elseBranch");
		
		assertAssignmentSet(processObject(json), assn1);
		
		json.put("then", new JSONArray().put(makeTerm(assn2)));
		assertAssignmentSet(processObject(json), assn1, assn2);
		
		json.put("else", makeTerm(assn3));
		assertAssignmentSet(processObject(json), assn1, assn2, assn3);

		// incompatible type
		assertThrows(DetailException.class, () -> processObject(new JSONObject().put("ifany", assn1).put("then", 123)));
	}
	
	@Test
	public void testProcessIfValue() throws DetailException
	{
		//--- ifvalue
		// missing branches
		JSONObject json = new JSONObject();
		json.put("ifvalue", new JSONArray().put(val1).put(assn1));
		assertAssignmentSet(processObject(json), assn1);
		
		json.put("then", "thenBranch");
		json.put("else", "elseBranch");
		assertAssignmentSet(processObject(json), assn1);

		json.put("then", new JSONArray().put(makeTerm(assn2)));
		assertAssignmentSet(processObject(json), assn1, assn2);
		
		json.put("else", makeTerm(assn3));
		assertAssignmentSet(processObject(json), assn1, assn2, assn3);
		
		//--- ifbranch
		// missing branches
		json = new JSONObject();
		json.put("ifvalue", new JSONArray().put(branch1).put(assn1));
		assertAssignmentSet(processObject(json), assn1);
		
		json.put("then", "thenBranch");
		json.put("else", "elseBranch");
		assertAssignmentSet(processObject(json), assn1);

		json.put("then", new JSONArray().put(makeTerm(assn2)));
		assertAssignmentSet(processObject(json), assn1, assn2);
		
		json.put("else", makeTerm(assn3));
		assertAssignmentSet(processObject(json), assn1, assn2, assn3);
		
		// incompatible type
		final JSONObject json1 = new JSONObject();
		json1.put("ifvalue", new JSONArray().put(branch1).put(assn1));
		assertThrows(DetailException.class, () -> processObject(json1.put("then", 123)));

	}

	//--- private methods ----------
	
	private static JSONObject makeTerm(String assn)
	{
		JSONObject json = new JSONObject();
		json.put("term", assn);
		return json;
	}

	private Set<Assignment> processContent(JSONArray arr) throws DetailException
	{
		analysis.reset();
		analysis.processContent(arr);
		return analysis.assignments;
	}
	
	private Set<Assignment> processObject(JSONObject json) throws DetailException
	{
		analysis.reset();
		analysis.processObject(json);
		return analysis.assignments;
	}
	
	private void assertAssignmentSet(Set<Schema.Assignment> assnSet, String... expected)
	{
		Set<String> propURIs = assnSet.stream().map(assn -> ModelSchema.collapsePrefix(assn.propURI)).collect(Collectors.toSet());
		assertThat(propURIs, is(new HashSet<String>(Arrays.asList(expected))));
	}
}
