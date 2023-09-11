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

import java.util.*;

import org.slf4j.*;

/*
	Utility class for timing sections of code
*/

public class LogTimer
{
	private Logger logger;
	private long start;

	public LogTimer()
	{
		this(null, null);
	}

	public LogTimer(Logger logger)
	{
		this(logger, null);
	}

	public LogTimer(Logger logger, String message)
	{
		this.logger = logger;
		if (message != null) this.logger.debug(message);
		reset();
	}

	public void reset()
	{
		start = System.currentTimeMillis();
	}

	public double getTime()
	{
		return (System.currentTimeMillis() - start) / 1000.0;
	}

	// log the required time, if additional arguments are given, they will be printed first and the time last
	public void report(String message, String... arguments)
	{
		if (logger == null) return;
		if (!logger.isDebugEnabled()) return;

		List<String> args = new ArrayList<>(Arrays.asList(arguments));
		args.add(Double.toString(getTime()));
		String msg = message.contains("{}") ? message : message + " {}";
		logger.debug(msg, args.toArray());
	}
}
