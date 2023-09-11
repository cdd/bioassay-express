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

import com.cdd.bae.data.DataObject.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;

import org.junit.jupiter.api.*;
import org.mockito.*;

public class DataProvisionalTest
{
	DataProvisional dataProvisional;

	@BeforeEach
	public void initialize()
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		dataProvisional = new DataProvisional(mongo.getDataStore());
		dataProvisional = Mockito.spy(dataProvisional);
	}

	@Test
	public void testGetNextSequence() throws IOException
	{
		long seq = dataProvisional.getNextSequence();
		assertThat(dataProvisional.getNextSequence(), is(seq + 1));
	}

	@Test
	public void testGetNextURISequence() throws IOException
	{
		long seq = dataProvisional.getNextURISequence();
		assertThat(dataProvisional.getNextURISequence(), is(seq + 1));
	}

	@Test
	public void testProvisionalHandling() throws IOException
	{
		assertThat(dataProvisional.countProvisionals(), is(0));

		// insert
		Provisional prov = DataStoreSupport.makeProvisional();
		dataProvisional.updateProvisional(prov);
		assertThat(dataProvisional.countProvisionals(), is(1));

		Provisional[] provs = dataProvisional.fetchAllTerms();
		assertThat(provs.length, is(1));

		// update
		dataProvisional.updateProvisional(provs[0]);
		assertThat(dataProvisional.countProvisionals(), is(1));

		long provisionalID = provs[0].provisionalID;
		Provisional prov2 = dataProvisional.getProvisional(provisionalID);
		assertThat(prov2.provisionalID, is(provisionalID));
		assertThat(dataProvisional.getProvisional(provisionalID + 1), is(nullValue()));

		String uri = provs[0].uri;
		prov2 = dataProvisional.getProvisionalURI(uri);
		assertThat(prov2.provisionalID, is(provisionalID));
		assertThat(prov2.uri, is(uri));
		assertThat(dataProvisional.getProvisionalURI(uri + "x"), is(nullValue()));

		assertThat(dataProvisional.deleteProvisional(provisionalID), is(true));
		assertThat(dataProvisional.deleteProvisional(provisionalID), is(false));
	}

	@Test
	public void testChildParentTerms()
	{
		assertThat(dataProvisional.countProvisionals(), is(0));
		dataProvisional.updateProvisional(DataStoreSupport.makeProvisional());
		Provisional prov = dataProvisional.fetchAllTerms()[0];

		assertThat(dataProvisional.getParentTerm(prov.uri), is(prov.parentURI));
		Provisional[] childTerms = dataProvisional.fetchChildTerms(prov.parentURI);
		assertThat(childTerms.length, is(1));
		assertThat(childTerms[0].uri, is(prov.uri));

		assertThat(dataProvisional.getParentTerm("unkonwn"), is(nullValue()));
		assertThat(dataProvisional.fetchChildTerms("unkonwn").length, is(0));
	}

	// ------------ private methods ------------

}
