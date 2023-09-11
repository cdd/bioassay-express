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
import com.cdd.bae.data.DataStore.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for com.cdd.bae.data.DataCompound
*/

public class DataCompoundTest extends TestBaseClass
{
	private static final String NEW_STRUCTURE = "new structure";

	DataStore store;
	DataCompound dataCompound;

	@BeforeEach
	public void initialize()
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/compound");
		store = mongo.getDataStore();
		Notifier notifier = mock(Notifier.class);
		store.setNotifier(notifier);
		dataCompound = new DataCompound(store);
	}

	@Test
	public void testGetCompound()
	{
		Compound compound = dataCompound.getCompound(1);
		assertThat(compound.pubchemCID, is(3232585));
		assertThat(dataCompound.getCompound(12345), is(nullValue()));

		compound = dataCompound.getCompound(compound.id);
		assertThat(compound.pubchemCID, is(3232585));
		assertThat(dataCompound.getCompound("ffffffffffffffffffffffff"), is(nullValue()));

		Compound[] compounds = dataCompound.getCompoundsWithPubChemCID(3232585);
		assertThat(compounds.length, is(1));
		assertThat(compounds[0].pubchemCID, is(3232585));

		compounds = dataCompound.getCompoundsWithPubChemSID(4237472);
		assertThat(compounds.length, is(1));
		assertThat(compounds[0].pubchemCID, is(3232585));
	}

	@Test
	public void testGetHashECFP6()
	{
		assertThat(dataCompound.getHashECFP6(1), is(2117857030));
		assertThat(dataCompound.getHashECFP6(12345), is(0));
	}

	@Test
	public void testFetchCompoundsNeedCID()
	{
		Compound[] compounds = dataCompound.fetchCompoundsNeedCID(10);
		assertThat(compounds.length, is(1));
		assertThat(compounds[0].pubchemCID, is(3232591));
	}

	@Test
	public void testFetchCompoundsNeedVaultMol()
	{
		Compound[] compounds = dataCompound.fetchCompoundsNeedVaultMol(10);
		assertThat(compounds.length, is(1));
		assertThat(compounds[0].vaultMID, is(456789L));
	}

	@Test
	public void testFetchCompoundsNeedHash()
	{
		Compound[] compounds = dataCompound.fetchCompoundsNeedHash(10);
		assertThat(compounds.length, is(2));
		long[] cids = new long[]{compounds[0].compoundID, compounds[1].compoundID};
		Arrays.sort(cids);
		assertThat(cids, is(new long[]{4, 5}));
	}

	@Test
	public void testUpdateCompound() throws IOException
	{
		assertThat(dataCompound.countTotal(), is(7));

		// craeate a new compound
		Compound compound = dataCompound.getCompound(1);
		compound.compoundID = 0;
		compound.id = null;

		dataCompound.updateCompound(compound);
		assertThat(dataCompound.countTotal(), is(8));
		verify(store.notifier, times(1)).datastoreCompoundsChanged();
		verify(store.notifier, times(1)).datastoreStructuresChanged();

		compound = dataCompound.getCompound(10000000);
		assertThat(compound.molfile, is(not(equalTo(NEW_STRUCTURE))));
		compound.molfile = NEW_STRUCTURE;
		dataCompound.updateCompound(compound);
		assertThat(dataCompound.countTotal(), is(8));
		verify(store.notifier, times(2)).datastoreCompoundsChanged();
		verify(store.notifier, times(2)).datastoreStructuresChanged();

		compound = dataCompound.getCompound(10000000);
		assertThat(compound.molfile, is(NEW_STRUCTURE));
	}

	@Test
	public void resetCompoundsEmptyVaultMol() throws IOException
	{
		assertThat(dataCompound.countWithStructures(), is(5));
		Compound compound = dataCompound.getCompound(7);
		assertThat(compound.molfile, is(""));

		dataCompound.resetCompoundsEmptyVaultMol();
		assertThat(dataCompound.countWithStructures(), is(4));
		compound = dataCompound.getCompound(7);
		assertThat(compound.molfile, is(nullValue()));
	}

	@Test
	public void testReserveCompoundPubChemSID() throws IOException
	{
		assertThat(dataCompound.countTotal(), is(7));

		long id = dataCompound.reserveCompoundPubChemSID(123456);
		assertThat(id, is(10000000L));

		Compound compound = dataCompound.getCompound(id);
		assertThat(compound.pubchemSID, is(123456));

		verify(store.notifier, times(1)).datastoreCompoundsChanged();
	}

	@Test
	public void testReserveCompoundVault() throws IOException
	{
		assertThat(dataCompound.countTotal(), is(7));

		long id = dataCompound.reserveCompoundVault(123, 456);
		assertThat(id, is(10000000L));

		Compound compound = dataCompound.getCompound(id);
		assertThat(compound.vaultID, is(123L));
		assertThat(compound.vaultMID, is(456L));

		verify(store.notifier, times(1)).datastoreCompoundsChanged();
	}

	@Test
	public void testDeleteCompound() throws IOException
	{
		assertThat(dataCompound.countTotal(), is(7));
		dataCompound.deleteCompound(getValidID());
		assertThat(dataCompound.countTotal(), is(6));
	}

	@Test
	public void testCounts()
	{
		assertThat(dataCompound.countTotal(), is(7));
		assertThat(dataCompound.countWithStructures(), is(5));
	}

	@Test
	public void testWatermark() throws IOException
	{
		assertEquals(10000000, dataCompound.getWatermarkCompound());
		assertEquals(10000000, dataCompound.nextWatermarkCompound());
		assertEquals(10000001, dataCompound.getWatermarkCompound());
	}
	
	// ------------ private methods ------------

	private String getValidID()
	{
		Compound compound = dataCompound.getCompound(1);
		return compound.id;
	}
}
