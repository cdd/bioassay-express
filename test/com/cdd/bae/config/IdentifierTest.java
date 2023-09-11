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

import com.cdd.bae.config.Identifier.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.junit.jupiter.api.*;

public class IdentifierTest extends TestBaseClass
{
	private static final String ABCDE = "ABCDE";
	private static final String PUBCHEM_AID = "pubchemAID:";

	// public TemporaryFolder folder = new TemporaryFolder();
	public TestResourceFile identifierValid = new TestResourceFile("/testData/config/identifierValid.json");
	public TestResourceFile identifierInvalid = new TestResourceFile("/testData/config/identifierInvalid.json");

	@Test
	public void testIdentifier() throws IOException, JSONSchemaValidatorException
	{
		Identifier identifier = null;
		File file = null;
		file = identifierValid.getAsFile(folder, "identifierValid.json");
		identifier = new Identifier(file.getAbsolutePath());
		assertEquals(3, identifier.getSources().length);
		assertEquals(PUBCHEM_AID, identifier.getSources()[1].prefix);
		assertArrayEquals(Identifier.defaultSummary, identifier.getSources()[0].summary);
		assertArrayEquals(new String[]{null, "http://www.bioassayontology.org/bao#BAO_0002853"},
				identifier.getSources()[1].summary);
		assertFalse(identifier.hasChanged());
		identifier.reload();

		identifier.setFile(file);
		assertFalse(identifier.hasChanged(), "Reset");
		identifier.setFile(createFile("identifier"));
		assertTrue(identifier.hasChanged(), "New file");
		identifier.loader.getWatcher().reset();
		assertFalse(identifier.hasChanged(), "Reset");
		identifier.setFile(null);
		assertTrue(identifier.hasChanged(), "New file");

		// exception for invalid configuration
		final File invalidFile = identifierInvalid.getAsFile(folder, "identifierInvalid.json");
		JSONSchemaValidatorException ex = Assertions.assertThrows(JSONSchemaValidatorException.class, () -> new Identifier(invalidFile.getAbsolutePath()));
		assertThat(ex.getMessage(), startsWith("Error loading configuration"));
		assertThat(ex.getDetails(), hasSize(4));
	}
	
	@Test
	public void testNoIdentifierFile() throws JSONSchemaValidatorException
	{
		Identifier identifier = new Identifier();
		assertEquals(0, identifier.getSources().length);
		identifier.reload();

		identifier = new Identifier((String)null);
		assertEquals(0, identifier.getSources().length);
		identifier.reload();

		identifier = new Identifier((String)"");
		assertEquals(0, identifier.getSources().length);
		identifier.reload();
	}

	@Test
	public void testIdentifierGetSource() throws IOException, JSONSchemaValidatorException
	{
		Identifier identifier = getTestIdentifier();

		for (Source source : identifier.getSources())
		{
			String prefix = source.prefix;
			Source sourceByPrefix = identifier.getSource(prefix);
			assertEquals(prefix, sourceByPrefix.prefix);
		}
		assertNull(identifier.getSource("unknownPrefix"));
	}

	@Test
	public void testIdentifierUIDkeys() throws IOException, JSONSchemaValidatorException
	{
		Identifier identifier = getTestIdentifier();

		Source pubchem = identifier.getSource(PUBCHEM_AID);

		String key = Identifier.makeKey(pubchem, ABCDE);
		assertEquals("pubchemAID:ABCDE", key);

		UID uid = identifier.parseKey(key);
		assertEquals(pubchem, uid.source);
		assertEquals(ABCDE, uid.id);

		assertNull(identifier.parseKey(null), "Return null for null key");
		assertNull(identifier.parseKey("unknown:prefix"), "Return null for key that cannot be resolved");

		assertEquals(Identifier.makeKey(pubchem, ABCDE), Identifier.makeKey(uid),
				"We can also make the key based on the uid");
	}

	@Test
	public void testComposeRefURL() throws IOException, JSONSchemaValidatorException
	{
		Identifier identifier = getTestIdentifier();

		Source pubchem = identifier.getSource(PUBCHEM_AID);
		String key = Identifier.makeKey(pubchem, ABCDE);

		String refURL = identifier.composeRefURL(key);
		assertThat(refURL, startsWith("http"));
		assertThat(refURL, endsWith(ABCDE));

		assertNull(identifier.composeRefURL("invalidKey"), "Invalid key gives null");
	}

	private Identifier getTestIdentifier() throws IOException, JSONSchemaValidatorException
	{
		File file = identifierValid.getAsFile(folder, "identifierValid.json");
		return new Identifier(file.getAbsolutePath());
	}
	
	@Test
	public void testSource()
	{
		TestUtilities.assertEquality(() -> 
		{
			Identifier.Source source = new Identifier.Source();
			source.name = "name";
			source.shortName = "shortName";
			source.prefix = "prefix";
			source.baseURL = "baseURL";
			source.baseRegex = "baseRegex";
			source.recogRegex = "recogRegex";
			source.defaultSchema = "defaultSchema";
			source.summary = new String[]{"a", "b"};
			return source;
		});
	}
}
