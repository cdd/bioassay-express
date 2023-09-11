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

import com.cdd.bae.data.*;
import com.cdd.bao.util.*;
import com.mongodb.*;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;

import de.bwaldvogel.mongo.*;
import de.bwaldvogel.mongo.backend.memory.*;

import java.io.*;
import java.net.*;
import java.util.*;

import org.bson.*;
import org.json.*;

/*
	Fake MongoDB driver that provides an embedded instance to more accurately simulate derived functionality. It is intended
	to be used as a single instance that runs for the lifetime of the execution of the JUnit tests.
*/

public class FauxMongo
{
	private static Map<String, FauxMongo> mapInstances = new HashMap<>(); // dir-to-instance

	private static final String DB_NAME = "bae";

	private TestResourceFile contentDir;
	private MongoServer server;
	private MongoClient mongo;
	private MongoDatabase database;
	private DataStore store = null;
	private int dbHash;

	// ------------ public methods ------------

	// public invocation, which provides a singleton instance for each unique content directory
	public static FauxMongo getInstance(String contentName)
	{
		TestResourceFile contentDir = new TestResourceFile(contentName);
		try
		{
			FauxMongo instance = mapInstances.get(contentDir.getPath());
			if (instance == null)
			{
				instance = new FauxMongo(contentDir);
				instance.setup();
				mapInstances.put(contentDir.getPath(), instance);

				final FauxMongo hook = instance;
				Runtime.getRuntime().addShutdownHook(new Thread(() -> hook.shutdown()));
			}
			else if (instance.dbHash != instance.getDatabaseHash()) 
			{
				instance.restoreContent();
			}
			else
			{ /* database not changed */ }
			return instance;
		}
		catch (Exception ex)
		{
			// NOTE: this method is invoked from the test classes in a special static initialiser; unfortunately if that
			// method throws an exception, it causes JUnit to just skip that class without issuing a warning, which breaks
			// everything; returning null will ensure that the subsequent tests fail hard, and the stack trace is available
			// in the console
			Util.writeln("Global FauxMongo failure");
			ex.printStackTrace();
			return null;
		}
	}

	private FauxMongo(TestResourceFile contentDir)
	{
		this.contentDir = contentDir;
	}

	// create a hash key for the current database content
	public int getDatabaseHash()
	{
		Map<String, Integer> result = new HashMap<>();
		for (String colName : database.listCollectionNames())
		{
			List<Integer> h = new ArrayList<>();
			for (Document doc : database.getCollection(colName).find())
			{
				JSONObject json = new JSONObject(doc.toJson());
				json.remove("_id");
				h.add(json.toString().hashCode());
			}
			Collections.sort(h);
			result.put(colName, h.hashCode());
		}
		return result.hashCode();
	}

	// returns the instantiated datastore, which can be subbed-in to the Common instance
	public DataStore getDataStore() {return store;}

	// heavyweight operation that guts the current list of collections and reloads the data from the filesystem; can be
	// useful after a test is expected to leave stains that could interfere with another test; note that it also clears
	// the tainted flag
	// in general, this method needs only to be called in rare cases
	public void restoreContent() throws IOException
	{
		emptyAllCollections();
		inloadContent();
	}

	// ------------ private methods ------------

	// setup the server to take connections, and then go through to poke the content from the source directory
	private FauxMongo setup() throws IOException
	{
		server = new MongoServer(new MemoryBackend());
		InetSocketAddress serverAddress = server.bind(); // random port
		mongo = new MongoClient(new ServerAddress(serverAddress));
		database = mongo.getDatabase(DB_NAME);
		store = new DataStore(mongo, database);

		restoreContent();
		return this;
	}

	private FauxMongo shutdown()
	{
		database = null;
		if (mongo != null) mongo.close();
		mongo = null;
		if (server != null) server.shutdown();
		server = null;
		return this;
	}

	// scan a list of JSON files in a given directory and insert them into the corresponding collections
	private void inloadContent() throws IOException
	{
		Set<String> jsonFiles = new TreeSet<>();
		for (TestResourceFile res : contentDir.enumerateFiles()) if (res.getName().endsWith(".json")) jsonFiles.add(res.getPath());

		// all eligible JSON files should be of the form "{prefix}_*.json", and contain an array of all of the documents
		// that should be loaded into the corresponding collection
		final String[] COLLECTIONS =
		{
			"assay",
			"holding",
			"provisional",
			"user",
			"sequences",
			"model",
			"annotations",
			"compound",
			"measurement",
			"template",
			/* TBD? some of these are probably irrelevant
			compound
			loadfiles
			nlp
			*/
		};
		for (String collName : COLLECTIONS)
		{
			for (Iterator<String> it = jsonFiles.iterator(); it.hasNext();)
			{
				TestResourceFile res = new TestResourceFile(it.next());
				if (res.getName().startsWith(collName + "_"))
				{
					try
					{
						loadJSONFile(res.getContent(), collName);
					}
					catch (Exception ex)
					{
						throw new IOException("Failed to load " + res.getPath(), ex);
					}
					it.remove();
				}
			}
		}
		if (!jsonFiles.isEmpty()) throw new IOException("FauxMongo directory [" + contentDir + "] has unrecognised files: " + jsonFiles);
		dbHash = getDatabaseHash();
	}

	// loads up a single JSON file, and jams each entry into the given collection
	private void loadJSONFile(String jsonStr, String collName) throws IOException
	{
		MongoCollection<Document> coll = database.getCollection(collName);
		long countThen = coll.countDocuments();

		// parse the BSON-style string; note that because it is an array, and the parser only has an object entrypoint, it gets
		// wrapped with object syntax then deferenced
		Document enclosing = Document.parse("{\"list\":" + jsonStr + "}");
		List<?> dblist = (List<?>)enclosing.get("list");

		if (dblist == null || dblist.isEmpty()) throw new IOException("Invalid/unparseable content");

		for (int n = 0; n < dblist.size(); n++)
		{
			Document doc = (Document)dblist.get(n);
			coll.insertOne(doc);
		}

		// quick sanity check to make sure loading really did happen
		long countNow = coll.countDocuments();
		if (countNow != countThen + dblist.size()) throw new IOException("Document insertion failed");
	}

	// remove all documents from all collections
	private void emptyAllCollections() throws IOException
	{
		for (String collName : database.listCollectionNames())
		{
			MongoCollection<Document> coll = database.getCollection(collName);
			coll.deleteMany(new Document());
		}
	}
}
