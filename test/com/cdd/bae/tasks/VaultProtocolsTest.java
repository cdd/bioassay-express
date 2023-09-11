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
import com.cdd.bae.config.InitParams.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
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

/*
	Test for com.cdd.bae.tasks.VaultProtocols
 */

public class VaultProtocolsTest
{
	private static final String SCHEMA_URI = "http://www.bioassayontology.org/bas#";

	VaultProtocols vault;
	DataCompound dataCompound;
	DataAssay dataAssay;

	@BeforeEach
	public void initialize() throws ConfigurationException, IOException
	{
		// test configuration - note that TestConfiguration requires a working configuration
		Configuration configuration = TestConfiguration.getConfiguration();
		Common.setConfiguration(configuration);

		vault = Mockito.spy(new VaultProtocols());
		vault.module = new ModuleVault();
		vault.module.propertyMap = new HashMap<>();
		vault.module.propertyMap.put("field", "fieldURI");
		vault.module.propertyMap.put("units", "unitsURI");
		vault.module.propertyMap.put("operator", "operatorURI");
		vault.module.propertyMap.put("threshold", "thresholdURI");

		dataCompound = mock(DataCompound.class);
		dataAssay = mock(DataAssay.class);
		vault.store = mock(DataStore.class);
		when(vault.store.compound()).thenReturn(dataCompound);
		when(vault.store.assay()).thenReturn(dataAssay);

		vault.logger = TestUtilities.mockLogger();
	}

	@Test
	public void testImportMolecules() throws IOException
	{
		doReturn(getVaultResponse()).when(vault).makeRequest(any(), any());

		long vaultID = 123;
		Long[] vaultMIDs = new Long[]{600000L, 600001L, 600002L};

		Map<Long, String> result = vault.importMolFiles(vaultID, vaultMIDs);
		assertEquals(3, result.size());
		for (int i = 0; i < 3; i++)
			assertEquals("Molfile " + i, result.get(Long.valueOf(600000 + i)));

		ArgumentCaptor<String> arg1 = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> arg2 = ArgumentCaptor.forClass(String.class);
		verify(vault).makeRequest(arg1.capture(), arg2.capture());
		assertTrue(arg1.getValue().contains("vaults/123/molecules"));
		assertTrue(arg1.getValue().endsWith("molecules=600000,600001,600002"));
		assertEquals(null, arg2.getValue());
	}

	@Test
	public void testProcessMolecules() throws IOException
	{
		doReturn(getVaultResponse()).when(vault).makeRequest(any(), any());

		DataObject.Compound[] compounds = new DataObject.Compound[]{new DataObject.Compound(), new DataObject.Compound(), new DataObject.Compound(), new DataObject.Compound()};
		for (int i = 0; i < 4; i++)
		{
			compounds[i].vaultID = i % 2;
			compounds[i].vaultMID = 600000 + i;
			compounds[i].molfile = null;
		}

		vault.processMolecules(compounds);

		ArgumentCaptor<DataObject.Compound> args = ArgumentCaptor.forClass(DataObject.Compound.class);
		verify(dataCompound, times(4)).updateCompound(args.capture());
		List<DataObject.Compound> modified = args.getAllValues();
		assertEquals(600000, modified.get(0).vaultMID);
		assertEquals("Molfile 0", modified.get(0).molfile);
		assertEquals(600002, modified.get(1).vaultMID);
		assertEquals("Molfile 2", modified.get(1).molfile);
		assertEquals(600001, modified.get(2).vaultMID);
		assertEquals("Molfile 1", modified.get(2).molfile);
		assertEquals(600003, modified.get(3).vaultMID);
		assertEquals("", modified.get(3).molfile);
	}

	@Test
	public void testCullDuplicateActivity()
	{
		DataObject.Measurement measure = new DataObject.Measurement();
		measure.compoundID = new long[]{1L, 1L, 1L, 5L, 3L, 2L, 3L, 5L, 4L};
		measure.value = new Double[]{1.0, 0.0, 1.0, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0};
		VaultProtocols.cullDuplicateActivity(measure);

		assertArrayEquals(new long[]{1L, 5L, 2L, 3L, 4L}, measure.compoundID);
		assertArrayEquals(new Double[]{1.0, 0.0, 1.0, 1.0, 0.0}, measure.value);
		assertNull(measure.relation);

		measure.compoundID = new long[]{1L, 1L, 1L, 5L, 3L, 2L, 3L, 5L, 4L};
		measure.value = new Double[]{1.0, 0.0, 0.5, 0.0, 0.0, 1.0, 1.0, 0.0, 0.0};
		measure.relation = new String[]{"1", "2", "3", "1", "1", "1", "2", "2", "1"};
		VaultProtocols.cullDuplicateActivity(measure);

		assertArrayEquals(new long[]{1L, 5L, 2L, 3L, 4L}, measure.compoundID);
		assertArrayEquals(new Double[]{1.0, 0.0, 1.0, 1.0, 0.0}, measure.value);
		assertArrayEquals(new String[]{"1", "1", "1", "2", "1"}, measure.relation);
	}

	@Test
	public void testAppendAnnotationsTextLabel()
	{
		DataObject.Assay assay = new DataObject.Assay();
		assertNull(assay.annotations);
		assertNull(assay.textLabels);

		VaultProtocols.appendAnnotation(assay, "propURI", "valueURI");
		VaultProtocols.appendTextLabel(assay, "propURI", "text");
		assertEquals(1, assay.annotations.length);
		assertEquals(1, assay.textLabels.length);

		VaultProtocols.appendAnnotation(assay, null, "valueURI");
		VaultProtocols.appendTextLabel(assay, null, "text");
		assertEquals(1, assay.annotations.length);
		assertEquals(1, assay.textLabels.length);

		VaultProtocols.appendAnnotation(assay, "propURI", null);
		VaultProtocols.appendTextLabel(assay, "propURI", null);
		assertEquals(1, assay.annotations.length);
		assertEquals(1, assay.textLabels.length);
	}

	// @Test
	public void testPollVault() throws IOException
	{
		long vaultID = 1352;
		vault.module.apiKey = "<add API key to get data from Vault>";
		doNothing().when(vault).queryProtocol(anyLong(), any());
		vault.pollVault(vaultID);
	}

	@Test
	public void testQueryProtocol() throws IOException
	{
		long vaultID = 1352;
		vault.queryProtocol(vaultID, getVaultProtocolNoMeasures());
	}

	@Test
	public void testEnsureAssay()
	{
		DataObject.Assay assay = vault.ensureAssay(123L, "protocolName", "description");
		assertEquals(Identifier.VAULT_PREFIX + 123, assay.uniqueID);
		assertEquals("protocolName\n\ndescription", assay.text);
		assertEquals(SCHEMA_URI, assay.schemaURI);
		assertNotNull(assay.curationTime);
		assertEquals(1, assay.textLabels.length);
		assertEquals(AssayUtil.URI_ASSAYTITLE, assay.textLabels[0].propURI);
		assertEquals("protocolName", assay.textLabels[0].text);

		assay = vault.ensureAssay(123L, "protocolName", "");
		assertEquals("protocolName", assay.text);
		assertEquals(1, assay.textLabels.length);
		assay = vault.ensureAssay(123L, "", "description");
		assertEquals("description", assay.text);
		assertNull(assay.textLabels);

		// assay already in database
		when(dataAssay.getAssayFromUniqueID(anyString())).thenReturn(assay);
		DataObject.Assay oldAssay = vault.ensureAssay(123L, "protocolName", "description");
		assertEquals("description", oldAssay.text, "old assay is returned");
		assertNull(assay.textLabels, "old assay is returned and title untouched");
	}

	@Test
	public void testGetMeasureState()
	{
		assertEquals("?", VaultProtocols.getMeasureState(null), "handles null");
		JSONArray runs = new JSONArray();
		assertEquals("?", VaultProtocols.getMeasureState(runs), "no runs");
		runs.put(new JSONObject());
		assertEquals("?:?:?", VaultProtocols.getMeasureState(runs), "run with no information");
		runs.put((new JSONObject()).put("run_date", "date").put("id", "id").put("person", "person"));
		assertEquals("?:?:?&date:id:person", VaultProtocols.getMeasureState(runs), "run with information");
	}

	@Test
	public void testGetGreenInformation()
	{
		JSONArray hitdefs = new JSONArray();
		VaultProtocols.GreenInformation information = VaultProtocols.getGreenInformation(hitdefs);
		assertFalse(information.assayHasHits(), "no data");

		hitdefs.put((new JSONObject()).put("id", 1).put("color", "red").put("operator", ">").put("value", 10.0).put("readout_definition_id", 1));
		information = VaultProtocols.getGreenInformation(hitdefs);
		assertTrue(information.assayHasHits(), "hits");
		assertEquals(1, information.hitID);

		hitdefs.put((new JSONObject()).put("id", 2).put("color", "yellow").put("operator", "=").put("value", 3.5).put("readout_definition_id", 1));
		information = VaultProtocols.getGreenInformation(hitdefs);
		assertEquals(2, information.hitID, "finds the yellow");
		hitdefs.put((new JSONObject()).put("id", 3).put("color", "yellow").put("operator", "=").put("value", 3.5).put("readout_definition_id", 1));
		information = VaultProtocols.getGreenInformation(hitdefs);
		assertEquals(2, information.hitID, "finds the first yellow");

		hitdefs.put((new JSONObject()).put("id", 4).put("color", "lightGreen").put("operator", "=").put("value", 0.5).put("readout_definition_id", 1));
		information = VaultProtocols.getGreenInformation(hitdefs);
		assertEquals(4, information.hitID, "finds the lightGreen");

		hitdefs.put((new JSONObject()).put("id", 5).put("color", "green").put("operator", "=").put("value", 0.5).put("readout_definition_id", 1));
		information = VaultProtocols.getGreenInformation(hitdefs);
		assertEquals(5, information.hitID, "finds the green");
	}

	@Test
	public void testRemovePropertyAnnotationsTextLabels()
	{
		DataObject.Assay assay = new DataObject.Assay();
		assay.annotations = new DataObject.Annotation[0];
		assay.textLabels = new DataObject.TextLabel[0];
		vault.removePropertyAnnotationsTextLabels(assay);
		assertEquals(0, assay.annotations.length);
		assertEquals(0, assay.textLabels.length);

		assay.annotations = ArrayUtils.add(assay.annotations, new DataObject.Annotation("unitsURI", "unit"));
		assay.annotations = ArrayUtils.add(assay.annotations, new DataObject.Annotation("operatorURI", "operator"));
		assay.annotations = ArrayUtils.add(assay.annotations, new DataObject.Annotation("otherURI", "other"));
		assay.textLabels = ArrayUtils.add(assay.textLabels, new DataObject.TextLabel("fieldURI", "field"));
		assay.textLabels = ArrayUtils.add(assay.textLabels, new DataObject.TextLabel("thresholdURI", "threshold"));
		assay.textLabels = ArrayUtils.add(assay.textLabels, new DataObject.TextLabel("otherURI", "other"));
		assertEquals(3, assay.annotations.length);
		assertEquals(3, assay.textLabels.length);
		vault.removePropertyAnnotationsTextLabels(assay);
		assertEquals(1, assay.annotations.length);
		assertEquals(1, assay.textLabels.length);
	}

	private JSONObject getVaultProtocolNoMeasures()
	{
		JSONObject json = new JSONObject();
		json.put("id", 1);
		json.put("name", "name").put("description", "description");

		return json;
	}

	private String getVaultResponse()
	{
		JSONObject json = new JSONObject();
		int n = 3;
		json.put("count", 3);
		json.put("offset", 0);
		json.put("page_size", 50);
		JSONArray objects = new JSONArray();
		json.put("objects", objects);
		for (int i = 0; i < n; i++)
		{
			JSONObject object = new JSONObject();
			object.put("id", 600000 + i);
			object.put("class", "molecule");
			object.put("molfile", "Molfile " + i);
			objects.put(object);
		}
		return json.toString();
	}
}
