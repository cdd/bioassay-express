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
import com.cdd.bae.util.*;

import static com.cdd.bae.data.DataStore.*;

import java.util.*;

import org.bson.*;

import com.mongodb.client.*;
import com.mongodb.client.result.*;

/*
	Specialisation based on DataStore: provides access to provisional term content only.
*/

public class DataProvisional
{
	private DataStore store;

	// values used by the .status field; this list is nominally a superset of the status values used by the external OntoloBridge service
	public static final String BRIDGESTATUS_UNSUBMITTED = "unsubmitted"; // term requested created in BAE but otherwise unsubmitted to ontolobridge
	public static final String BRIDGESTATUS_SUBMITTED = "submitted"; // term request submitted to ontolobridge
	public static final String BRIDGESTATUS_REJECTED = "rejected"; // after submission to ontolobridge term request is rejected
	public static final String BRIDGESTATUS_EXPIRED = "expired"; // term request was submitted to ontolobridge but expired before approval
	public static final String BRIDGESTATUS_UNDER_REVIEW = "underReview"; // term requested submitted to ontolobridge and under review
	public static final String BRIDGESTATUS_ACCEPTED = "accepted"; // term request accepted by ontolobridge

	// ------------ public methods ------------

	public DataProvisional(DataStore store)
	{
		this.store = store;
	}

	public long getNextSequence()
	{
		return store.getNextSequence(DataStore.SEQ_ID_PROVISIONAL);
	}
	
	public long getNextURISequence()
	{
		return store.getNextSequence(DataStore.SEQ_PROVISIONAL_URI);
	}
	
	// fetches a single provisional term, given its primary key identifier
	public Provisional getProvisional(long provisionalID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_PROVISIONAL);
		Document filter = new Document(FLD_PROVISIONAL_ID, provisionalID);
		for (Document doc : coll.find(filter).limit(1)) return fromDoc(doc);
		return null;
	}
	public Provisional getProvisionalURI(String uri)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_PROVISIONAL);
		Document filter = new Document(FLD_PROVISIONAL_URI, uri);
		for (Document doc : coll.find(filter).limit(1)) return fromDoc(doc);
		return null;
	}

	// delete provisional term with the specified database ID
	public boolean deleteProvisional(long provisionalID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_PROVISIONAL);
		Document idx = new Document(FLD_PROVISIONAL_ID, provisionalID);
		DeleteResult delResult = coll.deleteOne(idx);
		return delResult.getDeletedCount() > 0;
	}

	// total # of provisional term, with no qualifiers
	public int countProvisionals()
	{
		return (int)store.db.getCollection(COLL_PROVISIONAL).countDocuments();
	}

	// modifies the provisional term (if a database ID is present), or adds it otherwise;
	// it will also manufacture an ID if there is none already
	public void updateProvisional(Provisional prov)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_PROVISIONAL);

		Date now = new Date();
		Document doc = new Document();
		doc.append(FLD_PROVISIONAL_ID, prov.provisionalID);
		doc.append(FLD_PROVISIONAL_PARENTURI, prov.parentURI);
		doc.append(FLD_PROVISIONAL_LABEL, prov.label);
		doc.append(FLD_PROVISIONAL_URI, prov.uri);
		doc.append(FLD_PROVISIONAL_DESCRIPTION, prov.description);
		doc.append(FLD_PROVISIONAL_EXPLANATION, prov.explanation);
		doc.append(FLD_PROVISIONAL_PROPOSERID, prov.proposerID);
		if (prov.role != null) doc.append(FLD_PROVISIONAL_ROLE, prov.role.toString());
		doc.append(FLD_PROVISIONAL_MODIFIEDTIME, now.getTime());
		doc.append(FLD_PROVISIONAL_REMAPPEDTO, prov.remappedTo);
		doc.append(FLD_PROVISIONAL_BRIDGESTATUS, prov.bridgeStatus);
		doc.append(FLD_PROVISIONAL_BRIDGEURL, prov.bridgeURL);
		doc.append(FLD_PROVISIONAL_BRIDGETOKEN, prov.bridgeToken);

		// update or create, depending on whether it already has an ID
		if (prov.provisionalID > 0)
		{
			Document idx = new Document(FLD_PROVISIONAL_ID, prov.provisionalID);
			coll.updateOne(idx, new Document("$set", doc));
		}
		else
		{
			prov.provisionalID = getNextSequence();
			doc.append(FLD_PROVISIONAL_ID, prov.provisionalID);
			doc.append(FLD_PROVISIONAL_CREATEDTIME, now.getTime());
			coll.insertOne(doc);
		}
	}

	// return parent of the provisional term with specified uri, or null if none exists
	public String getParentTerm(String uri)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_PROVISIONAL);
		Document filter = new Document(FLD_PROVISIONAL_URI, uri);
		Document proj = new Document(FLD_PROVISIONAL_PARENTURI, true);
		for (Document doc : coll.find(filter).projection(proj)) return doc.getString(FLD_PROVISIONAL_PARENTURI);
		return null;
	}

	// fetch every term
	public Provisional[] fetchAllTerms()
	{
		List<Provisional> provTerms = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_PROVISIONAL);
		for (Document doc : coll.find()) provTerms.add(fromDoc(doc));
		return provTerms.toArray(new Provisional[0]);
	}
	
	// return all the terms with the given parent (which may be a provisional term or a more permanent one)
	public Provisional[] fetchChildTerms(String parentURI)
	{
		List<Provisional> provTerms = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_PROVISIONAL);
		Document filter = new Document(FLD_PROVISIONAL_PARENTURI, parentURI);
		for (Document doc : coll.find(filter)) provTerms.add(fromDoc(doc));
		return provTerms.toArray(new Provisional[0]);
	}

	// ------------ private methods ------------

	protected static Provisional fromDoc(Document doc)
	{
		Provisional prov = new Provisional();
		prov.provisionalID = doc.getLong(FLD_PROVISIONAL_ID);
		prov.parentURI = doc.getString(FLD_PROVISIONAL_PARENTURI);
		prov.label = doc.getString(FLD_PROVISIONAL_LABEL);
		prov.uri = doc.getString(FLD_PROVISIONAL_URI);
		prov.description = doc.getString(FLD_PROVISIONAL_DESCRIPTION);
		prov.explanation = doc.getString(FLD_PROVISIONAL_EXPLANATION);
		prov.proposerID = doc.getString(FLD_PROVISIONAL_PROPOSERID);
		String role = doc.getString(FLD_PROVISIONAL_ROLE);
		try
		{
			if (role != null) prov.role = DataObject.ProvisionalRole.fromString(role);
		}
		catch (IllegalArgumentException ex) {} // silent fail if no match: leave as null
		try
		{
			prov.createdTime = new Date(doc.getLong(FLD_PROVISIONAL_CREATEDTIME));
			prov.modifiedTime = new Date(doc.getLong(FLD_PROVISIONAL_MODIFIEDTIME));
		}
		catch (ClassCastException ex) {} // silent fail if not date; can remove this later on
		prov.remappedTo = doc.getString(FLD_PROVISIONAL_REMAPPEDTO);
		prov.bridgeStatus = doc.getString(FLD_PROVISIONAL_BRIDGESTATUS);
		prov.bridgeURL = doc.getString(FLD_PROVISIONAL_BRIDGEURL);
		prov.bridgeToken = doc.getString(FLD_PROVISIONAL_BRIDGETOKEN);
		
		return prov;
	}
}
