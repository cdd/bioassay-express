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

public class SemanticWebPageTest extends PageTestBase 
{

	private static String defaultPageURL = "/REST/RDF/all";

	@BeforeEach
	public void setup()
	{	
		this.pageURL = SemanticWebPageTest.defaultPageURL;
		super.setup();
	}

	@Test
	public void testCorrectCall() throws IOException
	{
		this.returnToStartingPage();

		String pageTitle = driver.getTitle();
		assertThat(pageTitle, is("BioAssayExpress RDF"));		
	}
}
