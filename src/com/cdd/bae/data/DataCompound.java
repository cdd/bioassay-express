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

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.*;

import org.bson.*;
import org.bson.conversions.*;
import org.bson.types.*;

/*
	Specialisation based on DataStore: provides access to individual compound records.
*/

public class DataCompound
{
	private DataStore store;

	// ------------ public methods ------------

	public DataCompound(DataStore store)
	{
		this.store = store;
	}

	// fetches a single compound, given its database ID	
	public Compound getCompound(String id)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document filter = new Document("_id", new ObjectId(id));
		for (Document doc : coll.find(filter).limit(1)) return compoundFromDoc(doc);
		return null;
	}

	// fetches a single compound, given its primary key identifier
	public Compound getCompound(long compoundID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document filter = new Document(FLD_COMPOUND_ID, compoundID);
		for (Document doc : coll.find(filter).limit(1)) return compoundFromDoc(doc);
		return null;
	}

	// fetches any compound(s) with the corresponding PubChem identifier
	public Compound[] getCompoundsWithPubChemCID(int cid)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document filter = new Document(FLD_COMPOUND_PUBCHEMCID, cid);
		List<Compound> results = new ArrayList<>();
		for (Document doc : coll.find(filter)) results.add(compoundFromDoc(doc));
		return results.toArray(new Compound[results.size()]);
	}

	public Compound[] getCompoundsWithPubChemSID(int sid)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document filter = new Document(FLD_COMPOUND_PUBCHEMSID, sid);
		List<Compound> results = new ArrayList<>();
		for (Document doc : coll.find(filter)) results.add(compoundFromDoc(doc));
		return results.toArray(new Compound[results.size()]);
	}

	// returns the hash code corresponding to the compound; zero is returned if not found (but note that
	// this is not necessarily an indication of failure)
	public int getHashECFP6(long compoundID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document filter = new Document(FLD_COMPOUND_ID, compoundID);
		Document proj = new Document(FLD_COMPOUND_ID, true).append(FLD_COMPOUND_HASHECFP6, true);
		for (Document doc : coll.find(filter).projection(proj).limit(1)) return doc.getInteger(FLD_COMPOUND_HASHECFP6, 0);
		return 0;
	}

	// fetch some number of compounds that have PubChem SIDs but not CIDs, meaning that more downloading
	// is required; up to a maximum number is returned; empty implies nothing to do
	public Compound[] fetchCompoundsNeedCID(int maxCount)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document filter = new Document(FLD_COMPOUND_PUBCHEMSID, new Document("$gt", 0));
		//filter.append(FLD_COMPOUND_PUBCHEMCID, null);
		filter.append(FLD_COMPOUND_MOLFILE, null);
		List<Compound> results = new ArrayList<>();
		for (Document doc : coll.find(filter).limit(maxCount)) results.add(compoundFromDoc(doc));
		return results.toArray(new Compound[results.size()]);
	}

	// fetch some number of compounds that have a Vault molecule ID, but no structure yet, meaning that downloading
	// is required; up to a maximum number is returned; empty implies nothing to do
	public Compound[] fetchCompoundsNeedVaultMol(int maxCount)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document filter = new Document(FLD_COMPOUND_VAULTMID, new Document("$gt", 0));
		filter.append(FLD_COMPOUND_MOLFILE, null);
		List<Compound> results = new ArrayList<>();
		for (Document doc : coll.find(filter).limit(maxCount)) results.add(compoundFromDoc(doc));
		return results.toArray(new Compound[results.size()]);
	}

	// grab the next few compounds with structures but not hash codes
	public Compound[] fetchCompoundsNeedHash(int maxCount)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Bson filter = Filters.and(
				Filters.ne(FLD_COMPOUND_MOLFILE, null),
				Filters.ne(FLD_COMPOUND_MOLFILE, ""),
				Filters.eq(FLD_COMPOUND_HASHECFP6, null));
		List<Compound> results = new ArrayList<>();
		for (Document doc : coll.find(filter).limit(maxCount)) results.add(compoundFromDoc(doc));
		return results.toArray(new Compound[results.size()]);
	}

	// modifies the compound (if a database ID is present), or adds it otherwise; it will also manufacture an ID if there
	// is none already
	public void updateCompound(Compound cpd)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);

		if (cpd.compoundID == 0) cpd.compoundID = store.getNextSequence(SEQ_ID_COMPOUND);

		Document doc = new Document();
		doc.append(FLD_COMPOUND_ID, cpd.compoundID);
		doc.append(FLD_COMPOUND_MOLFILE, cpd.molfile);
		doc.append(FLD_COMPOUND_HASHECFP6, cpd.hashECFP6);
		doc.append(FLD_COMPOUND_PUBCHEMCID, cpd.pubchemCID);
		doc.append(FLD_COMPOUND_PUBCHEMSID, cpd.pubchemSID);

		if (cpd.id != null)
		{
			Document idx = new Document("_id", new ObjectId(cpd.id));
			coll.updateOne(idx, new Document("$set", doc));
		}
		else
		{
			coll.insertOne(doc);
			cpd.id = doc.getObjectId("_id").toHexString();
		}

		if (store.notifier != null)
		{
			store.notifier.datastoreCompoundsChanged();
			store.notifier.datastoreStructuresChanged();
		}
	}

	// identify Vault molecules with missing structure and replace with null
	public void resetCompoundsEmptyVaultMol()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document query = new Document(FLD_COMPOUND_VAULTMID, new Document("$gt", 0));
		query.append(FLD_COMPOUND_MOLFILE, "");

		BasicDBObject change = new BasicDBObject();
		change.append("$set", new BasicDBObject().append(FLD_COMPOUND_MOLFILE, null));
		coll.updateMany(query, change);
	}

	// checks to see if a particular SID (PubChem Substance ID) is already in the compounds collection; if not,
	// it gets added in, and actions are bumped; the new-or-existing compoundID is returned	
	public long reserveCompoundPubChemSID(int sid)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document filter = new Document(FLD_COMPOUND_PUBCHEMSID, sid);
		for (Document doc : coll.find(filter).limit(1)) return doc.getLong(FLD_COMPOUND_ID);

		long compoundID = store.getNextSequence(SEQ_ID_COMPOUND);
		Document doc = new Document();
		doc.append(FLD_COMPOUND_ID, compoundID);
		doc.append(FLD_COMPOUND_PUBCHEMSID, sid);
		coll.insertOne(doc);

		if (store.notifier != null) store.notifier.datastoreCompoundsChanged();
		return compoundID;
	}

	// as above, but for Vault: the molecule ID is the only one that matters to BAE, but the vault/protocol IDs are needed in
	// order to be able to fetch the content
	public long reserveCompoundVault(long vaultID, long vaultMID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document filter = new Document(FLD_COMPOUND_VAULTMID, vaultMID);
		for (Document doc : coll.find(filter).limit(1)) return doc.getLong(FLD_COMPOUND_ID);

		long compoundID = store.getNextSequence(SEQ_ID_COMPOUND);
		Document doc = new Document();
		doc.append(FLD_COMPOUND_ID, compoundID);
		doc.append(FLD_COMPOUND_VAULTID, vaultID);
		doc.append(FLD_COMPOUND_VAULTMID, vaultMID);
		coll.insertOne(doc);

		if (store.notifier != null) store.notifier.datastoreCompoundsChanged();
		return compoundID;
	}

	public void deleteCompound(String id)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document idx = new Document("_id", new ObjectId(id));
		coll.deleteOne(idx);
	}

	// stats
	public int countTotal()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		return (int)coll.countDocuments();
	}

	public int countWithStructures()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_COMPOUND);
		Document filter = new Document(FLD_COMPOUND_MOLFILE, new Document("$ne", null));
		return (int)coll.countDocuments(filter);
	}

	// compound watermark: when compounds need to be updated (e.g. downloading structures)
	public long getWatermarkCompound() {return store.getSequence(SEQ_WATERMARK_COMPOUND);}
	public long nextWatermarkCompound() {return store.getNextSequence(SEQ_WATERMARK_COMPOUND);}

	// ------------ private methods ------------

	private Compound compoundFromDoc(Document doc)
	{
		Compound cpd = new Compound();
		cpd.id = doc.getObjectId("_id").toHexString();
		cpd.compoundID = doc.getLong(FLD_COMPOUND_ID);
		cpd.molfile = doc.getString(FLD_COMPOUND_MOLFILE);
		cpd.hashECFP6 = doc.getInteger(FLD_COMPOUND_HASHECFP6, 0);
		cpd.pubchemCID = doc.getInteger(FLD_COMPOUND_PUBCHEMCID, 0);
		cpd.pubchemSID = doc.getInteger(FLD_COMPOUND_PUBCHEMSID, 0);
		cpd.vaultID = doc.containsKey(FLD_COMPOUND_VAULTID) ? doc.getLong(FLD_COMPOUND_VAULTID) : 0;
		cpd.vaultMID = doc.containsKey(FLD_COMPOUND_VAULTMID) ? doc.getLong(FLD_COMPOUND_VAULTMID) : 0;
		return cpd;
	}
}
