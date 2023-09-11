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
import com.cdd.bae.config.authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.util.*;
import com.cdd.testutil.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.junit.jupiter.api.*;
import org.mockito.*;

/*
	Test for AdminFilter
*/

public class AdminFilterTest
{
	private AdminFilter adminFilter;
	private Authentication authentication;

	@BeforeEach
	public void initialize() throws IOException, ConfigurationException
	{
		Configuration configuration = spy(TestConfiguration.getConfiguration(false));
		Common.setConfiguration(configuration);
		authentication = mock(Authentication.class);
		Mockito.doReturn(authentication).when(configuration).getAuthentication();

		adminFilter = new AdminFilter();
		AdminFilter.logger = TestUtilities.mockLogger();
	}

	@Test
	public void testDoFilterUnauthenticated() throws IOException, ServletException
	{
		HttpServletRequest request = mockUnauthenticatedRequest();
		HttpServletResponse response = mock(HttpServletResponse.class);
		FilterChain chain = mock(FilterChain.class);
		adminFilter.doFilter(request, response, chain);
		verify(response, times(1)).sendRedirect(any());
		verify(chain, times(0)).doFilter(any(), any());
	}

	@Test
	public void testDoFilterDefault() throws IOException, ServletException
	{
		HttpServletRequest request = mockAuthenticatedRequest();
		HttpServletResponse response = mock(HttpServletResponse.class);
		Authentication.Session session = getSession(DataUser.STATUS_DEFAULT);
		when(authentication.getSession(any())).thenReturn(session);
		FilterChain chain = mock(FilterChain.class);
		adminFilter.doFilter(request, response, chain);
		verify(response, times(1)).sendRedirect(any());
		verify(chain, times(0)).doFilter(any(), any());
	}

	@Test
	public void testDoFilterAdmin() throws IOException, ServletException
	{
		HttpServletRequest request = mockAuthenticatedRequest();
		HttpServletResponse response = mock(HttpServletResponse.class);
		Authentication.Session session = getSession(DataUser.STATUS_ADMIN);
		when(authentication.getSession(any())).thenReturn(session);
		FilterChain chain = mock(FilterChain.class);
		adminFilter.doFilter(request, response, chain);
		verify(response, times(0)).sendRedirect(any());
		verify(chain, times(1)).doFilter(any(), any());
	}

	// ------------ private methods ------------

	private HttpServletRequest mockUnauthenticatedRequest()
	{
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getCookies()).thenReturn(new Cookie[]{});
		return request;
	}

	private HttpServletRequest mockAuthenticatedRequest()
	{
		HttpServletRequest request = mock(HttpServletRequest.class);
		Cookie[] cookies = new Cookie[]
		{
			mockCookie("curatorID", "default"),
			mockCookie("serviceName", "oauthService"),
			mockCookie("accessToken", "12345678")
		};
		when(request.getCookies()).thenReturn(cookies);
		return request;
	}

	private Cookie mockCookie(String name, String value)
	{
		Cookie cookie = mock(Cookie.class);
		when(cookie.getName()).thenReturn(name);
		when(cookie.getValue()).thenReturn(value);
		return cookie;
	}
	
	private Authentication.Session getSession(String status)
	{
		Authentication.Session session = new Authentication.Session();
		session.serviceName = "oauthService";
		session.accessToken = "12345678";
		session.status = status;
		return session;
	}
}
