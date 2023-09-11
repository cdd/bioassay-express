/*
	BioAssay Express (BAE)

	(c) 2016-2018 Collaborative Drug Discovery Inc.
	All rights reserved
*/

package com.cdd.testutil;

import java.io.*;
import java.time.*;
import java.util.*;

import org.apache.tools.ant.taskdefs.optional.junitlauncher.*;
import org.junit.platform.engine.*;
import org.junit.platform.engine.TestDescriptor.*;
import org.junit.platform.engine.TestExecutionResult.*;
import org.junit.platform.launcher.*;

public class SeleniumAntJUnit5Listener implements TestExecutionListener, TestResultFormatter
{
	public class Metrics
	{
		public int succeeded = 0;
		public int skipped = 0;
		public int aborted = 0;
		public int failed = 0;
		public Instant startTime = Instant.now();
		
		public int total()
		{
			return succeeded + skipped + aborted + failed;
		}
		
		public double numSeconds()
		{
			Duration duration = Duration.between(startTime, Instant.now());
			return duration.getNano() / (double)1_000_000_000;
		}
		
		public String report()
		{
			String fmt = "%d, Failures: %d, Aborted: %d, Skipped: %d, Time elapsed: %f sec\n";
			return String.format(fmt, total(), failed, aborted, skipped, numSeconds());
		}
		
		public void addCounts(Metrics other)
		{
			if (other == null) return;
			succeeded += other.succeeded; 
			skipped += other.skipped; 
			aborted += other.aborted; 
			failed += other.failed; 
		}
	}

	private OutputStream out;

	private Metrics currentClass;
	private static Metrics total = (new SeleniumAntJUnit5Listener()).new Metrics();
	
	@Override
	public void executionStarted(TestIdentifier testIdentifier)
	{
		if ("[engine:junit-jupiter]".equals(testIdentifier.getParentId().orElse("")))
		{
			println("Ran " + testIdentifier.getLegacyReportingName());
			System.out.println("Ran " + testIdentifier.getLegacyReportingName());
			currentClass = new Metrics();
		}
	}

	@Override
	public void executionSkipped(TestIdentifier testIdentifier, String reason)
	{
		currentClass.skipped++;
	}

	@Override
	public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult)
	{
		if ("[engine:junit-jupiter]".equals(testIdentifier.getParentId().orElse("")))
		{
			String output = "  Tests run: " + currentClass.report();
			println(output);
			System.out.println(output);
			total.addCounts(currentClass);
		}

		// don't count containers since looking for legacy JUnit 4 counting style
		if (testIdentifier.getType() == Type.TEST)
		{
			StringJoiner sj = new StringJoiner("\n  ", "  ", "");
			if (testExecutionResult.getStatus() == Status.SUCCESSFUL)
			{
				sj.add("SUCCESS: " + testIdentifier.getDisplayName());
				currentClass.succeeded++;
			}
			else if (testExecutionResult.getStatus() == Status.ABORTED)
			{
				sj.add("ABORTED: " + testIdentifier.getDisplayName());
				currentClass.aborted++;
			}
			else if (testExecutionResult.getStatus() == Status.FAILED)
			{
				sj.add("FAILED: " + testIdentifier.getDisplayName());
				if (testExecutionResult.getThrowable().isPresent())
				{
					Throwable exception = testExecutionResult.getThrowable().get();
					sj.add("  " + exception.getMessage());
					for (StackTraceElement st : parseStackTrace(exception.getStackTrace())) sj.add("    " + st.toString());
				}
				currentClass.failed++;
			}
			println(sj.toString());
			System.out.println(sj.toString());
		}
	}
	
	private StackTraceElement[] parseStackTrace(StackTraceElement[] stackTraceElements)
	{
		Integer first = null;
		int last = 0;
		int idx = 0;
		for (StackTraceElement st : stackTraceElements)
		{
			if (st.getClassName().startsWith("com.cdd"))
			{
				last = idx;
				if (first == null) first = idx;
			}
			idx++;
		}
		if (last == 0)
		{
			first = 0; 
			last = stackTraceElements.length;
		}
				
		return Arrays.copyOfRange(stackTraceElements, first, last + 1);
	}

	private void println(String str)
	{
		try
		{
			out.write((str + "\n").getBytes());
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void testPlanExecutionFinished(TestPlan testPlan)
	{
		System.out.println("Tests run so far: " + total.report());
		try
		{
			out.flush();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	@Override
	public void close() throws IOException
	{
		// Nothing required
	}

	@Override
	public void setContext(TestExecutionContext arg0)
	{
		// Nothing required
	}

	@Override
	public void setDestination(OutputStream out)
	{
		this.out = out;
	}
}
