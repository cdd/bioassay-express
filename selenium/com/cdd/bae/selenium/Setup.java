/*
	BioAssay Express (BAE)

	(c) 2016-2018 Collaborative Drug Discovery Inc.
	All rights reserved
*/

package com.cdd.bae.selenium;

import java.io.*;
import java.util.*;

import org.json.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.*;
import org.openqa.selenium.firefox.*;

public class Setup
{
	private static final String DEFAULT_URL = "http://localhost:8080/bae";
	private static String baseURL;
	private static WebDriver driver;
	private static RestAPI api;

	private Setup()
	{

	}

	public static String getURL(String path)
	{
		return getBaseURL() + "/" + path.replaceAll("^/*", "");
	}

	public static WebDriver getWebDriver()
	{
		if (driver == null)
		{
			if (System.getProperty("chrome") != null || System.getProperty("headless") != null)
				driver = getChromeDriver();
			else
				driver = getFirefoxDriver();
			driver.manage().window().setSize(new Dimension(1024, 768));
			Runtime.getRuntime().addShutdownHook(new Thread(() -> driver.quit()));
		}
		return driver;
	}

	public static Map<String, String> getValidAssayID() throws IOException
	{
		JSONObject json = getRESTapi().getCuratedAssays();
		Map<String, String> assay = new HashMap<>();
		assay.put("uniqueID", json.getJSONArray("uniqueIDList").getString(0).split(":")[1]);
		assay.put("assayID", Integer.toString(json.getJSONArray("assayIDList").getInt(0)));
		return assay;
	}

	public static int getCuratedAssayCount() throws IOException
	{
		JSONObject json = getRESTapi().getCuratedAssays();
		return json.getJSONArray("uniqueIDList").length();
	}

	public static boolean hasUncuratedAssays() throws IOException
	{
		return getRESTapi().hasUncuratedAssays();
	}

	public static JSONObject getAssay(int assayId) throws IOException
	{
		JSONObject json = getRESTapi().getAssay(assayId);
		return json;
	}

	public static File getScreenshotPath()
	{
		File dirname = new File(System.getProperty("screenshots", "screenshots"));
		if (!dirname.exists()) dirname.mkdir();
		return dirname;
	}

	private static RestAPI getRESTapi()
	{
		if (api == null) api = new RestAPI();
		return api;
	}

	private static String getBaseURL()
	{
		if (baseURL == null) baseURL = System.getProperty("baseURL", DEFAULT_URL).replaceAll("/*$", "");
		return baseURL;
	}

	// Firefox specific configuration 
	private static WebDriver getFirefoxDriver()
	{
		// default: ?
		String webdriverFirefoxBin = System.getProperty("firefoxPath", null);
		// default: /usr/local/bin
		String driverPath = System.getProperty("geckoPath", null);
		if (webdriverFirefoxBin != null) System.setProperty("webdriver.firefox.bin", webdriverFirefoxBin);
		if (driverPath != null) System.setProperty("webdriver.gecko.driver", driverPath);

		return new FirefoxDriver();
	}

	// Chromedriver specific configuration 
	private static WebDriver getChromeDriver()
	{
		// default: /usr/local/bin
		String driverPath = System.getProperty("chromedriverPath", null);
		if (driverPath != null) System.setProperty("webdriver.chrome.driver", driverPath);

		ChromeOptions chromeOptions = new ChromeOptions();
		if (System.getProperty("headless") != null) chromeOptions.addArguments("--headless");
		return new ChromeDriver(chromeOptions);
	}
}
