/*
	BioAssay Express (BAE)

	(c) 2016-2018 Collaborative Drug Discovery Inc.
	All rights reserved
*/

package com.cdd.bae.selenium;

import com.cdd.bae.selenium.utilities.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;

import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

/*
	Base abstract class that contains reusable methods for Selenium Tests
*/

@ExtendWith(ScreenshotWatcher.class)
public abstract class PageTestBase
{
	protected String pageURL;
	protected WebDriverWait wait;
	protected WebDriver driver;
	protected Frontend ui;
	
	@BeforeEach
	public void setup()
	{
		driver = Setup.getWebDriver();
		wait = new WebDriverWait(driver, 10);
		ui = new Frontend(driver);
		ui.closeOtherTabs();
	}

	protected void returnToStartingPage()
	{
		driver.get(Setup.getURL(this.pageURL));
	}
	
	protected void gotoPage(String pageURL)
	{
		driver.get(Setup.getURL(pageURL));
	}

	protected void assertAssignPageOpen(Map<String, String> assay)
	{
		// Assign page for assay is opened
		/* ... these no longer referred to by ID
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("uniqueIDValue")));
		if (driver.getCurrentUrl().contains("uniqueID="))
			assertThat(driver.getCurrentUrl(), containsString(assay.get("uniqueID")));
		else
			assertThat(driver.getCurrentUrl(), containsString(assay.get("assayID")));
		ui.waitUntilElementContains(By.id("uniqueIDValue"), assay.get("uniqueID"));*/
	}
	
	protected void assertAnnotationValuesInWebElement(By seleniumFinder, JSONObject assay, String[] annotationLabels) 
	{
		assertAnnotationValuesInWebElement(seleniumFinder, assay, annotationLabels, driver);
	}

	protected void assertAnnotationValuesInWebElement(By seleniumFinder, JSONObject assay, String[] annotationLabels, SearchContext searchContext) 
	{
		if (searchContext == null) 
		{
			searchContext = driver;
		}
		WebElement webElement = searchContext.findElement(seleniumFinder);
		assertAnnotationValuesInText(webElement.getText(), assay, annotationLabels);
	}
		
	protected void assertAnnotationValuesInText(String text, JSONObject assay, String[] annotationLabels) 
	{
		List<JSONObject> annotationList = findAnnotationsForLabels(assay, annotationLabels);
		
		for (JSONObject annotation : annotationList)
		{
			assertThat(text, containsString(annotation.getString("valueLabel")));	
		}
	}	
	
	// returns a list of annotations for an assay
	
	protected JSONArray findAnnotationsForAssay(JSONObject assay) 
	{
		JSONArray returnList = null;
		
		if (assay.has("annotations"))
		{
			returnList = assay.getJSONArray("annotations");
		}
		
		return returnList;
	}
	
	// returns a list of annotations for an assay for a set of labels
	
	protected List<JSONObject> findAnnotationsForLabels(JSONObject assay, String[] labels) 
	{
		List<JSONObject> returnList = new ArrayList<>();
		
		for (String label : labels) 
		{
			List<JSONObject> annotationList = findAnnotationForLabel(assay, label);
			returnList.addAll(annotationList);
		}
		
		return returnList;
	}	
	
	// given a label, returns the annotations associated with that label, if any
	
	protected List<JSONObject> findAnnotationForLabel(JSONObject assay, String label) 
	{
		List<JSONObject> returnList = new ArrayList<>();

		JSONArray annotationList = this.findAnnotationsForAssay(assay);

		if (annotationList != null) 
		{			
			for (int i = 0; i < annotationList.length(); i++) 
			{
				JSONObject annotation = annotationList.getJSONObject(i);
				
				if (annotation.has("propLabel") && annotation.getString("propLabel").equals(label))
				{
					returnList.add(annotation);
				}
			}
		}
		
		return returnList;
	}
}
