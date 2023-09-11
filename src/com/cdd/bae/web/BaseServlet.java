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

import com.cdd.bae.data.*;
import com.cdd.bae.rest.RESTException.*;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.slf4j.*;

/*
	Base class for servlets: provides extra utility.
*/

public abstract class BaseServlet extends HttpServlet 
{
	private static final long serialVersionUID = 1L;
	private Logger logger = null;

	@Override
	public void init()
	{
		logger = LoggerFactory.getLogger(this.getClass().getName());
	}

	@Override
	public void init(ServletConfig config) throws ServletException
	{
		this.init();
		Common.bootstrap(config.getServletContext());
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		try
		{
			process(request, response);
		}
		catch (Exception e)
		{
			logger.error(e.getMessage(), e);
			logger.error("URL causing exception : {}", request.getRequestURL());
			logger.error("Query string : {}", request.getQueryString());
			
			// only throw an exception if the configuration is not production
			if (!Common.isProduction()) throw e;
			response.setStatus(HTTPStatus.INTERNAL_SERVER_ERROR.code());
		}
		
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException
	{
		doGet(request, response);
	}
	
	
	protected abstract void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException;

}
