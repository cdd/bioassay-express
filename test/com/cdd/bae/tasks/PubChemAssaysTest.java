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

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.tasks.PubChemMeasurements.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.SchemaTree.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;
import org.json.*;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.slf4j.*;

/*
	Test for com.cdd.bae.tasks.PubChemAssays
*/

public class PubChemAssaysTest extends TestBaseClass
{
	private PubChemAssays pubChemAssays;
	private PubChemMeasurements pubChemMeasurements;
	private DataAssay dataAssay;
	private DataMeasure dataMeasure;
	private DataMisc dataMisc;

	public TestResourceFile pubchemAssaysZip = new TestResourceFile("/testData/tasks/pubchem_bioassay.zip");

	@BeforeEach
	public void prepareMocks() throws IOException
	{
		DataStore store = mock(DataStore.class);
		DataCompound dataCompound = mock(DataCompound.class);
		dataAssay = mock(DataAssay.class);
		dataMeasure = mock(DataMeasure.class);
		dataMisc = mock(DataMisc.class);

		when(store.compound()).thenReturn(dataCompound);
		when(store.assay()).thenReturn(dataAssay);
		when(store.measure()).thenReturn(dataMeasure);
		when(store.misc()).thenReturn(dataMisc);
		when(dataAssay.uniqueIDFromAssayID(any()))
				.thenReturn(new String[]{"pubchemAID:12", "pubchemAID:2014", "unknown:2014", "ucsfAID:1234"});
		when(dataCompound.reserveCompoundPubChemSID(1)).thenReturn((long)1111);
		when(dataCompound.reserveCompoundPubChemSID(2)).thenReturn((long)2222);

		doNothing().when(dataMeasure).deleteMeasurementsForAssay(anyLong());

		doNothing().when(dataMisc).submitLoadedFile(anyString());
		doNothing().when(dataMisc).submitLoadedFile(anyString());

		when(dataAssay.uniqueIDFromAssayID(any()))
				.thenReturn(new String[]{"pubchemAID:12", "pubchemAID:2014", "unknown:2014", "ucsfAID:1234"});

		pubChemMeasurements = mock(PubChemMeasurements.class);
		doNothing().when(pubChemMeasurements).download(anyInt());

		// Mocking
		pubChemAssays = Mockito.spy(new PubChemAssays());
		pubChemAssays.store = store;
		pubChemAssays.logger = TestUtilities.mockLogger();

	}

	@Test
	public void testPubChemAssays()
	{
		PubChemAssays pubChemAssays = new PubChemAssays();
		assertEquals(pubChemAssays, PubChemAssays.main());
	}

	@Test
	public void testFillMeasurements()
	{
		List<Row> rows = new ArrayList<>();
		DataStore.Measurement measure = new DataStore.Measurement();

		pubChemAssays.fillMeasurements(measure, rows, 1);
		assertEquals(0, measure.compoundID.length);
		assertEquals(0, measure.value.length);
		assertEquals(0, measure.relation.length);

		rows = getTestRows(true, false);
		pubChemAssays.fillMeasurements(measure, rows, 0);
		assertEquals(rows.size(), measure.compoundID.length);
		assertEquals(rows.size(), measure.value.length);
		assertEquals(rows.size(), measure.relation.length);

		assertEquals(1111, measure.compoundID[0]);
		assertEquals(1.1, measure.value[0].doubleValue(), 0.001);
		assertEquals(2222, measure.compoundID[1]);
		assertEquals(2.1, measure.value[1].doubleValue(), 0.001);

		pubChemAssays.fillMeasurements(measure, rows, 1);
		assertEquals(1.2, measure.value[0].doubleValue(), 0.001);
		assertEquals(2.2, measure.value[1].doubleValue(), 0.001);

		pubChemAssays.fillMeasurements(measure, rows, -2);
		assertEquals(1.0, measure.value[0].doubleValue(), 0.001);
		assertEquals(1.0, measure.value[1].doubleValue(), 0.001);

		pubChemAssays.fillMeasurements(measure, rows, -1);
		assertEquals(1.0, measure.value[0].doubleValue(), 0.001);
		assertEquals(0.0, measure.value[1].doubleValue(), 0.001);
	}

	@Test
	public void testParseMeasurements() throws IOException, ConfigurationException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		
		TestResourceFile pubchem238512 = new TestResourceFile("/testData/tasks/pubchemFTP_AID238512.json");
		try (InputStream istr = pubchem238512.getAsStream())
		{
			PubChemMeasurements measure = new PubChemMeasurements(istr);
			PubChemMeasurements.Column[] columns = measure.getColumns();
			assertEquals(columns.length, 6);
			assertEquals(measure.numRows(), 4);

			assertEquals(columns[0].name, "PubChem Standard Value");
			assertEquals(columns[0].type, PubChemMeasurements.Type.FLOAT);
			assertEquals(columns[0].units, PubChemMeasurements.Units.MICROMOLAR);
			assertTrue(columns[0].activeColumn);

			assertEquals(columns[1].name, "Standard Type");
			assertEquals(columns[1].type, PubChemMeasurements.Type.STRING);
			assertEquals(columns[1].units, PubChemMeasurements.Units.NONE);
			assertFalse(columns[1].activeColumn);

			assertEquals(columns[2].name, "Standard Relation");
			assertEquals(columns[2].type, PubChemMeasurements.Type.STRING);
			assertEquals(columns[2].units, PubChemMeasurements.Units.NONE);
			assertFalse(columns[2].activeColumn);

			assertEquals(columns[3].name, "Standard Value");
			assertEquals(columns[3].type, PubChemMeasurements.Type.FLOAT);
			assertEquals(columns[3].units, PubChemMeasurements.Units.NONE);
			assertFalse(columns[3].activeColumn);

			assertEquals(columns[4].name, "Standard Units");
			assertEquals(columns[4].type, PubChemMeasurements.Type.STRING);
			assertEquals(columns[4].units, PubChemMeasurements.Units.NONE);
			assertFalse(columns[4].activeColumn);

			assertEquals(columns[5].name, "Data Validity Comment");
			assertEquals(columns[5].type, PubChemMeasurements.Type.STRING);
			assertEquals(columns[5].units, PubChemMeasurements.Units.NONE);
			assertFalse(columns[5].activeColumn);
			
			PubChemMeasurements.Row row = measure.getRow(0);
			assertEquals(row.sid, 103446806);
			assertEquals(row.outcome, Outcome.UNSPECIFIED);
			assertEquals(row.data[0].value, 12000, 0.1);
			assertEquals(row.data[1].str, "Ki");
			assertEquals(row.data[2].str, "=");
			assertEquals(row.data[3].value, 1.2E7, 1);
			assertEquals(row.data[4].str, "nM");
			assertEquals(row.data[5].str, "Outside typical range");
			
			row = measure.getRow(1);
			assertEquals(row.sid, 103453412);
			assertEquals(row.outcome, Outcome.ACTIVE);
			assertEquals(row.data[0].value, 9.4, 0.1);
			assertEquals(row.data[1].str, "Ki");
			assertEquals(row.data[2].str, "=");
			assertEquals(row.data[3].value, 9400.0, 1);
			assertEquals(row.data[4].str, "nM");
			assertNull(row.data[5]);
			
			row = measure.getRow(2);
			assertEquals(row.sid, 103453459);
			assertEquals(row.outcome, Outcome.UNSPECIFIED);
			assertEquals(row.data[0].value, 780.0, 0.1);
			assertEquals(row.data[1].str, "Ki");
			assertEquals(row.data[2].str, "=");
			assertEquals(row.data[3].value, 780000.0, 1);
			assertEquals(row.data[4].str, "nM");
			assertEquals(row.data[5].str, "Outside typical range");
			
			row = measure.getRow(3);
			assertEquals(row.sid, 103453665);
			assertEquals(row.outcome, Outcome.UNSPECIFIED);
			assertEquals(row.data[0].value, 15000.0, 0.1);
			assertEquals(row.data[1].str, "Ki");
			assertEquals(row.data[2].str, "=");
			assertEquals(row.data[3].value, 1.5E7, 1);
			assertEquals(row.data[4].str, "nM");
			assertEquals(row.data[5].str, "Outside typical range");	
		}
		
		TestResourceFile pubchem346 = new TestResourceFile("/testData/tasks/pubchemFTP_AID346.json");
		try (InputStream istr = pubchem346.getAsStream())
		{
			PubChemMeasurements measure = new PubChemMeasurements(istr);
			PubChemMeasurements.Column[] columns = measure.getColumns();
		}
	}

	/* DEPRECATED: been replaced
	@Test
	public void testInvestigateMeasurements() throws IOException, ConfigurationException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		when(dataAssay.getAssay(9999)).thenReturn(null);
		assertFalse("Unknown assayID", pubChemAssays.investigateMeasurements("program", 9999, 1000));

		// test the various cases for assays
		DataStore.Assay assay = new DataStore.Assay();
		when(dataAssay.getAssay(9999)).thenReturn(assay);
		assay.uniqueID = null;
		assertFalse("Unknown assay.uniqueID", pubChemAssays.investigateMeasurements("program", 9999, 1000));

		assay.uniqueID = "unknown:1234";
		assertFalse("Unknown assay prefix", pubChemAssays.investigateMeasurements("program", 9999, 1000));

		assay.uniqueID = "ucsfAID:1234";
		assertFalse("Vault prefix", pubChemAssays.investigateMeasurements("program", 9999, 1000));

		assay.uniqueID = Identifier.PUBCHEM_PREFIX + "1234";

		// now we have a valid - handle the special cases for the data
		doReturn(pubChemMeasurements).when(pubChemAssays).getPubChemMeasurements(anyLong(), anyInt());
		when(pubChemMeasurements.getColumns()).thenReturn(null);
		assertFalse("Null columns", pubChemAssays.investigateMeasurements("program", 9999, 1000));
		when(pubChemMeasurements.getColumns()).thenReturn(new Column[0]);
		assertFalse("No columns", pubChemAssays.investigateMeasurements("program", 9999, 1000));
		when(pubChemMeasurements.getColumns()).thenReturn(new Column[2]);
		when(pubChemMeasurements.numRows()).thenReturn(0);
		assertFalse("No Rows", pubChemAssays.investigateMeasurements("program", 9999, 1000));

		// done with the special cases, we can process "real" data
		when(pubChemMeasurements.getColumns()).thenReturn(getTestColumns());
		List<Row> rows = getTestRows(true, true);
		when(pubChemMeasurements.numRows()).thenReturn(rows.size());
		for (int i = 0; i < rows.size(); i++)
			when(pubChemMeasurements.getRow(i)).thenReturn(rows.get(i));
		assertTrue("Analysis successful", pubChemAssays.investigateMeasurements("program", 9999, 1000));
		verify(dataMeasure, times(4)).appendMeasurements(any());

		rows = getTestRows(false, true);
		when(pubChemMeasurements.numRows()).thenReturn(rows.size());
		for (int i = 0; i < rows.size(); i++)
			when(pubChemMeasurements.getRow(i)).thenReturn(rows.get(i));
		assertTrue("Analysis successful (no summary)", pubChemAssays.investigateMeasurements("program", 9999, 1000));
		verify(dataMeasure, times(4 + 3)).appendMeasurements(any());

		rows = getTestRows(true, false);
		when(pubChemMeasurements.numRows()).thenReturn(rows.size());
		for (int i = 0; i < rows.size(); i++)
			when(pubChemMeasurements.getRow(i)).thenReturn(rows.get(i));
		assertTrue("Analysis successful (no probes)", pubChemAssays.investigateMeasurements("program", 9999, 1000));
		verify(dataMeasure, times(4 + 4 + 2)).appendMeasurements(any());
	}*/

	@Test
	public void testUpdateAssayFiles() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);

		// initial state checking

		// empty module
		InitParams.ModulePubChem module = new InitParams.ModulePubChem();
		pubChemAssays.setModule(module);
		pubChemAssays.updateAssayFiles();
		// directory missing
		module.assays = true;
		pubChemAssays.updateAssayFiles();
		module.directory = "missing directory";
		pubChemAssays.updateAssayFiles();

		// use the test configuration
		module = Common.getModulePubChem();
		module.assays = true;
		module.directory = folder.toAbsolutePath().toString();
		pubChemAssays.setModule(module);

		// Add a file to the folder for import - updateAssayFiles will load it
		File downloadedFile = pubchemAssaysZip.getAsFileBinary(createFile("pubchemAssays.zip"));
		String[] lf = {"oldfile.zip"};
		when(dataMisc.getLoadedFiles()).thenReturn(getLoadedFiles(lf));
		pubChemAssays.updateAssayFiles();
		verify(dataMisc, times(1)).submitLoadedFile(any());

		// now we add the working zip file to the list of previously downloaded files
		lf = new String[]{"oldfile.zip", downloadedFile.getAbsolutePath()};
		when(dataMisc.getLoadedFiles()).thenReturn(getLoadedFiles(lf));
		pubChemAssays.updateAssayFiles();
		// and see that it has not been parsed a second time (count doesn't change)
		verify(dataMisc, times(1 + 0)).submitLoadedFile(any());
	}

	@Test // This test currently only covers merging RELATED_URI labels since that is all that is covered within mergePubChemAssays()
	public void testMergePubChemAssays() throws IOException
	{
		// nothing has changed => old assay will not be modified
		Assay oldAssay = getAssay();
		int oldNumAnnots = oldAssay.annotations.length, oldNumLabels = oldAssay.textLabels.length;
		Assay newAssay = getAssay();
		assertFalse(PubChemAssays.mergePubChemAssays(newAssay, oldAssay));

		// assay text has changed => old assay will not be modified 
		newAssay = getAssay();
		newAssay.text = "Assay text has changed";
		assertFalse(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay not modified");
		assertEquals("old text", oldAssay.text);
		assertEquals(oldNumLabels, oldAssay.textLabels.length, "no additional textLabels");

		// assay text is null => old assay text filled out
		oldAssay = getAssay();
		newAssay = getAssay();
		oldAssay.text = null;
		newAssay.text = "new text";
		assertTrue(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay not modified");
		assertEquals("new text", oldAssay.text);

		// assay text is blank => old assay text filled out
		oldAssay = getAssay();
		newAssay = getAssay();
		oldAssay.text = "";
		newAssay.text = "new text";
		assertTrue(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay not modified");
		assertEquals("new text", oldAssay.text);
		
		// a new SOURCE_URI value is found
		oldAssay = getAssay();
		newAssay = getAssay();
		newAssay.annotations = new Annotation[]{new Annotation(PubChemAssays.SOURCE_URI, ModelSchema.expandPrefix("bao:newsource"))};
		assertTrue(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay modified");
		assertEquals("old text", oldAssay.text);
		assertEquals(oldNumAnnots + 1, oldAssay.annotations.length, "new source added");

		// a different SOURCE_URI value is found
		oldAssay = getAssay();
		newAssay = getAssay();
		newAssay.annotations = new Annotation[]{new Annotation("different URI", ModelSchema.expandPrefix("bao:oldsource"))};
		assertFalse(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay not modified");
		assertEquals("old text", oldAssay.text);
		assertEquals(oldNumAnnots, oldAssay.annotations.length, "new source not added");

		// old assay is missing SOURCE_URI
		oldAssay = getAssay();
		oldAssay.annotations[0].propURI = "different URI";
		newAssay = getAssay();
		assertTrue(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay modified");
		assertEquals("old text", oldAssay.text);
		assertEquals(oldNumAnnots + 1, oldAssay.annotations.length, "new source added");

		// a new TITLE_URI text label is found
		oldAssay = getAssay();
		newAssay = getAssay();
		newAssay.textLabels = ArrayUtils.add(newAssay.textLabels, new TextLabel(PubChemAssays.TITLE_URI, "new title"));
		assertTrue(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay modified");
		assertEquals("old text", oldAssay.text);
		assertEquals(oldNumLabels + 1, oldAssay.textLabels.length, "new title added");

		// a different TITLE_URI text label is found
		oldAssay = getAssay();
		newAssay = getAssay();
		newAssay.textLabels = ArrayUtils.add(newAssay.textLabels, new TextLabel("different URI", "new title"));
		assertFalse(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay not modified");
		assertEquals("old text", oldAssay.text);
		assertEquals(oldNumLabels, oldAssay.textLabels.length, "new title not added");

		// old assay is missing TITLE_URI
		oldAssay = getAssay();
		oldAssay.textLabels[0].propURI = "different URI";
		newAssay = getAssay();
		assertTrue(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay modified");
		assertEquals("old text", oldAssay.text);
		assertEquals(oldNumLabels + 1, oldAssay.textLabels.length, "new title added");

		// a new RELATED_URI text label is found
		oldAssay = getAssay();
		newAssay = getAssay();
		newAssay.textLabels = ArrayUtils.add(newAssay.textLabels, new TextLabel(PubChemAssays.RELATED_URI, "new label"));
		assertTrue(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay modified");
		assertEquals("old text", oldAssay.text);
		assertEquals(oldNumLabels + 1, oldAssay.textLabels.length, "new label added");

		// a different RELATED_URI text label is found
		oldAssay = getAssay();
		newAssay = getAssay();
		newAssay.textLabels = ArrayUtils.add(newAssay.textLabels, new TextLabel("different URI", "new label"));
		assertFalse(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay not modified");
		assertEquals("old text", oldAssay.text);
		assertEquals(oldNumLabels, oldAssay.textLabels.length, "new label not added");

		// old assay is missing RELATED_URI
		oldAssay = getAssay();
		oldAssay.textLabels[1].propURI = "different URI";
		newAssay = getAssay();
		assertTrue(PubChemAssays.mergePubChemAssays(newAssay, oldAssay), "Assay modified");
		assertEquals("old text", oldAssay.text);
		assertEquals(oldNumLabels + 1, oldAssay.textLabels.length, "new label added");
	}

	private Assay getAssay()
	{
		Assay assay = new Assay();
		assay.text = "old text";
		
		assay.annotations = new Annotation[]{new Annotation(PubChemAssays.SOURCE_URI, ModelSchema.expandPrefix("bao:oldsource"))};
		
		TextLabel title = new TextLabel(PubChemAssays.TITLE_URI, "old title");
		TextLabel related = new TextLabel(PubChemAssays.RELATED_URI, "old label");
		assay.textLabels = new TextLabel[]{title, related};
		
		return assay;
	}

	@Test
	public void testParsePubChemAssay() throws IOException
	{
		TestResourceFile pubchemJsonData = new TestResourceFile("/testData/tasks/pubchem.concise.json");
		JSONObject json = new JSONObject(pubchemJsonData.getContent());
		Map<String, SchemaTree.Node> sourceMap = new HashMap<>();
		SchemaTree.Node node = new SchemaTree.Node();
		node.uri = "Source URI";
		sourceMap.put("GlaxoSmithKline (GSK)", node);

		Assay assay = PubChemAssays.parsePubChemAssay(json.getJSONObject("PC_AssaySubmit"), sourceMap, new HashSet<>());
		assertNotNull(assay, "Parsing is successful");
		assertEquals("pubchemAID:2306", assay.uniqueID);

		assertNotNull("Assay text loaded", assay.text);
	}

	/* deprecated
	@Test
	public void testGetSourceMap() throws ConfigurationException, IOException
	{
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Map<String, Node> sourceMap = PubChemAssays.getSourceMap();

		assertEquals(30, sourceMap.size());
	}*/

	private LoadedFile[] getLoadedFiles(String[] paths)
	{
		List<LoadedFile> result = new ArrayList<>();
		for (String p : paths)
		{
			LoadedFile lf = new LoadedFile();
			lf.path = p;
			result.add(lf);
		}
		return result.toArray(new LoadedFile[0]);
	}

	private List<Row> getTestRows(boolean hasSummary, boolean hasProbes)
	{
		// the fourth data point is always null - column will be ignored
		List<Row> rows = new ArrayList<>();
		Row row = new Row();
		if (hasSummary)
		{
			row.sid = 1;
			row.outcome = PubChemMeasurements.Outcome.ACTIVE;
			row.data = new PubChemMeasurements.Datum[]{createDatum(1.1), createDatum(1.2), createDatum("A"), createDatum(null)};
			rows.add(row);
			row = new Row();
			row.sid = 2;
			row.outcome = PubChemMeasurements.Outcome.INACTIVE;
			row.data = new PubChemMeasurements.Datum[]{createDatum(2.1), createDatum(2.2), createDatum("B"), createDatum(null)};
			rows.add(row);
		}
		row = new Row();
		if (hasProbes)
		{
			row.sid = 3;
			row.outcome = PubChemMeasurements.Outcome.PROBE;
			row.data = new PubChemMeasurements.Datum[]{createDatum(3.1), null, createDatum("C"), createDatum(null)};
			rows.add(row);
		}
		row = new Row();
		// measurements that are ignored
		row.sid = 4;
		row.outcome = PubChemMeasurements.Outcome.INCONCLUSIVE;
		row.data = new PubChemMeasurements.Datum[]{createDatum(4.1), createDatum(4.2), createDatum("D"), createDatum(null)};
		rows.add(row);
		row = new Row();
		// measurements are missing or have null values
		if (hasSummary)
		{
			row.sid = 5;
			row.outcome = PubChemMeasurements.Outcome.INACTIVE;
			row.data = new PubChemMeasurements.Datum[]{createDatum(null), createDatum(null), createDatum("B"), createDatum(null)};
			rows.add(row);
		}
		return rows;
	}

//	private Column[] getTestColumns()
//	{
//		Column[] columns = new Column[4];
//		Column column = new Column();
//		column.type = PubChemMeasurements.Type.FLOAT;
//		column.activeColumn = true;
//		columns[0] = column;
//		column = new Column();
//		column.type = PubChemMeasurements.Type.FLOAT;
//		column.activeColumn = false;
//		columns[1] = column;
//		column = new Column();
//		column.type = PubChemMeasurements.Type.STRING;
//		columns[2] = column;
//		// column will have only missing data
//		column = new Column();
//		column.type = PubChemMeasurements.Type.FLOAT;
//		columns[3] = column;
//		return columns;
//	}

	private PubChemMeasurements.Datum createDatum(double value)
	{
		PubChemMeasurements.Datum datum = new PubChemMeasurements.Datum();
		datum.value = value;
		return datum;
	}

	private PubChemMeasurements.Datum createDatum(String value)
	{
		PubChemMeasurements.Datum datum = new PubChemMeasurements.Datum();
		datum.str = value;
		return datum;
	}

	public File createFile(String filename, String content) throws IOException
	{
		File f = createFile(filename);
		try (FileWriter out = new FileWriter(f))
		{
			out.write(content);
		}
		return f;
	}
}
