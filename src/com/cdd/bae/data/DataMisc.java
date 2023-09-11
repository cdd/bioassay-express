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

import static com.cdd.bae.data.DataStore.*;

import java.io.*;
import java.util.*;

import org.bson.*;

import com.mongodb.client.*;
import com.mongodb.client.model.*;

/*
	Specialisation based on DataStore: provides access to miscellaneous small collections that only have a couple of access modes.
*/

public class DataMisc
{
	private DataStore store;

//	protected static final String FLD_LOADFILES_DATE = "date"; // last modified date (long)
//	protected static final String FLD_LOADFILES_SIZE = "size"; // file size (long)


	// ------------ public methods ------------

	public DataMisc(DataStore store)
	{
		this.store = store;
	}

	// fetches the list of paths that represent assay files that have already been scanned and loaded, i.e. no need to look at them again
	public LoadedFile[] getLoadedFiles()
	{
		List<LoadedFile> files = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_LOADFILES);
		for (Document doc : coll.find())
		{
			LoadedFile lf = new LoadedFile();
			lf.path = doc.getString(FLD_LOADFILES_PATH);
			Long date = doc.getLong(FLD_LOADFILES_DATE);
			Long size = doc.getLong(FLD_LOADFILES_SIZE);
			lf.date = date == null ? 0 : date;
			lf.size = size == null ? 0 : size;
			files.add(lf);
		}
		return files.toArray(new LoadedFile[files.size()]);
	}
	
	// ensures that an indicated path is in the collection
	public void submitLoadedFile(String path)
	{
		File f = new File(path);
		if (!f.exists()) return;

		MongoCollection<Document> coll = store.db.getCollection(COLL_LOADFILES);
		Document idx = new Document(FLD_LOADFILES_PATH, path);
		Document doc = new Document(FLD_LOADFILES_PATH, path).append(FLD_LOADFILES_DATE, f.lastModified()).append(FLD_LOADFILES_SIZE, f.length());
		coll.replaceOne(idx, doc, new ReplaceOptions().upsert(true));
	}
	
	// if the indicated path is in the collection, make it not so; returns true if it actually deleted anything
	public boolean unsubmitLoadedFile(String path)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_LOADFILES);
		Document doc = new Document(FLD_LOADFILES_PATH, path);
		return coll.deleteOne(doc).getDeletedCount() > 0;
	}

	// ------------ private methods ------------

}
