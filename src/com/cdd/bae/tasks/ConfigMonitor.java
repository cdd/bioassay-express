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

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import javax.servlet.*;

/*
	Monitors the configuration file and various files that it references, looking for changes. When discovered, will take
	action and refresh the cached state (e.g. changed the vocabulary tree).

	The current implementation loads a new configuration every time something has changed for the existing configuration.
	The NLP models take about 5 seconds to load and the schemaVocabFile takes 3.4 seconds. 
*/

public class ConfigMonitor extends BaseMonitor implements Runnable
{
	private static ConfigMonitor main = null;

	static final long DELAY_SECONDS = 1; // delay before first check of configuration change
	static final long PAUSE_SECONDS = 10; // interval between checks of configuration change

	// ------------ lifecycle ------------

	@Override
	public void contextInitialized(ServletContextEvent ev)
	{
		super.contextInitialized(ev);

		if (Common.getConfiguration() == null || Common.getParams() == null)
		{
			logger.info("Configuration not available or invalid: disabled");
			return;
		}

		new Thread(this).start();
	}

	// ------------ public methods ------------

	public ConfigMonitor()
	{
		super();
		main = this;
	}

	public static ConfigMonitor main()
	{
		return main;
	}

	// run in a background thread; expected to respond promptly to flipping of the
	// stopped flag
	public void run()
	{
		// give it a moment: allow the server a chance to get settled in, or to be
		// terminated
		waitTask(DELAY_SECONDS);
		if (stopped) return;

		// start the main loop
		while (!stopped)
		{
			// try to reload configuration if either the file for InitParams or anything in
			// the current
			// configuration has changed.
			Configuration currentConfig = Common.getConfiguration();
			if (currentConfig.hasChanged())
			{
				try
				{
					Configuration newConfig;
					newConfig = new Configuration(Common.getConfigFN(), currentConfig);
					synchronized (mutex)
					{
						Common.setConfiguration(newConfig);
					}
					logger.info("Configuration updated");
					Util.writeln(Common.getParams().toString());
				}
				catch (ConfigurationException e)
				{
					logger.error("Error loading configuration\n{}", e.getMessage());
					for (String detail : e.getDetails()) logger.error("  {}", detail);
				}
			}
			waitTask(PAUSE_SECONDS);
		}
	}
}
