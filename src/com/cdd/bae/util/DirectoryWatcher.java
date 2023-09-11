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

import org.slf4j.*;

import com.cdd.bao.util.*;

public class DirectoryWatcher
{
	protected Logger logger = null;
	protected String label = "watched";
	protected String dirName;
	protected boolean recursive;
	protected List<String> requiredFiles;

	private Map<File, FileWatcher> loaders = new HashMap<>();

	// ------------ public methods ------------

	public DirectoryWatcher(String dirName, boolean recursive) throws ConfigurationException
	{
		this(dirName, null, recursive);
	}

	public DirectoryWatcher(String dirName, String[] requiredFiles, boolean recursive) throws ConfigurationException
	{
		logger = LoggerFactory.getLogger(this.getClass().getName());
		if (Util.isBlank(dirName) || !new File(dirName).exists()) throw new IllegalArgumentException("A valid directory must be provided");
		this.dirName = dirName;
		this.requiredFiles = requiredFiles == null ? new ArrayList<>() : Arrays.asList(requiredFiles);
		this.recursive = recursive;

		load();
	}
	
	// override this method to define default files
	public List<String> getDefaultFiles()
	{
		return new ArrayList<>();
	}
	
	// override to filter files by name or pattern (e.g. suffix)
	public boolean isValidFile(File f)
	{
		return f.exists();
	}
	
	// override to have a DirectoryWatcher that works without any files
	public boolean requireFiles()
	{
		return true;
	}
	
	// override for any processing after the loading step
	public void postLoad() throws ConfigurationException
	{
		/* override to add functionality */
	}
	
	// override for custom file watcher
	public FileWatcher newFileWatcher(File file)
	{
		return new FileWatcher(file);
	}

	public FileWatcher getFileWatcher(File file)
	{
		return loaders.get(file);
	}

	public boolean hasChanged()
	{
		// first check if the list of templates has changed
		List<File> files;
		try
		{
			files = getFilesOrdered();
		}
		catch (ConfigurationException e)
		{
			logger.error("Error determining the {} files: {}", label, e.getMessage());
			return false;
		}
		if (!loaders.keySet().equals(new HashSet<>(files))) return true;

		// if that is not the case, check if the files have changed
		for (FileWatcher loader : loaders.values()) if (loader.hasChanged()) return true;
		return false;
	}

	public void load() throws ConfigurationException
	{
		loaders.clear();

		List<File> files = getFilesOrdered();
		for (File file : files) loaders.put(file, newFileWatcher(file));
		postLoad();
	}

	public void reload()
	{
		try
		{
			synchronized (this)
			{
				load();
			}
		}
		catch (ConfigurationException e)
		{
			/* ignore exception */
		}
	}

	// ------------ private and protected methods ------------

	private File getFile(String name)
	{
		return new File(dirName + File.separator + name);
	}

	// find files in directory (order is important)
	protected List<File> getFilesOrdered() throws ConfigurationException
	{
		List<File> files = new ArrayList<>();
		
		// first: all default files if not listed in priorityFiles (they may be missing)
		for (String fn : getDefaultFiles())
		{
			File f = getFile(fn);
			if (f.exists() && !requiredFiles.contains(fn)) files.add(f);
		}

		// second: all files in priorityFiles (they must exist)
		for (String fn : requiredFiles)
		{
			File f = getFile(fn);
			if (!f.exists())
			{
				String msg = "Referenced " + label + " file [" + fn + "] not found";
				logger.error(msg, fn);
				throw new ConfigurationException(msg);
			}
			files.add(f);
		}

		// we need either default files or priority files
		if (requireFiles() && files.isEmpty())
			throw new ConfigurationException("You need to define required files in the config file or provide a default file.");

		// and the remaining files found in the directory in alphabetical order
		addDirectory(files, new File(dirName));

		return files;
	}
	
	// adds all not-yet-contained files in a directory, and recursively includes subdirectories
	protected void addDirectory(List<File> files, File dir)
	{
		List<File> flist = new ArrayList<>(), dlist = new ArrayList<>();
		for (File f : dir.listFiles()) if (!f.getName().startsWith("."))
		{		
			if (f.isDirectory()) dlist.add(f);
			else if (isValidFile(f) && !files.contains(f)) flist.add(f);
		}
		
		Collections.sort(flist);
		files.addAll(flist);
		
		if (recursive)
		{
			Collections.sort(dlist);
			for (File d : dlist) addDirectory(files, d);
		}
	}
}



