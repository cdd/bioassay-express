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

package com.hankcs.algorithm;

import com.cdd.bae.model.hankcs.*;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.*;

import org.junit.jupiter.api.*;

public class AhoCorasickDoubleArrayTrieTest
{

	@Test
	public void testAhoCorasickAlgorithm()
	{
		// Collect test data set
		TreeMap<String, String> map = new TreeMap<String, String>();
		String[] keyArray = new String[]{"hers", "his", "she", "he"};
		for (String key : keyArray)
			map.put(key, key);

		// Build an AhoCorasickDoubleArrayTrie
		AhoCorasickDoubleArrayTrie<String> acdat = new AhoCorasickDoubleArrayTrie<String>();
		acdat.build(map);

		// Test it
		final String text = "uhers";
		List<AhoCorasickDoubleArrayTrie<String>.Hit<String>> wordList = acdat.parseText(text);
		Set<String> hits = wordList.stream().map(h -> h.value).collect(Collectors.toSet());
		Set<String> expected = new HashSet<String>(Arrays.asList(new String[]{"he", "hers"}));
		assertEquals(expected, hits);
		
	}
}
