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

package com.cdd.bae.model;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.model.dictionary.*;
import com.cdd.bae.tasks.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for DictionaryPredict
*/

public class DictionaryPredictTest
{
	@BeforeEach
	public void setup() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
	}

	@Test
	public void testPrioritisation() throws IOException
	{
		// this test depends on the underlying ontology, should this change adapt the outcome as necessary
		DictionaryPredict predictor = new DictionaryPredict();

		List<ScoredHit> result = predictor.getPredictionHits(new String[]{"text cho cell text", "text hek293. cho cells. text\ncho cell"}).get(PubChemAssays.CELLLINE_URI);
		assertEquals(2, result.size());
		result.sort((h1, h2) -> h1.hit.label.compareTo(h2.hit.label));
		// both hits are found in the last string, mappings are returned for both
		assertFound(result.get(0), "CHO cell", new int[]{13, 29}, new int[]{21, 37});
		assertFound(result.get(1), "HEK-293", new int[]{5}, new int[]{11});

		result = predictor.getPredictionHits(new String[]{"hek293 cho cell", "text \ncho cells\tcho cell text"}).get(PubChemAssays.CELLLINE_URI);
		assertEquals(2, result.size());
		result.sort((h1, h2) -> h1.hit.label.compareTo(h2.hit.label));
		assertFound(result.get(0), "CHO cell", new int[]{6, 16}, new int[]{14, 24});
		assertFound(result.get(1), "HEK-293", new int[0], new int[0]);
	}

	private void assertFound(ScoredHit hit, String text, int[] begin, int[] end)
	{
		assertThat(hit.toString(), containsString(text));
		if (begin.length == 0)
		{
			assertNull(hit.begin);
			assertNull(hit.end);
		}
		else
		{
			assertArrayEquals(begin, hit.begin);
			assertArrayEquals(end, hit.end);
		}
	}
}
