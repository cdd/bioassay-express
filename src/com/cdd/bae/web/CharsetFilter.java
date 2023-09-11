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

import java.io.*;

import javax.servlet.*;

/*
	Add various headers to the response
*/

public class CharsetFilter implements Filter
{
	String characterEncoding; 
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
	{
		if (servletRequest.getCharacterEncoding() == null)
		{
			servletRequest.setCharacterEncoding(characterEncoding);
		}
		filterChain.doFilter(servletRequest, servletResponse);
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
		characterEncoding = filterConfig.getInitParameter("requestEncoding");
		if (characterEncoding == null) characterEncoding = "UTF-8";
	}

	@Override
	public void destroy()
	{
		/* empty */
	}
}
