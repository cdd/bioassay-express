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
import java.nio.charset.*;
import java.util.zip.*;

import com.cdd.bao.util.*;

/*
	...
*/

public class ResourceFile
{
	private String resource;

	// ------------ public methods ------------

	public ResourceFile(String resource)
	{
		this.resource = resource;
	}

	public String getContent() throws IOException
	{
		return getContent(Util.UTF8);
	}
	
	public InputStream getResourceAsStream() throws IOException
	{
		InputStream in = getClass().getResourceAsStream(resource);
		if (resource.endsWith(".gz")) in = new GZIPInputStream(in);
		return in;
	}

	public String getContent(String charSet) throws IOException
	{
		try (InputStreamReader reader = new InputStreamReader(getResourceAsStream(), Charset.forName(charSet)))
		{
			char[] tmp = new char[4096];
			StringBuilder builder = new StringBuilder();
			while (true)
			{
				int len = reader.read(tmp);
				if (len < 0) break;
				builder.append(tmp, 0, len);
			}
			return builder.toString();
		}
		catch (NullPointerException e)
		{
			throw new IOException("Resource not found: " + this.resource);
		}
	}

	/**
	 * Write a byte array to the given file. Writing binary data is significantly simpler than
	 * reading it.
	 * @throws IOException 
	 */
	public void binaryToFile(String outputFilename) throws IOException
	{
		try (InputStream fin = getClass().getResourceAsStream(resource))
		{
			try (OutputStream fout = new FileOutputStream(outputFilename)) 
			{
				int c;
				while ((c = fin.read()) != -1)
				{
					fout.write(c);
				}
			}
		}
	}

	// ------------ private methods ------------

}
