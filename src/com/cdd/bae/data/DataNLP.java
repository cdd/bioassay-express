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

import java.util.*;

import com.mongodb.client.*;
import org.bson.*;

/*
	Specialisation based on DataStore: provides access to NLP fingerprint content only.
*/

public class DataNLP
{
	private DataStore store;
	
	protected long cacheWatermark = -1;
	protected Map<String, Integer> cacheMapping = null;

	// ------------ public methods ------------

	public DataNLP(DataStore store)
	{
		this.store = store;
	}

	public int countFingerprints()
	{
		return (int)store.db.getCollection(COLL_NLP).countDocuments();
	}

	// fetches the current NLP-to-fingerprint mappings
	// NOTE: this is in a synchronized block because it makes use of the underlying cache; fetching all the entries from the database is rather slow, and
	// they don't change all that often, so it's easiest to keep the latest edition hanging around
	// NOTE also: the caller must take care not to modify the result
	public synchronized Map<String, Integer> fetchFingerprints()
	{
		long watermark = getWatermark();
		if (cacheMapping != null && cacheWatermark == watermark) return cacheMapping;
	
		Map<String, Integer> mapping = new TreeMap<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_NLP);
		for (Document doc : coll.find())
		{
			String block = doc.getString(FLD_NLP_BLOCK);
			int fp = doc.getInteger(FLD_NLP_FP);
			mapping.put(block, fp);
		}

		cacheWatermark = watermark;
		cacheMapping = mapping;

		return mapping;
	}
	
	// zap everything
	public void deleteAllFingerprints()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_NLP);
		coll.deleteMany(new Document());
		nextWatermark();
	}

	// inserts a new fingerprint into the NLP-to-fingerprint mappings; note that indexes enforce no duplicates
	public void addNLPFingerprint(String nlp, int fp)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_NLP);
		Document doc = new Document(FLD_NLP_BLOCK, nlp).append(FLD_NLP_FP, fp);
		coll.insertOne(doc);
		nextWatermark();
	}

	// NLP fingerprint watermark: bumped each time the underlying data is changed
	public long getWatermark() {return store.getSequence(SEQ_WATERMARK_NLP);}
	public long nextWatermark() {return store.getNextSequence(SEQ_WATERMARK_NLP);}

	// ------------ private methods ------------

}
