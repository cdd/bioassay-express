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

package com.cdd.bae.util;

import com.cdd.testutil.*;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;

import org.junit.jupiter.api.*;

public class DirectoryWatcherTest extends TestBaseClass
{
	private final TestUtilities.MuteLogger muteLogger = new TestUtilities.MuteLogger(DirectoryWatcherTxt.class);

	public static class DirectoryWatcherTxt extends DirectoryWatcher
	{
		protected String defaultFile = DEFAULT;
		public DirectoryWatcherTxt(String dirName) throws ConfigurationException
		{
			super(dirName, false);
		}

		public DirectoryWatcherTxt(String dirName, String[] requiredFiles) throws ConfigurationException
		{
			super(dirName, requiredFiles, false);
		}

		@Override
		public boolean isValidFile(File f)
		{
			return f.exists() && f.getName().endsWith(".txt");
		}

		@Override
		public List<String> getDefaultFiles()
		{
			return Arrays.asList(this.defaultFile == null ? DEFAULT : this.defaultFile);
		}
	}

	private static final String DEFAULT = "default.txt";
	private static final String A_TXT = "a.txt";
	private static final String B_TXT = "b.txt";
	private static final String Z_TXT = "z.txt";
	private static final String MISSING = "missing.txt";

	public TestResourceFile template = new TestResourceFile("/testData/config/schema.ttl");

	@Test
	public void testInitialisation() throws ConfigurationException, IOException
	{
		// require dirName
		Assertions.assertThrows(IllegalArgumentException.class, () -> new DirectoryWatcherTxt(null, null));

		// require either existing default files or required files
		String dirName = folder.toAbsolutePath().toString();
		Assertions.assertThrows(ConfigurationException.class, () -> new DirectoryWatcherTxt(dirName, null));
		
		// fails if required files are missing
		muteLogger.mute();
		Assertions.assertThrows(ConfigurationException.class, () -> new DirectoryWatcherTxt(dirName, new String[]{B_TXT, DEFAULT}));
		muteLogger.restore();

		// initialisation is successful if the required files exists
		createFile(Z_TXT);
		DirectoryWatcherTxt watcher = new DirectoryWatcherTxt(dirName, new String[]{Z_TXT});
		assertFalse(watcher.hasChanged(), "after initialisation watcher loaded the files");

		// or if the default file exists
		createFile(DEFAULT);
		watcher = new DirectoryWatcherTxt(dirName);
		assertFalse(watcher.hasChanged(), "after initialisation watcher loaded the files");

	}
	
	@Test
	public void testGetTemplateFilesOrdered() throws ConfigurationException, IOException
	{
		// An empty directory gives an empty list
		String dirName = folder.toAbsolutePath().toString();
		File tDefault = createFile(DEFAULT);
		DirectoryWatcherTxt watcher = new DirectoryWatcherTxt(dirName);

		// provide the default file setting and add the file to folder
		assertEquals(Arrays.asList(new String[]{DEFAULT}), watcher.getDefaultFiles());
		assertFilesList(new String[]{DEFAULT}, watcher.getFilesOrdered());

		// add a few more files in the directory
		createFile("SomeOtherFile");
		createFile(Z_TXT);
		createFile(A_TXT);
		createFile(B_TXT);
		assertFilesList(new String[]{DEFAULT, A_TXT, B_TXT, Z_TXT}, watcher.getFilesOrdered());

		// use requiredFiles to get them first
		watcher = new DirectoryWatcherTxt(dirName, new String[]{Z_TXT, B_TXT});
		assertFilesList(new String[]{DEFAULT, Z_TXT, B_TXT, A_TXT}, watcher.getFilesOrdered());

		// if default files are defined, they will come first
		assertFilesList(new String[]{DEFAULT, Z_TXT, B_TXT, A_TXT}, watcher.getFilesOrdered());

		// but not if they are in the list of required files
		watcher = new DirectoryWatcherTxt(dirName, new String[]{B_TXT, DEFAULT});
		assertFilesList(new String[]{B_TXT, DEFAULT, A_TXT, Z_TXT}, watcher.getFilesOrdered());

		watcher = new DirectoryWatcherTxt(dirName, new String[]{B_TXT, DEFAULT});
		watcher.defaultFile = MISSING;
		assertFilesList(new String[]{B_TXT, DEFAULT, A_TXT, Z_TXT}, watcher.getFilesOrdered());

		// --------
		// now delete the default file
		assertTrue(tDefault.delete(), "Could not delete file");

		// use requiredFiles to get them first
		watcher = new DirectoryWatcherTxt(dirName, new String[]{Z_TXT, B_TXT});
		assertFilesList(new String[]{Z_TXT, B_TXT, A_TXT}, watcher.getFilesOrdered());

		// if default files are defined, they will come first
		assertFilesList(new String[]{Z_TXT, B_TXT, A_TXT}, watcher.getFilesOrdered());
	}

	@Test
	public void testHasChanged() throws ConfigurationException, IOException
	{
		String dirName = folder.toAbsolutePath().toString();
		createFile(DEFAULT);
		DirectoryWatcherTxt watcher = new DirectoryWatcherTxt(dirName);
		assertFalse(watcher.hasChanged(), "Require default or required files");

		// create a new file in the directory
		File fA = createFile(A_TXT);
		assertTrue(watcher.hasChanged(), "new file added");
		watcher.load();
		assertFalse(watcher.hasChanged(), "watcher just loaded all files");

		// remove the new file
		assertTrue(fA.delete(), "Could not delete file");
		assertTrue(watcher.hasChanged(), "file removed");
		watcher.reload();
		assertFalse(watcher.hasChanged(), "watcher just loaded all files");
	}

	public void assertFilesList(String[] expected, List<File> actual)
	{
		assertEquals(expected.length, actual.size());
		for (int i = 0; i < expected.length; ++i)
		{
			assertEquals(expected[i], actual.get(i).getName());
		}
	}
}
