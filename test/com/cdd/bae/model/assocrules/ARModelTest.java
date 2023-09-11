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

import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.model.dictionary.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for ARModel
*/

public class ARModelTest extends TestClassConfiguration
{
	private ARModel assocModel = null;

	@BeforeEach
	public void prepare()
	{
		// learn model
//		ARModel assocModel;
		try {assocModel = ARModel.loadDefaultModel();}
		catch (IOException ioe) {}
	}

	@Test
	public void testLearn() throws IOException
	{
		if (assocModel == null) 
		{
			Util.writeln("WARNING: The file with association rules is missing");
			return;
		}

		Set<String> items = new HashSet<>();
		items.add("bao:BAO_0140031");

		ARModel.ARTree model = assocModel.model;
		List<ARModel.RulePrediction> result = model.predict(items);
		assertThat(result, not(empty()));
		
		// save model
		String filename = createFile("model.gz").getAbsolutePath();
		assocModel.saveModel(filename, false);
		
		// load it again
		ARModel assocModel2 = ARModel.fromFile(filename);
		ARModel.ARTree model2 = assocModel2.model;
		List<ARModel.RulePrediction> result2 = model2.predict(items);
		
		assertEquals(new HashSet<>(result), new HashSet<>(result2));
	}
	
	@Test
	public void testLoadRules()
	{
		if (assocModel == null) 
		{
			Util.writeln("WARNING: The file with association rules is missing");
			return;
		}

		ARModel.ARTree model = assocModel.model;

		Set<String> items = new HashSet<>();
		items.clear();
		items.add("bao:BAO_0140031");
		List<ARModel.RulePrediction> result = model.predict(items);
		assertThat(result, not(empty()));

		items.clear();
		items.addAll(Arrays.asList("bao:BAO_0000150", "bao:BAO_0002762"));
		result = model.predict(items);
		assertThat(result, not(empty()));
	}

	@Test
	public void testPredict()
	{
		if (assocModel == null) 
		{
			Util.writeln("WARNING: The file with association rules is missing");
			return;
		}

		List<Annotation> annotations = new ArrayList<>();
		Map<String, List<ScoredHit>> results = assocModel.predict(annotations.toArray(new Annotation[0]));
		assertEquals(0, results.size());

		annotations.add(new Annotation("propURI", ModelSchema.expandPrefix("bao:BAO_0000150")));
		results = assocModel.predict(annotations.toArray(new Annotation[0]));
		// System.out.println(results);

		annotations.add(new Annotation("propURI", ModelSchema.expandPrefix("bao:BAO_0003013")));
		results = assocModel.predict(annotations.toArray(new Annotation[0]));
		// System.out.println(results);
	}

	@Test
	public void testBuildTree()
	{
		ARModel.ARTree tree = new ARModel.ARTree();
		List<ARLearner.Rule> rules = getRules();
		tree.addRules(rules);

		for (ARLearner.Rule rule : rules)
		{
			List<ARModel.RulePrediction> predictions = tree.predict(new TreeSet<>(Arrays.asList(rule.lhs.items)));
			predictions.sort(ARModel.RulePrediction.byConfidence);
			boolean found = false;
			String expected = rule.rhs.getKey();
			float prevConfidence = predictions.get(0).confidence;
			for (ARModel.RulePrediction prediction : predictions)
			{
				if (prediction.label.equals(expected)) found = true;
				assertTrue(prevConfidence >= prediction.confidence, "predictions are sorted in descending order");
				prevConfidence = prediction.confidence;
			}
			assertTrue(found, "rule rhs was found");
		}

		// handles empty item sets
		Set<String> items = new HashSet<>();
		List<ARModel.RulePrediction> predictions = tree.predict(items);
		assertEquals(0, predictions.size());

		// and if none of the items is known
		items.add("new");
		predictions = tree.predict(items);
		assertEquals(0, predictions.size());
	}

	private List<ARLearner.Rule> getRules()
	{
		ARLearner learner = new ARLearner(2, 10);
		List<ARLearner.ItemSet> itemSets = learner.generateItemSets(getTransactions());
		return learner.generateRules(itemSets);
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
