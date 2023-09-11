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

import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

/*
	Reference a file or directory within the testing environment.
*/

public class TestResourceFile
{
	private String resource;

	// ------------ public methods ------------

	public TestResourceFile(String resource)
	{
		this.resource = resource;
	}
	
	// returns the path description of a resource
	public String getPath()
	{
		return resource;
	}
	
	// returns just the last part of a resource (i.e. the filename, minus the path)
	public String getName() 
	{
		return new File(resource).getName();
	}

	public String getContent() throws IOException
	{
		return getContent(Util.UTF8);
	}

	public String getContent(String charSet) throws IOException
	{
		ResourceFile resourceFile = new ResourceFile(this.resource);
		return resourceFile.getContent(charSet);
	}

	// rewrites the resource content to the indicated file and returns it
	public File getAsFile(Path folder, String fileName) throws IOException
	{
		return getAsFile(new File(folder.toFile(), fileName));
	}
	public File getAsFile(File file) throws IOException
	{
		try (FileWriter out = new FileWriter(file))
		{
			out.write(getContent());
		}
		return file;
	}
	public File getAsFileBinary(File file) throws IOException
	{
		ResourceFile resourceFile = new ResourceFile(this.resource);
		resourceFile.binaryToFile(file.getCanonicalPath()); 
		return file;
	}
	
	// returns the content of the file as a stream; the caller is responsible for closing it
	public InputStream getAsStream() throws IOException
	{
		return getClass().getResourceAsStream(resource);
	}
	
	// reads in everything, and returns the bytes
	public byte[] getAsBytes() throws IOException
	{
		try (InputStream stream = new BufferedInputStream(getAsStream()))
		{
			ByteArrayOutputStream buff = new ByteArrayOutputStream();
			int b;
			while ((b = stream.read()) >= 0) buff.write(b);
			return buff.toByteArray();
		}
	}
	
	// assuming that the resource is a directory, list files & directories contained within
	public TestResourceFile[] enumerateFiles() throws IOException
	{
		List<TestResourceFile> files = new ArrayList<>();
		
		URL url = getClass().getResource(resource);
		if (url != null)
		{
			File file = new File(url.getFile());
			for (File sub : file.listFiles()) if (!sub.getName().startsWith(".")) files.add(new TestResourceFile(resource + "/" + sub.getName()));
		}
		
		return files.toArray(new TestResourceFile[files.size()]);
	}	
}
