/*
	BioAssay Express (BAE)

	(c) 2016-2018 Collaborative Drug Discovery Inc.
	All rights reserved
*/

package com.cdd.bae.selenium.pages;

import com.cdd.bae.selenium.*;
import com.cdd.bae.selenium.utilities.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;

import java.io.*;
import java.util.*;

import org.json.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

public class RandomPageTest extends PageTestBase
{

	private static String defaultPageURL = "/diagnostics/random.jsp";

	@BeforeEach
	public void setup()
	{
		this.pageURL = RandomPageTest.defaultPageURL;
		super.setup();
	}

	@Test
	public void testCorrectCall() throws IOException
	{
		this.returnToStartingPage();

		WebElement navTitle = driver.findElement(By.className("navtitle"));
		String title = navTitle.getText();
		assertThat(title, is("Random Assays"));
	}

	@Test
	@ExtendWith(UncuratedAssayExecutionCondition.class)
	public void testAssayContent() throws IOException
	{
		this.returnToStartingPage();

		List<WebElement> assayContainerList = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(By.className("assayContainer")));

		for (WebElement assayContainer : assayContainerList)
		{
			String assayIdString = assayContainer.getAttribute("assayId");
			JSONObject anAssay = Setup.getAssay(Integer.parseInt(assayIdString));

			WebElement assayDataContainer = driver.findElement(By.id("assayDataContainer_" + assayIdString));
			String assayText = anAssay.getString("text");

			if (assayText.length() > 50)
			{
				assayText = assayText.substring(0, 50);
			}
			assertThat(ui.normalizeString(assayDataContainer.getText()), containsString(ui.normalizeString(assayText)));
		}
	}
}
