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

public class OverviewPageTest extends PageTestBase 
{

	private static String defaultPageURL = "/docs/";

	@BeforeEach
	public void setup()
	{	
		this.pageURL = OverviewPageTest.defaultPageURL;
		super.setup();
	}

	@Test
	public void testCorrectCall() throws IOException
	{
		this.returnToStartingPage();

		WebElement headerContainer = driver.findElement(By.id("headerContainer"));
		String headerText = headerContainer.getText();
		assertThat(headerText, containsString("The BioAssay Express is a web-based tool"));		
	}
}
