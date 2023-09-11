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

package com.cdd.bae.tasks;

import java.lang.management.*;
import java.lang.management.OperatingSystemMXBean;

import javax.servlet.*;

import com.sun.management.*;

/*
	Monitors the system. 
*/

public class SystemMonitor extends BaseMonitor implements Runnable
{
	static final long DELAY_SECONDS = 1; // Initial delay
	static final long PAUSE_SECONDS = 3600; // interval between system checks

	// ------------ lifecycle ------------

	@Override
	public void contextInitialized(ServletContextEvent ev)
	{
		super.contextInitialized(ev);
		new Thread(this).start();
	}

	// ------------ public methods ------------

	public void run()
	{
		// give it a moment: allow the server a chance to get settled in, or to be terminated
		waitTask(DELAY_SECONDS);
		if (stopped) return;
		
		OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
		if (!(os instanceof UnixOperatingSystemMXBean))
		{
			logger.info("Cannot monitor system on this server");
			return;
		}

		// start the main loop
		while (!stopped)
		{
			logger.info("Number of open files : {}", ((UnixOperatingSystemMXBean) os).getOpenFileDescriptorCount());
			waitTask(logger.isDebugEnabled() ? 10 : PAUSE_SECONDS);
		}
	}

	// ------------ private methods ------------

}
