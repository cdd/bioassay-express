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

package com.cdd.bae.data;

import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.net.*;
import java.util.*;

import org.bson.*;
import org.junit.jupiter.api.*;

import de.bwaldvogel.mongo.*;
import de.bwaldvogel.mongo.backend.memory.*;

/*
	Making sure the embedded MongoDB service works (a minimal proof-of-life test for the dependency).
*/

public class FauxMongoTest
{
	@Test
	public void testEmbedMongo() throws IOException
	{
		MongoServer server = null;
		MongoClient mongo = null;
	
		try
		{
			server = new MongoServer(new MemoryBackend());
			InetSocketAddress serverAddress = server.bind(); // random port
			mongo = new MongoClient(new ServerAddress(serverAddress));
		
			MongoDatabase db = mongo.getDatabase("test");
			MongoCollection<Document> coll = db.getCollection("testCol");
			coll.insertOne(new Document("testDoc", new BasicDBObject("fnord", 1)));
			
			boolean hit = false;
			for (Document doc : coll.find())
			{
				//Util.writeln("DOC:"+doc);
				assertTrue(!hit, "Want just one document");
				Map<?, ?> subDoc = (Map<?, ?>)doc.get("testDoc");
				int fnord = (Integer)subDoc.get("fnord");
				assertEquals(fnord, 1);
				hit = true;
			}
			assertTrue(hit, "Inserted document didn't register");
		}
		finally
		{
			if (mongo != null) mongo.close();
			if (server != null) server.shutdown();
		}
	}
}
