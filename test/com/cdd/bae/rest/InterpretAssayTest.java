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

package com.cdd.bae.rest;

import com.cdd.bae.data.DataObject.*;
import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.*;

/*
	Tests for InterpretAssay REST API.
*/

public class InterpretAssayTest
{
	private final static String UNIQUEID = "pubchemAID:123";
	private final static String SCHEMAURI = "http://www.bioassayontology.org/bas#";
	
	private InterpretAssay interpretAssay;
	
	@BeforeEach
	public void prepare()
	{
		interpretAssay = new InterpretAssay();
		interpretAssay.logger = TestUtilities.mockLogger();
	}

	@Test
	public void testParseText() throws Exception
	{
		TestResourceFile res = new TestResourceFile("/testData/rest/Interpret.txt");
		byte[] bytes = res.getAsBytes();
		Assay assay = interpretAssay.parseArbitrary(UNIQUEID, SCHEMAURI, null, null, bytes);
		evaluateParsedAssay(assay);
	}

	@Test
	public void testParseJSON() throws Exception
	{
		TestResourceFile res = new TestResourceFile("/testData/rest/Interpret.json");
		byte[] bytes = res.getAsBytes();
		Assay assay = interpretAssay.parseArbitrary(UNIQUEID, SCHEMAURI, null, null, bytes);
		evaluateParsedAssay(assay);
	}

	/* currently not implemented, but we may add it later
	@Test
	public void testParseHTML() throws Exception
	{
		TestResourceFile res = new TestResourceFile("/testData/rest/Interpret.html");
		try (InputStream stream = res.getAsStream())
		{
			Assay assay = interpretAssay.parseArbitrary(UNIQUEID, SCHEMAURI, stream);
			evaluateParsedAssay(assay);
		}
	}*/

	/* currently not implemented, but we may add it later
	@Test
	public void testParseRTF() throws Exception
	{
		TestResourceFile res = new TestResourceFile("/testData/rest/Interpret.rtf");
		try (InputStream stream = res.getAsStream())
		{
			Assay assay = interpretAssay.parseArbitrary(UNIQUEID, SCHEMAURI, stream);
			evaluateParsedAssay(assay);
		}
	}*/

	@Test
	public void testParseDoc() throws Exception
	{
		TestResourceFile res = new TestResourceFile("/testData/rest/Interpret.doc");
		byte[] bytes = res.getAsBytes();
		Assay assay = interpretAssay.parseArbitrary(UNIQUEID, SCHEMAURI, null, null, bytes);
		evaluateParsedAssay(assay);
	}

	@Test
	public void testParseDocX() throws Exception
	{
		TestResourceFile res = new TestResourceFile("/testData/rest/Interpret.docx");
		byte[] bytes = res.getAsBytes();
		Assay assay = interpretAssay.parseArbitrary(UNIQUEID, SCHEMAURI, null, null, bytes);
		evaluateParsedAssay(assay);
	}

	/* currently not implemented, but we may add it later
	@Test
	public void testParsePDF() throws Exception
	{
		TestResourceFile res = new TestResourceFile("/testData/rest/Interpret.pdf");
		try (InputStream stream = res.getAsStream())
		{
			Assay assay = new InterpretAssay().parseArbitrary(UNIQUEID, SCHEMAURI, stream);
			evaluateParsedAssay(assay);
		}
	}*/

	// ------------ private methods ------------

	// require that the interpreted assays are parsed out, some at least some hints of the expected content
	private void evaluateParsedAssay(Assay assay)
	{
		assertEquals(assay.uniqueID, UNIQUEID);
		assertEquals(assay.schemaURI, SCHEMAURI);
		assertNotNull(assay.text);
		String msg = "Text: [" + assay.text + "]";
		assertTrue(assay.text.indexOf("Foo") >= 0, msg);
		assertTrue(assay.text.indexOf("ning") >= 0, msg);
		assertTrue(assay.text.indexOf("nang") >= 0, msg);
		assertTrue(assay.text.indexOf("nong") >= 0, msg);
	}	
}
