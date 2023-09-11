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
import javax.servlet.http.*;

/*
	Add various headers to the response
*/

public class HeaderFilter implements Filter
{
	@Override
	public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException
	{
		HttpServletResponse response = (HttpServletResponse)servletResponse;

		response.addHeader("X-Frame-Options", "DENY");
		response.addHeader("X-Content-Type-Options", "nosniff");
		response.addHeader("X-XSS-Protection", "1; mode=block");

		CustomResponseWrapper wrapper = new CustomResponseWrapper(response);

		filterChain.doFilter(servletRequest, wrapper);

		String setCookie = response.getHeader("Set-Cookie");
		if (setCookie != null)
		{
			setCookie = setCookie.replaceAll("Secure;", "SameSite=Strict; Secure;");
			response.setHeader("Set-Cookie", "__Host-" + setCookie);
		}
		response.getOutputStream().write(wrapper.getResponseContent().getBytes());
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException
	{
		/* empty */
	}

	@Override
	public void destroy()
	{
		/* empty */
	}

	public class CustomResponseWrapper extends HttpServletResponseWrapper
	{
		private StringWriter stringWriter;
		private boolean isOutputStreamCalled;

		public CustomResponseWrapper(HttpServletResponse response)
		{
			super(response);
		}

		@Override
		public ServletOutputStream getOutputStream() throws IOException
		{
			if (this.stringWriter != null) throw new IllegalStateException("The getWriter() is already called.");
			isOutputStreamCalled = true;
			return super.getOutputStream();
		}

		@Override
		public PrintWriter getWriter() throws IOException
		{
			if (isOutputStreamCalled) throw new IllegalStateException("The getOutputStream() is already called.");
			this.stringWriter = new StringWriter();
			return new PrintWriter(this.stringWriter);
		}

		public String getResponseContent()
		{
			if (this.stringWriter != null) return this.stringWriter.toString();
			return "";
		}
	}
}
