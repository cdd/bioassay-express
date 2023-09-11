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
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;

import org.json.*;
import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.data.AssayJSON
*/

public class AssayJSONTest extends TestBaseClass
{
	private DataAssay dataAssay;

	@BeforeEach
	public void initialize() throws ConfigurationException, IOException
	{
		TestUtilities.ensureOntologies();
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
		Common.setProvCache(new ProvisionalCache());
		
		dataAssay = new DataAssay(mongo.getDataStore());
	}

	@Test
	public void testSerialiseDeserialiseAssay()
	{
		// create an assay
		Assay assay = dataAssay.getAssay(2);

		JSONObject json = AssayJSON.serialiseAssay(assay);

		Assay roundtrip = AssayJSON.deserialiseAssay(json.toString());
		if (roundtrip == null)
		{
			Util.writeln(json.toString());
		}		
		assertThat(roundtrip, is(not(nullValue())));
		if (roundtrip != null) assertThat(assay.assayID, is(roundtrip.assayID));
	}

	@Test
	public void testSerialiseDeserialiseCollection() throws IOException
	{
		// create an assay
		Assay[] assays = new Assay[]{dataAssay.getAssay(2), dataAssay.getAssay(101)};
		File file = createFile("filename");

		AssayJSON.serialiseCollection(file, assays);

		Assay[] roundtrip = AssayJSON.deserialiseCollection(file);
		assertEquals(assays.length, roundtrip.length);
		for (int i = 0; i < assays.length; i++)
		{
			assertEquals(assays[i].assayID, roundtrip[i].assayID);
		}
	}
}
