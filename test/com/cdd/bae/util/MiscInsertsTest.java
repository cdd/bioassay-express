/*
	BioAssay Express (BAE)

	Copyright 2016-2023 Collaborative Drug Discovery, Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

package com.cdd.bae.util;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.*;
import com.cdd.bae.rest.MockRESTUtilities.*;
import com.cdd.testutil.*;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.servlet.http.*;

import org.json.*;
import org.junit.jupiter.api.*;

public class MiscInsertsTest
{
	private InitParams.URIPatternMap[] oldPattern;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
		oldPattern = Common.getParams().uriPatternMaps;
	}

	@AfterEach
	public void restore()
	{
		Common.getParams().uriPatternMaps = oldPattern;
	}

	@Test
	public void testCSPPolicy() throws IOException
	{
		HttpServletRequest mockRequest = MockRESTUtilities.mockedGETRequest();
		MockServletResponse mockResponse = new MockServletResponse();
		MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(mockRequest, mockResponse.getResponse());
		String cspHeader = mockResponse.getHeader("Content-Security-Policy-Report-Only");
		assertNotNull(cspHeader);
		assertThat(cspHeader, containsString("default-src 'self';"));
		assertThat(cspHeader, containsString("base-uri 'self';"));
		assertThat(cspHeader, containsString("object-src 'none';"));
		assertThat(cspHeader, containsString("frame-src 'self'"));
		assertThat(cspHeader, containsString("script-src 'self'"));
		assertThat(cspHeader, containsString("style-src 'self'"));
		assertThat(cspHeader, containsString("font-src 'self'"));
		assertThat(cspHeader, containsString(cspPolicy.nonce));
	}

	@Test
	public void testUniqueCSPPolicyPerRequest() throws IOException
	{
		HttpServletRequest mockRequest = MockRESTUtilities.mockedGETRequest();
		MockServletResponse mockResponse = new MockServletResponse();
		MiscInserts.CSPPolicy cspPolicy1 = MiscInserts.getCSPPolicy(mockRequest, mockResponse.getResponse());
		MiscInserts.CSPPolicy cspPolicy2 = MiscInserts.getCSPPolicy(mockRequest, mockResponse.getResponse());
		assertEquals(cspPolicy1.nonce, cspPolicy2.nonce);

		// nonces should differ for different requests
		HttpServletRequest otherRequest = MockRESTUtilities.mockedGETRequest();
		MiscInserts.CSPPolicy cspPolicy3 = MiscInserts.getCSPPolicy(otherRequest, mockResponse.getResponse());
		assertNotEquals(cspPolicy1.nonce, cspPolicy3.nonce);
	}

	@Test
	public void testIncludeCommonHead()
	{
		assertThat(MiscInserts.includeCommonHead(0), containsString("\"css"));
		assertThat(MiscInserts.includeCommonHead(1), containsString("\"../css"));
		assertThat(MiscInserts.includeCommonHead(2), containsString("\"../../css"));
	}

	@Test
	public void testIncludeJSLibraries()
	{
		assertThat(MiscInserts.includeJSLibraries(0, "nonce"), containsString("\"js"));
		assertThat(MiscInserts.includeJSLibraries(1, "nonce"), containsString("\"../js"));
		assertThat(MiscInserts.includeJSLibraries(2, "nonce"), containsString("\"../../js"));
	}

	@Test
	public void testRecentCuration() throws IOException
	{
		Map<String, String> cookies = new HashMap<>();
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(null, cookies);

		for (int i = 1; i < 5; i++)
		{
			JSONArray result = MiscInserts.recentCuration(i, request).getJSONArray("all");
			assertThat(result.length(), is(i));
		}
	}

	@Test
	public void testHoldingBayCount() throws IOException
	{
		DataHolding dataHolding = Common.getDataStore().holding();
		for (long holdingID : dataHolding.fetchHoldings())
			dataHolding.deleteHolding(holdingID);
		assertThat(dataHolding.countTotal(), is(0));
		assertThat(MiscInserts.holdingBayCount(), is(""));
		
		dataHolding.depositHolding(DataStoreSupport.makeHolding(2268L, 2));
		assertThat(dataHolding.countTotal(), is(1));
		assertThat(MiscInserts.holdingBayCount(), is(" (1)"));

		dataHolding.depositHolding(DataStoreSupport.makeHolding(2269L, 3));
		assertThat(dataHolding.countTotal(), is(2));
		assertThat(MiscInserts.holdingBayCount(), is(" (2)"));
	}

	@Test
	public void testShowHoldingBay()
	{
		assertTrue(MiscInserts.showHoldingBay());
	}

	@Test
	public void testEmbedIdentifiers()
	{
		assertThat(MiscInserts.embedIdentifiers(), containsString("UNIQUE_IDENTIFIER_SOURCES"));
		assertThat(MiscInserts.embedIdentifiers(), not(containsString("nonce")));
		assertThat(MiscInserts.embedIdentifiers("ABCDEF"), containsString("UNIQUE_IDENTIFIER_SOURCES"));
		assertThat(MiscInserts.embedIdentifiers("ABCDEF"), containsString("nonce='ABCDEF'"));
	}

	@Test
	public void testEmbedTemplates()
	{
		assertThat(MiscInserts.embedTemplates(), containsString("SCHEMA_TEMPLATES"));
		assertThat(MiscInserts.embedTemplates(), not(containsString("nonce")));
		assertThat(MiscInserts.embedTemplates("ABCDEF"), containsString("SCHEMA_TEMPLATES"));
		assertThat(MiscInserts.embedTemplates("ABCDEF"), containsString("nonce='ABCDEF'"));
	}

	@Test
	public void testEmbedTemplateDescriptions()
	{
		assertThat(MiscInserts.embedTemplateDescriptions(), containsString("SCHEMA_DESCRIPTIONS"));
		assertThat(MiscInserts.embedTemplateDescriptions(), not(containsString("nonce")));
		assertThat(MiscInserts.embedTemplateDescriptions("ABCDEF"), containsString("SCHEMA_DESCRIPTIONS"));
		assertThat(MiscInserts.embedTemplateDescriptions("ABCDEF"), containsString("nonce='ABCDEF'"));
	}

	@Test
	public void testEmbedCompoundStatus()
	{
		assertThat(MiscInserts.embedCompoundStatus(), containsString("COMPOUNDS_EXIST"));
		assertThat(MiscInserts.embedCompoundStatus(), not(containsString("nonce")));
		assertThat(MiscInserts.embedCompoundStatus("ABCDEF"), containsString("COMPOUNDS_EXIST"));
		assertThat(MiscInserts.embedCompoundStatus("ABCDEF"), containsString("nonce='ABCDEF'"));
	}
	
	@Test
	public void testEmbedOntoloBridges()
	{
		assertThat(MiscInserts.embedOntoloBridges(), containsString("ONTOLOBRIDGES"));
		assertThat(MiscInserts.embedOntoloBridges(), not(containsString("nonce")));
		assertThat(MiscInserts.embedOntoloBridges("ABCDEF"), containsString("ONTOLOBRIDGES"));
		assertThat(MiscInserts.embedOntoloBridges("ABCDEF"), containsString("nonce='ABCDEF'"));
	}
	
	@Test
	public void testEmbedUserInformation()
	{
		assertThat(MiscInserts.embedUserInformation(), containsString("USERS"));
		assertThat(MiscInserts.embedUserInformation(), not(containsString("nonce")));
		assertThat(MiscInserts.embedUserInformation("ABCDEF"), containsString("USERS"));
		assertThat(MiscInserts.embedUserInformation("ABCDEF"), containsString("nonce='ABCDEF'"));
	}

	@Test
	public void testEmbedURIPatternMaps()
	{
		Common.getParams().uriPatternMaps = new InitParams.URIPatternMap[0];
		assertThat(MiscInserts.embedURIPatternMaps(), containsString("URI_PATTERN_MAPS"));
		assertThat(MiscInserts.embedURIPatternMaps(), containsString("[]"));
		assertThat(MiscInserts.embedURIPatternMaps(), not(containsString("nonce")));
		assertThat(MiscInserts.embedURIPatternMaps("ABCDEF"), containsString("URI_PATTERN_MAPS"));
		assertThat(MiscInserts.embedURIPatternMaps("ABCDEF"), containsString("nonce='ABCDEF'"));

		InitParams.URIPatternMap pattern = new InitParams.URIPatternMap();
		Common.getParams().uriPatternMaps = new InitParams.URIPatternMap[]{pattern};
		pattern.externalURL = "externalURL";
		pattern.label = "label";
		pattern.matchPrefix = "matchPrefix";

		assertThat(MiscInserts.embedURIPatternMaps(), containsString("matchPrefix"));
		assertThat(MiscInserts.embedURIPatternMaps(), containsString("externalURL"));
		assertThat(MiscInserts.embedURIPatternMaps(), containsString("label"));

		Common.getParams().uriPatternMaps = oldPattern;
	}

	@Test
	public void testUsesPubChem()
	{
		assertTrue(MiscInserts.usesPubChem());
	}

	@Test
	public void testParseAssayRequest()
	{
		assertEquals("null", MiscInserts.parseAssayParameter(null));
		assertEquals("[]", MiscInserts.parseAssayParameter(""));
		assertEquals("[]", MiscInserts.parseAssayParameter("     "));
		assertEquals("[101,103,164,169]", MiscInserts.parseAssayParameter("101%2C103%2C164%2C169"));
		assertEquals("[101,103,164,169]", MiscInserts.parseAssayParameter("169,164,103,101"));
		assertEquals("[101,169]", MiscInserts.parseAssayParameter("169,101,101,101"), "Remove duplicates");
		assertEquals("[]", MiscInserts.parseAssayParameter("abc"), "Strings are ignored");
		assertEquals("[]", MiscInserts.parseAssayParameter("%3C/script%3E%3Cscript%3Ealert(1)%3C/script%3E"), "XSS attacks are ignored");
	}

	@Test
	public void testRequestAssays() throws UnsupportedEncodingException
	{
		String query = null;
		assertEquals("null", MiscInserts.parseQueryParameter(query));
		query = "(bao:BAO_0002854=@bao:BAO_0000009);(bao:BAO_0000205=@bao:BAO_0000219)";
		assertEquals("\"" + query + "\"", MiscInserts.parseQueryParameter(query));
		assertEquals("\"" + query + "\"", MiscInserts.parseQueryParameter(URLEncoder.encode(query, "UTF-8")));
		assertEquals("\"\"", MiscInserts.parseQueryParameter(""));
		assertEquals("\"\"", MiscInserts.parseQueryParameter("     "));
		query = "%3C/script%3E%3Cscript%3Ealert(1)%3C/script%3E";
		assertEquals("\"" + MiscInserts.xssPrevention(URLDecoder.decode(query, "UTF-8")) + "\"", MiscInserts.parseQueryParameter(query), "XSS attacks are ignored");
		query = "\");%20alert(1);%20(\"";
		assertEquals("\"" + MiscInserts.xssPrevention(URLDecoder.decode(query, "UTF-8")) + "\"", MiscInserts.parseQueryParameter(query), "XSS attacks are ignored");
	}

	@Test
	public void testParseModelCodeParameter() throws UnsupportedEncodingException
	{
		String query = null;
		assertEquals("null", MiscInserts.parseModelCodeParameter(query));
		query = "%3C/script%3E%3Cscript%3Ealert(1)%3C/script%3E";
		assertEquals("\"" + MiscInserts.xssPrevention(URLDecoder.decode(query, "UTF-8")) + "\"", MiscInserts.parseModelCodeParameter(query), "XSS attacks are ignored");
	}
}
