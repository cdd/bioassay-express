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

import org.bson.*;

import com.mongodb.client.*;
import com.mongodb.client.model.*;

/*
	Specialisation based on DataStore: provides access to annotation fingerprints only.
*/

public class DataAnnot
{
	private DataStore store;

	// ------------ public methods ------------

	public DataAnnot(DataStore store)
	{
		this.store = store;
	}

	// fetch all of the annotation fingerprints
	public AnnotationFP[] fetchAnnotationFP()
	{
		List<AnnotationFP> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ANNOT);
		for (Document doc : coll.find())
		{
			AnnotationFP annot = new AnnotationFP();
			annot.propURI = doc.getString(FLD_ANNOT_PROPURI);
			annot.valueURI = doc.getString(FLD_ANNOT_VALUEURI);
			annot.fp = doc.getInteger(FLD_ANNOT_FP);
			list.add(annot);
		}
		return list.toArray(new AnnotationFP[list.size()]);
	}

	// fetch for a specific property
	public AnnotationFP[] fetchAnnotationFP(String propURI)
	{
		List<AnnotationFP> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ANNOT);
		for (Document doc : coll.find(new Document(FLD_ANNOT_PROPURI, propURI)))
		{
			AnnotationFP annot = new AnnotationFP();
			annot.propURI = doc.getString(FLD_ANNOT_PROPURI);
			annot.valueURI = doc.getString(FLD_ANNOT_VALUEURI);
			annot.fp = doc.getInteger(FLD_ANNOT_FP);
			list.add(annot);
		}
		return list.toArray(new AnnotationFP[list.size()]);
	}

	// inserts a new fingerprint into the annotation-to-fingerprint mappings
	public void addAssnFingerprint(String propURI, String valueURI, int fp)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ANNOT);
		Document doc = new Document(FLD_ANNOT_PROPURI, propURI).append(FLD_ANNOT_VALUEURI, valueURI);
		doc.append(FLD_ANNOT_FP, fp);
		coll.insertOne(doc);
	}
	
	// returns true if the annotation pair exists at all (regardless of schema)
	public boolean hasAnnotation(String propURI, String valueURI)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ANNOT);
		Document idx = new Document(FLD_ANNOT_PROPURI, propURI).append(FLD_ANNOT_VALUEURI, valueURI);
		return coll.countDocuments(idx, new CountOptions().limit(1)) > 0;
	}
	
	// zap everything
	public void deleteAllAnnotations()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ANNOT);
		coll.deleteMany(new Document());
	}
	

	// ------------ private methods ------------

}
