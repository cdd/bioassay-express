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

import com.cdd.bao.template.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.junit.jupiter.api.*;

/*
	Test for BAO vocabularies (imported from the template project)
*/

public class AxiomVocabTest 
{
	private String uri1 = ModelSchema.expandPrefix("bao:FNORD0000001");
	private String uri2 = ModelSchema.expandPrefix("bao:FNORD0000002");
	private String uri3 = ModelSchema.expandPrefix("bao:FNORD0000003");
	private String uri4 = ModelSchema.expandPrefix("obo:FNORD0000004");
	private String uri5 = ModelSchema.expandPrefix("rdf:FNORD0000005");
	
	private AxiomVocab.Term term1 = new AxiomVocab.Term(uri1, true);

	@Test
	public void testAxiomRules() throws IOException
	{
		AxiomVocab av1 = new AxiomVocab();

		AxiomVocab.Type[] types = AxiomVocab.Type.values();
		String[] uris = {uri1, uri2, uri3, uri4, uri5};

		for (int n = 0, p = 0; n < 100; n++)
		{
			AxiomVocab.Rule r = new AxiomVocab.Rule();
			r.type = types[n % types.length];
			r.subject = new AxiomVocab.Term[]{new AxiomVocab.Term(uris[p++ % uris.length], n % 2 == 0)};
			r.impact = new AxiomVocab.Term[n % 5 + 1];
			for (int i = 0; i < r.impact.length; i++)
				r.impact[i] = new AxiomVocab.Term(uris[p++ % uris.length], (n + i) % 2 == 0);

			assertEquals(r, r); // sanity test the equality operator

			av1.addRule(r);
		}

		assertEquals(100, av1.numRules());
		
		String serial;
		try (StringWriter wtr = new StringWriter())
		{
			av1.serialise(wtr, null);
			serial = wtr.toString();
		}
		AxiomVocab av2;
		try (Reader rdr = new StringReader(serial))
		{
			av2 = AxiomVocab.deserialise(rdr);
		}

		// Util.writeln("# rules: input=" + av1.numRules() + ", output=" + av2.numRules());
		assertEquals(av1.numRules(), av2.numRules());
		for (int n = 0; n < av1.numRules(); n++)
		{
			AxiomVocab.Rule r1 = av1.getRule(n), r2 = av2.getRule(n);
			assertEquals(r1, r2);
			assertEquals(r2, r1);
		}
	}

	@Test
	public void testRule()
	{
		AxiomVocab.Rule rule1 = new AxiomVocab.Rule(AxiomVocab.Type.LIMIT, new AxiomVocab.Term[]{term1});
		AxiomVocab.Rule rule2 = new AxiomVocab.Rule(AxiomVocab.Type.EXCLUDE, new AxiomVocab.Term[]{term1});

		assertEquals("LIMIT type axiom; subject: [bao:FNORD0000001/true], impacts: []", rule1.toString());
		assertEquals("EXCLUDE type axiom; subject: [bao:FNORD0000001/true], impacts: []", rule2.toString());

		// test equality
		TestUtilities.assertEquality(() -> 
		{
			AxiomVocab.Rule result = new AxiomVocab.Rule();
			result.type = AxiomVocab.Type.LIMIT;
			result.subject = new AxiomVocab.Term[]{term1};
			result.keyword = new AxiomVocab.Keyword();
			result.impact = new AxiomVocab.Term[]{term1};
			return result;
		});
	}

	@Test
	public void testType()
	{
		assertEquals(1, AxiomVocab.Type.LIMIT.raw());
		assertEquals(2, AxiomVocab.Type.EXCLUDE.raw());

		assertEquals(AxiomVocab.Type.LIMIT, AxiomVocab.Type.valueOf(1));
		assertEquals(AxiomVocab.Type.EXCLUDE, AxiomVocab.Type.valueOf(2));

		assertNull(AxiomVocab.Type.valueOf(0));
		assertNull(AxiomVocab.Type.valueOf(5));
	}

	@Test
	public void testTerm()
	{
		String valueURI = "http://www.bioassayontology.org/bao#BAO_0000008";
		AxiomVocab.Term term1 = new AxiomVocab.Term(valueURI, false);
		assertEquals("bao:BAO_0000008/false", term1.toString());

		AxiomVocab.Term term2 = new AxiomVocab.Term(valueURI, true);
		assertEquals("bao:BAO_0000008/true", term2.toString());

		// test equality
		assertNotEquals(term1, term2);
		assertNotEquals(term2, term1);
		assertNotEquals(term1.hashCode(), term2.hashCode());

		term2.wholeBranch = false;
		assertEquals(term1, term2);
		assertEquals(term2, term1);
		assertEquals(term1.hashCode(), term2.hashCode());

		AxiomVocab.Term term3 = new AxiomVocab.Term(null, false);
		assertNotEquals(term1, term3);
		assertNotEquals(term3, term1);
		assertNotEquals(term1.hashCode(), term3.hashCode());
		term1.valueURI = null;
		assertEquals(term1.hashCode(), term3.hashCode());
		
		// test equality
		TestUtilities.assertEquality(() -> 
		{
			AxiomVocab.Term result = new AxiomVocab.Term();
			result.valueURI = "valueURI";
			result.valueLabel = "valueLabel";
			result.propURI = "propURI";
			return result;
		}, "groupNest");
	}
	
	@Test
	public void testKeyword()
	{
		// test equality
		TestUtilities.assertEquality(() -> 
		{
			AxiomVocab.Keyword result = new AxiomVocab.Keyword();
			result.text = "text";
			result.propURI = "propURI";
			return result;
		});
	}

}
