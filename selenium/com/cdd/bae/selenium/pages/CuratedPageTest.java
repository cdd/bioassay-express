/*
	BioAssay Express (BAE)

	(c) 2016-2018 Collaborative Drug Discovery Inc.
	All rights reserved
*/

package com.cdd.bae.selenium.pages;

import com.cdd.bae.selenium.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;

import java.io.*;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

public class CuratedPageTest extends PageTestBase 
{

	private static String defaultPageURL = "/curated.jsp";

	@BeforeEach
	public void setup()
	{	
		this.pageURL = CuratedPageTest.defaultPageURL;
		super.setup();
	}

	@Test
	public void testCorrectCall() throws IOException
	{
		this.returnToStartingPage();
		
		// Added wait condition -- otherwise NEXT test will fail because an 
		//   expected alert is thrown when window is closed-- "Fetching Curated List Failed" 

		wait.until(ExpectedConditions.presenceOfElementLocated(By.id("headerContainer")));
		
		WebElement navTitle = driver.findElement(By.className("navtitle"));
		String title = navTitle.getText();
		assertThat(title, is("Curated Assays"));	
		
		assertCorrectCuratedAssayCount();
	}

	private void assertCorrectCuratedAssayCount() throws IOException
	{
		wait.until(ExpectedConditions.presenceOfElementLocated(By.id("headerContainer")));
		WebElement countContainer = driver.findElement(By.xpath("//p[@id='headerContainer']//b"));

		String countString = countContainer.getText();
		int curatedAssayCount = Integer.parseInt(countString);	

		assertThat(curatedAssayCount, equalTo(Setup.getCuratedAssayCount()));		
	}
}
