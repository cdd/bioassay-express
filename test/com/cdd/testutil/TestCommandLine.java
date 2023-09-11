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

/*
	Utility for testing command line tools
*/

public class TestCommandLine
{
	private TestCommandLine() 
	{
	}
	
	public static class CaptureOut implements AutoCloseable
	{
		private final ByteArrayOutputStream content = new ByteArrayOutputStream();
		private PrintStream oldStream;

		// restore default functionality
		public CaptureOut()
		{
			oldStream = System.out;
			captureStream();
		}

		@Override
		public void close() throws Exception
		{
			resetStreams();
		}

		// redirect streams
		public void captureStream()
		{
			System.setOut(new PrintStream(content));
		}

		// restore default functionality
		public void resetStreams()
		{
			System.setOut(oldStream);
		}

		public String toString()
		{
			try
			{
				content.flush();
				String result = content.toString();
				content.reset();
				return result;
			}
			catch (IOException e)
			{
				throw new IllegalStateException("Error occured getting content of stream", e);
			}
		}
	}
	
	public static String captureOutput(Runnable r)
	{
		try (TestCommandLine.CaptureOut capture = new TestCommandLine.CaptureOut())
		{
			r.run();
			return capture.toString();
		}
		catch (Exception e)
		{
			return "Exception: " + e.getMessage();
		}
	}
}
