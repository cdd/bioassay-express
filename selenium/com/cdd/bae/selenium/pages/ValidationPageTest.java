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

public class ValidationPageTest extends PageTestBase 
{

	private static String defaultPageURL = "/diagnostics/validation.jsp";

	@BeforeEach
	public void setup()
	{	
		this.pageURL = ValidationPageTest.defaultPageURL;
		super.setup();
	}

	@Test
	public void testCorrectCall() throws IOException
	{
		this.returnToStartingPage();

		WebElement navTitle = driver.findElement(By.className("navtitle"));
		String title = navTitle.getText();
		assertThat(title, is("Validation Report"));		
	}
}
