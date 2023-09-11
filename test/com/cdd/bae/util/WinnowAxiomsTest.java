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
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;
import org.mockito.*;

public class WinnowAxiomsTest
{
	private Configuration configuration;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
	}

	@Test
	public void testSimpleReduction()
	{
		// create a single rule: if IC50 is present, then limit measurements to the concentration branch
		AxiomVocab axvoc = new AxiomVocab();
		AxiomVocab.Rule rule = new AxiomVocab.Rule();
		rule.type = AxiomVocab.Type.LIMIT;
		rule.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(ModelSchema.expandPrefix("bao:BAO_0000190"), false)};
		rule.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(ModelSchema.expandPrefix("obo:UO_0000051"), true)};
		axvoc.addRule(rule);
		
		WinnowAxioms winnow = new WinnowAxioms(axvoc);
		Schema schema = Common.getSchemaCAT();
		
		String resultsPropURI = ModelSchema.expandPrefix("bao:BAO_0000208");
		String ic50ValueURI = ModelSchema.expandPrefix("bao:BAO_0000190");
		String unitsPropURI = ModelSchema.expandPrefix("bao:BAO_0002874");
		String measureGroupURI = ModelSchema.expandPrefix("bao:BAX_0000017");
		List<WinnowAxioms.SubjectContent> subjects = new ArrayList<>();
		List<WinnowAxioms.KeywordContent> keywords = new ArrayList<>();
		subjects.add(new WinnowAxioms.SubjectContent(ic50ValueURI, Common.obtainTree(schema, resultsPropURI, null)));
		SchemaTree impactTree = Common.obtainTree(schema, unitsPropURI, new String[]{measureGroupURI});
		
		Set<String> results = WinnowAxioms.filterURI(winnow.winnowBranch(subjects, keywords, impactTree));
		
		String concValueURI = ModelSchema.expandPrefix("obo:UO_0000051");
		String umolValueURI = ModelSchema.expandPrefix("obo:UO_0000064");
		String unitValueURI = ModelSchema.expandPrefix("obo:UO_0000000");
		String densityValueURI = ModelSchema.expandPrefix("obo:UO_0000182");
		assertTrue(results.contains(concValueURI), "Expected to find concentration unit branch");
		assertTrue(results.contains(umolValueURI), "Expected to find micromolar units");
		assertFalse(results.contains(unitValueURI), "Expected not to find units root branch");
		assertFalse(results.contains(densityValueURI), "Expected not to find density branch");
	}
	
	@Test
	public void testKeywordReduction()
	{
		final String PROP_TITLE = ModelSchema.expandPrefix("bao:BAO_0002853");
	
		// single rule: if "IC50" keyword is present in the title, then limit measurements to that
		AxiomVocab axvoc = new AxiomVocab();
		AxiomVocab.Rule rule = new AxiomVocab.Rule();
		rule.type = AxiomVocab.Type.LIMIT;
		rule.keyword = new AxiomVocab.Keyword("IC50", PROP_TITLE);
		rule.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(ModelSchema.expandPrefix("obo:UO_0000051"), true)};
		axvoc.addRule(rule);
	
		WinnowAxioms winnow = new WinnowAxioms(axvoc);
		Schema schema = Common.getSchemaCAT();
		
		String resultsPropURI = ModelSchema.expandPrefix("bao:BAO_0000208");
		String ic50ValueURI = ModelSchema.expandPrefix("bao:BAO_0000190");
		String unitsPropURI = ModelSchema.expandPrefix("bao:BAO_0002874");
		String measureGroupURI = ModelSchema.expandPrefix("bao:BAX_0000017");
		List<WinnowAxioms.SubjectContent> subjects = new ArrayList<>();
		List<WinnowAxioms.KeywordContent> keywords = new ArrayList<>();
		keywords.add(new WinnowAxioms.KeywordContent("blah blah blah IC50", PROP_TITLE));
		SchemaTree impactTree = Common.obtainTree(schema, unitsPropURI, new String[]{measureGroupURI});
		
		Set<String> results = WinnowAxioms.filterURI(winnow.winnowBranch(subjects, keywords, impactTree));
		
		String concValueURI = ModelSchema.expandPrefix("obo:UO_0000051");
		String umolValueURI = ModelSchema.expandPrefix("obo:UO_0000064");
		String unitValueURI = ModelSchema.expandPrefix("obo:UO_0000000");
		String densityValueURI = ModelSchema.expandPrefix("obo:UO_0000182");
		assertTrue(results.contains(concValueURI), "Expected to find concentration unit branch");
		assertTrue(results.contains(umolValueURI), "Expected to find micromolar units");
		assertFalse(results.contains(unitValueURI), "Expected not to find units root branch");
		assertFalse(results.contains(densityValueURI), "Expected not to find density branch");	
	}
	
	@Test
	public void testAssnGroup()
	{
		// create a rule whose subject is very specific (prop/group/value), and impact: general value/specific assn
		String subjPropURI = ModelSchema.expandPrefix("bao:BAO_0000208"); // result
		String subjValueURI = ModelSchema.expandPrefix("bao:BAO_0080024"); // binary endpoint
		String impactPropURI = ModelSchema.expandPrefix("bao:BAO_0002874"); // units
		String[] impactGroupNest = new String[]{ModelSchema.expandPrefix("bao:BAX_0000017")}; // measurement
		String impactValueURI = ModelSchema.expandPrefix("bat:NotApplicable");
		AxiomVocab axvoc = new AxiomVocab();
		AxiomVocab.Rule rule = new AxiomVocab.Rule();
		rule.type = AxiomVocab.Type.LIMIT;
		rule.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(subjValueURI, false, subjPropURI, new String[0])};
		rule.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(impactValueURI, false, impactPropURI, impactGroupNest)};
		axvoc.addRule(rule);
		
		WinnowAxioms winnow = new WinnowAxioms(axvoc);
		Schema schema = Common.getSchemaCAT();
		
		List<WinnowAxioms.SubjectContent> subjects = new ArrayList<>();
		List<WinnowAxioms.KeywordContent> keywords = new ArrayList<>();
		subjects.add(new WinnowAxioms.SubjectContent(subjValueURI, Common.obtainTree(schema, subjPropURI, null)));
		SchemaTree impactTree = Common.obtainTree(schema, impactPropURI, impactGroupNest);
		
		Set<String> results = WinnowAxioms.filterURI(winnow.winnowBranch(subjects, keywords, impactTree));
		assertNotNull(results);
		assertEquals(1, results.size());
		String gotValue = results.toArray(new String[1])[0];
		assertEquals(gotValue, impactValueURI);	
	}
	
	@Test
	public void testLiteralNA()
	{
		// create a rule that implies N/A for a literal assignment (i.e. lacks the absence tree)
		String subjPropURI = ModelSchema.expandPrefix("bao:BAO_0002854"); // bioassay type
		String subjValueURI = ModelSchema.expandPrefix("bao:BAO_0000009"); // ADMET
		String impactPropURI = ModelSchema.expandPrefix("bao:BAO_0002853"); // assay title
		String impactValueURI = ModelSchema.expandPrefix("bat:NotApplicable");
		AxiomVocab axvoc = new AxiomVocab();
		AxiomVocab.Rule rule = new AxiomVocab.Rule();
		rule.type = AxiomVocab.Type.LIMIT;
		rule.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(subjValueURI, false)};
		rule.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(impactValueURI, false, impactPropURI, null)};
		axvoc.addRule(rule);
		
		WinnowAxioms winnow = new WinnowAxioms(axvoc);
		Schema schema = Common.getSchemaCAT();
		
		List<WinnowAxioms.SubjectContent> subjects = new ArrayList<>();
		List<WinnowAxioms.KeywordContent> keywords = new ArrayList<>();
		subjects.add(new WinnowAxioms.SubjectContent(subjValueURI, Common.obtainTree(schema, subjPropURI, null)));
		SchemaTree impactTree = Common.obtainTree(schema, impactPropURI, null);
		
		Set<String> results = WinnowAxioms.filterURI(winnow.winnowBranch(subjects, keywords, impactTree));
		assertNotNull(results);
		assertEquals(1, results.size());
		String gotValue = results.toArray(new String[1])[0];
		assertEquals(gotValue, impactValueURI);
	}
	
	@Test
	public void testImplyLiteral()
	{
		// creates a rule that implies a literal label
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
		
		WinnowAxioms winnow = new WinnowAxioms(axvoc);
		Schema schema = Common.getSchemaCAT();
		
		List<WinnowAxioms.SubjectContent> subjects = new ArrayList<>();
		List<WinnowAxioms.KeywordContent> keywords = new ArrayList<>();
		subjects.add(new WinnowAxioms.SubjectContent(subjValueURI, Common.obtainTree(schema, subjPropURI, null)));
		
		Schema.Assignment assn = schema.findAssignmentByProperty(impactPropURI)[0];
		Set<String> results = WinnowAxioms.filterLiteral(winnow.impliedLiterals(subjects, keywords, assn));
		assertNotNull(results);
		assertEquals(1, results.size());
		String gotValue = results.toArray(new String[1])[0];
		assertEquals(gotValue, impactLiteral);
	}
	
	@Test
	public void testExclusive()
	{
		String resultsPropURI = ModelSchema.expandPrefix("bao:BAO_0000208");
		String unitsPropURI = ModelSchema.expandPrefix("bao:BAO_0002874");
		String measureGroupURI = ModelSchema.expandPrefix("bao:BAX_0000017");
		String percentinhibValueURI = ModelSchema.expandPrefix("bao:BAO_0000201");
		String percentUnitsValueURI = ModelSchema.expandPrefix("obo:UO_0000187");
	
		// general rules that narrow down results -> units broadly
		AxiomVocab axvoc = new AxiomVocab();
		AxiomVocab.Rule rule = new AxiomVocab.Rule();
		rule.type = AxiomVocab.Type.LIMIT;
		rule.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(ModelSchema.expandPrefix("bao:BAO_0002162"), true)};
		rule.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(ModelSchema.expandPrefix("obo:UO_0000051"), true)};
		axvoc.addRule(rule);
		rule = new AxiomVocab.Rule();
		rule.type = AxiomVocab.Type.LIMIT;
		rule.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(ModelSchema.expandPrefix("bao:BAO_0000082"), true)};
		rule.impact = new AxiomVocab.Term[]
		{
			new AxiomVocab.Term(ModelSchema.expandPrefix("obo:UO_0000051"), true),
			new AxiomVocab.Term(ModelSchema.expandPrefix("obo:UO_0000186"), true)
		};
		axvoc.addRule(rule);
		
		WinnowAxioms winnow = new WinnowAxioms(axvoc);
		Schema schema = Common.getSchemaCAT();
		
		List<WinnowAxioms.SubjectContent> subjects = new ArrayList<>();
		List<WinnowAxioms.KeywordContent> keywords = new ArrayList<>();
		subjects.add(new WinnowAxioms.SubjectContent(percentinhibValueURI, Common.obtainTree(schema, resultsPropURI, null)));
		SchemaTree impactTree = Common.obtainTree(schema, unitsPropURI, new String[]{measureGroupURI});
		Set<String> results = WinnowAxioms.filterURI(winnow.winnowBranch(subjects, keywords, impactTree));
		
		assertNotNull(results);
		assertTrue(results.contains(ModelSchema.expandPrefix("obo:UO_0000051")), "Expected to find [concentration unit]");
		assertTrue(results.contains(ModelSchema.expandPrefix("obo:UO_0000062")), "Expected to find [molar]");
		assertTrue(results.contains(ModelSchema.expandPrefix("obo:UO_0000187")), "Expected to find [percent]");
		assertFalse(results.contains(ModelSchema.expandPrefix("obo:UO_0000182")), "Expected to find [density unit]");
		
		// second part: add a specific rule and mark it as exclusive
		rule = new AxiomVocab.Rule();
		rule.type = AxiomVocab.Type.LIMIT;
		rule.exclusive = true;
		rule.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(percentinhibValueURI, true)};
		rule.impact = new AxiomVocab.Term[]{new AxiomVocab.Term(percentUnitsValueURI, false)};
		axvoc.addRule(rule);
		
		results = WinnowAxioms.filterURI(winnow.winnowBranch(subjects, keywords, impactTree));
		assertNotNull(results);
		assertTrue(results.contains(percentUnitsValueURI), "Expected to find [percent units]");
		assertEquals(1, results.size()); // percent units is the *only* allowed result
	}
}


