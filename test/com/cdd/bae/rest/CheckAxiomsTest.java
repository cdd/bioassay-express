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

package com.cdd.bae.rest;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for CheckAxioms REST API.
*/

public class CheckAxiomsTest
{
	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
	}
	
	@Test
	public void testViolationSimple() throws Exception
	{
		// create a single rule: if IC50 is present, then limit measurements to the concentration branch
		// note: this has to be poked into the CheckAxioms class as an override
		AxiomVocab axvoc = new AxiomVocab();
		AxiomVocab.Rule rule = new AxiomVocab.Rule();
		rule.type = AxiomVocab.Type.LIMIT;
		rule.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(ModelSchema.expandPrefix("bao:BAO_0000190"), false)};
		rule.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(ModelSchema.expandPrefix("obo:UO_0000051"), true)};
		axvoc.addRule(rule);
		CheckAxioms.useAxioms = axvoc;
		
		String resultsPropURI = ModelSchema.expandPrefix("bao:BAO_0000208");
		String ic50ValueURI = ModelSchema.expandPrefix("bao:BAO_0000190");
		String unitsPropURI = ModelSchema.expandPrefix("bao:BAO_0002874");
		String measureGroupURI = ModelSchema.expandPrefix("bao:BAX_0000017");
		String umolValueURI = ModelSchema.expandPrefix("obo:UO_0000064");
		String densityValueURI = ModelSchema.expandPrefix("obo:UO_0000182");
		
		// setup the assay substitute
		JSONObject request = new JSONObject();
		request.put("schemaURI", Common.getSchemaCAT().getSchemaPrefix());
		JSONArray jsonAnnots = new JSONArray();
		jsonAnnots.put(makeAnnot(resultsPropURI, ic50ValueURI, null));
		jsonAnnots.put(makeAnnot(unitsPropURI, umolValueURI, new String[]{measureGroupURI}));
		jsonAnnots.put(makeAnnot(unitsPropURI, densityValueURI, new String[]{measureGroupURI}));
		request.put("annotations", jsonAnnots);
		
		JSONObject result = new CheckAxioms().processRequest(request, TestUtilities.mockSessionCurator());
		
		// just the "density" annotation is bogus and should be highlighted; everything else is left alone
		JSONArray violations = result.getJSONArray("violations");
		assertEquals(1, violations.length());
		JSONObject json = violations.getJSONObject(0);
		assertEquals(unitsPropURI, json.getString("propURI"));
		assertEquals(densityValueURI, json.getString("valueURI"));
		JSONArray triggers = json.getJSONArray("triggers");
		assertEquals(1, triggers.length());
		assertEquals(ic50ValueURI, triggers.getString(0));
		
		// remove the override
		CheckAxioms.useAxioms = null;
	}
	
	@Test
	public void testViolationTwo() throws Exception
	{
		// two rules, each of which imply different things
		String rule1Subject = ModelSchema.expandPrefix("bao:BAO_0000009"); // bioassay type: ADMET
		String rule1Impact = ModelSchema.expandPrefix("bao:BAO_0000252"); // assay format: mitochondrion format
		String rule2Subject = ModelSchema.expandPrefix("bao:BAO_0002776"); // bioassay: radioligand binding assay
		String rule2Impact = ModelSchema.expandPrefix("bao:BAO_0000164"); // assay design method: ATP quantitation
		String rule2Violate = ModelSchema.expandPrefix("bao:BAO_0040015"); // assay design method: cell density determination
	
		// create the vocab as an override
		AxiomVocab axvoc = new AxiomVocab();
		AxiomVocab.Rule rule1 = new AxiomVocab.Rule();
		rule1.type = AxiomVocab.Type.LIMIT;
		rule1.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(rule1Subject, false)};
		rule1.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(rule1Impact, false)};
		axvoc.addRule(rule1);
		AxiomVocab.Rule rule2 = new AxiomVocab.Rule();
		rule2.type = AxiomVocab.Type.LIMIT;
		rule2.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(rule2Subject, false)};
		rule2.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(rule2Impact, false)};
		axvoc.addRule(rule2);
		CheckAxioms.useAxioms = axvoc;
		
		// setup the assay substitute
		JSONObject request = new JSONObject();
		request.put("schemaURI", Common.getSchemaCAT().getSchemaPrefix());
		JSONArray jsonAnnots = new JSONArray();
		jsonAnnots.put(makeAnnot("bao:BAO_0002854", rule1Subject, null));
		jsonAnnots.put(makeAnnot("bao:BAO_0000205", rule1Impact, null));
		jsonAnnots.put(makeAnnot("bao:BAO_0002855", rule2Subject, null));
		jsonAnnots.put(makeAnnot("bao:BAO_0095009", rule2Violate, null));
		request.put("annotations", jsonAnnots);

		JSONObject result = new CheckAxioms().processRequest(request, TestUtilities.mockSessionCurator());
		JSONObject[] justifications = result.getJSONArray("justifications").toObjectArray();
		JSONObject[] violations = result.getJSONArray("violations").toObjectArray();
		assertEquals(0, justifications.length);
		assertEquals(1, violations.length);
		assertEquals("http://www.bioassayontology.org/bao#BAO_0095009", violations[0].getString("propURI"));
		assertEquals("http://www.bioassayontology.org/bao#BAO_0040015", violations[0].getString("valueURI"));
		String[] triggers = violations[0].getJSONArray("triggers").toStringArray();
		assertEquals(1, triggers.length);
		assertEquals("http://www.bioassayontology.org/bao#BAO_0002776", triggers[0]);
		
		// remove the override
		CheckAxioms.useAxioms = null;		
	}
	
	@Test
	public void testJustification() throws Exception
	{
		// two rules, each of which imply different things
		String rule1Subject = ModelSchema.expandPrefix("bao:BAO_0000009"); // bioassay type: ADMET
		String rule1Impact = ModelSchema.expandPrefix("bao:BAO_0000252"); // assay format: mitochondrion format
		String rule2Subject = ModelSchema.expandPrefix("bao:BAO_0002776"); // bioassay: radioligand binding assay
		String rule2Impact = ModelSchema.expandPrefix("bao:BAO_0000164"); // assay design method: ATP quantitation
	
		// create the vocab as an override
		AxiomVocab axvoc = new AxiomVocab();
		AxiomVocab.Rule rule1 = new AxiomVocab.Rule();
		rule1.type = AxiomVocab.Type.LIMIT;
		rule1.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(rule1Subject, false)};
		rule1.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(rule1Impact, false)};
		axvoc.addRule(rule1);
		AxiomVocab.Rule rule2 = new AxiomVocab.Rule();
		rule2.type = AxiomVocab.Type.LIMIT;
		rule2.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(rule2Subject, false)};
		rule2.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(rule2Impact, false)};
		axvoc.addRule(rule2);
		CheckAxioms.useAxioms = axvoc;
		
		// setup the assay substitute
		JSONObject request = new JSONObject();
		request.put("schemaURI", Common.getSchemaCAT().getSchemaPrefix());
		JSONArray jsonAnnots = new JSONArray();
		jsonAnnots.put(makeAnnot("bao:BAO_0002854", rule1Subject, null));
		jsonAnnots.put(makeAnnot("bao:BAO_0002855", rule2Subject, null));
		request.put("annotations", jsonAnnots);

		JSONObject result = new CheckAxioms().processRequest(request, TestUtilities.mockSessionCurator());
		JSONObject[] justifications = result.getJSONArray("justifications").toObjectArray();
		JSONObject[] violations = result.getJSONArray("violations").toObjectArray();
		assertEquals(2, justifications.length);
		assertEquals(0, violations.length);
		for (JSONObject justif : justifications)
		{
			String propURI = justif.getString("propURI");
			String valueURI = justif.getString("valueURI");
			String[] groupNest = justif.getJSONArray("groupNest").toStringArray();
			String[] triggers = justif.getJSONArray("triggers").toStringArray();

			assertEquals(0, groupNest.length);
			assertEquals(1, triggers.length);
			if (valueURI.equals(rule1Impact))
			{
				assertEquals(propURI, "http://www.bioassayontology.org/bao#BAO_0000205");
				assertEquals(triggers[0], rule1Subject);
			}
			else if (valueURI.equals(rule2Impact))
			{
				assertEquals(propURI, "http://www.bioassayontology.org/bao#BAO_0095009");
				assertEquals(triggers[0], rule2Subject);
			}
			else fail("Justification of invalid URI: " + valueURI);
		}
		
		// remove the override
		CheckAxioms.useAxioms = null;
	}
	
	@Test
	public void testAddedNotApplic() throws Exception
	{
		String propOrganism = ModelSchema.expandPrefix("bao:BAO_0002921");
		String valueHuman = ModelSchema.expandPrefix("obo:NCBITaxon_9606");
		String propTitle = AssayUtil.URI_ASSAYTITLE;
		
		AxiomVocab.Term subject = new AxiomVocab.Term(valueHuman, false);
		AxiomVocab.Term impact = new AxiomVocab.Term(AssayUtil.URI_NOTAPPLICABLE, false, propTitle, null);
		AxiomVocab axvoc = new AxiomVocab();
		axvoc.addRule(new AxiomVocab.Rule(AxiomVocab.Type.LIMIT, new AxiomVocab.Term[]{subject}, new AxiomVocab.Term[]{impact}));

		CheckAxioms.useAxioms = axvoc;

		JSONObject annot = new JSONObject();
		annot.put("propURI", propOrganism);
		annot.put("valueURI", valueHuman);
		JSONArray annotList = new JSONArray();
		annotList.put(annot);

		JSONObject request = new JSONObject();
		request.put("schemaURI", Common.getSchemaCAT().getSchemaPrefix());
		request.put("annotations", annotList);
		JSONObject result = new CheckAxioms().processRequest(request, TestUtilities.mockSessionCurator());
		JSONObject[] resultList = result.getJSONArray("additional").toObjectArray();

		assertEquals(1, resultList.length);
		assertEquals(propTitle, resultList[0].getString("propURI"));
		assertEquals(AssayUtil.URI_NOTAPPLICABLE, resultList[0].getString("valueURI"));
		String[] resultTriggers = resultList[0].getJSONArray("triggers").toStringArray();
		assertEquals(1, resultTriggers.length);
		assertEquals(valueHuman, resultTriggers[0]);
		
		// remove the override
		CheckAxioms.useAxioms = null;
	}
	
	@Test
	public void testImplyLiteral() throws Exception
	{
		String subjPropURI = ModelSchema.expandPrefix("bao:BAO_0000196"); // mode of action
		String subjValueURI = ModelSchema.expandPrefix("bao:BAO_0000441"); // competitive binding
		String impactPropURI = ModelSchema.expandPrefix("bao:BAO_0002853"); // assay title
		String impactLiteral = ModelSchema.expandPrefix("mode of action is competitive binding");
		AxiomVocab axvoc = new AxiomVocab();
		AxiomVocab.Rule rule = new AxiomVocab.Rule();
		rule.type = AxiomVocab.Type.LIMIT;
		rule.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(subjValueURI, false)};
		rule.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(null, impactLiteral, false, impactPropURI, null)};
		axvoc.addRule(rule);
	
		CheckAxioms.useAxioms = axvoc;

		JSONObject annot = new JSONObject();
		annot.put("propURI", subjPropURI);
		annot.put("valueURI", subjValueURI);
		JSONArray annotList = new JSONArray();
		annotList.put(annot);

		JSONObject request = new JSONObject();
		request.put("schemaURI", Common.getSchemaCAT().getSchemaPrefix());
		request.put("annotations", annotList);
		JSONObject result = new CheckAxioms().processRequest(request, TestUtilities.mockSessionCurator());
		JSONObject[] resultList = result.getJSONArray("additional").toObjectArray();
		
		assertEquals(1, resultList.length);
		assertEquals(impactPropURI, resultList[0].getString("propURI"));
		assertEquals(impactLiteral, resultList[0].getString("valueLabel"));
		String[] resultTriggers = resultList[0].getJSONArray("triggers").toStringArray();
		assertEquals(1, resultTriggers.length);
		assertEquals(subjValueURI, resultTriggers[0]);
		
		// remove the override
		CheckAxioms.useAxioms = null;
	}	
	// ------------ private methods ------------
	
	private JSONObject makeAnnot(String propURI, String valueURI, String[] groupNest)
	{
		JSONObject json = new JSONObject();
		json.put("propURI", ModelSchema.expandPrefix(propURI));
		json.put("valueURI", ModelSchema.expandPrefix(valueURI));
		if (groupNest != null) json.put("groupNest", groupNest);
		return json;
	}
}
