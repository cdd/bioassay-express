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

package com.cdd.bae.main;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;

import java.io.*;
import java.util.*;
import java.util.stream.*;

import org.junit.jupiter.api.*;

/*
	Test for BulkCommands.
*/

public class BulkCommandsTest
{

	private static final String DONE_TOTAL_CHANGED_0 = "Done. Total changed: 0";
	private static final String DONE_TOTAL_CHANGED_1 = "Done. Total changed: 1";
	//	private static final String OOSDELETE = "oosdelete";
	//	private static final String OOSMIGRATE = "oosmigrate";
	private static final String OOSRENPROP = "oosrenprop";
	private static final String OOSPREFIX = "oosprefix";
	private BulkCommands bulkCommands;
	private DataAssay dataAssay;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		dataAssay = new DataAssay(mongo.getDataStore());

		bulkCommands = new BulkCommands();
	}

	@Test
	public void testPrintHelp()
	{
		String output = TestCommandLine.captureOutput(() -> bulkCommands.printHelp());
		assertThat(output, containsString("Options"));

		// help is printed for empty arguments
		output = executeCommand(new String[0]);
		assertThat(output, containsString("Options"));
	}

	//	@Test
	//	public void testOosDelete()
	//	{
	//		try (TestCommandLine.CaptureOut capture = new TestCommandLine.CaptureOut())
	//		{
	//			// missing parameter
	//			bulkCommands.execute(OOSDELETE);
	//			assertThat(capture.toString(), containsString(PARAMETERS_ARE));
	//
	//			bulkCommands.execute(OOSDELETE, UNKNOWN_PROP_URI);
	//			assertThat(capture.toString(), containsString(DONE_TOTAL_CHANGED_0));
	//
	//			bulkCommands.execute(OOSDELETE, PROP_URI_1);
	//			assertThat(capture.toString(), containsString("Done. Total changed: 2"));
	//		}
	//		catch (Exception e)
	//		{
	//			e.printStackTrace();
	//			assertTrue(EXCEPTION_OCCURRED, false);
	//		}
	//	}

	//	@Test
	//	public void testOosMigrate()
	//	{
	//		try (TestCommandLine.CaptureOut capture = new TestCommandLine.CaptureOut())
	//		{
	//			// missing parameter
	//			bulkCommands.execute(OOSMIGRATE);
	//			assertThat(capture.toString(), containsString(PARAMETERS_ARE));
	//
	//			bulkCommands.execute(OOSMIGRATE, UNKNOWN_PROP_URI, PROP_URI_1);
	//			assertThat(capture.toString(), containsString(DONE_TOTAL_CHANGED_0));
	//
	//			bulkCommands.execute(OOSMIGRATE, PROP_URI_1, UNKNOWN_PROP_URI);
	//			assertThat(capture.toString(), containsString(DONE_TOTAL_CHANGED_0));
	//
	//			bulkCommands.execute(OOSMIGRATE, PROP_URI_1, PROP_URI_2);
	//			assertThat(capture.toString(), containsString(DONE_TOTAL_CHANGED_0));
	//
	//			// no test for modification
	//		}
	//		catch (Exception e)
	//		{
	//			e.printStackTrace();
	//			assertTrue(EXCEPTION_OCCURRED, false);
	//		}
	//	}

	@Test
	public void testOosRenameProp_1() throws IOException
	{
		String output = executeCommand(OOSRENPROP);
		assertThat(output, containsString("Parameters are {propFrom} {propTo}"));

		String propFrom = "bao:BAO_0095009";
		String propTo = "bao:BAX_0000002";
		String propUnknown = "bao:BAX_0000002";

		output = executeCommand(OOSRENPROP, propUnknown, propTo);
		assertThat(output, containsString(DONE_TOTAL_CHANGED_0));

		Assay assay = dataAssay.getAssay(2);
		assertHasAnnotation(assay, propFrom);
		assertNotHasAnnotation(assay, propTo);
		
		output = executeCommand(OOSRENPROP, propFrom, propTo);
		
		assertThat(output, containsString(DONE_TOTAL_CHANGED_1));
		assay = dataAssay.getAssay(2);
		assertNotHasAnnotation(assay, propFrom);
		assertHasAnnotation(assay, propTo);
	}

	@Test
	public void testOosRenameProp_2() throws IOException
	{
		String propTextlabel = "bao:BAO_0000539";
		String propTo = "bao:BAX_0000002";
		
		Assay assay = dataAssay.getAssay(2);
		assertHasTextlabel(assay, propTextlabel);
		assertNotHasTextlabel(assay, propTo);
		
		String output = executeCommand(OOSRENPROP, propTextlabel, propTo);
		
		assertThat(output, containsString(DONE_TOTAL_CHANGED_1));
		assay = dataAssay.getAssay(2);
		assertNotHasTextlabel(assay, propTextlabel);
		assertHasTextlabel(assay, propTo);
	}

	@Test
	public void testOosChangePrefix()
	{
		final String propURI = "bao:BAO_0000205";
		final String oldpfx = "http://www.bioassayontology.org/bao";
		final String newpfx = "http://www.bioassayontology.org/bat";

		String output = executeCommand(OOSPREFIX);
		assertThat(output, containsString("Parameters are {propURI} {oldpfx} {newpfx}"));

		output = executeCommand(OOSPREFIX, propURI, oldpfx, newpfx);
		assertThat(output, containsString("** turning [http://www.bioassayontology.org/bao#BAO_0000357] into [http://www.bioassayontology.org/bat#BAO_0000357] not possible (not in tree)"));
		assertThat(output, containsString("Done. Total changed: 0, almost changed: 4"));
	}

	// ------------ private methods ------------

	private String executeCommand(String... commands)
	{
		return TestCommandLine.captureOutput(() -> bulkCommands.execute(commands));
	}

	private void assertHasAnnotation(Assay assay, String propURI)
	{
		String newURI = ModelSchema.expandPrefix(propURI);
		Set<String> propURIs = Arrays.stream(assay.annotations).map(a -> a.propURI).collect(Collectors.toSet());
		assertThat(propURIs, hasItem(newURI));
	}

	private void assertNotHasAnnotation(Assay assay, String propURI)
	{
		String newURI = ModelSchema.expandPrefix(propURI);
		Set<String> propURIs = Arrays.stream(assay.annotations).map(a -> a.propURI).collect(Collectors.toSet());
		assertThat(propURIs, not(hasItem(newURI)));
	}

	private void assertHasTextlabel(Assay assay, String propURI)
	{
		String newURI = ModelSchema.expandPrefix(propURI);
		Set<String> propURIs = Arrays.stream(assay.textLabels).map(a -> a.propURI).collect(Collectors.toSet());
		assertThat(propURIs, hasItem(newURI));
	}

	private void assertNotHasTextlabel(Assay assay, String propURI)
	{
		String newURI = ModelSchema.expandPrefix(propURI);
		Set<String> propURIs = Arrays.stream(assay.textLabels).map(a -> a.propURI).collect(Collectors.toSet());
		assertThat(propURIs, not(hasItem(newURI)));
	}
}
