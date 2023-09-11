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

package com.cdd.bae.rest;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.MockRESTUtilities.*;
import com.cdd.bae.rest.RESTBaseServlet.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.*;

import javax.servlet.http.*;

import org.junit.jupiter.api.*;

/*
	Test for RDF REST API.
*/

public class RDFTest
{
	RDF rdfServlet;

	@BeforeEach
	public void initialize() throws IOException, ConfigurationException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());

		rdfServlet = new RDF();
		rdfServlet.logger = TestUtilities.mockLogger();
	}

	// ------------ mockito-based tests ------------

	@Test
	public void testDoGet() throws IOException
	{
		MockServletResponse response = getQuery("/all");
		// make sure that we have the correct content type
		verify(response.getResponse()).setContentType(ContentType.HTML.toString());
		// and that it is indeed a HTML document
		String content = response.getContent();
		assertThat(content, containsString("<html>"));
		assertThat(content, containsString("</html>"));
	}

	@Test
	public void testDoGetRDF() throws IOException
	{
		MockServletResponse response = getQuery("/all.rdf");
		// make sure that we have the correct content type
		verify(response.getResponse()).setContentType(ContentType.RDF.toString());
		// and that it is indeed a HTML document
		String content = response.getContent();
		assertThat(content, containsString("<rdf:RDF"));
		assertThat(content, containsString("</rdf:RDF>"));
	}

	@Test
	public void testDoGetTTL() throws IOException
	{
		MockServletResponse response = getQuery("/all.ttl");
		// make sure that we have the correct content type
		verify(response.getResponse()).setContentType(ContentType.TTL.toString());
		// and that it is indeed a HTML document
		String content = response.getContent();
		assertThat(content, containsString("@prefix"));
		assertThat(content, containsString("\"BioAssayExpress RDF\""));
	}

	@Test
	public void testDoGetJSONLD() throws IOException
	{
		MockServletResponse response = getQuery("/all.jsonld");
		// make sure that we have the correct content type
		verify(response.getResponse()).setContentType(ContentType.JSONLD.toString());
		// and that it is indeed a HTML document
		assertThat(response.getContent(), containsString("@graph"));

		response = getQuery("/all.json");
		// make sure that we have the correct content type
		verify(response.getResponse()).setContentType(ContentType.JSONLD.toString());
		// and that it is indeed a HTML document
		assertThat(response.getContent(), containsString("@graph"));
	}

	@Test
	public void testTemplates() throws IOException
	{
		MockServletResponse response = getQuery("/templates");
		// make sure that we have the correct content type
		verify(response.getResponse()).setContentType(ContentType.HTML.toString());
		// and that it is indeed a HTML document
		String content = response.getContent();
		assertThat(content, containsString("template schema"));
		assertThat(content, containsString("</html>"));

		response = getQuery("/schema-0");
		// make sure that we have the correct content type
		verify(response.getResponse()).setContentType(ContentType.HTML.toString());
		// and that it is indeed a HTML document
		content = response.getContent();
		assertThat(content, containsString("template schema"));
		assertThat(content, containsString("</html>"));
	}

	@Test
	public void testAssays() throws IOException
	{
		MockServletResponse response = getQuery("/curated");
		// make sure that we have the correct content type
		verify(response.getResponse()).setContentType(ContentType.HTML.toString());
		// and that it is indeed a HTML document
		assertThat(response.getContent(), containsString("curated assays"));

		response = getQuery("/uncurated");
		// make sure that we have the correct content type
		verify(response.getResponse()).setContentType(ContentType.HTML.toString());
		// and that it is indeed a HTML document
		String content = response.getContent();
		assertThat(content, containsString("uncurated assays"));
	}

	@Test
	public void testAssay() throws IOException
	{
		MockServletResponse response = getQuery("/assay-pubchemAID%3A1020");
		// make sure that we have the correct content type
		verify(response.getResponse()).setContentType(ContentType.HTML.toString());
		// and that it is indeed a HTML document
		String content = response.getContent();
		assertThat(content, containsString("pubchemAID:1020"));
		assertThat(content, containsString("has property"));
		assertThat(content, containsString("has group"));
		assertThat(content, containsString("has measurement"));
	}


	// ------------ private methods ------------

	private MockServletResponse getQuery(String query) throws IOException
	{
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getContextPath()).thenReturn("/context/");
		when(request.getServletPath()).thenReturn("servlet");
		String baseURL = "http://hostname" + request.getContextPath() + request.getServletPath();

		when(request.getRequestURL()).thenReturn(new StringBuffer().append(baseURL).append(query));

		MockServletResponse response = new MockServletResponse();

		rdfServlet.doGet(request, response.getResponse());
		return response;
	}
}
