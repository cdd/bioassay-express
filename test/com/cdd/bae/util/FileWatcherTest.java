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

public class FileWatcherTest extends TestBaseClass
{
	private static final String MAP_CHANGED = "Map changed";
	private static final String FILE3 = "file3";
	private static final String FILE2 = "file2";
	private static final String FILE1 = "file1";
	private static final String AFTER_INITIALIZATION_UNCHANGED = "After initialization unchanged";
	private static final String FILE_WAS_DELETED = "File was deleted";
	private static final String AFTER_RESET_UNCHANGED = "after reset, unchanged";

	@Test
	public void testWatchSingleFile() throws IOException
	{
		File file = createFile(FILE1);
		FileWatcher watcher = new FileWatcher(file);
		assertEquals(watcher.getFile(), file);

		assertFalse(watcher.hasChanged(), AFTER_INITIALIZATION_UNCHANGED);
		// Can call it again - get the same result
		assertFalse(watcher.hasChanged(), "same result");
		assertTrue(watcher.getChangedFiles().isEmpty());

		// Mimic file has changed
		fakeFileModification(watcher, file);
		assertTrue(watcher.hasChanged(), "File was modified");
		assertTrue(watcher.hasChanged(), "File was modified same result");
		assertEquals(Arrays.asList(file), watcher.getChangedFiles());

		// We reset the internal state of the watcher
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);
		assertTrue(watcher.getChangedFiles().isEmpty());
		// and again after modification
		fakeFileModification(watcher, file);
		assertTrue(watcher.hasChanged(), "File was modified");

		// Deleting the file
		watcher.reset();
		assertTrue(file.delete(), "Deleting file");
		assertTrue(watcher.hasChanged(), FILE_WAS_DELETED);
		assertEquals(Arrays.asList(file), watcher.getChangedFiles());
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);
		assertTrue(watcher.getChangedFiles().isEmpty());

		// And create it again
		createFile(file.getName());
		assertTrue(watcher.hasChanged(), FILE_WAS_DELETED);
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);

		// We change the file that is watched
		File file2 = createFile(FILE2);
		watcher.watchFile(file2);
		assertEquals(watcher.getFile(), file2);
		assertTrue(watcher.hasChanged(), "we now watch a different file");
		assertEquals(Arrays.asList(file2), watcher.getChangedFiles());
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);
		assertTrue(watcher.getChangedFiles().isEmpty());

		// we can set the same file again and again without changing
		watcher.watchFile(file2);
		assertFalse(watcher.hasChanged(), "Setting the same file again is no change");

		// however if the file was changed, we want to find out
		fakeFileModification(watcher, file2);
		assertTrue(watcher.hasChanged(), "file has changed now");
		watcher.watchFile(file2);
		assertTrue(watcher.hasChanged(), "and this isn't changed by telling the watcher that we watch it");
	}

	@Test
	public void testWatchMultipleFiles() throws IOException
	{
		File file1 = createFile(FILE1);
		File file2 = createFile(FILE2);
		File file3 = createFile(FILE3);
		List<File> files = Arrays.asList(file1, file2, file3);
		FileWatcher watcher = new FileWatcher(files);
		assertEquals(3, watcher.getFiles().size());
		assertTrue(watcher.getFiles().containsAll(files), "Watcher watches the files we want.");

		assertFalse(watcher.hasChanged(), AFTER_INITIALIZATION_UNCHANGED);
		// Can call it again - get the same result
		assertFalse(watcher.hasChanged(), "same result");
		assertTrue(watcher.getChangedFiles().isEmpty());

		// Mimic file has changed
		fakeFileModification(watcher, file1);
		assertTrue(watcher.hasChanged(), "File 1 was modified");
		assertTrue(watcher.hasChanged(), "File was modified same result");
		assertEquals(Arrays.asList(file1), watcher.getChangedFiles());

		// We reset the internal state of the watcher
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);
		assertTrue(watcher.getChangedFiles().isEmpty());
		// and again after modification
		fakeFileModification(watcher, file3);
		assertTrue(watcher.hasChanged(), "File 3 was modified");

		// Deleting the file
		watcher.reset();
		assertTrue(file2.delete(), "Deleting file");
		assertTrue(watcher.hasChanged(), "File 2 was deleted");
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);

		// And create it again
		createFile(file2.getName());
		assertTrue(watcher.hasChanged(), FILE_WAS_DELETED);
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);

		// Two files change (list is [file1, file2, file3])
		fakeFileModification(watcher, file2);
		fakeFileModification(watcher, file3);
		assertEquals(2, watcher.getChangedFiles().size());
		assertTrue(watcher.getChangedFiles().containsAll(Arrays.asList(file2, file3)));

		// We change the list of files that is begin watched (file1 and file3)
		List<File> newFiles = Arrays.asList(file1, file3);
		watcher.watchFiles(newFiles);
		assertTrue(watcher.hasChanged(), "we now watch a different list of files");
		assertEquals(2, watcher.getChangedFiles().size());
		assertTrue(watcher.getChangedFiles().containsAll(Arrays.asList(file1, file3)));
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);

		// we can set the same file again and again without changing
		watcher.watchFiles(newFiles);
		assertFalse(watcher.hasChanged(), "Setting the same file again is no change");

		fakeFileModification(watcher, file1);
		assertTrue(watcher.hasChanged(), "changing a file is noted");
		watcher.watchFiles(newFiles);
		assertTrue(watcher.hasChanged(), "even after reassigning the same list");
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);

		// The order of the list doesn't matter
		watcher.watchFiles(Arrays.asList(file3, file1));
		assertFalse(watcher.hasChanged(), "changing the order doesn't matter");

		// And the size of the new list
		newFiles = Arrays.asList(file2, file1);
		watcher.watchFiles(newFiles);
		assertTrue(watcher.hasChanged(), "Adding the same size");

		// Get the file lists
		assertTrue(watcher.getFiles().containsAll(newFiles), "Watcher watches the files we want.");
		// getFile now throws an exception
		Assertions.assertThrows(IllegalStateException.class, () -> watcher.getFile());
	}

	@Test
	public void testMapOfFiles() throws IOException
	{
		Map<String, File> fileMap = new HashMap<>();
		fileMap.put(FILE1, createFile(FILE1));
		fileMap.put(FILE2, createFile(FILE2));
		fileMap.put(FILE3, createFile(FILE3));

		FileWatcher watcher = new FileWatcher(fileMap);
		assertFalse(watcher.hasChanged(), AFTER_INITIALIZATION_UNCHANGED);
		assertEquals(3, watcher.getFiles().size());
		assertTrue(watcher.getFiles().containsAll(fileMap.values()));
		assertEquals(fileMap.get(FILE1), watcher.getFile(FILE1));
		assertTrue(watcher.getChangedFiles().isEmpty());
		assertTrue(watcher.getChangedLabels().isEmpty());

		fakeFileModification(watcher, fileMap.get(FILE1));
		fakeFileModification(watcher, fileMap.get(FILE3));
		assertEquals(2, watcher.getChangedFiles().size());
		assertTrue(watcher.getChangedFiles().containsAll(Arrays.asList(fileMap.get(FILE1), fileMap.get(FILE3))));
		assertEquals(2, watcher.getChangedLabels().size());
		assertTrue(watcher.getChangedLabels().containsAll(Arrays.asList(FILE1, FILE3)));
		
		
		// we can set the same file again and again without changing
		watcher.reset();
		watcher.watchFiles(fileMap);
		assertFalse(watcher.hasChanged(), "Setting the same file map again is no change");

		fileMap.remove(FILE1);
		watcher.watchFiles(fileMap);
		assertTrue(watcher.hasChanged(), MAP_CHANGED);
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);

		// replace existing file in map with new one
		fileMap.put(FILE2, createFile("newFile2"));
		watcher.watchFiles(fileMap);
		assertTrue(watcher.hasChanged(), MAP_CHANGED);
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);
		
		Map<String, File> mapNull = null;
		watcher.watchFiles(mapNull);
		assertTrue(watcher.hasChanged(), MAP_CHANGED);
		watcher.reset();
		watcher.watchFiles(mapNull);
		assertFalse(watcher.hasChanged(), "unchanged");
		watcher.reset();
		assertFalse(watcher.hasChanged(), AFTER_RESET_UNCHANGED);
		watcher.watchFiles(fileMap);
		assertTrue(watcher.hasChanged(), MAP_CHANGED);
	}

	@Test
	public void testBulletproofing() throws IOException
	{
		File fileNull = null;
		File file1 = createFile(FILE1);
		File file2 = createFile(FILE2);
		File file3 = createFile(FILE3);
		List<File> filesNull = null;
		List<File> files = Arrays.asList(file1, file2, file3);

		FileWatcher watcher;

		// Behaviour for single file watching if that file is null
		watcher = new FileWatcher(fileNull);
		assertFalse(watcher.hasChanged(), "We can start watching even if the file is null");
		assertNull(watcher.getFile());
		assertTrue(watcher.getFiles().isEmpty());
		watcher.watchFile(fileNull);
		assertFalse(watcher.hasChanged(), "and that is still the case when we tell it to watch the null file again");
		watcher.watchFile(file1);
		assertTrue(watcher.hasChanged(), "Let's watch a real file");
		watcher.watchFile(fileNull);
		assertTrue(watcher.hasChanged(), "And back to the null file");
		watcher.reset();
		assertFalse(watcher.hasChanged(), "reset also works in this case");

		// And the case that the list of files is null
		watcher = new FileWatcher(filesNull);
		assertFalse(watcher.hasChanged(), "We can watch even if we watch null");
		assertTrue(watcher.getFiles().isEmpty(), "We get empty list");
		watcher.watchFiles(filesNull);
		assertFalse(watcher.hasChanged(), "And we can the null list again");
		watcher.watchFiles(files);
		assertTrue(watcher.hasChanged(), "Let's watch a real list of files");
		watcher.watchFiles(filesNull);
		assertTrue(watcher.hasChanged(), "And back to the null list");
		watcher.reset();
		assertFalse(watcher.hasChanged(), "reset also works in this case");
	}

	@Test
	public void testFileWatcherExceptions() throws IOException
	{
		File file1 = createFile(FILE1);
		File file3 = createFile(FILE3);
		File dir = createFolder("folder");

		// FileWatcher requires a list of files, doesn't accept null in this list
		Assertions.assertThrows(IllegalArgumentException.class, 
				() -> new FileWatcher(Arrays.asList(file1, null, file3)));

		// FileWatcher requires a list of files, doesn't accept folders in this list
		Assertions.assertThrows(IllegalArgumentException.class, 
				() -> new FileWatcher(Arrays.asList(file1, dir, file3)));

		// FileWatcher doesn't accept a folder as single argument
		Assertions.assertThrows(IllegalArgumentException.class, 
				() -> new FileWatcher(dir));
	}
	

	// We fake the modification of the file
	void fakeFileModification(FileWatcher watcher, File file)
	{
		watcher.lastModified.put(file, watcher.lastModified.get(file) - 10);
	}
}
