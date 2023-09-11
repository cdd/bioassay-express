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

package com.cdd.bae.web;

import com.cdd.bae.config.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.*;
import com.cdd.bae.rest.MockRESTUtilities.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.junit.jupiter.api.*;

/*
	Test for HeaderFilter
*/

public class HeaderFilterTest
{
	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");

		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		Common.setDataStore(mongo.getDataStore());
	}

	@Test
	public void testDoFilter() throws IOException, ServletException
	{
		HttpServletRequest request = mock(HttpServletRequest.class);
		MockServletResponse responseWrapper = new MockServletResponse();
		HttpServletResponse response = responseWrapper.getResponse();
		when(response.getHeader("Set-Cookie")).thenReturn("JSESSIONID=ABCDEF; Secure; HttpOnly");

		FilterChain chain = mock(FilterChain.class);
		HeaderFilter filter = new HeaderFilter();
		filter.doFilter(request, response, chain);

		verify(chain, times(1)).doFilter(any(), any());
		assertThat(responseWrapper.getHeader("X-Frame-Options"), is("DENY"));
		assertThat(responseWrapper.getHeader("X-Content-Type-Options"), is("nosniff"));
		assertThat(responseWrapper.getHeader("X-XSS-Protection"), is("1; mode=block"));
		assertThat(responseWrapper.getHeader("Set-Cookie"), is("__Host-JSESSIONID=ABCDEF; SameSite=Strict; Secure; HttpOnly"));
	}

	@Test
	public void testServletHeadersReturned() throws IOException, ServletException
	{
		HttpServletRequest request = MockRESTUtilities.mockedJSONRequest("{\"schemaURI\":\"http://www.bioassayontology.org/bas#\",\"locator\":\"0\"}");
		MockServletResponse responseWrapper = new MockServletResponse();
		HttpServletResponse response = responseWrapper.getResponse();

		FilterChain chain = new GetLiteralValueFilterChain();
		HeaderFilter filter = new HeaderFilter();
		filter.doFilter(request, response, chain);

		assertThat(responseWrapper.getHeader("ETag"), is("assay-100000"));
	}

	private class GetLiteralValueFilterChain implements FilterChain
	{

		@Override
		public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse) throws IOException, ServletException
		{
			HttpServletResponse response = (HttpServletResponse)servletResponse;
			HttpServletRequest request = (HttpServletRequest)servletRequest;
			new GetLiteralValues().doPost(request, response);
		}
	}
}
