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


/*
	Test for com.cdd.bae.data.DataMisc
*/

public class DataMiscTest extends TestBaseClass
{
	DataStore store;
	DataMisc dataMisc;

	@BeforeEach
	public void initialize()
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		dataMisc = new DataMisc(mongo.getDataStore());
	}

	@Test
	public void testLoadedFileHandling() throws IOException
	{
		assertThat(dataMisc.getLoadedFiles().length, is(0));

		File file = new File(folder.toFile(), "abc");
		dataMisc.submitLoadedFile(file.getAbsolutePath());
		assertThat("Non-existing file was not added", dataMisc.getLoadedFiles().length, is(0));
		
		file = createFile("abc");
		dataMisc.submitLoadedFile(file.getAbsolutePath());

		LoadedFile[] loadedFiles = dataMisc.getLoadedFiles();
		assertThat(loadedFiles.length, is(1));
		assertThat(loadedFiles[0].path, is(file.getAbsolutePath()));
		
		assertThat(dataMisc.unsubmitLoadedFile(file.getAbsolutePath()), is(true));
		assertThat(dataMisc.getLoadedFiles().length, is(0));
		
		// Remove file that is not in the database will return false
		assertThat(dataMisc.unsubmitLoadedFile(file.getAbsolutePath()), is(false));
	}
}
