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

import com.cdd.bae.util.*;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.slf4j.*;

/*
	Add various header to the response
*/

public class AdminFilter implements Filter
{
	protected static Logger logger = LoggerFactory.getLogger(CustomResource.class);
	
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain) throws IOException, ServletException
	{
		HttpServletRequest request = (HttpServletRequest)servletRequest;
		HttpServletResponse response = (HttpServletResponse)servletResponse;

		LoginSupport login = new LoginSupport(request);
		if (login.isAdministrator())
		{
			// pass the request along the filter chain
			chain.doFilter(servletRequest, servletResponse);
		}
		else
		{
			logger.error("Unauthorized access to admin resources {}", request.getRequestURI());
			response.sendRedirect(request.getContextPath() + "/accessRestricted.jsp");
		}
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
		// nop
	}

	@Override
	public void destroy()
	{
		// nop
	}
}
