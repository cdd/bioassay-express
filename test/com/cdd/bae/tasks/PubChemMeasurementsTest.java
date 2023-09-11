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

import com.cdd.bae.tasks.PubChemMeasurements.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;

import org.json.*;
import org.junit.jupiter.api.*;
import org.mockito.*;

/*
	Test for com.cdd.bae.tasks.PubChemMeasurements
*/

public class PubChemMeasurementsTest
{
	static final private int AID = 1811;
	static final private String AIDquery = "https://pubchem.ncbi.nlm.nih.gov/rest/pug/assay/aid/1811/sids/JSON";

	// result from AIDquery
	public TestResourceFile pubchemResponseAID = new TestResourceFile("/testData/tasks/pubchemREST_AID_SID.json");
	public TestResourceFile pubchemResponseAIDshort = new TestResourceFile("/testData/tasks/pubchemREST_AID_SID_SHORT.json");
	public TestResourceFile pubchemResponseAIDempty = new TestResourceFile("/testData/tasks/pubchemREST_AID_SID_EMPTY.json");
	// result from SIDquery with post SIDpost
	public TestResourceFile pubchemResponseAID_SID = new TestResourceFile("/testData/tasks/pubchemREST_AID_SIDINFO.json");
	public TestResourceFile pubchemResponseAID_SID_1 = new TestResourceFile("/testData/tasks/pubchemREST_AID_SIDINFO_1.json");
	public TestResourceFile pubchemResponseAID_SID_2 = new TestResourceFile("/testData/tasks/pubchemREST_AID_SIDINFO_2.json");
	public TestResourceFile pubchemResponseAID_SID_3 = new TestResourceFile("/testData/tasks/pubchemREST_AID_SIDINFO_3.json");

	@Test
	public void testFetchSubstances() throws IOException
	{
		PubChemMeasurements pubChemMeasurements = Mockito.spy(new PubChemMeasurements(1234, AID));

		doReturn(pubchemResponseAID.getContent()).when(pubChemMeasurements).makeRequest(AIDquery, null, 3);
		int[] sidlist = pubChemMeasurements.fetchSubstances();
		assertEquals(3073, sidlist.length);

		doReturn(null).when(pubChemMeasurements).makeRequest(AIDquery, null, 3);
		sidlist = pubChemMeasurements.fetchSubstances();
		assertEquals(0, sidlist.length);
	}

	/* deprecated
	@Test
	public void testFetchBlock() throws IOException
	{
		PubChemMeasurements pubChemMeasurements = Mockito.spy(new PubChemMeasurements(1234, AID));
		mockFetchBlockResponse(pubChemMeasurements);

		pubChemMeasurements.fetchBlock(true, SID_LIST);
		Column[] columns = pubChemMeasurements.getColumns();
		assertEquals(10, columns.length);
		assertEquals(3, pubChemMeasurements.numRows());
		Set<Integer> sids = new HashSet<>(Arrays.stream(SID_LIST).boxed().collect(Collectors.toList()));
		for (int i = 0; i < 3; i++)
		{
			Row row = pubChemMeasurements.getRow(i);
			assertTrue(sids.contains(row.sid));
		}
	}*/

	/* deprecated
	@Test
	public void testDownload() throws IOException
	{
		PubChemMeasurements pubChemMeasurements = Mockito.spy(new PubChemMeasurements(1234, AID));

		doReturn(pubchemResponseAIDshort.getContent()).when(pubChemMeasurements).makeRequest(AIDquery, null, 3);
		mockFetchBlockResponse(pubChemMeasurements);

		pubChemMeasurements.download();
		Column[] columns = pubChemMeasurements.getColumns();
		assertEquals(10, columns.length);
		assertEquals(3, pubChemMeasurements.numRows());
		for (int i = 0; i < 3; i++)
		{
			Row row = pubChemMeasurements.getRow(i);
			assertEquals(SID_LIST[i], row.sid);
		}

		pubChemMeasurements.download(1);
		columns = pubChemMeasurements.getColumns();
		assertEquals(10, columns.length);
		assertEquals(3, pubChemMeasurements.numRows());
		Set<Integer> sids = new HashSet<>(Arrays.stream(SID_LIST).boxed().collect(Collectors.toList()));
		for (int i = 0; i < 3; i++)
		{
			Row row = pubChemMeasurements.getRow(i);
			assertTrue(sids.contains(row.sid));
		}
	}*/

	@Test
	public void testEmpty() throws IOException
	{
		PubChemMeasurements pubChemMeasurements = Mockito.spy(new PubChemMeasurements(1234, AID));
		doReturn(pubchemResponseAIDempty.getContent()).when(pubChemMeasurements).makeRequest(AIDquery, null, 3);
		pubChemMeasurements.download();
		assertEquals(0, pubChemMeasurements.numRows());
	}

	@Test
	public void testRetryFetch() throws IOException
	{
		// This test basically fails the fetching from pubchem every time and repeats with smaller
		// and smaller blocksizes
		// until it gives up. I would have preferred failing once and then succeeding, but couldn't
		// get this to work
		// with mockito.
		PubChemMeasurements pubChemMeasurements = Mockito.spy(new PubChemMeasurements(1234, AID));
		doReturn(pubchemResponseAID.getContent()).when(pubChemMeasurements).makeRequest(AIDquery, null, 3);
		doThrow(new IOException("Mocked")).when(pubChemMeasurements).makeRequest(anyString(), anyString(), anyInt());

		Assertions.assertThrows(IOException.class, () -> pubChemMeasurements.download());
	}


	@Test
	public void testParseData()
	{
		PubChemMeasurements pubChemMeasurements = new PubChemMeasurements(1234, 123);
		Row row = new Row();
		Column[] columns = new Column[5];
		for (int i = 0; i < 5; i++) columns[i] = new Column();
		columns[0].type = PubChemMeasurements.Type.FLOAT;
		columns[1].type = PubChemMeasurements.Type.INT;
		columns[2].type = PubChemMeasurements.Type.BOOL;
		columns[3].type = PubChemMeasurements.Type.STRING;
		columns[4].type = PubChemMeasurements.Type.BOOL;
		
		row.data = new Datum[columns.length];
		pubChemMeasurements.setColumns(columns);

		JSONArray dlist = new JSONArray();
		dlist.put(new JSONObject("{\"tid\":1, \"value\":{ \"fval\":3.14}}"));
		dlist.put(new JSONObject("{\"tid\":2, \"value\":{ \"ival\":1234}}"));
		dlist.put(new JSONObject("{\"tid\":3, \"value\":{ \"bval\":true}}"));
		dlist.put(new JSONObject("{\"tid\":4, \"value\":{ \"sval\":\"abcde\"}}"));
		dlist.put(new JSONObject("{\"tid\":5, \"value\":{ \"bval\":false}}"));
		dlist.put(new JSONObject("{\"tid\":-1, \"value\":{ \"sval\":\"incorrect\"}}"));
		dlist.put(new JSONObject("{\"tid\":11, \"value\":{ \"sval\":\"incorrect\"}}"));

		pubChemMeasurements.parseData(dlist, row);

		assertEquals(row.data[0].value, 3.14, 0.01);
		assertEquals(row.data[1].value, 1234, 0.01);
		assertEquals(row.data[2].value, 1, 0.01);
		assertEquals(row.data[3].str, "abcde");
		assertEquals(row.data[4].value, 0, 0.01);
	}

	@Test
	public void testUnitsToString()
	{
		assertEquals("ppt", Units.unitsToString(1));
		assertEquals("mM", Units.unitsToString(4));
		assertEquals("M", Units.unitsToString(14));
		assertEquals("mL/min/kg", Units.unitsToString(23));
		
		assertEquals("", Units.unitsToString(254)); // None
		assertEquals("?", Units.unitsToString(255)); // Unspecified
		
		// return the representation for unspecified if pubchem unit not implemented / known
		assertEquals(Units.UNSPECIFIED.toString(), Units.unitsToString(250));
	}
}
