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

package com.cdd.bae.model.assocrules;

import com.cdd.bae.model.assocrules.ARLearner.*;
import com.cdd.bae.model.assocrules.ARModel.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.*;

import org.junit.jupiter.api.*;

/*
	Test for ARLearner
*/

public class ARLearnerTest extends TestClassConfiguration
{

	@Test
	public void testGenerateItemSets()
	{
		ARLearner learner = new ARLearner(3, 10);

		List<Set<String>> transactions = getTransactions();
		List<ItemSet> initItemset = learner.initialItemSets(transactions);
		assertEquals(4, initItemset.size());
		Map<String, ItemSet> result = new HashMap<>();
		for (ItemSet itemset : initItemset)
			result.put(String.join("::", itemset.items), itemset);
		assertEquals(3, result.get("1").support);
		assertEquals(6, result.get("2").support);
		assertEquals(4, result.get("3").support);
		assertEquals(5, result.get("4").support);

		List<ItemSet> itemSets = learner.generateItemSets(transactions);
		result = itemSets.stream().collect(Collectors.toMap(ItemSet::getKey, p -> p));
		assertEquals(8, itemSets.size());
		assertEquals(3, result.get("1::2").support);
		assertEquals(3, result.get("2::3").support);
		assertEquals(4, result.get("2::4").support);
		assertEquals(3, result.get("3::4").support);

		learner = new ARLearner(2, 10);
		transactions = getTransactions();
		itemSets = learner.generateItemSets(transactions);
		result = itemSets.stream().collect(Collectors.toMap(ItemSet::getKey, p -> p));
		assertEquals(11, result.size());
		assertEquals(3, result.get("1").support);
		assertEquals(6, result.get("2").support);
		assertEquals(4, result.get("3").support);
		assertEquals(5, result.get("4").support);
		assertEquals(3, result.get("1::2").support);
		assertEquals(2, result.get("1::4").support);
		assertEquals(3, result.get("2::3").support);
		assertEquals(4, result.get("2::4").support);
		assertEquals(3, result.get("3::4").support);
		assertEquals(2, result.get("1::2::4").support);
		assertEquals(2, result.get("2::3::4").support);
	}

	@Test
	public void testGenerateRules()
	{
		ARLearner learner = new ARLearner(2, 10);
		List<ItemSet> itemSets = learner.generateItemSets(getTransactions());
		List<Rule> rules = learner.generateRules(itemSets);
		assertEquals(11, rules.size());
		ARModel assocModel = ARModel.fromRules(rules);
		assertRule(assocModel, "1", "2,4");
		assertRule(assocModel, "1/2", "4");
		assertRule(assocModel, "1/4", "2");
		assertRule(assocModel, "2", "4");
		assertRule(assocModel, "2/3", "4");
		assertRule(assocModel, "3", "2,4");
		assertRule(assocModel, "3/4", "2");
		assertRule(assocModel, "4", "2,3");
		// System.out.println(assocModel);

		learner = new ARLearner(1, 10);
		itemSets = learner.generateItemSets(getTransactions());
		rules = learner.generateRules(itemSets);
		assertEquals(15, rules.size());
		assocModel = ARModel.fromRules(rules);
		assertRule(assocModel, "1", "2,4");
		assertRule(assocModel, "1/2", "4");
		assertRule(assocModel, "1/2/3", "4");
		assertRule(assocModel, "1/3", "2,4");
		assertRule(assocModel, "1/3/4", "2");
		assertRule(assocModel, "1/4", "2");
		assertRule(assocModel, "2", "4");
		assertRule(assocModel, "2/3", "4");
		assertRule(assocModel, "3", "2,4");
		assertRule(assocModel, "3/4", "2");
		assertRule(assocModel, "4", "2,3");
		// System.out.println(assocModel);
	}

	@Test
	public void testAddString()
	{
		String[] s = {"a"};
		assertArrayEquals("a,b".split(","), ARLearner.addString(s, "b"));
		assertArrayEquals("a".split(","), s);
		s = new String[]{"a", "b"};
		assertArrayEquals("a,b,b".split(","), ARLearner.addString(s, "b"));
		assertArrayEquals("a,b".split(","), s);
	}

	private void assertRule(ARModel assocModel, String prefix, String expected)
	{
		Set<String> sExpected = new HashSet<>(Arrays.asList(expected.split(",")));
		List<RulePrediction> result = assocModel.model.lookup(prefix.split("/"));
		Set<String> sActual = result.stream().map(r -> r.label).collect(Collectors.toSet());
		assertEquals(sExpected, sActual);
	}

	private List<Set<String>> getTransactions()
	{
		List<Set<String>> transactions = new ArrayList<>();
		transactions.add(new HashSet<String>(Arrays.asList("1", "2", "3", "4")));
		transactions.add(new HashSet<String>(Arrays.asList("1", "2", "4")));
		transactions.add(new HashSet<String>(Arrays.asList("1", "2")));
		transactions.add(new HashSet<String>(Arrays.asList("2", "3", "4")));
		transactions.add(new HashSet<String>(Arrays.asList("2", "3")));
		transactions.add(new HashSet<String>(Arrays.asList("3", "4")));
		transactions.add(new HashSet<String>(Arrays.asList("2", "4")));
		return transactions;
	}
}
