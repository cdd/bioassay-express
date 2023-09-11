/*
	BioAssay Express (BAE)

	(c) 2016-2018 Collaborative Drug Discovery Inc.
	All rights reserved
*/

package com.cdd.bae.selenium.utilities;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;

import org.openqa.selenium.*;
import org.openqa.selenium.support.ui.*;

public class Frontend
{
	private WebDriverWait wait;
	private WebDriver driver;

	public Frontend(WebDriver driver)
	{
		this.driver = driver;
		wait = new WebDriverWait(driver, 10);
	}

	public void waitUntilElementContains(By locator, String expected)
	{
		wait.until(d ->
			{
				WebElement e = d.findElement(locator);
				String text = e.getText();
				if (d.findElement(locator).getTagName().equals("input"))
					text = d.findElement(locator).getAttribute("value");
				return text.contains(expected);
			});
	}

	public void waitUntilNumberOfTabsIsEqual(int expected)
	{
		wait.until(d -> driver.getWindowHandles().size() == expected);
	}

	public void switchToOtherTab()
	{
		String current = driver.getWindowHandle();
		Set<String> browserTabs = driver.getWindowHandles();
		assertEquals(2, browserTabs.size());
		for (String tab : browserTabs)
			if (!tab.equals(current)) driver.switchTo().window(tab);
	}

	public void closeOtherTabs()
	{
		String current = driver.getWindowHandle();
		for (String handle : driver.getWindowHandles())
		{
			if (handle.equals(current)) continue;
			driver.switchTo().window(handle);
			driver.close();
		}
		driver.switchTo().window(current);
	}
	
	public List<String> getElementsText(SearchContext container, String xpath)
	{
		return getElementsText(container, By.xpath(xpath));
	}
	
	public List<String> getElementsText(SearchContext container, By byCriteria)
	{
		while (true)
		{
			try
			{
				List<String> result = new ArrayList<>();
				for (WebElement input : container.findElements(byCriteria))
					result.add(input.getText());
				return result;
			}
			catch (StaleElementReferenceException e) { /* retry */ }
		}
	}

	public List<WebElement> saveFindElements(String xpath)
	{
		return saveFindElements((SearchContext)driver, xpath);
	}
	
	public List<WebElement> saveFindElements(SearchContext container, String xpath)
	{
		return saveFindElements(container, By.xpath(xpath));
	}
	
	public List<WebElement> saveFindElements(SearchContext container, By byCriteria)
	{
		while (true)
		{
			try
			{
				List<WebElement> result = new ArrayList<>();
				for (WebElement input : container.findElements(byCriteria))
					result.add(input);
				return result;
			}
			catch (StaleElementReferenceException e) { /* retry */ }
		}
	}
	
	public void waitForAttributeChanged(By locator, String attr, String newValue)
	{
		wait.until(d -> driver.findElement(locator).getAttribute(attr).contains(newValue));
	}
	
	public String normalizeString(String s)
	{
		return s.replaceAll("\\s+", " ").replaceAll("\\n", "").toLowerCase();
	}
}
