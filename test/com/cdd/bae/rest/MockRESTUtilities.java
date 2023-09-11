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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.io.*;
import org.json.*;
import org.mockito.invocation.*;
import org.mockito.stubbing.*;

/*
	Mocks for testing REST services
*/

public class MockRESTUtilities
{
	private MockRESTUtilities()
	{
	}

	protected static class MockJSONRequest
	{
		HttpServletRequest request;
		protected String content = null;
		protected Map<String, String> parameters;
		protected Map<String, Object> attributes = new HashMap<>();
		protected Map<String, String> header = new HashMap<>();
		private Cookie[] cookies = null;

		public MockJSONRequest()
		{
			this((String)null, new HashMap<>());
		}

		public MockJSONRequest(String content)
		{
			this(content, new HashMap<>());
		}

		public MockJSONRequest(Map<String, String> parameters)
		{
			this((String)null, parameters);
		}

		public MockJSONRequest(Map<String, String> parameters, Map<String, String> cookies)
		{
			this(null, parameters, cookies);
		}

		public MockJSONRequest(String content, Map<String, String> parameters)
		{
			request = mock(HttpServletRequest.class);
			this.content = content;
			this.parameters = parameters == null ? new HashMap<>() : parameters;
			initializeMocks(request);
		}
		

		public MockJSONRequest(String content, Map<String, String> parameters, Map<String, String> cookies)
		{
			request = mock(HttpServletRequest.class);
			this.content = content;
			this.parameters = parameters == null ? new HashMap<>() : parameters;
			setCookies(cookies);
			initializeMocks(request);
		}
		
		public void setCookies(Map<String, String> cookies)
		{
			if (cookies == null) return;
			this.cookies = cookies.entrySet().stream()
					.map(entry -> new Cookie(entry.getKey(), entry.getValue()))
					.toArray(Cookie[]::new);
		}

		protected void setHeader(String key, String value)
		{
			header.put(key, value);
		}

		protected HttpServletRequest getJSONRequest() throws IOException
		{
			when(request.getContentType()).thenReturn("application/json");
			reset();
			return request;
		}

		protected HttpServletRequest getPOSTRequest() throws IOException
		{
			when(request.getContentType()).thenReturn("application/x-www-form-urlencoded");
			reset();
			return request;
		}

		private void reset() throws IOException
		{
			if (content != null) when(request.getReader()).thenReturn(new BufferedReader(new StringReader(content)));
		}

		private void initializeMocks(HttpServletRequest request)
		{
			when(request.getParameterNames()).thenReturn(new ParameterNames(new ArrayList<String>(parameters.keySet())));
			when(request.getParameter(anyString())).thenAnswer(new GetParameter(parameters));
			when(request.getAttribute(anyString())).thenAnswer(new GetAttribute(attributes));
			when(request.getCookies()).thenReturn(cookies);
			doAnswer(new SetAttribute(attributes)).when(request).setAttribute(anyString(), any());
			doAnswer(invocation -> header.get(invocation.getArgument(0))).when(request).getHeader(anyString());
		}
	}

	public static class MockServletResponse
	{
		StubServletOutputStream outputStream = new StubServletOutputStream();
		HttpServletResponse response;
		Map<String, String> headers = new HashMap<>();
		private boolean outputStreamLocked = false;
		protected int status = 0;

		public MockServletResponse() throws IOException
		{
			response = mock(HttpServletResponse.class);
			doAnswer(invocation ->
			{
				outputStreamLocked = true;
				return outputStream;

			}).when(response).getOutputStream();

			// response cannot set or add header when output has started
			doAnswer(invocation -> setHeader(invocation.getArguments()))
					.when(response).setHeader(anyString(), anyString());
			doAnswer(invocation -> setHeader(invocation.getArguments()))
					.when(response).addHeader(anyString(), anyString());

			doAnswer(invocation ->
			{
				status = invocation.getArgument(0);
				return null;
			}).when(response).setStatus(anyInt());
			doAnswer(invocation -> status).when(response).getStatus();
		}

		public void reset()
		{
			outputStreamLocked = false;
			outputStream = new StubServletOutputStream();
		}

		public HttpServletResponse getResponse()
		{
			return response;
		}

		public String getContent()
		{
			return outputStream.getContent();
		}

		public byte[] getContentBytes()
		{
			return outputStream.baos.toByteArray();
		}

		public String getContentUnzipped() throws IOException
		{
			return outputStream.getContentUnzipped();
		}

		public String getHeader(String key)
		{
			return headers.getOrDefault(key, null);
		}

		private Object setHeader(Object[] args)
		{
			if (!outputStreamLocked) headers.put((String)args[0], (String)args[1]);
			return null;
		}
	}

	public static class MockJSONResponse extends MockServletResponse
	{
		public MockJSONResponse() throws IOException
		{
			super();
		}

		public JSONObject getContentAsJSON()
		{
			return outputStream.getContentAsJSON();
		}

		protected JSONArray getContentAsJSONArray()
		{
			return outputStream.getContentAsJSONArray();
		}
	}

	// -- helper for convenient construction of mocked HttpServletRequest and HttpServletResponse

	public static HttpServletRequest mockedJSONRequest(String content) throws IOException
	{
		MockJSONRequest mockRequest = new MockJSONRequest(content);
		return mockRequest.getJSONRequest();
	}

	protected static HttpServletRequest mockedJSONRequest(String content, Map<String, String> parameters) throws IOException
	{
		MockJSONRequest mockRequest = new MockJSONRequest(content, parameters);
		return mockRequest.getJSONRequest();
	}

	public static HttpServletRequest mockedPOSTRequest(Map<String, String> parameters, Map<String, String> cookies) throws IOException
	{
		MockJSONRequest mockRequest = new MockJSONRequest(parameters, cookies);
		return mockRequest.getPOSTRequest();
	}

	public static HttpServletRequest mockedPOSTRequest(Map<String, String> parameters) throws IOException
	{
		MockJSONRequest mockRequest = new MockJSONRequest(parameters);
		return mockRequest.getPOSTRequest();
	}

	protected static HttpServletRequest mockedGETRequest(Map<String, String> parameters) throws IOException
	{
		return mockedPOSTRequest(parameters);
	}

	public static HttpServletRequest mockedGETRequest() throws IOException
	{
		return mockedPOSTRequest(new HashMap<>());
	}

	// helper for mocking getParameter call
	private static class GetParameter implements Answer<String>
	{
		protected Map<String, String> parameters;

		GetParameter(Map<String, String> parameters)
		{
			this.parameters = parameters;
		}

		@Override
		public String answer(InvocationOnMock arg0) throws Throwable
		{
			Object[] args = arg0.getArguments();
			return this.parameters.get((String)args[0]);
		}
	}

	// helper for mocking getAttribute call
	private static class GetAttribute implements Answer<Object>
	{
		protected Map<String, Object> attributes;

		GetAttribute(Map<String, Object> attributes)
		{
			this.attributes = attributes;
		}

		@Override
		public Object answer(InvocationOnMock arg0) throws Throwable
		{
			Object[] args = arg0.getArguments();
			return this.attributes.get((String)args[0]);
		}
	}

	// helper for mocking setAttribute call
	private static class SetAttribute implements Answer<Object>
	{
		protected Map<String, Object> attributes;

		SetAttribute(Map<String, Object> attributes)
		{
			this.attributes = attributes;
		}

		@Override
		public Object answer(InvocationOnMock arg0) throws Throwable
		{
			Object[] args = arg0.getArguments();
			this.attributes.put((String)args[0], args[1]);
			return null;
		}
	}

	// helper for mocking getParameterNames

	private static class StubServletOutputStream extends ServletOutputStream
	{
		public ByteArrayOutputStream baos = new ByteArrayOutputStream();

		@Override
		public void write(int i) throws IOException
		{
			baos.write(i);
		}

		@Override
		public void write(byte[] b) throws IOException
		{
			baos.write(b);
		}

		@Override
		public boolean isReady()
		{
			return true;
		}

		@Override
		public void setWriteListener(WriteListener arg0)
		{
			/* implementation not required */
		}

		public String getContent()
		{
			return baos.toString();
		}

		public JSONObject getContentAsJSON()
		{
			String content = getContent();
			return new JSONObject(content);
		}

		public JSONArray getContentAsJSONArray()
		{
			String content = getContent();
			return new JSONArray(content);
		}

		public String getContentUnzipped() throws IOException
		{
			try (GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(baos.toByteArray())))
			{
				byte[] bytes = IOUtils.toByteArray(gis);
				return new String(bytes, StandardCharsets.UTF_8);
			}
		}
	}

	private static class ParameterNames implements Enumeration<String>
	{
		private List<String> parameters = null;
		private int current = 0;

		ParameterNames(List<String> parameters)
		{
			this.parameters = parameters;
		}

		@Override
		public boolean hasMoreElements()
		{
			if (parameters == null) return false;
			return current < parameters.size();
		}

		@Override
		public String nextElement()
		{
			if (parameters == null) return null;
			String e = parameters.get(current);
			current++;
			return e;
		}
	}

}
