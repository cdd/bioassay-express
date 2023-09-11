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
import com.cdd.bao.util.*;

import java.io.*;
import java.net.*;
import java.nio.file.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.slf4j.*;

/*
	Resource passthrough: looks into the "custom web" directory to see if there's a specific replacement file; if so, that
	gets served up. If not, goes to the default web file directory.
*/

public class CustomResource extends BaseServlet 
{
	private static final Logger logger = LoggerFactory.getLogger(CustomResource.class);

	private static final long serialVersionUID = 1L;

	public CustomResource()
	{
		super();
	}

	// TODO: Why override doPost if POST is not supported. My understanding is that if doPost is not defined, POST is not supported anyway.
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException
	{
		throw new ServletException("POST not supported.");
	}

	@Override
	protected void process(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
	{
		String pfx = request.getContextPath() + request.getServletPath();
		String url = request.getRequestURL().toString();
		String path = new URL(url).getPath();

		String fn = path.substring(pfx.length());
		if (fn.startsWith("/")) fn = fn.substring(1);

		File file = findFile(fn);

		if (fn.endsWith(".png")) response.setContentType("image/png");
		else if (fn.endsWith(".html")) response.setContentType("text/html");
		// (others as necessary)

		response.setContentLength((int)file.length());
		OutputStream out = response.getOutputStream();
		out.write(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
	}

	// alternate entrypoint: called directly from JSP; uses custom resource if available, or built in resource if not; the content
	// should generally be text, such as HTML snippets
	public static String embed(String subPath)
	{
		try
		{
			File file = findFile(subPath);
			return new String(Files.readAllBytes(Paths.get(file.getAbsolutePath())));
		}
		catch (Exception ex)
		{
			return "Fatal exception for embedded link <b>" + subPath + "</b>:<br><pre>" + Util.exToString(ex) + "</pre>";
		}
	}

	protected static File findFile(String filename) throws ServletException, IOException
	{
		String[] directories = new String[] {Common.getCustomWebDir(), Common.getWebDir()};
		for (String dir : directories)
		{
			File file = new File(dir, filename);
			if (!file.exists()) continue;
			file = new File(file.getCanonicalPath());
			if (!file.getCanonicalPath().startsWith(new File(dir).getCanonicalPath())) 
			{
				logger.error("Resource outside of allowed directories requested: {}", filename);
				throw new ServletException("Cannot find resource: " + filename);
			}
			return file;
		}
		logger.error("Requested resource [{}] which cannot be found in row [{}] or custom [{}]", filename, directories[0], directories[1]);
		throw new ServletException("Cannot find resource: " + filename);
	}

}
