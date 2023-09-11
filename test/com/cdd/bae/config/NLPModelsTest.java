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

package com.cdd.bae.config;

import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.junit.jupiter.api.*;

public class NLPModelsTest
{
	static final File NLP_DIRECTORY = new File(TestConfiguration.getProductionConfiguration() + "/nlp");

	public TestResourceFile assayDescription = new TestResourceFile("/testData/nlp/AssayDescription.txt");

	@Test
	public void testNlpModels() throws IOException
	{
		// We require the NLP configuration
		if (!NLP_DIRECTORY.isDirectory()) return;

		NLPModels nlpModels = new NLPModels(NLP_DIRECTORY.getAbsolutePath());
		assertNotNull(nlpModels.sentenceModel);
		assertFalse(nlpModels.hasChanged());

		nlpModels.reload();

		try
		{
			nlpModels.load(new String[]{"incorrect"});
			throw new AssertionError("Exception expected");
		}
		catch (IllegalArgumentException e)
		{ /* Exception expected */ }
	}
}
