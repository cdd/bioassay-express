/*
	BioAssay Express (BAE)

	(c) 2016-2018 Collaborative Drug Discovery Inc.
	All rights reserved
*/

package com.cdd.bae.selenium.pages;

import com.cdd.bae.selenium.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

/*
	Selenium test for landing page
*/

public class LandingPageTest extends PageTestBase
{
	private static String defaultPageURL = "/";

	@BeforeEach
	public void setup()
	{	
		this.pageURL = LandingPageTest.defaultPageURL;
		super.setup();
	}

	@Test
	public void testCorrectCall() throws IOException
	{
		this.returnToStartingPage();

		String validUID = Setup.getValidAssayID().get("uniqueID");
		WebElement searchBox = driver.findElement(By.id("searchValue"));
		searchBox.sendKeys(validUID);

		// Wait until the autocomplete box is shown
		wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("findIdentifier")));
		String text = driver.findElement(By.id("findIdentifier")).getText();
		assertThat(text, containsString(validUID));

		// Confirm and open the assign page
		searchBox.sendKeys(Keys.ENTER);

		// Assign page for assay is opened
		assertAssignPageOpen(Setup.getValidAssayID());
	}

	@Test
	public void testCurationInput() throws IOException
	{
		By xpathInput = By.xpath("//div[@class='identity-box']//input");

		this.returnToStartingPage();
		wait.until(ExpectedConditions.visibilityOfElementLocated(xpathInput));
		
		String validAID = Setup.getValidAssayID().get("uniqueID");
		WebElement input = driver.findElement(xpathInput);
		WebElement button = driver.findElement(By.xpath("//button[text()='Go']"));

		// By default button is disabled
		assertFalse(button.isEnabled());
		input.sendKeys(validAID);
		assertTrue(button.isEnabled());
		button.click();

		// Confirm the alert
		wait.until(ExpectedConditions.alertIsPresent());
		Alert alert = driver.switchTo().alert();
		String alertText = alert.getText(); 
		assertThat(alertText, containsString("Assay already exists. Would you like to edit it"));
		alert.accept();

		// Wait until new tab is open and switch to it
		ui.waitUntilNumberOfTabsIsEqual(2);
		ui.switchToOtherTab();

		// Assign page for assay is opened
		assertAssignPageOpen(Setup.getValidAssayID());
	}
}
