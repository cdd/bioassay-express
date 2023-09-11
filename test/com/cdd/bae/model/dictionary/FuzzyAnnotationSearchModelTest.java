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

package com.cdd.bae.model.dictionary;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.tasks.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for AnnotationSearchModel
*/

public class FuzzyAnnotationSearchModelTest
{

	@BeforeEach
	public void setup() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
	}

	@Test
	public void testFuzzyScore()
	{
		assertEquals(1, FuzzyAnnotationSearchModel.fuzzyScore("Workshop", "w"));
		assertEquals(2, FuzzyAnnotationSearchModel.fuzzyScore("Workshop", "ws"));
		assertEquals(4, FuzzyAnnotationSearchModel.fuzzyScore("Workshop", "wo"));
		assertEquals(5, FuzzyAnnotationSearchModel.fuzzyScore("Workshop", "wos"));
		assertEquals(8, FuzzyAnnotationSearchModel.fuzzyScore("Workshop", "wosh"));
		assertEquals(17, FuzzyAnnotationSearchModel.fuzzyScore("Workshop", "wrkshop"));
		assertEquals(22, FuzzyAnnotationSearchModel.fuzzyScore("Workshop", "workshop"));
		assertEquals(22, FuzzyAnnotationSearchModel.fuzzyScore("WORKSHOP", "workshop"));
		assertEquals(10, FuzzyAnnotationSearchModel.fuzzyScore("WORKSHOP", "work"));
		assertEquals(10, FuzzyAnnotationSearchModel.fuzzyScore("WORKSHOP", "shop"));
		assertEquals(20, FuzzyAnnotationSearchModel.fuzzyScore("WORKSHOP", new String[] {"work", "shop"}));
		assertEquals(10, FuzzyAnnotationSearchModel.fuzzyScore("WORKSHOP", new String[] {"wrk", "shp"}));
		assertEquals(12, FuzzyAnnotationSearchModel.fuzzyScore("WORKSHOP", "wrkshp"));
	}

	@Test
	public void testSearch1() throws IOException
	{
		FuzzyAnnotationSearchModel search = new FuzzyAnnotationSearchModel();

		List<?> result = search.search("ce", "unknown URI");
		assertTrue(result.isEmpty());

		result = search.search("chlang", PubChemAssays.CELLLINE_URI);
		assertFalse(result.isEmpty());
	}
}
