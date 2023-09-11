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
import com.cdd.testutil.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

/*
	Test for TransferCommands.
*/

public class TransferCommandsTest extends TestBaseClass
{
	private static final String SCHEMA_URI = "schemaURI";
	private static final String IDENTITY = "identity";
	private static final String NOTHING_HAS_CHANGED_RETURNS_NULL = "Nothing has changed, returns null";
	private static final String SCHEMA = "schema";
	private static final String IMPORTASSAYS = "importassays";
	private static final String EXPORTASSAYS = "exportassays";
	private static final String VALUE_URI1 = "valueURI";
	private static final String VALUE_URI2 = "valueURI2";
	private static final String PROP_URI1 = "propURI";
	private static final String PROP_URI2 = "propURI2";
	private static final String TEXT1 = "text1";
	private static final String TEXT2 = "text2";
	private static final Date DATE1 = new Date();

	private TransferCommands transferCommands;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
		Common.makeBootstrapped();

		transferCommands = new TransferCommands();
	}

	@Test
	public void testPrintHelp()
	{
		String output = TestCommandLine.captureOutput(() -> transferCommands.printHelp());
		assertThat(output, containsString("Options"));

		// help is printed for empty arguments
		output = executeCommand();
		assertThat(output, containsString("Options"));
	}

	@Test
	public void testExportImportAssays() throws IOException
	{
		File file = createFile("export.zip");

		// option checking for exportassays
		String output = executeCommand(EXPORTASSAYS);
		assertThat(output, containsString("must provide filename"));
		
		output = executeCommand(EXPORTASSAYS, "filename.txt");
		assertThat(output, containsString("zip suffix"));

		// option checking for importassays
		output = executeCommand(IMPORTASSAYS);
		assertThat(output, containsString("must provide filename"));
		
		output = executeCommand(IMPORTASSAYS, "filename.txt");
		assertThat(output, containsString("zip suffix"));
		
		output = executeCommand(IMPORTASSAYS, "filename.txt.zip");
		assertThat(output, containsString("File not found"));

		// export and import assays
		output = executeCommand(EXPORTASSAYS, file.getAbsolutePath());
		assertThat(output, containsString("curated assays: 6"));
		assertThat(output, containsString("Export complete"));
		
		output = executeCommand(IMPORTASSAYS, file.getAbsolutePath());
		assertThat(output, containsString("Parsed assays: 6"));
		assertThat(output, containsString("Updated assays: 0"));
	}

	@Test
	public void testAugmentAssayBase()
	{
		DataStore.Assay assay = makeAssay(1, SCHEMA);
		DataStore.Assay extra = makeAssay(2, SCHEMA);
		DataStore.Assay result = TransferCommands.augmentAssay(assay, extra);

		assertEquals(null, result, NOTHING_HAS_CHANGED_RETURNS_NULL);

		extra.schemaURI = "extra";
		result = TransferCommands.augmentAssay(assay, extra);
		assertTrue(assay == result, "schemaURI was changed");
		assertEquals("extra", assay.schemaURI);
	}

	@Test
	public void testAugmentAssayTextLabel()
	{
		DataStore.Assay assay = makeAssay(1, SCHEMA);
		DataStore.Assay extra = makeAssay(2, SCHEMA);

		// extra has a new text label
		extra.textLabels = new DataStore.TextLabel[]{makeTextLabel(TEXT1, PROP_URI1)};
		DataStore.Assay result = TransferCommands.augmentAssay(assay, extra);
		assertEquals(1, result.textLabels.length);
		assertEquals(TEXT1, result.textLabels[0].text);

		// if the text label already exists, nothing is changed
		extra.textLabels = new DataStore.TextLabel[]{makeTextLabel(TEXT1, PROP_URI1)};
		result = TransferCommands.augmentAssay(assay, extra);
		assertEquals(null, result, NOTHING_HAS_CHANGED_RETURNS_NULL);

		// new text label for same propURI found, add it to assay
		extra.textLabels = new DataStore.TextLabel[]{makeTextLabel(TEXT2, PROP_URI1)};
		result = TransferCommands.augmentAssay(assay, extra);
		assertEquals(2, result.textLabels.length);
		assertEquals(TEXT1, result.textLabels[0].text);
		assertEquals(TEXT2, result.textLabels[1].text);

		// new propURI will also be added
		extra.textLabels = new DataStore.TextLabel[]{makeTextLabel(TEXT1, PROP_URI2)};
		result = TransferCommands.augmentAssay(assay, extra);
		assertEquals(3, result.textLabels.length);
		assertEquals(PROP_URI1, result.textLabels[0].propURI);
		assertEquals(PROP_URI1, result.textLabels[1].propURI);
		assertEquals(PROP_URI2, result.textLabels[2].propURI);
	}

	@Test
	public void testAugmentAssayAnnotation()
	{
		DataStore.Assay assay = makeAssay(1, SCHEMA);
		DataStore.Assay extra = makeAssay(2, SCHEMA);

		// extra has a new text label
		extra.annotations = new DataStore.Annotation[]{new DataStore.Annotation(PROP_URI1, VALUE_URI1)};
		DataStore.Assay result = TransferCommands.augmentAssay(assay, extra);
		assertEquals(1, result.annotations.length);
		assertEquals(VALUE_URI1, result.annotations[0].valueURI);

		// if the text label already exists, nothing is changed
		extra.annotations = new DataStore.Annotation[]{new DataStore.Annotation(PROP_URI1, VALUE_URI1)};
		result = TransferCommands.augmentAssay(assay, extra);
		assertEquals(null, result, NOTHING_HAS_CHANGED_RETURNS_NULL);

		// new text label for same propURI found, add it to assay
		extra.annotations = new DataStore.Annotation[]{new DataStore.Annotation(PROP_URI1, VALUE_URI2)};
		result = TransferCommands.augmentAssay(assay, extra);
		assertEquals(2, result.annotations.length);
		assertEquals(VALUE_URI1, result.annotations[0].valueURI);
		assertEquals(VALUE_URI2, result.annotations[1].valueURI);

		// new propURI will also be added
		extra.annotations = new DataStore.Annotation[]{new DataStore.Annotation(PROP_URI2, VALUE_URI2)};
		result = TransferCommands.augmentAssay(assay, extra);
		assertEquals(3, result.annotations.length);
		assertEquals(PROP_URI1, result.annotations[0].propURI);
		assertEquals(PROP_URI1, result.annotations[1].propURI);
		assertEquals(PROP_URI2, result.annotations[2].propURI);
	}

	@Test
	public void testAnnotationsSame()
	{
		DataStore.Annotation annotationA = new DataStore.Annotation(PROP_URI1, VALUE_URI1);
		DataStore.Annotation[] annotA = new DataStore.Annotation[]{annotationA};
		DataStore.Annotation[] annot12 = new DataStore.Annotation[]{new DataStore.Annotation(PROP_URI1, VALUE_URI2)};
		DataStore.Annotation[] annot22 = new DataStore.Annotation[]{new DataStore.Annotation(PROP_URI2, VALUE_URI2)};

		assertTrue(TransferCommands.annotationsSame(annotA, annotA), IDENTITY);
		assertTrue(TransferCommands.annotationsSame(annot12, annot12), IDENTITY);
		assertTrue(TransferCommands.annotationsSame(annot22, annot22), IDENTITY);

		assertFalse(TransferCommands.annotationsSame(annotA, annot12), IDENTITY);
		assertFalse(TransferCommands.annotationsSame(annotA, annot22), IDENTITY);
		assertFalse(TransferCommands.annotationsSame(annot22, annot12), IDENTITY);

		// test groupNest equality
		DataStore.Annotation annotationB = new DataStore.Annotation(PROP_URI1, VALUE_URI1);
		DataStore.Annotation[] annotB = new DataStore.Annotation[]{annotationB};
		assertTrue(TransferCommands.annotationsSame(annotA, annotB), IDENTITY);
		annotationA.groupNest = new String[]{"a", "b"};
		assertFalse(TransferCommands.annotationsSame(annotA, annotB), "different groupNest");
		assertFalse(TransferCommands.annotationsSame(annotB, annotA), "different groupNest");
		annotationB.groupNest = new String[]{"a", "b"};
		assertTrue(TransferCommands.annotationsSame(annotA, annotB), "identical groupNest");

		assertFalse(TransferCommands.annotationsSame(annotA, new DataStore.Annotation[]{annotationA, annotationB}), IDENTITY);
	}

	@Test
	public void testAssaysEquivalent()
	{
		DataStore.Assay assay1 = makeAssay(100, SCHEMA_URI);

		DataStore.Assay assay2 = makeAssay(100, SCHEMA_URI);
		assertTrue(TransferCommands.assaysEquivalent(assay1, assay2));

		assay2 = makeAssay(100, "differentSchemaURI");
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));

		assay2 = makeAssay(100, SCHEMA_URI);
		assay2.text = "newText";
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));

		assay2 = makeAssay(100, SCHEMA_URI);
		assay2.annotations = new DataStore.Annotation[]{new DataStore.Annotation(PROP_URI1, VALUE_URI2)};
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));

		assay1 = makeAssay(100, SCHEMA_URI);
		assay2 = makeAssay(100, SCHEMA_URI);
		assay2.textLabels = new DataStore.TextLabel[]{makeTextLabel(TEXT1, PROP_URI1)};
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));
		assay1.textLabels = new DataStore.TextLabel[]{makeTextLabel(TEXT1, PROP_URI1)};
		assertTrue(TransferCommands.assaysEquivalent(assay1, assay2));
		assay1.textLabels = new DataStore.TextLabel[]{makeTextLabel(TEXT1, PROP_URI2)};
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));
		assay1.textLabels = new DataStore.TextLabel[]{makeTextLabel(TEXT2, PROP_URI1)};
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));

		/*assay1 = makeAssay(100, SCHEMA_URI);
		assay2 = makeAssay(100, SCHEMA_URI);
		assay2.pubchemSource = "differentPubchemSource";
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));*/

		assay1 = makeAssay(100, SCHEMA_URI);
		assay2 = makeAssay(100, SCHEMA_URI);
		assay2.pubchemXRefs = new DataStore.PubChemXRef[]{makePubChemXRef(TEXT1)};
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));
		assay1.pubchemXRefs = new DataStore.PubChemXRef[]{makePubChemXRef(TEXT1)};
		assertTrue(TransferCommands.assaysEquivalent(assay1, assay2));
		assay1.pubchemXRefs = new DataStore.PubChemXRef[]{makePubChemXRef(TEXT2)};
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));

		assay1 = makeAssay(100, SCHEMA_URI);
		assay2 = makeAssay(100, SCHEMA_URI);
		assay2.curatorID = "differentCurator";
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));

		assay1 = makeAssay(100, SCHEMA_URI);
		assay2 = makeAssay(100, SCHEMA_URI);
		assay2.isCurated = true;
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));

		assay1 = makeAssay(100, SCHEMA_URI);
		assay2 = makeAssay(100, SCHEMA_URI);
		assay2.history = new DataStore.History[]{makeHistory(TEXT1)};
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));
		assay1.history = new DataStore.History[]{makeHistory(TEXT1)};
		assertTrue(TransferCommands.assaysEquivalent(assay1, assay2));
		assay1.history = new DataStore.History[]{makeHistory(TEXT2)};
		assertFalse(TransferCommands.assaysEquivalent(assay1, assay2));
	}

	// ------------ private methods ------------

	private DataStore.Assay makeAssay(long assayID, String schemaURI)
	{
		DataStore.Assay assay = new DataStore.Assay();
		assay.assayID = assayID;
		assay.schemaURI = schemaURI;
		assay.curatorID = "curatorID";
		assay.isCurated = false;
		assay.annotations = new DataStore.Annotation[0];
		assay.textLabels = new DataStore.TextLabel[0];
		assay.pubchemXRefs = new DataStore.PubChemXRef[0];
		return assay;
	}

	private DataStore.TextLabel makeTextLabel(String text, String propURI)
	{
		DataStore.TextLabel textLabel = new TextLabel();
		textLabel.text = text;
		textLabel.propURI = propURI;
		return textLabel;
	}

	private DataStore.PubChemXRef makePubChemXRef(String comment)
	{
		DataStore.PubChemXRef pubChemXRef = new PubChemXRef();
		pubChemXRef.comment = comment;
		return pubChemXRef;
	}

	private DataStore.History makeHistory(String curatorID)
	{
		DataStore.History history = new DataStore.History();
		history.curatorID = curatorID;
		history.curationTime = DATE1;
		return history;
	}

	private String executeCommand(String... commands)
	{
		return TestCommandLine.captureOutput(() -> transferCommands.execute(commands));
	}
}
