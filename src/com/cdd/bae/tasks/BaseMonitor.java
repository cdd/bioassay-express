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

import com.cdd.bae.data.*;

import javax.servlet.*;

import org.slf4j.*;

/*
	Base class for the various monitors that are started during the service initialisation.
*/

public class BaseMonitor implements ServletContextListener
{
	protected Logger logger = null;
	protected volatile boolean stopped = false;
	protected final Object mutex = new Object();
	private boolean paused = false;

	public void contextInitialized(ServletContextEvent ev)
	{
		Common.bootstrap(ev.getServletContext());
		logger = LoggerFactory.getLogger(this.getClass().getName());

		logger.info("starting");
	}

	public void contextDestroyed(ServletContextEvent ev)
	{
		logger.info("shutdown");

		stopped = true;
		// make sure the task cleans up
		bump();

		logger.info("shutdown complete");
	}

	// wait task for seconds
	public void waitTask(long seconds)
	{
		waitThread(mutex, seconds);
	}

	// tell the task to wake up and work or stop 
	public void bump()
	{
		bumpThread(mutex);
	}

	// synchronization of pauses between compute intensive tasks
	protected boolean isPaused()
	{
		return paused;
	}

	protected void pauseTask(long seconds)
	{
		try
		{
			paused = true;
			logger.info("{}: paused", this.getClass().getName());
			logMemoryUsage();
			Thread.sleep(seconds * 1000);
			logMemoryUsage();
		}
		catch (InterruptedException e)
		{
			/* ignore exception and resume processing in run */ 
		} 
		finally
		{
			paused = false;
		}
	}
	
	protected void logMemoryUsage()
	{
		Runtime rt = Runtime.getRuntime();
		long total = rt.totalMemory();
		long free = rt.freeMemory();
		logger.info("Memory: total {}, free {}, used {}", total, total - free, free);
	}

	// helper function to wait thread for seconds based on mutex
	protected static void waitThread(Object mutex, long seconds)
	{
		synchronized (mutex)
		{
			try
			{
				mutex.wait(seconds * 1000);
			}
			catch (InterruptedException ex)
			{
				/* ignore exception and resume processing in run */
			}
		}
	}

	// helper function to wait thread based on mutex forever
	protected static void waitThread(Object mutex)
	{
		synchronized (mutex)
		{
			try
			{
				mutex.wait();
			}
			catch (InterruptedException ex)
			{
				/* ignore exception and resume processing in run */
			}
		}
	}

	// helper function to wake up sleeping thread
	protected static void bumpThread(Object mutex)
	{
		synchronized (mutex)
		{
			mutex.notify();
		}
	}

}
