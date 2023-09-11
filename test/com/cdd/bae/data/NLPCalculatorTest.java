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

package com.cdd.bae.data;

import com.cdd.bae.config.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.data.NLPCalculator
 */

public class NLPCalculatorTest
{
	public TestResourceFile assayDescription = new TestResourceFile("/testData/nlp/AssayDescription.txt");

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration();
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
	}

	@Test
	public void testRecalculate() throws IOException
	{
		NLPCalculator nlpCalculator = new NLPCalculator(assayDescription.getContent());
		String[] blocks = nlpCalculator.calculate();
		assertEquals(69, blocks.length);
		// Blacklist blocks are not included
		assertFalse(Arrays.asList(blocks).contains("(RP on)"));
		// but other more interesting
		assertTrue(Arrays.asList(blocks).contains("(JJ carcinogenic)"));

		// untested - but redoing the same calculation is ok as we store previous texts in a cache
		blocks = nlpCalculator.calculate();
		assertEquals(69, blocks.length);
		// Blacklist blocks are not included
		assertFalse(Arrays.asList(blocks).contains("(RP on)"));
		// but other more interesting
		assertTrue(Arrays.asList(blocks).contains("(JJ carcinogenic)"));
	}
	
	@Test
	public void testLongLines()
	{
		String text = "When she finally was able to speak to a doctor on the phone, the doctor told her to wait in line outside a local hospital to get tested, but she didn't have a mask for her or her son, and she didn't want to infect others in line, so she stayed home. She and her son are now in good health, but she said the episode left her wondering how prepared German society is for this pandemic.";
		for (int n = 0; n < 3000; n++) text = text + " 1234";
		text = text + ". \"I know the diagnostics community in Germany a bit,\" Drosten said. \"My feeling is that actually the supply of tests is still good. And of course, our epidemic is now also very much up-ramping and we will lose track here, too.\"";
		NLPCalculator nlpCalculator = new NLPCalculator(text);
		String[] blocks = nlpCalculator.calculate();
		assertEquals(158, blocks.length);
	}
}
