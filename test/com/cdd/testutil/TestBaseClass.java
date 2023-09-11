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

package com.cdd.testutil;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.junit.jupiter.api.*;

public class TestBaseClass
{
	protected Path folder;

	@BeforeEach
	public void prepareTempFolder() throws IOException
	{
		folder = Files.createTempDirectory("baeTest");
	}

	@AfterEach
	public void deleteTempFolder() throws IOException
	{
		Files.walk(folder)
				.sorted(Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(File::delete);
	}

	protected File createFile(String filename) throws IOException
	{
		File file = new File(folder.toFile(), filename);
		file.createNewFile();
		return file;
	}

	protected File createFolder(String foldername)
	{
		File result = new File(folder.toFile(), foldername);
		result.mkdirs();
		return result;
	}
}
