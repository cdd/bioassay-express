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
import com.cdd.bae.rest.RESTException.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.apache.commons.io.*;
import org.hamcrest.*;
import org.junit.jupiter.api.*;
import java.nio.charset.*;

/*
	Test for DownloadCompounds
*/

public class DownloadAnnotationsTest
{
	private final TestUtilities.MuteLogger muteLogger = new TestUtilities.MuteLogger(DownloadAnnotations.class);
	DownloadAnnotations servlet;

	@BeforeEach
	public void prepare() throws ConfigurationException, IOException
	{
		TestUtilities.ensureOntologies();
		FauxMongo mongo = FauxMongo.getInstance("/testData/db/basic");
		Configuration configuration = TestConfiguration.getConfiguration(false);
		Common.setConfiguration(configuration);
		// required mongo database calls use JavaScript - therefore need to mock
		//Common.setDataStore(MockDataStore.mockedDataStore());
		Common.setDataStore(mongo.getDataStore());
		Common.setProvCache(ProvisionalCache.loaded());

		servlet = new DownloadAnnotations();
		servlet.init();
	}
	
	@Test
	public void testDownloadAll() throws ServletException, IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters(null));
		when(request.getContextPath()).thenReturn("/context/");
		when(request.getServletPath()).thenReturn("servlet/");
		String baseURL = request.getContextPath() + request.getServletPath();
		when(request.getRequestURI()).thenReturn(baseURL + "all.zip");

		MockServletResponse response = new MockServletResponse();

		servlet.doPost(request, response.getResponse());
		try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response.getContentBytes())))
		{
			ZipEntry entry;
			while ((entry = zip.getNextEntry()) != null)
			{
				assertThat(entry.getName(), Matchers.endsWith(".tsv"));
				assertThat(entry.getName(), Matchers.startsWith("annotation_"));
				StringWriter writer = new StringWriter();
				IOUtils.copy(zip, writer, StandardCharsets.UTF_8);
				String s = writer.toString();
				assertThat(s, containsString("count"));
				assertThat(s, containsString("\t"));
			}
		}
		

		// check exception
		when(request.getRequestURI()).thenReturn(baseURL + "incorrect");
		response = new MockServletResponse();
		muteLogger.mute();
		servlet.doPost(request, response.getResponse());
		muteLogger.restore();
		verify(response.getResponse()).setStatus(HTTPStatus.INTERNAL_SERVER_ERROR.code());
	}

	@Test
	public void testDownloadOne() throws ServletException, IOException
	{
		HttpServletRequest request = MockRESTUtilities.mockedPOSTRequest(getParameters("http://www.bioassayontology.org/bao#BAO_0002854"));
		String baseURL = "/context/servlet/";
		when(request.getRequestURI()).thenReturn(baseURL + "one.tsv");

		MockServletResponse response = new MockServletResponse();

		servlet.doPost(request, response.getResponse());
		assertThat(response.getContent(), containsString("count"));
		assertThat(response.getContent(), containsString("\t"));

		// check exception
		when(request.getRequestURI()).thenReturn(baseURL + "incorrect");
		response = new MockServletResponse();
		muteLogger.mute();
		servlet.doPost(request, response.getResponse());
		muteLogger.restore();
		verify(response.getResponse()).setStatus(HTTPStatus.INTERNAL_SERVER_ERROR.code());
	}

	// - private methods

	private static Map<String, String> getParameters(String propURI)
	{
		Map<String, String> parameters = new HashMap<>();
		parameters.put("propURI", propURI);
		return parameters;
	}

}
