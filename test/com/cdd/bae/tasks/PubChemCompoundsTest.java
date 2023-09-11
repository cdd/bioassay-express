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

package com.cdd.bae.tasks;

import com.cdd.bae.data.*;
import com.cdd.testutil.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;

import org.junit.jupiter.api.*;
import org.mockito.*;
import org.openscience.cdk.exception.*;

/*
	Test for com.cdd.bae.tasks.PubChemCompounds
 */

public class PubChemCompoundsTest
{
	// result from query
	// https://pubchem.ncbi.nlm.nih.gov/rest/pug/substance/sid/223737042,49855734,317231311/SDF
	public TestResourceFile pubchemResponse = new TestResourceFile("/testData/tasks/pubchemREST_SID.sdf");

	@Test
	public void testGetPubchemURL()
	{
		DataStore.Compound[] compounds = getTestCompounds();
		PubChemCompounds pubChemCompounds = new PubChemCompounds(compounds);

		String url = pubChemCompounds.getPubChemURL();
		assertThat(url, not(containsString(" ")));
		for (DataStore.Compound compound : compounds)
		{
			assertThat(url, containsString(String.valueOf(compound.pubchemSID)));
		}
	}

	@Test
	public void testDownload() throws IOException, CDKException
	{

		DataStore.Compound[] compounds = getTestCompounds();
		PubChemCompounds orgPubChemCompounds = new PubChemCompounds(compounds);
		PubChemCompounds pubChemCompounds = Mockito.spy(orgPubChemCompounds);
		doReturn(pubchemResponse.getContent()).when(pubChemCompounds).makeRequest(anyString());

		pubChemCompounds.download();
		for (DataStore.Compound compound : compounds)
		{
			assertEquals(241, compound.pubchemCID);
			assertEquals(compounds[0].hashECFP6, compound.hashECFP6);
			assertThat(compound.molfile, containsString("V2000"));
			assertThat(compound.molfile, containsString("CDK"));
		}

		doThrow(new IOException()).when(pubChemCompounds).makeRequest(anyString());
		try
		{
			pubChemCompounds.download();
		}
		catch (IOException e)
		{
			assertThat(e.getMessage(), containsString("Failed to download"));
		}
	}

	@Test
	public void testGetCompound() throws IOException
	{
		DataStore.Compound[] compounds = getTestCompounds();
		PubChemCompounds pubChemCompounds = new PubChemCompounds(compounds);
		for (DataStore.Compound compound : compounds)
		{
			int pubchemSID = compound.pubchemSID;
			DataStore.Compound found = pubChemCompounds.getCompound(pubchemSID);
			assertEquals(pubchemSID, found.pubchemSID);
		}

		Assertions.assertThrows(IOException.class, () -> pubChemCompounds.getCompound(111));
	}

	@Test
	public void testParseSDF() throws CDKException, IOException
	{
		DataStore.Compound[] compounds = getTestCompounds();
		PubChemCompounds pubChemCompounds = new PubChemCompounds(compounds);

		pubChemCompounds.parseSDF(pubchemResponse.getContent());
		for (DataStore.Compound compound : compounds)
		{
			assertEquals(241, compound.pubchemCID);
			assertEquals(compounds[0].hashECFP6, compound.hashECFP6);
			assertThat(compound.molfile, containsString("V2000"));
			assertThat(compound.molfile, containsString("CDK"));
		}
	}

	@Test
	public void testGetStandardizedPubchemString()
	{
		assertEquals(241, PubChemCompounds.getStandardizedPubChemSID("241 1"));
		assertEquals(241, PubChemCompounds.getStandardizedPubChemSID("123 0\n241 1"));
		assertEquals(241, PubChemCompounds.getStandardizedPubChemSID("123 0\n241 1\n456 3"));
		assertEquals(241, PubChemCompounds.getStandardizedPubChemSID("123 1 more entries\n241 1\n456 3"));
		assertEquals(0, PubChemCompounds.getStandardizedPubChemSID("123 0\n241 3"));
		assertEquals(0, PubChemCompounds.getStandardizedPubChemSID(null));
	}

	private DataStore.Compound[] getTestCompounds()
	{
		// Three different substances that all have the benzene structure (CID = 241)
		DataStore.Compound[] compounds = new DataStore.Compound[3];
		compounds[0] = new DataStore.Compound();
		compounds[0].pubchemSID = 223737042;
		compounds[1] = new DataStore.Compound();
		compounds[1].pubchemSID = 49855734;
		compounds[2] = new DataStore.Compound();
		compounds[2].pubchemSID = 317231311;
		return compounds;
	}
}
