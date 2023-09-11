/*
	BioAssay Express (BAE)

	(c) 2016-2018 Collaborative Drug Discovery Inc.
	All rights reserved
*/

package com.cdd.bae.selenium.utilities;

import com.cdd.bae.selenium.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.text.*;
import java.util.*;

import org.junit.jupiter.api.extension.*;
import org.openqa.selenium.*;

public class ScreenshotWatcher implements AfterTestExecutionCallback
{
	private WebDriver webDriver;
	private File screenShotsPath;

	public ScreenshotWatcher()
	{
		super();
		this.webDriver = Setup.getWebDriver();
		this.screenShotsPath = Setup.getScreenshotPath();
	}

	@Override
	public void afterTestExecution(ExtensionContext extensionContext) throws Exception
	{
		if (!extensionContext.getExecutionException().isPresent()) return;

		Method method = extensionContext.getRequiredTestMethod();
		Class<?> testedClass = extensionContext.getRequiredTestClass();
		Optional<Throwable> exception = extensionContext.getExecutionException();

		Util.writeln("Seleniumfailure in " + method.getName() + " [" + testedClass.getName() + "]\n");
		if (exception.isPresent()) exception.get().printStackTrace();

		String filename = new SimpleDateFormat("yyyy-MM-dd").format(new Date())
				+ "-" + testedClass.getName()
				+ "-" + method.getName();
		dumpPage(filename);
		dumpScreenshot(filename);
	}

	private void dumpScreenshot(String filename)
	{
		File scrFile = ((TakesScreenshot)webDriver).getScreenshotAs(OutputType.FILE);
		File outFile = new File(screenShotsPath, filename + ".png");
		try
		{
			Files.copy(scrFile.toPath(), outFile.toPath());
			Util.writeln("Screenshot written to " + filename);
		}
		catch (IOException e)
		{
			Util.writeln("Writing screenshot file failed");
		}
	}

	private void dumpPage(String filename)
	{
		String javascript = "return arguments[0].innerHTML";
		WebElement html = webDriver.findElement(By.tagName("html"));
		String pageSource = (String)((JavascriptExecutor)webDriver).executeScript(javascript, html);
		pageSource = "<html>" + pageSource + "</html>";
		File outFile = new File(screenShotsPath, filename + ".html");
		try (BufferedWriter writer = Files.newBufferedWriter(outFile.toPath()))
		{
			writer.write("<html>" + pageSource + "</html>");
			Util.writeln("HTML file written to " + filename);
		}
		catch (IOException e)
		{
			Util.writeln("Writing HTML file failed");
		}
	}
}
