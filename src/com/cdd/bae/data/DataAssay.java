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
import com.cdd.bae.util.diff.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import static com.cdd.bae.data.DataStore.*;

import java.util.*;

import org.apache.commons.lang3.*;
import org.bson.*;

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.*;
import com.mongodb.client.result.*;

/*
	Specialisation based on DataStore: provides access to assay content only.
*/

public class DataAssay
{
	private DataStore store;

	// ------------ public methods ------------

	public DataAssay(DataStore store)
	{
		this.store = store;
	}

	// total # of assays, with no qualifiers
	public int countAssays()
	{
		return (int)store.db.getCollection(COLL_ASSAY).countDocuments();
	}
	
	public long getWatermark()
	{
		return store.getSequence(SEQ_WATERMARK_ASSAY);
	}
	
	public long nextWatermark()
	{
		return store.getNextSequence(SEQ_WATERMARK_ASSAY);
	}
	
	// fetches an assay based on its internal ID number
	public Assay getAssay(long assayID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_ID, assayID);
		for (Document doc : coll.find(filter).limit(1)) 
		{
			Assay assay = assayFromDoc(doc);
			AssayUtil.conformAnnotations(assay);
			return assay;
		}
		return null;
	}
	public Assay getAssayFromUniqueID(String uniqueID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_UNIQUEID, uniqueID);
		for (Document doc : coll.find(filter).limit(1))
		{
			Assay assay = assayFromDoc(doc);
			AssayUtil.conformAnnotations(assay);
			return assay;
		}
		return null;
	}
	
	// pulls out selections of all assays, referring to them by their assay IDs
	public long[] fetchAllAssayID()
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find().projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	public long[] fetchAllCuratedAssayID()
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_ISCURATED, true);
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	public long[] fetchAllNonCuratedAssayID()
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_ISCURATED, new Document("$ne", true));
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	
	
	// pulls out selections of all assays, referring to them by their unique IDs
	public String[] fetchAllUniqueID()
	{
		List<String> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_UNIQUEID, new Document("$ne", null));
		Document proj = new Document(FLD_ASSAY_UNIQUEID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getString(FLD_ASSAY_UNIQUEID));
		return Util.primString(list);
	}
	public String[] fetchAllCuratedUniqueID()
	{
		List<String> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_UNIQUEID, new Document("$ne", null)).append(FLD_ASSAY_ISCURATED, true);
		Document proj = new Document(FLD_ASSAY_UNIQUEID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getString(FLD_ASSAY_UNIQUEID));
		return Util.primString(list);
	}
	public String[] fetchAllNonCuratedUniqueID()
	{
		List<String> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_UNIQUEID, new Document("$ne", null)).append(FLD_ASSAY_ISCURATED, new Document("$ne", true));
		Document proj = new Document(FLD_ASSAY_UNIQUEID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getString(FLD_ASSAY_UNIQUEID));
		return Util.primString(list);
	}

	// returns up to a certain number of assayIDs, starting with those most recently modified
	public long[] fetchRecentlyCurated(int maxNum)
	{
		long[] assayIDList = new long[0];
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_ISCURATED, true);
		Document proj = new Document(FLD_ASSAY_ID, true);
		Document order = new Document(FLD_ASSAY_CURATIONTIME, -1);
		FindIterable<Document> query = coll.find(filter).sort(order).projection(proj);
		if (maxNum > 0) query = query.limit(maxNum);
		for (Document doc : query)
			assayIDList = ArrayUtils.add(assayIDList, doc.getLong(FLD_ASSAY_ID));
		return assayIDList;
	}

	public long[] fetchAssayIDWithoutFP()
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_FPLIST, null);
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	
	// fetches identifiers that have the given schema
	public long[] fetchAssayIDWithSchemaCurated(String schemaURI)
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_ISCURATED, true).append(FLD_ASSAY_SCHEMAURI, schemaURI);
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	public long[] fetchAssayIDWithFPSchema(String schemaURI)
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_FPLIST, new Document("$ne", null)).append(FLD_ASSAY_SCHEMAURI, schemaURI);
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	public long[] fetchAssayIDWithAnnotationsSchema(String schemaURI)
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document condition = new Document("$exists", true).append("$ne", new BasicDBList());
		Document filter = new Document(FLD_ASSAY_ANNOTATIONS, condition).append(FLD_ASSAY_SCHEMAURI, schemaURI);
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	public long[] fetchAssayIDCuratedWithAnnotations()
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document condition = new Document("$exists", true).append("$ne", new BasicDBList());
		Document filter = new Document(FLD_ASSAY_ANNOTATIONS, condition).append(FLD_ASSAY_ISCURATED, true);
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	
	// fetch identifiers for all assay that have an annotation with valueURI
	public long[] fetchCuratedAssayIDWithAnnotation(String valueURI)
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		BasicDBList clauses = new BasicDBList();
		clauses.add(new Document(FLD_ASSAY_ISCURATED, true));
		clauses.add(new Document(FLD_ASSAY_ANNOTATIONS, new Document("$elemMatch", new Document("$elemMatch", new Document("$eq", valueURI)))));
		Document filter = new Document("$and", clauses);
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}

	// returns identifiers for assays that have/don't have annotations
	public long[] fetchAssayIDWithAnnotations()
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document condition = new Document("$exists", true).append("$ne", new BasicDBList());
		Document filter = new Document(FLD_ASSAY_ANNOTATIONS, condition); 
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	public long[] fetchAssayIDWithoutAnnotations()
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		BasicDBList clauses = new BasicDBList();
		clauses.add(new Document(FLD_ASSAY_ANNOTATIONS, new Document("$exists", false)));
		clauses.add(new Document(FLD_ASSAY_ANNOTATIONS, new Document("$eq", new BasicDBList())));
		Document filter = new Document("$or", clauses);
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	
	// grab identifiers for all assays that aren't marked as having dubious text
	public long[] fetchAssayIDCurated()
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_ISCURATED, true);
		Document proj = new Document(FLD_ASSAY_ID, true);
		Document order = new Document(FLD_ASSAY_ID, 1);
		for (Document doc : coll.find(filter).projection(proj).sort(order)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	
	// runs down a list of PubChem AIDs, and for each case, looks to find a curation time; return array has >0 if the record exists and a curation time
	// is set, or zero if not for whatever reason
	public long[] fetchCurationTimes(long[] assayIDList)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document proj = new Document(FLD_ASSAY_CURATIONTIME, true);

		long[] times = new long[assayIDList.length];
		for (int n = 0; n < assayIDList.length; n++)
		{
			Document filter = new Document(FLD_ASSAY_ID, assayIDList[n]).append(FLD_ASSAY_CURATIONTIME, new Document("$gt", 0));
			for (Document doc : coll.find(filter).projection(proj).limit(1)) times[n] = doc.getLong(FLD_ASSAY_CURATIONTIME);
		}
		return times;
	}
	
	// conversion between unique identifiers and assay IDs; note that the uniqueness of uniqueID is a guideline rather than a rule,
	// so some assays could not have one (null), or they could be degenerate (hence the array of arrays)
	public long[][] assayIDFromUniqueID(String[] uniqueIDList)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		BasicDBList idlist = new BasicDBList();
		Map<String, Integer> map = new HashMap<>();
		for (int n = 0; n < uniqueIDList.length; n++)
		{
			idlist.add(uniqueIDList[n]);
			map.put(uniqueIDList[n], n);
		}
		Document filter = new Document(FLD_ASSAY_UNIQUEID, new Document("$in", idlist));
		Document proj = new Document(FLD_ASSAY_ID, true).append(FLD_ASSAY_UNIQUEID, true);
		long[][] ret = new long[uniqueIDList.length][];
		for (Document doc : coll.find(filter).projection(proj))
		{
			int idx = map.get(doc.getString(FLD_ASSAY_UNIQUEID));
			ret[idx] = ArrayUtils.add(ret[idx], doc.getLong(FLD_ASSAY_ID));
		}
		return ret; // note: no match = null
	}
	
	// returns the assay IDs that match a regex on the uniqueID field
	public long[] assayIDFromUniqueIDRegex(String uniqueIDRegex)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_UNIQUEID, new Document("$regex", uniqueIDRegex));
		Document proj = new Document(FLD_ASSAY_ID, true);
		List<Long> assayIDList = new ArrayList<>();
		for (Document doc : coll.find(filter).projection(proj)) assayIDList.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(assayIDList);
	}
	
	// grabs the uniqueIDs that correspond to the assay IDs
	public String[] uniqueIDFromAssayID(long[] assayIDList)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		BasicDBList idlist = new BasicDBList();
		Map<Long, Integer> map = new HashMap<>();
		for (int n = 0; n < assayIDList.length; n++)
		{
			idlist.add(assayIDList[n]);
			map.put(assayIDList[n], n);
		}
		Document filter = new Document(FLD_ASSAY_ID, new Document("$in", idlist));
		Document proj = new Document(FLD_ASSAY_ID, true).append(FLD_ASSAY_UNIQUEID, true);
		String[] ret = new String[assayIDList.length];
		for (Document doc : coll.find(filter).projection(proj))
		{
			int idx = map.get(doc.getLong(FLD_ASSAY_ID));
			ret[idx] = doc.getString(FLD_ASSAY_UNIQUEID);
		}
		return ret;
	}	

	// deletes a specific assay; returns true if anything happened
	public boolean deleteAssay(long assayID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_ID, assayID);
		DeleteResult result = coll.deleteOne(filter);

		if (result.getDeletedCount() > 0)
		{
			nextWatermark();
			if (store.notifier != null)
			{
				store.notifier.datastoreTextChanged();
				store.notifier.datastoreFingerprintsChanged();
				store.notifier.datastoreAnnotationsChanged();
			}
		}

		return result.getDeletedCount() > 0;
	}

	// adds or replaces an assay as-is: it does not look at whatever was in the database before with the same assayID, it just
	// clobbers whatever is there; the history is inserted as-is, erasing any previous tracking
	public void setAssay(Assay assay)
	{
		if (assay.assayID == 0) assay.assayID = store.getNextSequence(SEQ_ID_ASSAY);
		
		AssayUtil.conformAnnotations(assay);

		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		BasicDBList dbannot = new BasicDBList();
		BasicDBList dblabel = new BasicDBList();
		if (assay.annotations != null) for (Annotation annot : assay.annotations) dbannot.add(formulateAnnotation(annot));
		if (assay.textLabels != null) for (TextLabel label : assay.textLabels) dblabel.add(formulateTextLabel(label));

		Document doc = new Document();
		doc.append(FLD_ASSAY_ID, assay.assayID);
		doc.append(FLD_ASSAY_UNIQUEID, assay.uniqueID);
		doc.append(FLD_ASSAY_TEXT, assay.text);
		doc.append(FLD_ASSAY_ANNOTATIONS, dbannot);
		doc.append(FLD_ASSAY_TEXTLABELS, dblabel);
		doc.append(FLD_ASSAY_ISCURATED, assay.isCurated);
		doc.append(FLD_ASSAY_FPLIST, null);
		// NOTE: pubchemSource is deprecated
		//if (Util.notBlank(assay.pubchemSource)) doc.append(FLD_ASSAY_PUBCHEMSOURCE, assay.pubchemSource);
		if (assay.curationTime != null) doc.append(FLD_ASSAY_CURATIONTIME, assay.curationTime.getTime());
		if (assay.touchedTime != null) doc.append(FLD_ASSAY_TOUCHEDTIME, assay.touchedTime.getTime());
		doc.append(FLD_ASSAY_CURATORID, assay.curatorID);
		if (assay.schemaURI != null) doc.append(FLD_ASSAY_SCHEMAURI, assay.schemaURI);
		if (assay.schemaBranches != null) doc.append(FLD_ASSAY_SCHEMABRANCHES, serialiseSchemaBranches(assay.schemaBranches));
		if (assay.schemaDuplication != null) doc.append(FLD_ASSAY_SCHEMADUPLICATION, serialiseSchemaDuplication(assay.schemaDuplication));

		doc.put(FLD_ASSAY_HISTORY, serialiseHistory(assay.history));
		doc.put(FLD_ASSAY_PUBCHEMXREF, serialisePubChemXRef(assay.pubchemXRefs));

		Document idx = new Document(FLD_ASSAY_ID, assay.assayID);
		coll.updateOne(idx, new Document("$set", doc), new UpdateOptions().upsert(true));

		nextWatermark();
		if (store.notifier != null)
		{
			store.notifier.datastoreTextChanged();
			store.notifier.datastoreAnnotationsChanged();
		}
	}
	
	// submits an assay entry, based on the datastructure; if the assayID value is zero, this translates to a request for a new assay
	// entry, and an ID will be generated; if the assay (by ID) already exists, then it will be amended; note that the history section
	// will be updated automatically (i.e. leave it blank within the parameter)
	public void submitAssay(Assay assay)
	{
		Assay previous = assay.assayID == 0 ? null : getAssay(assay.assayID);
		if (assay.assayID == 0) assay.assayID = store.getNextSequence(SEQ_ID_ASSAY);
		
		Document doc = submitAssayDoc(assay, previous);
				
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		
		if (previous != null)
		{
			Document idx = new Document(FLD_ASSAY_ID, assay.assayID);
			// (oops) coll.replaceOne(idx, doc, new UpdateOptions().upsert(true));
			coll.updateOne(idx, new Document("$set", doc), new UpdateOptions().upsert(true));
		}
		else coll.insertOne(doc);

		nextWatermark();
		if (store.notifier != null)
		{
			// TO DO: check to see if these actually changed...
			store.notifier.datastoreTextChanged();
			store.notifier.datastoreAnnotationsChanged();
			store.notifier.datastoreMeasurementsChanged();
		}
	}
	
	// associates the fingerprints with the indicated assay
	public void submitAssayFingerprints(long assayID, int[] fplist)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document idx = new Document(FLD_ASSAY_ID, assayID);
		BasicDBList dblist = new BasicDBList();
		for (int fp : fplist) dblist.add(fp);
		coll.updateOne(idx, new Document("$set", new Document(FLD_ASSAY_FPLIST, dblist)));

		if (store.notifier != null) store.notifier.datastoreFingerprintsChanged();
	}
	
	// remove fingerprints from a specific assay
	public void clearAssayFingerprints(long assayID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document idx = new Document(FLD_ASSAY_ID, assayID);
		coll.updateOne(idx, new Document("$set", new Document(FLD_ASSAY_FPLIST, null)));

		if (store.notifier != null) store.notifier.datastoreFingerprintsChanged();
	}
	
	// replaces just the semantic annotations
	public void submitAssayAnnotations(long assayID, Annotation[] annots)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document idx = new Document(FLD_ASSAY_ID, assayID);
		BasicDBList dbannot = new BasicDBList();
		if (annots != null) for (Annotation annot : annots) dbannot.add(formulateAnnotation(annot));
		coll.updateOne(idx, new Document("$set", new Document(FLD_ASSAY_ANNOTATIONS, dbannot)));

		nextWatermark();
		if (store.notifier != null) store.notifier.datastoreAnnotationsChanged();
	}
	
	public void submitAssayPubChemAnnotations(int pubchemAID, Annotation[] annots)
	{
		String uniqueID = "pubchemAID:" + pubchemAID; // vestigial hangover
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document idx = new Document(FLD_ASSAY_UNIQUEID, uniqueID);
		BasicDBList dbannot = new BasicDBList();
		if (annots != null) for (Annotation annot : annots) dbannot.add(formulateAnnotation(annot));
		coll.updateOne(idx, new Document("$set", new Document(FLD_ASSAY_ANNOTATIONS, dbannot)));

		nextWatermark();
		if (store.notifier != null) store.notifier.datastoreAnnotationsChanged();
	}	
	// replaces just the text for the indicated assay
	public void replaceAssayText(int pubchemAID, String text)
	{
		String uniqueID = "pubchemAID:" + pubchemAID; // vestigial hangover
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document idx = new Document(FLD_ASSAY_UNIQUEID, uniqueID);
		Document doc = new Document(FLD_ASSAY_TEXT, text).append(FLD_ASSAY_FPLIST, null);
		coll.updateOne(idx, new Document("$set", doc));

		if (store.notifier != null) store.notifier.datastoreTextChanged();
	}
	
	// updates the choice of schema for an assay
	public void submitAssaySchema(long assayID, String schemaURI)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document idx = new Document(FLD_ASSAY_ID, assayID);
		Document doc = new Document(FLD_ASSAY_SCHEMAURI, schemaURI);
		coll.updateOne(idx, new Document("$set", doc));

		if (store.notifier != null) store.notifier.datastoreAnnotationsChanged();
	}
	
	// obtains a list of PubChem AIDs of assays that do/do not have the "measureChecked" flag set, i.e. action is
	// required to go out and grab the measurement data for them
	public long[] fetchAssayIDHaveMeasure()
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_MEASURECHECKED, true);
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	
	public long[] fetchAssayIDNeedMeasure()
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_MEASURECHECKED, new Document("$ne", true));
		Document proj = new Document(FLD_ASSAY_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_ASSAY_ID));
		return Util.primLong(list);
	}
	
	// sets the measurement flag for the assay with the given PubChem AID
	public void submitPubChemAIDMeasured(int pubchemAID, boolean measureChecked)
	{
		String uniqueID = "pubchemAID:" + pubchemAID; // vestigial hangover
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document idx = new Document(FLD_ASSAY_UNIQUEID, uniqueID);
		coll.updateOne(idx, new Document("$set", new Document(FLD_ASSAY_MEASURECHECKED, measureChecked)));
	}
	
	// change just the curation state of a single assay
	public void submitIsCurated(long assayID, boolean isCurated)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document idx = new Document(FLD_ASSAY_ID, assayID);
		coll.updateOne(idx, new Document("$set", new Document(FLD_ASSAY_ISCURATED, isCurated)));
	}
	
	// changes the "measurement state" for an assay: this is an arbitrary string that can be compared to some external resource
	// to decide when/whether/how to update the corresponding measurements; applies to hardcoded functionality that is specific
	// to the uniqueID prefix (e.g. Vault)
	public void submitMeasureState(long assayID, String measureState)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document idx = new Document(FLD_ASSAY_ID, assayID);
		coll.updateOne(idx, new Document("$set", new Document(FLD_ASSAY_MEASURESTATE, measureState)));
	}
	
	// counts up use of properties: returns the number of curated documents that annotate each given property
	public Map<String, Integer> breakdownProperties()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_ISCURATED, true).append(FLD_ASSAY_ANNOTATIONS, new Document("$ne", null));
		Document proj = new Document(FLD_ASSAY_ANNOTATIONS, true);
		
		Map<String, Integer> propCount = new HashMap<>();
		
		Set<String> props = new HashSet<>();
		for (Document doc : coll.find(filter).projection(proj))
		{
			List<?> annotations = (List<?>)doc.get(FLD_ASSAY_ANNOTATIONS);
			props.clear();
			for (int n = 0; n < annotations.size(); n++)
			{
				List<?> annot = (List<?>)annotations.get(n);
				String propURI = (String)annot.get(0);
				props.add(propURI);
			}
			for (String propURI : props) propCount.put(propURI, propCount.getOrDefault(propURI, 0) + 1);
		}

		return propCount;
	}
	
	// digs through the curated assays and tallies up the valueURI counts for each propURI
	public Map<String, Map<String, Integer>> breakdownAssignments()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_ISCURATED, true).append(FLD_ASSAY_ANNOTATIONS, new Document("$ne", null));
		Document proj = new Document(FLD_ASSAY_ANNOTATIONS, true);
		
		Map<String, Map<String, Integer>> propValCount = new HashMap<>();

		for (Document doc : coll.find(filter).projection(proj))
		{
			List<?> annotations = (List<?>)doc.get(FLD_ASSAY_ANNOTATIONS);
			for (int n = 0; n < annotations.size(); n++)
			{
				List<?> annot = (List<?>)annotations.get(n);
				String propURI = (String)annot.get(0), valueURI = (String)annot.get(1);
				Map<String, Integer> valueCount = propValCount.get(propURI);
				if (valueCount == null) propValCount.put(propURI, valueCount = new HashMap<>());
				valueCount.put(valueURI, valueCount.getOrDefault(valueURI, 0) + 1);
			}
		}

		return propValCount;
	}
	
	// count up the number of assays belonging to each template
	public Map<String, Integer> breakdownTemplates()
	{
		var coll = store.db.getCollection(COLL_ASSAY);
		var proj = new Document(FLD_ASSAY_SCHEMAURI, true);
		Map<String, Integer> templCount = new HashMap<>();
		for (var doc : coll.find().projection(proj))
		{
			String schemaURI = doc.getString(FLD_ASSAY_SCHEMAURI);
			templCount.put(schemaURI, templCount.getOrDefault(schemaURI, 0) + 1);
		}
		return templCount;
	}
	
	/* both of the methods below are decomissioned because new versions of MongoDB don't support the "group"
	   command; keep the code around though, in case it's useful later

	// counts up use of properties: returns the number of documents that annotate each given property; the integer array
	// is of the form [#total, #curated] (referring to # of assays)
	public Map<String, int[]> breakdownProperties()
	{
		String reduce = String.join("\n", new String[]
		{
			"function(doc, prev)",
			"{",
			"  if (!doc.annotations) return;",
			"  var gotProps = [];",
			"  for (var n = 0; n < doc.annotations.length; n++)",
			"  {",
			"    var propURI = doc.annotations[n][0];",
			"    if (gotProps.indexOf(propURI) >= 0) continue;",
			"    var counts = prev[propURI];",
			"    if (!counts) counts = [0, 0];",
			"    counts[0]++;",
			"    if (doc.isCurated) counts[1]++;",
			"    prev[propURI] = counts;",
			"    gotProps.push(propURI);",
			"  }",
			"}"
		});
		Document group = new Document("ns", COLL_ASSAY);
		//group.append("key", ..?); (key doesn't seem to do anything, which is what we want anyway; right for the wrong reasons?)
		group.append("initial", new Document()).append("$reduce", reduce);
		
		Document result = store.db.runCommand(new Document("group", group));
		List<?> retvals = (List<?>)result.get("retval");
		Document props = (Document)retvals.get(0);
		
		Map<String, int[]> counts = new HashMap<>();
		for (String propURI : props.keySet())
		{
			List<?> pair = (List<?>)props.get(propURI);
			int total = ((Double)pair.get(0)).intValue();
			int curated = ((Double)pair.get(1)).intValue();
			counts.put(propURI, new int[]{total, curated});
		}
		
		return counts;
	}
	
	// lengthy counting operation: digs through the curated assays and tallies up the valueURI counts for each propURI; the integer array
	// for counts is [#total, #curated] (referring to # of values, keeping in mind that some assays have multiple values oer assignment)
	public Map<String, Map<String, int[]>> breakdownAssignments()
	{
		String reduce = String.join("\n", new String[]
		{
			"function(doc, prev)",
			"{",
			"  if (!doc.annotations) return;",
			"  for (var n = 0; n < doc.annotations.length; n++)",
			"  {",
			"    var propURI = doc.annotations[n][0], valueURI = doc.annotations[n][1];",
			"    var group = prev[propURI];",
			"    if (!group) {group = {}; prev[propURI] = group;}",
			"    var counts = group[valueURI];",
			"    if (!counts) counts = [0, 0];",
			"    counts[0]++;",
			"    if (doc.isCurated) counts[1]++;",
			"    group[valueURI] = counts;",
			"  }",
			"}"
		});
		Document group = new Document("ns", COLL_ASSAY);
		//group.append("key", ..?); (key doesn't seem to do anything, which is what we want anyway; right for the wrong reasons?)
		group.append("initial", new Document()).append("$reduce", reduce);
		
		Document result = store.db.runCommand(new Document("group", group));
		List<?> retvals = (List<?>)result.get("retval");
		Document props = (Document)retvals.get(0);
		
		Map<String, Map<String, int[]>> breakdown = new HashMap<>();
		for (String propURI : props.keySet())
		{
			Map<String, int[]> counts = new HashMap<>();
			Document values = (Document)props.get(propURI);
			for (String valueURI : values.keySet()) 
			{
				List<?> pair = (List<?>)values.get(valueURI);
				int total = ((Double)pair.get(0)).intValue();
				int curated = ((Double)pair.get(1)).intValue();
				counts.put(valueURI, new int[]{total, curated});
			}
			breakdown.put(propURI, counts);
		}
		
		return breakdown;
	}*/
	
	public static class UserCuration implements Comparable<UserCuration>
	{
		public long curationTime;
		public long assayID;

		public UserCuration(long assayID, long curationTime)
		{
			this.curationTime = curationTime;
			this.assayID = assayID;
		}

		@Override
		public int compareTo(UserCuration o)
		{
			return Long.compare(o.curationTime, this.curationTime);
		}
		
		@Override
		public String toString()
		{
			return curationTime + " " + assayID;
		}
	}

	public List<UserCuration> fetchAssayIDRecentCuration(String curatorID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_ASSAY);
		Document filter = new Document(FLD_ASSAY_HISTORY + "." + FLD_ASSAY_CURATORID, curatorID);
		Document proj = new Document(FLD_ASSAY_ID, true);
		proj.append(FLD_ASSAY_HISTORY + "." + FLD_ASSAY_CURATORID, true);
		proj.append(FLD_ASSAY_HISTORY + "." + FLD_ASSAY_CURATIONTIME, true);
		Document order = new Document(FLD_ASSAY_CURATIONTIME, -1);
		List<DataAssay.UserCuration> result = new ArrayList<>();
		for (Document doc : coll.find(filter).sort(order).projection(proj)) 
		{
			List<Document> histEntries = doc.getList(FLD_ASSAY_HISTORY, Document.class);
			for (int i = histEntries.size() - 1; i >= 0; i--)
			{
				Document hist = histEntries.get(i);
				if (!curatorID.equals(hist.getString(FLD_ASSAY_CURATORID))) continue;
				
				result.add(new UserCuration(doc.getLong(FLD_ASSAY_ID), hist.getLong(FLD_ASSAY_CURATIONTIME)));
				break;
			}
		}
		Collections.sort(result);
		return result;
	}
	
	// ------------ private methods ------------

	// pulls out everything from the source document
	protected static Assay assayFromDoc(Document doc)
	{
		Assay assay = new Assay();
		assay.assayID = doc.getLong(FLD_ASSAY_ID);
		assay.uniqueID = doc.getString(FLD_ASSAY_UNIQUEID);
		assay.text = doc.getString(FLD_ASSAY_TEXT);
		
		List<?> fplist = (List<?>)doc.get(FLD_ASSAY_FPLIST);		
		if (fplist != null)
		{
			assay.fplist = new int[fplist.size()];
			for (int n = 0; n < assay.fplist.length; n++) assay.fplist[n] = (Integer)fplist.get(n);
		}
		else assay.fplist = new int[0];
		
		List<?> annotlist = (List<?>)doc.get(FLD_ASSAY_ANNOTATIONS);
		if (annotlist != null)
		{
			assay.annotations = new Annotation[annotlist.size()];
			for (int n = 0; n < assay.annotations.length; n++)
			{
				List<?> pair = (List<?>)annotlist.get(n);
				assay.annotations[n] = parseAnnotation(pair);
			}
		}
		else assay.annotations = new Annotation[0];
		
		List<?> textlist = (List<?>)doc.get(FLD_ASSAY_TEXTLABELS);
		if (textlist != null)
		{
			assay.textLabels = new TextLabel[textlist.size()];
			for (int n = 0; n < assay.textLabels.length; n++)
			{
				List<?> pair = (List<?>)textlist.get(n);
				assay.textLabels[n] = parseTextLabel(pair);
			}
		}
		else assay.textLabels = new TextLabel[0];
		
		//assay.pubchemSource = doc.containsKey(FLD_ASSAY_PUBCHEMSOURCE) ? doc.getString(FLD_ASSAY_PUBCHEMSOURCE) : null;
		List<?> xrefs = (List<?>)doc.get(FLD_ASSAY_PUBCHEMXREF);
		if (xrefs != null)
		{
			assay.pubchemXRefs = new PubChemXRef[xrefs.size()];
			for (int n = 0; n < assay.pubchemXRefs.length; n++)
			{
				Document xref = (Document)xrefs.get(n);
				assay.pubchemXRefs[n] = new PubChemXRef();
				assay.pubchemXRefs[n].type = xref.getString("type");
				assay.pubchemXRefs[n].id = xref.getString("id");
				assay.pubchemXRefs[n].comment = xref.getString("comment");
			}
		}
		
		assay.isCurated = doc.getBoolean(FLD_ASSAY_ISCURATED, false);
		assay.measureChecked = doc.getBoolean(FLD_ASSAY_MEASURECHECKED, false);
		assay.measureState = doc.getString(FLD_ASSAY_MEASURESTATE);
		assay.curationTime = doc.containsKey(FLD_ASSAY_CURATIONTIME) ? new Date(doc.getLong(FLD_ASSAY_CURATIONTIME)) : null;
		assay.touchedTime = doc.containsKey(FLD_ASSAY_TOUCHEDTIME) ? new Date(doc.getLong(FLD_ASSAY_TOUCHEDTIME)) : null;
		assay.schemaURI = doc.containsKey(FLD_ASSAY_SCHEMAURI) ? doc.getString(FLD_ASSAY_SCHEMAURI) : null;
		assay.curatorID = doc.containsKey(FLD_ASSAY_CURATORID) ? doc.getString(FLD_ASSAY_CURATORID) : null;
		
		List<?> branches = (List<?>)doc.get(FLD_ASSAY_SCHEMABRANCHES);
		if (branches != null)
		{
			assay.schemaBranches = new SchemaBranch[branches.size()];
			for (int n = 0; n < assay.schemaBranches.length; n++) assay.schemaBranches[n] = parseSchemaBranch((Document)branches.get(n));
		}
		List<?> duplication = (List<?>)doc.get(FLD_ASSAY_SCHEMADUPLICATION);
		if (duplication != null)
		{
			assay.schemaDuplication = new SchemaDuplication[duplication.size()];
			for (int n = 0; n < assay.schemaDuplication.length; n++) assay.schemaDuplication[n] = parseSchemaDuplication((Document)duplication.get(n));
		}
		
		List<?> history = (List<?>)doc.get(FLD_ASSAY_HISTORY);
		if (history != null)
		{
			assay.history = new History[history.size()];
			for (int n = 0; n < assay.history.length; n++)
			{
				Document blk = (Document)history.get(n); // note: reusing the same FLD_* keys as the main document
				assay.history[n] = new History();
				assay.history[n].curationTime = blk.containsKey(FLD_ASSAY_CURATIONTIME) ? new Date(blk.getLong(FLD_ASSAY_CURATIONTIME)) : null;
				assay.history[n].curatorID = blk.containsKey(FLD_ASSAY_CURATORID) ? blk.getString(FLD_ASSAY_CURATORID) : null;
				assay.history[n].uniqueIDPatch = blk.containsKey(FLD_ASSAY_HISTORY_UNIQUEIDPATCH) ? blk.getString(FLD_ASSAY_HISTORY_UNIQUEIDPATCH) : null;
				assay.history[n].textPatch = blk.containsKey(FLD_ASSAY_HISTORY_TEXTPATCH) ? blk.getString(FLD_ASSAY_HISTORY_TEXTPATCH) : null;

				// note: kludgey little hack to work around renamed field
				List<?> annotsAdded = (List<?>)blk.get(FLD_ASSAY_HISTORY_ANNOTSADDED);
				List<?> annotsRemoved = (List<?>)blk.get(FLD_ASSAY_HISTORY_ANNOTSREMOVED);
				if (annotsAdded == null) annotsAdded = (List<?>)blk.get("added");
				if (annotsRemoved == null) annotsRemoved = (List<?>)blk.get("removed");
				assay.history[n].annotsAdded = new Annotation[annotsAdded == null ? 0 : annotsAdded.size()];
				assay.history[n].annotsRemoved = new Annotation[annotsRemoved == null ? 0 : annotsRemoved.size()];
				for (int i = 0; i < assay.history[n].annotsAdded.length; i++)
				{
					List<?> pair = (List<?>)annotsAdded.get(i);
					assay.history[n].annotsAdded[i] = parseAnnotation(pair);
				}
				for (int i = 0; i < assay.history[n].annotsRemoved.length; i++)
				{
					List<?> pair = (List<?>)annotsRemoved.get(i);
					assay.history[n].annotsRemoved[i] = parseAnnotation(pair);
				}
				
				List<?> labelsAdded = (List<?>)blk.get(FLD_ASSAY_HISTORY_LABELSADDED);
				List<?> labelsRemoved = (List<?>)blk.get(FLD_ASSAY_HISTORY_LABELSREMOVED);
				assay.history[n].labelsAdded = new TextLabel[labelsAdded == null ? 0 : labelsAdded.size()];
				assay.history[n].labelsRemoved = new TextLabel[labelsRemoved == null ? 0 : labelsRemoved.size()];
				for (int i = 0; i < assay.history[n].labelsAdded.length; i++)
				{
					List<?> pair = (List<?>)labelsAdded.get(i);
					assay.history[n].labelsAdded[i] = parseTextLabel(pair);
				}
				for (int i = 0; i < assay.history[n].labelsRemoved.length; i++)
				{
					List<?> pair = (List<?>)labelsRemoved.get(i);
					assay.history[n].labelsRemoved[i] = parseTextLabel(pair);
				}
			}
		}
		
		return assay;
	}

	// provides the mechanics for submitting new assay content, replacing what's necessary and generating history
	protected Document submitAssayDoc(Assay assay, Assay previous)
	{
		AssayUtil.conformAnnotations(assay);

		BasicDBList dbannot = new BasicDBList();
		BasicDBList dblabel = new BasicDBList();
		if (assay.annotations != null) for (Annotation annot : assay.annotations) dbannot.add(formulateAnnotation(annot));
		if (assay.textLabels != null) for (TextLabel label : assay.textLabels) dblabel.add(formulateTextLabel(label));

		Document doc = new Document();
		doc.append(FLD_ASSAY_UNIQUEID, assay.uniqueID);
		doc.append(FLD_ASSAY_TEXT, assay.text);
		doc.append(FLD_ASSAY_ANNOTATIONS, dbannot);
		doc.append(FLD_ASSAY_TEXTLABELS, dblabel);
		doc.append(FLD_ASSAY_ISCURATED, true);
		doc.append(FLD_ASSAY_FPLIST, null);
		doc.append(FLD_ASSAY_ID, assay.assayID);
		if (assay.curationTime != null) doc.append(FLD_ASSAY_CURATIONTIME, assay.curationTime.getTime());
		if (assay.touchedTime != null) doc.append(FLD_ASSAY_TOUCHEDTIME, assay.touchedTime.getTime());
		doc.append(FLD_ASSAY_CURATORID, assay.curatorID);
		if (assay.schemaURI != null) doc.append(FLD_ASSAY_SCHEMAURI, assay.schemaURI);
		if (assay.schemaBranches != null) doc.append(FLD_ASSAY_SCHEMABRANCHES, serialiseSchemaBranches(assay.schemaBranches));
		if (assay.schemaDuplication != null) doc.append(FLD_ASSAY_SCHEMADUPLICATION, serialiseSchemaDuplication(assay.schemaDuplication));
		if (assay.measureState != null) doc.append(FLD_ASSAY_MEASURESTATE, assay.measureState);
		
		pushHistory(assay.uniqueID, 
					previous != null ? previous.uniqueID : null,
					assay.text,
					previous != null ? previous.text : null,
					assay.annotations,
					previous != null ? previous.annotations : null,
					assay.textLabels,
					previous != null ? previous.textLabels : null,
					assay.curationTime,
					assay.curatorID,
					previous != null ? previous.history : null,
					doc);
		return doc;
	}

	// given that an assay is about to have a sequence of "new annotations" applied to it, takes the "current annotations" and
	// pushes the differences into the delta, and then serialises the modifications into the history part of the document
	private void pushHistory(String newUniqueID, String oldUniqueID,
							String newText, String oldText,
							Annotation[] newAnnots, Annotation[] curAnnots, TextLabel[] newLabels, TextLabel[] curLabels,
							Date curationTime, String curatorID, History[] curHistory, Document doc)
	{
		List<History> history = new ArrayList<>();
		if (curHistory != null) for (History h : curHistory) history.add(h);
		
		// patching text and label (syntax is to convert the "new replacing string" to the "previously existing string")
		String uniqueIDPatch = DiffMatchPatch.patchToText(DiffMatchPatch.patchMake(Util.safeString(newUniqueID), Util.safeString(oldUniqueID)));
		if (Util.isBlank(uniqueIDPatch)) uniqueIDPatch = null;
		String textPatch = DiffMatchPatch.patchToText(DiffMatchPatch.patchMake(Util.safeString(newText), Util.safeString(oldText)));
		if (Util.isBlank(textPatch)) textPatch = null;
		
		// assemble before & after maps
		
		Map<String, Annotation> allNewAnnots = new HashMap<>();
		Map<String, Annotation> allCurAnnots = new HashMap<>();
		if (newAnnots != null) for (Annotation annot : newAnnots) 
		{
			String key = Schema.keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI);
			allNewAnnots.put(key, annot);
		}
		if (curAnnots != null) for (Annotation annot : curAnnots) 
		{
			String key = Schema.keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI);
			allCurAnnots.put(key, annot);
		}
		Map<String, TextLabel> allNewLabels = new HashMap<>();
		Map<String, TextLabel> allCurLabels = new HashMap<>();
		if (newLabels != null) for (TextLabel label : newLabels) 
		{
			String key = Schema.keyPropGroupValue(label.propURI, label.groupNest, label.text);
			allNewLabels.put(key, label);
		}
		if (curLabels != null) for (TextLabel label : curLabels) 
		{
			String key = Schema.keyPropGroupValue(label.propURI, label.groupNest, label.text);
			allCurLabels.put(key, label);
		}
		
		// assemble before & after delta lists
		
		List<Annotation> annotsAdded = new ArrayList<>();
		List<Annotation> annotsRemoved = new ArrayList<>();
		for (String key : allNewAnnots.keySet()) if (!allCurAnnots.containsKey(key)) annotsAdded.add(allNewAnnots.get(key));
		for (String key : allCurAnnots.keySet()) if (!allNewAnnots.containsKey(key)) annotsRemoved.add(allCurAnnots.get(key));
		List<TextLabel> labelsAdded = new ArrayList<>();
		List<TextLabel> labelsRemoved = new ArrayList<>();
		for (String key : allNewLabels.keySet()) if (!allCurLabels.containsKey(key)) labelsAdded.add(allNewLabels.get(key));
		for (String key : allCurLabels.keySet()) if (!allNewLabels.containsKey(key)) labelsRemoved.add(allCurLabels.get(key));
				
		// add to the history, if anything changed
		if (uniqueIDPatch != null || textPatch != null || annotsAdded.size() > 0 || annotsRemoved.size() > 0 || 
			labelsAdded.size() > 0 || labelsRemoved.size() > 0)
		{
			History h = new History();
			h.curationTime = curationTime;
			h.curatorID = curatorID;
			h.uniqueIDPatch = uniqueIDPatch;
			h.textPatch = textPatch;
			h.annotsAdded = annotsAdded.toArray(new Annotation[annotsAdded.size()]);
			h.annotsRemoved = annotsRemoved.toArray(new Annotation[annotsRemoved.size()]);
			h.labelsAdded = labelsAdded.toArray(new TextLabel[labelsAdded.size()]);
			h.labelsRemoved = labelsRemoved.toArray(new TextLabel[labelsRemoved.size()]);
			history.add(h);
		}
		
		doc.put(FLD_ASSAY_HISTORY, serialiseHistory(history.toArray(new History[history.size()])));
	}
	
	// turn the history objects into BSON
	private BasicDBList serialiseHistory(History[] history)
	{
		BasicDBList dbHistory = new BasicDBList();
		if (history != null) for (History h : history)
		{
			Document obj = new Document();
			if (h.curationTime != null) obj.append(FLD_ASSAY_CURATIONTIME, h.curationTime.getTime());
			obj.append(FLD_ASSAY_CURATORID, h.curatorID);
			obj.append(FLD_ASSAY_HISTORY_UNIQUEIDPATCH, h.uniqueIDPatch);
			obj.append(FLD_ASSAY_HISTORY_TEXTPATCH, h.textPatch);

			BasicDBList dbAnnotsAdded = new BasicDBList();
			BasicDBList dbAnnotsRemoved = new BasicDBList();
			if (h.annotsAdded != null) for (Annotation annot : h.annotsAdded) dbAnnotsAdded.add(formulateAnnotation(annot));
			if (h.annotsRemoved != null) for (Annotation annot : h.annotsRemoved) dbAnnotsRemoved.add(formulateAnnotation(annot));
			obj.append(FLD_ASSAY_HISTORY_ANNOTSADDED, dbAnnotsAdded);
			obj.append(FLD_ASSAY_HISTORY_ANNOTSREMOVED, dbAnnotsRemoved);
			
			BasicDBList dbLabelsAdded = new BasicDBList();
			BasicDBList dbLabelsRemoved = new BasicDBList();
			if (h.labelsAdded != null) for (TextLabel label : h.labelsAdded) dbLabelsAdded.add(formulateTextLabel(label));
			if (h.labelsRemoved != null) for (TextLabel label : h.labelsRemoved) dbLabelsRemoved.add(formulateTextLabel(label));
			obj.append(FLD_ASSAY_HISTORY_LABELSADDED, dbLabelsAdded);
			obj.append(FLD_ASSAY_HISTORY_LABELSREMOVED, dbLabelsRemoved);
			
			dbHistory.add(obj);
		}
		return dbHistory;
	}

	// turns PubChem cross references into BSON	
	protected static BasicDBList serialisePubChemXRef(PubChemXRef[] xrefs)
	{
		BasicDBList xreflist = new BasicDBList();
		if (xrefs != null) for (PubChemXRef xref : xrefs)
		{
			Document obj = new Document();
			obj.append("type", xref.type);
			obj.append("id", xref.id);
			obj.append("comment", xref.comment);
			xreflist.add(obj);
		}
		return xreflist;
	}
	
	// turns schema branches into BSON
	protected static BasicDBList serialiseSchemaBranches(SchemaBranch[] branches)
	{
		BasicDBList branchlist = new BasicDBList();
		if (branches != null) for (SchemaBranch branch : branches)
		{
			Document obj = new Document();
			obj.append("schemaURI", branch.schemaURI);
			BasicDBList groupNest = new BasicDBList();
			if (branch.groupNest != null) for (String gn : branch.groupNest) groupNest.add(gn);
			obj.append("groupNest", groupNest);
			branchlist.add(obj);
		}
		return branchlist;
	}
	protected static BasicDBList serialiseSchemaDuplication(SchemaDuplication[] duplication)
	{
		BasicDBList duplist = new BasicDBList();
		if (duplication != null) for (SchemaDuplication dupl : duplication)
		{
			Document obj = new Document();
			obj.append("multiplicity", dupl.multiplicity);
			BasicDBList groupNest = new BasicDBList();
			if (dupl.groupNest != null) for (String gn : dupl.groupNest) groupNest.add(gn);
			obj.append("groupNest", groupNest);
			duplist.add(obj);
		}
		return duplist;
	}
	
	// turns a "sequence" into an annotation: at baseline, this is [propURI, valueURI], but it can also have the group nesting tacked
	// onto the end of it
	private static Annotation parseAnnotation(List<?> seq)
	{
		Annotation annot = new Annotation((String)seq.get(0), (String)seq.get(1));
		if (seq.size() > 2)
		{
			annot.groupNest = new String[seq.size() - 2];
			for (int n = 2; n < seq.size(); n++) annot.groupNest[n - 2] = (String)seq.get(n);
		}
		return annot;
	}
	private static TextLabel parseTextLabel(List<?> seq)
	{
		TextLabel label = new TextLabel((String)seq.get(0), (String)seq.get(1));
		if (seq.size() > 2)
		{
			label.groupNest = new String[seq.size() - 2];
			for (int n = 2; n < seq.size(); n++) label.groupNest[n - 2] = (String)seq.get(n);
		}
		return label;
	}
	protected static SchemaBranch parseSchemaBranch(Document doc)
	{
		SchemaBranch branch = new SchemaBranch();
		branch.schemaURI = doc.getString("schemaURI");
		List<?> groupNest = (List<?>)doc.get("groupNest");
		if (groupNest != null && groupNest.size() > 0)
		{
			branch.groupNest = new String[groupNest.size()];
			for (int i = 0; i < groupNest.size(); i++) branch.groupNest[i] = (String)groupNest.get(i);
		}
		return branch;
	}
	protected static SchemaDuplication parseSchemaDuplication(Document doc)
	{
		SchemaDuplication dupl = new SchemaDuplication();
		dupl.multiplicity = doc.getInteger("multiplicity");
		List<?> groupNest = (List<?>)doc.get("groupNest");
		if (groupNest != null && groupNest.size() > 0)
		{
			dupl.groupNest = new String[groupNest.size()];
			for (int i = 0; i < groupNest.size(); i++) dupl.groupNest[i] = (String)groupNest.get(i);
		}
		return dupl;
	}
	
	// turn field content into a properly typed string-to-string dictionary
	private static Map<String, String> parseDictionary(Document doc, String field)
	{
		Map<?, ?> raw = (Map<?, ?>)doc.get(field);
		if (raw == null) return null;
		Map<String, String> dict = new LinkedHashMap<>();
		for (Map.Entry<?, ?> ent : raw.entrySet()) dict.put((String)ent.getKey(), (String)ent.getValue());
		return dict;
	}	
	
	// turning annotations into the DB-ready format
	private static BasicDBList formulateAnnotation(Annotation annot)
	{
		BasicDBList seq = new BasicDBList();
		seq.add(annot.propURI);
		seq.add(annot.valueURI);
		if (annot.groupNest != null) for (String gn : annot.groupNest) seq.add(gn);
		return seq;
	}
	private static BasicDBList formulateTextLabel(TextLabel label)
	{
		BasicDBList seq = new BasicDBList();
		seq.add(label.propURI);
		seq.add(label.text);
		if (label.groupNest != null) for (String gn : label.groupNest) seq.add(gn);
		return seq;
	}	
}
