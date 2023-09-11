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
import com.cdd.bao.template.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;
import org.junit.jupiter.api.*;

/*
	Tests for BoilerPlatescript 
*/

public class BoilerplateScriptTest
{
	// assignments
	private String assn1 = "bao:BAO_0002854"; // bioassay type
	private String assn2 = "bao:BAO_0000205"; // assay format
	private String assn3 = "bao:BAO_0002853"; // assay title
	private String assn4 = "bao:BAO_0095009"; // assay design method
	
	// values
	private String val1 = "bao:BAO_0000009";
	private String val2a = "bao:BAO_0000219"; // cell based format
	private String val2b = "bao:BAO_0000366"; // cell-free format
	
	// branch
	private String branch1 = "bao:BAO_0000008"; // parent of val1

	// annotation 
	private DataObject.Annotation annot1 = new DataObject.Annotation(ModelSchema.expandPrefix(assn1), ModelSchema.expandPrefix(val1));
	private DataObject.Annotation annot2a = new DataObject.Annotation(ModelSchema.expandPrefix(assn2), ModelSchema.expandPrefix(val2a));
	private DataObject.Annotation annot2b = new DataObject.Annotation(ModelSchema.expandPrefix(assn2), ModelSchema.expandPrefix(val2b));
	
	// textlabels
	private DataObject.TextLabel text1 = new DataObject.TextLabel(ModelSchema.expandPrefix(assn3), "title <b>markup</b>");
	private String escapedText1 = "title &lt;b&gt;markup&lt;/b&gt;";
	BoilerplateScript script;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		// create an assay with one annotation and initialize the BoilerplateScript with it
		DataObject.Assay assay = mock(DataObject.Assay.class);
		assay.annotations = new DataObject.Annotation[]{annot1, annot2a, annot2b};
		assay.textLabels = new DataObject.TextLabel[]{text1};
		script = new BoilerplateScript(null, Common.getSchemaCAT(), assay);
	}
	
	@Test
	public void testProcessContent() throws DetailException
	{
		JSONArray arr = new JSONArray();
		arr.put(new JSONObject().put("term", assn1));
		arr.put(" and text");
		assertThat(processContent(arr), is("ADMET and text"));
		
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
		assertThat(processObject(json), is("ADMET"));

		// text labels
		assertThat(processObject(json.put("term", assn3)), is(escapedText1));
	}

	@Test
	public void testProcessTerms() throws DetailException
	{
		
		JSONObject json = new JSONObject();
		json.put("terms", new JSONArray().put(assn1).put(assn2));
		assertThat(processObject(json), is("ADMET, cell based format and cell-free format"));

		// text labels
		json.put("terms", new JSONArray().put(assn1).put(assn3));
		assertThat(processObject(json), is("ADMET and " + escapedText1));
	}

	@Test
	public void testProcessField() throws DetailException
	{
		JSONObject json = new JSONObject();
		
		// uniqueID
		json.put("field", "uniqueID");
		assertThat(processObject(json), is("?"));
		script.getAssay().uniqueID = "pubchemAID:1020";
		assertThat(processObject(json), is("AID1020"));
		
		// assayID
		json.put("field", "assayID");
		assertThat(processObject(json), is("0"));
		script.getAssay().assayID = 100;
		assertThat(processObject(json), is("100"));
		
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

		// case: matches annotation
		assertThat(processObject(json), is("thenBranch"));

		// case: matches textlabel
		json.put("ifany", assn3);
		assertThat(processObject(json), is("thenBranch"));

		// case: matches not
		json.put("ifany", assn4);
		assertThat(processObject(json), is(""));
		
		// then and else branches present - same three cases as above
		json = new JSONObject();
		json.put("ifany", assn1);
		json.put("then", "thenBranch");
		json.put("else", "elseBranch");

		assertThat(processObject(json), is("thenBranch"));
		assertThat(processObject(json.put("ifany", assn3)), is("thenBranch"));
		assertThat(processObject(json.put("ifany", assn4)), is("elseBranch"));

		// branches can be string 
		json = new JSONObject();
		json.put("ifany", assn1);
		json.put("then", "thenBranch");
		
		assertThat(processObject(json), is("thenBranch"));
		
		// ... JSONArray
		json.put("then", new JSONArray(Arrays.asList("A", "B", "C")));
		assertThat(processObject(json), is("ABC"));
		
		// ... or JSONObject
		json.put("then", new JSONObject().put("ifany", assn3).put("then", "nested then-then"));
		assertThat(processObject(json), is("nested then-then"));
		json.put("then", new JSONObject().put("ifany", assn4).put("else", "nested then-else"));
		assertThat(processObject(json), is("nested then-else"));

		// incompatible type
		assertThrows(DetailException.class, () -> processObject(new JSONObject().put("ifany", assn1).put("then", 123)));
		
		// multiple conditions
		JSONArray conditions = new JSONArray();
		conditions.put(new JSONArray().put(assn4));
		conditions.put(new JSONArray().put(assn1));
		conditions.put(new JSONArray().put(assn2));
		conditions.put(new JSONArray().put(assn3));
		json = new JSONObject();
		json.put("ifany", conditions);
		json.put("then", "thenBranch");
		assertThat(processObject(json), is("thenBranch"));
	}
	
	@Test
	public void testProcessIfValue() throws DetailException
	{
		//--- ifvalue
		// missing branches
		JSONObject json = new JSONObject();
		json.put("ifvalue", new JSONArray().put(val1).put(assn1));
		assertThat(processObject(json), is(""));
		
		// value is found for assignment
		json = new JSONObject();
		json.put("ifvalue", new JSONArray().put(val1).put(assn1));
		json.put("then", "thenBranch");
		json.put("else", "elseBranch");
		assertThat(processObject(json), is("thenBranch"));

		// value is not found for assignment
		json.put("ifvalue", new JSONArray().put(val2a).put(assn1));
		assertThat(processObject(json), is("elseBranch"));
		
		//--- ifbranch
		// missing branches
		json = new JSONObject();
		json.put("ifvalue", new JSONArray().put(branch1).put(assn1));
		assertThat(processObject(json), is(""));
		
		// value is found for assignment
		json = new JSONObject();
		json.put("ifbranch", new JSONArray().put(branch1).put(assn1));
		json.put("then", "thenBranch");
		json.put("else", "elseBranch");
		assertThat(processObject(json), is("thenBranch"));

		// value is not found for assignment
		json.put("ifbranch", new JSONArray().put(val2a).put(assn1));
		assertThat(processObject(json), is("elseBranch"));
	}
	
	@Test
	public void testExtractAssignments() throws DetailException
	{
		// strings
		Schema.Assignment[] assnList = script.extractAssignments(assn1);
		assertAssignmentList(assnList, assn1);
		
		// JSONArray (propURI followed by groupNest)
		assnList = script.extractAssignments(new JSONArray(Arrays.asList(assn1)));
		assertAssignmentList(assnList, assn1);
		
		assnList = script.extractAssignments(new JSONArray(Arrays.asList(assn2)));
		assertAssignmentList(assnList, assn2);

		// invalid propURIs
		assnList = script.extractAssignments("bao:invalid");
		assertAssignmentList(assnList);
		
		assnList = script.extractAssignments(new JSONArray(Arrays.asList("bao:invalid")));
		assertAssignmentList(assnList);
		
		// empty input
		assnList = script.extractAssignments("");
		assertAssignmentList(assnList);
		
		assnList = script.extractAssignments(new JSONArray());
		assertAssignmentList(assnList);

		// invalid input
		assertThrows(DetailException.class, () -> script.extractAssignments(null));
		assertThrows(DetailException.class, () -> script.extractAssignments(new JSONObject()));
	}
	
	@Test
	public void testMatchAnnotations() throws DetailException
	{
		// assignment with no annotations
		Schema.Assignment[] assnList = script.extractAssignments(new JSONArray(Arrays.asList(assn3)));
		assertArrayResult(script.matchAnnotations(assnList));
		
		// assignment with one annotation
		assnList = script.extractAssignments(new JSONArray(Arrays.asList(assn1)));
		assertArrayResult(script.matchAnnotations(assnList), annot1);

		// assignment with two annotations
		assnList = script.extractAssignments(new JSONArray(Arrays.asList(assn2)));
		assertArrayResult(script.matchAnnotations(assnList), annot2a, annot2b);
		
		// multiple assignments
		assnList = script.extractAssignments(new JSONArray(Arrays.asList(assn2)));
		assnList = ArrayUtils.addAll(assnList, script.extractAssignments(new JSONArray(Arrays.asList(assn1))));
		assertArrayResult(script.matchAnnotations(assnList), annot1, annot2a, annot2b);
		
		assnList = script.extractAssignments(new JSONArray(Arrays.asList(assn1)));
		assnList = ArrayUtils.addAll(assnList, script.extractAssignments(new JSONArray(Arrays.asList(assn2))));
		assertArrayResult(script.matchAnnotations(assnList), annot1, annot2a, annot2b);
	}
	
	@Test
	public void testMatchTextLabels() throws DetailException
	{
		// assignment with no text labels
		Schema.Assignment[] assnList = script.extractAssignments(new JSONArray(Arrays.asList(assn1)));
		assertArrayResult(script.matchTextLabels(assnList));
		
		// assignment with one text label
		assnList = script.extractAssignments(new JSONArray(Arrays.asList(assn3)));
		assertArrayResult(script.matchTextLabels(assnList), text1);
	}

	@Test
	public void testRenderTerms()
	{
		JSONObject json = new JSONObject();

		// case: no terms
		assertThat(renderTerms(json), is(""));
		assertThat(renderTerms(json.put("empty", "missing")), is("missing"));

		// case: term is empty or null
		assertThat(renderTerms(json, ""), is("?"));
		assertThat(renderTerms(json, (String)null), is("?"));

		// case: single term no formatting
		assertThat(renderTerms(json, "first"), is("first"));

		// case: two or more terms
		assertThat(renderTerms(json, "first", "second"), is("first and second"));
		assertThat(renderTerms(json, "first", "second", "third"), is("first, second and third"));

		// custom separator
		assertThat(renderTerms(json.put("sep", " / "), "first", "second", "third"), is("first / second / third"));
		assertThat(renderTerms(json.put("sep", " / "), "first"), is("first"));
		
		// case: add an article (single term)
		json = new JSONObject();
		assertThat(renderTerms(json.put("article", "definite"), "first"), is("the first"));
		assertThat(renderTerms(json.put("article", "indefinite"), "first"), is("a first"));
		
		// case: add an article (multiple terms)
		assertThat(renderTerms(json.put("article", "definite"), "a", "b", "c"), is("the a, b and c"));
		assertThat(renderTerms(json.put("article", "indefinite"), "a", "b", "c"), is("an a, b and c"));
		
		// case: more edge case for indefinite
		json = new JSONObject().put("article", "indefinite");
		assertThat(renderTerms(json, "aword"), is("an aword"));
		assertThat(renderTerms(json, "eword"), is("an eword"));
		assertThat(renderTerms(json, "iword"), is("an iword"));
		assertThat(renderTerms(json, "oword"), is("an oword"));
		assertThat(renderTerms(json, "uword"), is("an uword"));
		assertThat(renderTerms(json, "yword"), is("an yword"));

		// pluralize
		json = new JSONObject();
		assertThat(renderTerms(json, "house"), is("house"));
		assertThat(renderTerms(json.put("plural", true), "house"), is("houses"));
		assertThat(renderTerms(json.put("plural", true), "bus"), is("buses"));
		assertThat(renderTerms(json.put("plural", true), "house", "bus"), is("houses and buses"));

		// make sure we escape properly
		json = new JSONObject();
		assertThat(renderTerms(json, "a&b"), is("a&amp;b"));
	}
	
	//--- private methods ----------

	private String renderTerms(JSONObject json, String... terms)
	{
		script.reset();
		script.renderTerms(Arrays.asList(terms), json);
		return script.getHTML();
	}
	
	private String processContent(JSONArray arr) throws DetailException
	{
		script.reset();
		script.processContent(arr);
		return script.getHTML();
	}
	
	private String processObject(JSONObject json) throws DetailException
	{
		script.reset();
		script.processObject(json);
		return script.getHTML();
	}
	
	private void assertAssignmentList(Schema.Assignment[] assnList, String... expected)
	{
		assertThat(assnList.length, is(expected.length));
		for (int i = 0; i < assnList.length; i++)
		{
			assertThat(assnList[i].propURI, is(ModelSchema.expandPrefix(expected[i])));
		}
	}

	private <T> void assertArrayResult(T[] annotList, T... expected)
	{
		assertThat(annotList.length, is(expected.length));
		for (int i = 0; i < annotList.length; i++)
		{
			assertThat(annotList[i], is(expected[i]));
		}
	}
}
