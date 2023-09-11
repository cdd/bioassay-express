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
import com.cdd.bae.rest.MockRESTUtilities.*;
import com.cdd.testutil.*;

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;

import javax.servlet.*;
import javax.servlet.http.*;

import org.hamcrest.core.*;
import org.junit.jupiter.api.*;

/*
	Test for CustomResource
*/

public class CustomResourceTest extends TestBaseClass
{
	private final TestUtilities.MuteLogger muteLogger = new TestUtilities.MuteLogger(CustomResource.class);
	private static final String RESOURCE_FILE = "aspirin.mol";
	File customDir;
	File resource;
	File secretResource;

	public TestResourceFile aspirinMol = new TestResourceFile("/testData/cheminf/aspirin.mol");

	@BeforeEach
	public void initialize() throws IOException
	{
		customDir = createFolder("custom");
		resource = aspirinMol.getAsFile(new File(customDir, RESOURCE_FILE));
		secretResource = aspirinMol.getAsFile(createFile("secret"));

		Configuration configuration = mock(Configuration.class);
		when(configuration.getCustomWebDir()).thenReturn(customDir.getAbsolutePath());
		Common.setConfiguration(configuration);
	}

	@Test
	public void testEmbed() throws IOException
	{
		String content = CustomResource.embed(RESOURCE_FILE);
		assertEquals(aspirinMol.getContent(), content);

		muteLogger.mute();
		content = CustomResource.embed("resourceMissing");
		muteLogger.restore();
		assertThat(content, StringStartsWith.startsWith("Fatal exception"));
	}

	@Test
	public void testFindFile() throws IOException, ServletException
	{
		File found = CustomResource.findFile(RESOURCE_FILE);
		assertThat(found.getCanonicalPath(), StringStartsWith.startsWith(customDir.getCanonicalPath()));
		assertThat(found.getCanonicalPath(), StringEndsWith.endsWith(RESOURCE_FILE));

		muteLogger.mute();
		
		Assertions.assertThrows(ServletException.class, () -> CustomResource.findFile("resourceMissing"));
		// it must not be possible to access resources outside of the defined folders
		Assertions.assertThrows(ServletException.class, () -> CustomResource.findFile("../secret"));

		muteLogger.restore();
	}

	@Test
	public void testDoGet() throws ServletException, IOException
	{
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getContextPath()).thenReturn("/context/");
		when(request.getServletPath()).thenReturn("servlet/");
		String baseURL = "http://hostname" + request.getContextPath() + request.getServletPath();

		when(request.getRequestURL()).thenReturn(new StringBuffer().append(baseURL).append(RESOURCE_FILE));

		MockServletResponse response = new MockServletResponse();

		CustomResource servlet = new CustomResource();
		servlet.doGet(request, response.getResponse());
		assertEquals(aspirinMol.getContent(), response.getContent());
	}

	// - private methods

}
