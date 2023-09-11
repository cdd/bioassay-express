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
import com.cdd.bae.rest.MockRESTUtilities.*;
import com.cdd.bae.util.*;
import com.cdd.bae.util.WinnowTree.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.SchemaTree.*;
import com.cdd.testutil.*;

import static com.cdd.bae.rest.KeywordMatch.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for KeywordMatch REST API.
*/

public class KeywordMatchTest extends EndpointEmulator
{
	private static final String N67890 = "67890";
	private static final String N12345 = "12345";
	private static final String FGHIJ = "fghij";
	private static final String ABCDE = "abcde";

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		TestUtilities.ensureOntologies();
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
		Common.setProvCache(ProvisionalCache.loaded());

		restService = new KeywordMatch();
		restService.logger = TestUtilities.mockLogger();
	}

	@Test
	public void testRESTcall() throws IOException
	{
		JSONObject params = new JSONObject();
		params.put("keywords", "xy");
		params.put("schemaURI", "http://www.bioassayontology.org/bas#");
		params.put("annotations", new JSONArray());
		MockJSONResponse response = doPost(params);
		JSONArray json = response.getContentAsJSONArray();
		assertThat(json.length(), greaterThan(0));

		params.put("keywords", "xyz");
		response = doPost(params);
		json = response.getContentAsJSONArray();
		assertThat(json.length(), is(0));
	}

	@Test
	public void testDetermineProposals()
	{
		Schema schema = Common.getSchemaCAT();
		Set<String> already = new HashSet<>();
		List<Proposal> proposals = new ArrayList<>();

		determineProposals(proposals, "human", schema, already, null, null, null);
		assertThat(proposals.size(), is(MAX_PROPOSALS));

		for (Proposal proposal : proposals)
			already.add(Schema.keyPropGroup(proposal.assn.propURI, proposal.assn.groupNest()));

		// assignments found in already are not returned
		proposals.clear();
		determineProposals(proposals, "human", schema, already, null, null, null);
		assertThat(proposals.size(), is(greaterThan(0)));
		for (Proposal proposal : proposals)
			assertThat(Schema.keyPropGroup(proposal.assn.propURI, proposal.assn.groupNest()), not(in(already)));
	}
	
	@Test
	public void testURIsearch() throws IOException
	{
		Schema schema = Common.getSchemaCAT();
		List<Proposal> proposals = new ArrayList<>();
		JSONObject params = new JSONObject();
		params.put("schemaURI", "http://www.bioassayontology.org/bas#");
		params.put("annotations", new JSONArray());

		String[] keywords = 
		{
			"bao:BAO_0000009", "BAO_0000009", "BAO:BAO_0000009", "bao_0000009",
			"http://www.bioassayontology.org/bao#BAO_0000009",
		};
		for (String keyword : keywords)
		{
			proposals.clear();
			searchByPropURI(proposals, keyword, schema);
			assertThat(keyword, proposals, hasSize(1));
			assertThat(proposals.get(0).node.label, is("ADMET"));
			
			params.put("keywords", "bao:BAO_0000009");
			JSONArray json = doPost(params).getContentAsJSONArray();
			assertThat(json.length(), greaterThan(0));
			assertThat(json.getJSONObject(0).getString("valueLabel"), is("ADMET"));
			assertThat(json.getJSONObject(0).getString("highlightAltLabel"), is("**bao:BAO_0000009**"));
		}
	}

	@Test
	public void testTallyMatches()
	{
		assertTallyMatch("value equals word", 6, N12345);
		assertTallyMatch("prop equals word", 3, ABCDE);

		assertTallyMatch("value starts with word", 4, "123");
		assertTallyMatch("prop starts with word", 2, "abc");

		assertTallyMatch("value starts with word (single character)", 4, "1");
		assertTallyMatch("prop starts with word (single character)", 2, "a");

		assertTallyMatch("value contains word", 2, "234");
		assertTallyMatch("prop contains word", 1, "bcd");

		assertTallyMatch("ignore single character words inside prop or values", -1, "3");
		assertTallyMatch("ignore single character words inside prop or values", -1, "c");

		assertTallyMatch("result is 0 for no words", 0);

		assertTallyMatch("multiple words are added up", 9, N12345, ABCDE);
		assertTallyMatch("multiple words are added up", 7, "123", ABCDE);
		assertTallyMatch("multiple words are added up", 6, "123", "abc");
	}

	@Test
	public void testCollectTerms()
	{
		// find the assignment that corresponds to propURI (assay screening campaign
		// stage)
		String propURI = "http://www.bioassayontology.org/bao#BAO_0000210";
		Schema schema = Common.getSchemaCAT();
		Schema.Assignment assn = schema.findAssignmentByProperty(propURI)[0];

		List<Proposal> proposals = new ArrayList<>();
		Set<String> already = new HashSet<>();
		Set<String> query = new HashSet<>();

		collectTerms(schema, proposals, assn, query, already, null);
		assertThat(proposals, hasSize(0));

		// full match of confirmatory
		query.add("confirmatory");
		collectTerms(schema, proposals, assn, query, already, null);
		assertThat(proposals, hasSize(2));
		for (Proposal proposal : proposals)
			assertThat(proposal.matchCount, is(6));

		// full match of stage
		proposals.clear();
		query.clear();
		query.add("stage");
		collectTerms(schema, proposals, assn, query, already, null);
		assertThat(proposals, hasSize(1));
		for (Proposal proposal : proposals)
			assertThat(proposal.matchCount, is(6));

		// searching both words, stage is also found in props
		proposals.clear();
		query.add("confirmatory");
		collectTerms(schema, proposals, assn, query, already, null);
		assertThat(proposals, hasSize(3));
		for (Proposal proposal : proposals)
			assertThat(proposal.matchCount, is(oneOf(5, 9)));

		already.add(Schema.keyPropGroup(assn.propURI, assn.groupNest()));

		// returns nothing if assn is in already seen set
		proposals.clear();
		collectTerms(schema, proposals, assn, query, already, null);
		assertThat(proposals, hasSize(0));
	}

	@Test
	public void testGetWordScore()
	{
		Set<String> wordSet = new HashSet<>(Arrays.asList(ABCDE, FGHIJ));
		assertThat(getWordScore(ABCDE, wordSet), is(3));
		assertThat(wordSet, hasSize(1));
		assertThat(wordSet, contains(FGHIJ));

		wordSet.add(ABCDE);
		assertThat(wordSet, hasSize(2));

		assertThat(getWordScore("abc", wordSet), is(2));
		assertThat(wordSet, hasSize(1));
		assertThat(wordSet, contains(FGHIJ));

		wordSet.add(ABCDE);
		assertThat(wordSet, hasSize(2));

		assertThat(getWordScore("bc", wordSet), is(1));
		assertThat(wordSet, hasSize(1));
		assertThat(wordSet, contains(FGHIJ));

		wordSet.add(ABCDE);
		assertThat(wordSet, hasSize(2));

		assertThat(getWordScore("c", wordSet), is(0));
		assertThat(wordSet, hasSize(2));
		assertThat(wordSet, containsInAnyOrder(ABCDE, FGHIJ));
	}

	@Test
	public void testCalculateScore()
	{
		Set<String> wordList = new HashSet<>();

		assertFuzzyScore(-9, wordList, "nodelabel");

		wordList.add("label");
		assertFuzzyScore(4, wordList, "nodelabel");
		assertFuzzyScore(-12, wordList, "another title");
		assertFuzzyScore(4, wordList, "another title", "lab");
		assertFuzzyScore(8, wordList, "another title", "lab", "label");
		assertFuzzyScore(8, wordList, "another title", "label", "lab");
	}

	@Test
	public void testHighlightQuery()
	{
		Set<String> wordList = new HashSet<>();
		assertThat(highlightQuery("abcdef", wordList), is("abcdef"));
		wordList.add("xyz");
		assertThat(highlightQuery("abcdef", wordList), is("abcdef"));
		wordList.add("a");
		assertThat(highlightQuery("abcdef", wordList), is("abcdef"));
		wordList.add("ab");
		assertThat(highlightQuery("abcdef Abcdef", wordList), is("**ab**cdef **Ab**cdef"));
		assertThat(highlightQuery("abcdef", wordList), is("**ab**cdef"));
		wordList.add("abc");
		assertThat(highlightQuery("abcdef", wordList), is("**abc**def"));
		wordList.add("def");
		assertThat(highlightQuery("abcdef", wordList), is("**abcdef**"));
		wordList.clear();
		wordList.add("def");
		assertThat(highlightQuery("abcdef", wordList), is("abc**def**"));
		wordList.clear();
		wordList.add("de");
		assertThat(highlightQuery("abcdef", wordList), is("abc**de**f"));
	}

	@Test
	public void testDetermineEligibility()
	{
		Set<String> eligible = new HashSet<>();
		long[] assayIDList = new long[0];
		Layer[] layers = null;
		Result[] winnowed = new Result[0];
		determineEligibility(eligible, assayIDList, layers, winnowed);
		assertThat(eligible, hasSize(0));

		layers = new Layer[]{new Layer()};
		winnowed = new Result[]{new Result(null, null)};
		determineEligibility(eligible, assayIDList, layers, winnowed);
		assertThat(eligible, hasSize(0));

		NodeResult[] nodes = new NodeResult[]{new NodeResult()};
		nodes[0].uri = "uri";
		winnowed[0].nodes = nodes;
		layers[0].propURI = "propURI";
		determineEligibility(eligible, assayIDList, layers, winnowed);
		assertThat(eligible, hasSize(1));
		assertThat(eligible, contains("propURI::::uri"));

		eligible.clear();
		determineEligibility(eligible, new long[]{2L}, null, new Result[0]);
		assertThat(eligible, hasSize(20));
		assertThat(eligible, hasItem("http://www.bioassayontology.org/bao#BAO_0002855::::http://www.bioassayontology.org/bao#BAO_0000110"));

		eligible.clear();
		determineEligibility(eligible, new long[]{1234567L}, null, new Result[0]);
		assertThat(eligible, hasSize(0));
	}

	// ------------ private methods ------------

	private void assertFuzzyScore(int expected, Set<String> wordList, String label, String... altLabels)
	{
		Node node = new Node();
		node.label = label;
		if (!ArrayUtils.isEmpty(altLabels))
			node.altLabels = altLabels;

		Proposal proposal = new Proposal(null, node, 0);
		fuzzyScore(proposal, wordList);
		assertThat(proposal.matchCount, is(expected));
	}

	private void assertTallyMatch(String msg, int expected, String... wordList)
	{
		final Set<String> propBits = new HashSet<>(Arrays.asList(ABCDE, FGHIJ));
		final Set<String> valueBits = new HashSet<>(Arrays.asList(N12345, N67890));
		assertThat(msg, tallyMatches(new HashSet<>(Arrays.asList(wordList)), propBits, valueBits), is(expected));
	}
}
