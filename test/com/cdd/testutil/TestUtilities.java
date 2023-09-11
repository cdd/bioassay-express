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

package com.cdd.testutil;

import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.*;

import static org.hamcrest.MatcherAssert.*;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;
import java.util.zip.*;

import org.json.*;
import org.slf4j.*;

/*
	Collection of useful methods for testing
*/

public class TestUtilities
{
	private TestUtilities()
	{
		throw new IllegalStateException("Utility class");
	}
	
	// make sure that the global Common class has the normal ontology tree loaded
	public static void ensureOntologies() throws IOException
	{
		if (Common.getOntoProps() != null && Common.getOntoValues() != null) return;
		
		var file = new TestResourceFile("/testData/ontology/ontology.gz");
		//try (var istr = context.getResourceAsStream("/WEB-INF/data/ontology.gz"))
		try (var istr = file.getAsStream())
		{
			var gzip = new GZIPInputStream(istr);
			var ontoProps = OntologyTree.deserialise(gzip);
			var ontoValues = OntologyTree.deserialise(gzip);
			gzip.close();
			Common.setOntologies(ontoProps, ontoValues);
		}
	}	
	
	// checks all fields of a class if they are handled by equals or hashCode
	// it will not check private fields
	public static <T> void assertEquality(Supplier<T> supplier, String... ignore)
	{
		List<String> ignoreFields = new ArrayList<>(Arrays.asList(ignore));
		ignoreFields.add("$jacocoData");
		ignoreFields.add("logger");
		T source1 = supplier.get();
		assertThat(source1, is(not("abc")));
		assertThat(source1.equals(null), is(false));
		
		for (Field field : source1.getClass().getDeclaredFields())
		{
			if (ignoreFields.contains(field.getName())) continue;
			int modifiers = field.getModifiers();
			if (!Modifier.isPublic(modifiers)) continue;
			T source2 = supplier.get();
			assertThat(source1, is(source2));
			assertThat(field.getName() + " should not cause difference", source1.hashCode(), is(source2.hashCode()));
			field.setAccessible(true);
			try
			{
				if (field.getType().equals(boolean.class))
					field.set(source2, !Boolean.parseBoolean(field.get(source2).toString()));
				else if (field.getType().equals(int.class))
					field.set(source2, -Integer.parseInt(field.get(source2).toString()));
				else
					field.set(source2, null);
			}
			catch (IllegalArgumentException | IllegalAccessException e)
			{
				throw new AssertionError("Unable to set field " + field.getName());
			}
			assertThat(field.getName() + " not covered by equals", source1, is(not(source2)));
			assertThat(field.getName() + " not covered by hashCode", source1.hashCode(), not(source2.hashCode()));
		}
	}


	// mock a logger
	public static Logger mockLogger()
	{
		return mock(Logger.class);
	}

	public static void assertErrorResponse(JSONObject json, RESTException.HTTPStatus httpStatus)
	{
		assertEquals(httpStatus.code(), json.getInt("status"));
		assertTrue(json.has("userMessage"));
		assertTrue(json.has("developerMessage"));
	}

	public static Session mockNoSession()
	{
		return null;
	}

	public static Session mockSession()
	{
		Session session = new Session();
		session.curatorID = "Default";
		session.status = DataUser.STATUS_DEFAULT;
		return session;
	}

	public static Session mockSessionCurator()
	{
		Session session = new Session();
		session.curatorID = "Curator";
		session.status = DataUser.STATUS_CURATOR;
		session.userID = "12345";
		return session;
	}

	public static Session mockSessionAdmin()
	{
		Session session = new Session();
		session.curatorID = "Admin";
		session.status = DataUser.STATUS_ADMIN;
		session.userID = "12345";
		return session;
	}

	public static Session mockSessionBlocked()
	{
		Session session = new Session();
		session.curatorID = "Blocked";
		session.status = DataUser.STATUS_BLOCKED;
		return session;
	}	

	public static Session mockSessionTrusted()
	{
		Session session = new Session();
		session.serviceName = "Trusted";
		session.curatorID = "trusted:anyone";
		session.status = DataUser.STATUS_ADMIN;
		session.userID = "anyone";
		session.userName = "Trusted";
		session.email = "";
		return session;
	}

	public static Session mockSessionOAuth()
	{
		Session session = new Session();
		session.serviceName = "Google";
		session.curatorID = "google:101753031175172878470";
		session.status = DataUser.STATUS_DEFAULT;
		session.userID = "101753031175172878470";
		session.userName = "lloyd";
		session.email = "lloyd.blankfein@gmail.com";
		return session;
	}

	public static Session mockSessionOAuthAdmin()
	{
		Session session = new Session();
		session.serviceName = "Google";
		session.curatorID = "google:101753031175172878470";
		session.status = DataUser.STATUS_ADMIN;
		session.userID = "101753031175172878470";
		session.userName = "lloyd";
		session.email = "lloyd.blankfein@gmail.com";
		return session;
	}
	
	public static class MuteLogger
	{
		org.apache.log4j.Level oldLevel;
		private org.apache.log4j.Logger logger;
		
		@SuppressWarnings("rawtypes")
		public MuteLogger(Class clazz)
		{
			logger = org.apache.log4j.LogManager.getLogger(clazz);
			oldLevel = logger.getLevel();
		}
		
		public void mute()
		{
			logger.setLevel(org.apache.log4j.Level.FATAL);
		}

		public void restore()
		{
			logger.setLevel(oldLevel);
		}
	}
}
