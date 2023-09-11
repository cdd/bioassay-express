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

import com.cdd.bae.data.DataObject.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;
import com.mongodb.client.*;
import com.mongodb.client.result.*;

import static com.cdd.bae.data.DataStore.*;

import java.util.*;

import org.bson.*;

/*
	Specialisation based on DataStore: provides access to keyword mapping.
	
	When valueURI is defined, it refers to a value within the hierarchy for the assignment. When not defined, the mapping is
	to the assigment itself.
*/

public class DataKeywordMap
{
	private DataStore store;

	// ------------ public methods ------------

	public DataKeywordMap(DataStore store)
	{
		this.store = store;
	}

	public long getNextSequence()
	{
		return store.getNextSequence(DataStore.SEQ_ID_KEYWORDMAP);
	}
	
	public long getNextURISequence()
	{
		return store.getNextSequence(DataStore.SEQ_ID_KEYWORDMAP);
	}

	// fetches a keyword mapping by ID
	public KeywordMap getKeywordMap(long keywordmapID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_KEYWORDMAP);
		Document filter = new Document(FLD_KEYWORDMAP_ID, keywordmapID);
		for (Document doc : coll.find(filter).limit(1)) return fromDoc(doc);
		return null;
	}
	
	// looks up a keyword assignment mapping based on the keys, or null if there's nothing
	public KeywordMap getKeywordMapAssignment(String schemaURI, String keyword)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_KEYWORDMAP);
		Document filter = new Document(FLD_KEYWORDMAP_SCHEMAURI, schemaURI);
		filter.append(FLD_KEYWORDMAP_KEYWORD, keyword);
		filter.append(FLD_KEYWORDMAP_VALUEURI, null);
		for (Document doc : coll.find(filter).limit(1)) return fromDoc(doc);
		return null;
	}
	
	// looks up a keyword value mapping based on the keys, or null if there's nothing
	public KeywordMap getKeywordMapValue(String schemaURI, String propURI, String[] groupNest, String keyword)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_KEYWORDMAP);
		Document filter = new Document(FLD_KEYWORDMAP_SCHEMAURI, schemaURI);
		filter.append(FLD_KEYWORDMAP_PROPURI, propURI);
		if (groupNest != null) filter.append(FLD_KEYWORDMAP_GROUPNEST, BSONUtil.toList(groupNest));
		filter.append(FLD_KEYWORDMAP_KEYWORD, keyword);
		filter.append(FLD_KEYWORDMAP_VALUEURI, new Document("$ne", null));
		for (Document doc : coll.find(filter).limit(1)) return fromDoc(doc);
		return null;
	}

	// delete keyword mapping with the specified database ID
	public boolean deleteKeywordMap(long keywordmapID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_KEYWORDMAP);
		Document idx = new Document(FLD_KEYWORDMAP_ID, keywordmapID);
		DeleteResult delResult = coll.deleteOne(idx);
		return delResult.getDeletedCount() > 0;
	}
	
	// make sure the keyword mapping is present, overwriting any previous association
	public void defineKeywordMapAssignment(String schemaURI, String keyword, String propURI, String[] groupNest)
	{
		KeywordMap map = getKeywordMapAssignment(schemaURI, keyword);
		if (map == null)
		{
			map = new KeywordMap();
			map.schemaURI = schemaURI;
			map.propURI = propURI;
			map.groupNest = groupNest;
			map.keyword = keyword;
			map.valueURI = null;
			updateKeywordMap(map);
		}
		else if (!Schema.samePropGroupNest(propURI, groupNest, map.propURI, map.groupNest))
		{
			map.valueURI = map.valueURI;
			updateKeywordMap(map);
		}
	}
	public void defineKeywordMapValue(String schemaURI, String propURI, String[] groupNest, String keyword, String valueURI)
	{
		KeywordMap map = getKeywordMapValue(schemaURI, propURI, groupNest, keyword);
		if (map == null)
		{
			map = new KeywordMap();
			map.schemaURI = schemaURI;
			map.propURI = propURI;
			map.groupNest = groupNest;
			map.keyword = keyword;
			map.valueURI = valueURI;
			updateKeywordMap(map);
		}
		else if (!map.valueURI.equals(valueURI))
		{
			map.valueURI = map.valueURI;
			updateKeywordMap(map);
		}
	}

	// modifies the keyword mapping (if a database ID is present), or adds it otherwise;
	// it will also manufacture an ID if there is none already
	public void updateKeywordMap(KeywordMap map)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_KEYWORDMAP);

		Date now = new Date();
		Document doc = new Document();
		doc.append(FLD_KEYWORDMAP_ID, map.keywordmapID);
		doc.append(FLD_KEYWORDMAP_SCHEMAURI, map.schemaURI);
		doc.append(FLD_KEYWORDMAP_PROPURI, map.propURI);
		doc.append(FLD_KEYWORDMAP_GROUPNEST, BSONUtil.toList(map.groupNest));
		doc.append(FLD_KEYWORDMAP_KEYWORD, map.keyword);
		doc.append(FLD_KEYWORDMAP_VALUEURI, map.valueURI);

		// update or create, depending on whether it already has an ID
		if (map.keywordmapID > 0)
		{
			Document idx = new Document(FLD_KEYWORDMAP_ID, map.keywordmapID);
			coll.updateOne(idx, new Document("$set", doc));
		}
		else
		{
			map.keywordmapID = getNextSequence();
			doc.append(FLD_KEYWORDMAP_ID, map.keywordmapID);
			coll.insertOne(doc);
		}
	}

	// ------------ private methods ------------

	protected static KeywordMap fromDoc(Document doc)
	{
		KeywordMap map = new KeywordMap();
		map.keywordmapID = doc.getLong(FLD_KEYWORDMAP_ID);
		map.schemaURI = doc.getString(FLD_KEYWORDMAP_SCHEMAURI);
		map.propURI = doc.getString(FLD_KEYWORDMAP_PROPURI);
		map.groupNest = BSONUtil.getStringArray(doc, FLD_KEYWORDMAP_GROUPNEST);
		map.keyword = doc.getString(FLD_KEYWORDMAP_KEYWORD);
		map.valueURI = doc.getString(FLD_KEYWORDMAP_VALUEURI);
		return map;
	}
}
