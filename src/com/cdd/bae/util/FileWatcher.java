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

import java.io.*;
import java.util.*;

import com.cdd.bao.util.*;

/*
	FileWatcher

	Watch one or more files and report if files have changed.
		- single file: FileWatcher(File), watchFile(File), getFile
		- list of files: FileWatcher(List<File>), watchFiles(List<File>)
		- map of files: FileWatcher(Map<String, File>), watchFiles(Map<String, File>)

	In all cases, 
		- getFile(String label): returns file identified by label (default canonical path) 
		- getFiles: returns list of all watched files
		- hasChanged: have the watched files changed?
		- reset: reset the state of the watcher to the current; hasChanged will return false after this command until files change 
*/

public class FileWatcher
{
	private List<File> files = new ArrayList<>();
	private Map<String, File> filesByLabel = new HashMap<>();
	private Map<File, Boolean> fileExists = new HashMap<>();
	protected Map<File, Long> lastModified = new HashMap<>();
	private boolean stateChanged = false;

	// ------------ public methods ------------

	// we can watch a single file or a directory of files
	public FileWatcher(File file)
	{
		super();
		initFile(file);
	}

	// watch a list of files
	public FileWatcher(List<File> files)
	{
		super();
		initFiles(files);
	}

	public FileWatcher(Map<String, File> files)
	{
		super();
		initFiles(files);
	}

	public File getFile()
	{
		if (files.size() > 1) throw new IllegalStateException("Watcher watches more than one file");
		return files.isEmpty() ? null : getFiles().get(0);
	}

	public File getFile(String label)
	{
		if (!filesByLabel.containsKey(label)) throw new IllegalArgumentException("File " + label + " not known");
		return filesByLabel.get(label);
	}

	public List<File> getFiles()
	{
		return new ArrayList<>(files);
	}

	public List<File> getChangedFiles()
	{
		// If stateChanged, we assume that all have changed
		if (stateChanged) return files;

		List<File> changedFiles = new ArrayList<>();
		// a file has changed if
		// 	condition 1: file was either deleted or created (xor-operator)
		// 	condition 2: file exists and was modified
		for (File f : files)
			if ((fileExists.get(f).booleanValue() ^ f.exists())
					|| (f.exists() && lastModified.get(f) < f.lastModified()))
				changedFiles.add(f);
		return changedFiles;
	}

	public List<String> getChangedLabels()
	{
		List<String> changedLabels = new ArrayList<>();
		for (File f : getChangedFiles())
		{
			filesByLabel.forEach((k, v) ->
				{
					if (v == f) changedLabels.add(k);
				});
		}
		return changedLabels;
	}

	// Watch a new file
	public void watchFile(File file)
	{
		// handle the null case
		if (files.isEmpty() && file == null) return;
		// return immediately if we already watch the file - nothing has changed
		if (files.size() == 1 && files.contains(file)) return;
		// otherwise, it's like initialization
		initFile(file);
		stateChanged = true;
	}

	// Watch a new file
	public void watchFiles(List<File> files)
	{
		// handle the null case, return if we already have an empty list
		if (files == null && this.files.isEmpty()) return;
		// return immediately if nothing has changed (order doesn't matter)
		if (files != null && files.size() == this.files.size() && files.containsAll(this.files)) return;

		// otherwise, it's like initialization
		initFiles(files);
		stateChanged = true;
	}

	public void watchFiles(Map<String, File> files)
	{
		// Handle the null cases
		if (files == null && this.files.isEmpty()) return;
		// Return immediately if nothing has changed
		if (files != null && files.equals(filesByLabel)) return;

		// Otherwise, it's like initialization
		initFiles(files);
		stateChanged = true;
	}

	// Reset the internal state
	public void reset()
	{
		synchronized (this)
		{
			fileExists.clear();
			lastModified.clear();
			for (File f : files)
			{
				fileExists.put(f, f.exists());
				lastModified.put(f, f.exists() ? f.lastModified() : 0);
			}
			stateChanged = false;
		}
	}

	public boolean hasChanged()
	{
		// If the state has changed (new files , then we don't need to check anything else
		if (stateChanged) return true;
		return !getChangedFiles().isEmpty();
	}

	// ------------ private or protected methods ------------

	private void initFile(File file)
	{
		if (file == null)
			initFiles((List<File>) null);
		else
			initFiles(Arrays.asList(file));
	}

	// initialize the list of files
	private void initFiles(List<File> files)
	{
		this.files.clear();
		this.filesByLabel.clear();
		if (files != null)
		{
			for (File f : files)
			{
				if (f == null) throw new IllegalArgumentException("File cannot be null");
				if (f.isDirectory()) throw new IllegalArgumentException("Folders cannot be watched");
				this.files.add(f);
				this.filesByLabel.put(f.getAbsolutePath(), f);
			}
		}
		this.reset();
	}

	// initialize the map of files
	private void initFiles(Map<String, File> files)
	{
		this.files.clear();
		this.filesByLabel.clear();
		if (files != null)
		{
			for (Map.Entry<String, File> entry : files.entrySet())
			{
				if (entry.getValue() == null) throw new IllegalArgumentException("File cannot be null");
				if (entry.getValue().isDirectory()) throw new IllegalArgumentException("Folders cannot be watched");
				this.files.add(entry.getValue());
				this.filesByLabel.put(entry.getKey(), entry.getValue());
			}
		}
		this.reset();
	}
}
