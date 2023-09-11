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
import com.cdd.bao.util.*;

import java.util.*;

import com.mongodb.*;
import com.mongodb.client.*;
import org.bson.*;
import org.apache.commons.lang3.*;

/*
	Specialisation based on DataStore: provides access to individual "holding bay" records, which are requested modifications to
	assay data. These are intended as transient entities: they should be applied and/or deleted in a timely manner. The modification
	history is stored within the assay records themselves.
*/

public class DataHolding
{
	private DataStore store;

	// ------------ public methods ------------

	public DataHolding(DataStore store)
	{
		this.store = store;
	}
	
	// levels up the holding bay entry to an assay description; this is only appropriate when the assay didn't exist before;
	// for obvious reasons, the "removed" terms are not considered
	public static Assay createAssayFromHolding(Holding holding)
	{
		Assay assay = new Assay();
		
		assay.assayID = holding.assayID;
		assay.curationTime = holding.submissionTime;
		assay.touchedTime = holding.submissionTime;
		assay.curatorID = holding.curatorID;
		assay.uniqueID = holding.uniqueID;
		assay.schemaURI = holding.schemaURI;
		assay.schemaBranches = holding.schemaBranches;
		assay.schemaDuplication = holding.schemaDuplication;
		assay.text = holding.text;
		assay.annotations = holding.annotsAdded != null ? holding.annotsAdded : new Annotation[0];
		assay.textLabels = holding.labelsAdded != null ? holding.labelsAdded : new TextLabel[0];
		
		AssayUtil.conformAnnotations(assay);
		
		return assay;
	}

	// applies the holding bay "delta" to an assay, which may be null (creating a new entry); returns an assay that has as much information
	// as is possible to create from the two inputs, and is generally ready to be submitted to the database; keeping in mind that if the
	// assayID field is not defined, it will be created automatically later on	
	public static Assay createAssayDelta(Assay assay, Holding holding)
	{
		if (assay == null) assay = new Assay();
		
		assay.curationTime = holding.submissionTime;
		assay.touchedTime = holding.submissionTime;
		if (holding.curatorID != null) assay.curatorID = holding.curatorID;
		if (holding.uniqueID != null) assay.uniqueID = holding.uniqueID;
		if (holding.schemaURI != null) assay.schemaURI = holding.schemaURI;
		if (holding.schemaBranches != null) assay.schemaBranches = holding.schemaBranches;
		if (holding.schemaDuplication != null) assay.schemaDuplication = holding.schemaDuplication;
		if (holding.text != null) assay.text = holding.text;
		
		if (holding.annotsRemoved != null && assay.annotations != null) for (Annotation annot : holding.annotsRemoved)
		{
			for (int n = assay.annotations.length - 1; n >= 0; n--) 
			{
				Annotation look = assay.annotations[n];
				if (annot.matchesProperty(look.propURI, look.groupNest) && annot.valueURI.equals(look.valueURI))
					assay.annotations = ArrayUtils.remove(assay.annotations, n);
			}
		}
		if (holding.annotsAdded != null) skip: for (Annotation annot : holding.annotsAdded)
		{
			if (assay.annotations != null) for (Annotation look : assay.annotations)
				if (annot.matchesProperty(look.propURI, look.groupNest) && annot.valueURI.equals(look.valueURI)) continue skip;
			assay.annotations = ArrayUtils.add(assay.annotations, annot.clone());
		}

		if (holding.labelsRemoved != null && assay.textLabels != null) for (TextLabel annot : holding.labelsRemoved)
		{
			for (int n = assay.textLabels.length - 1; n >= 0; n--) 
			{
				TextLabel look = assay.textLabels[n];
				if (annot.matchesProperty(look.propURI, look.groupNest) && annot.text.equals(look.text))
					assay.textLabels = ArrayUtils.remove(assay.textLabels, n);
			}
		}
		if (holding.labelsAdded != null) skip: for (TextLabel label : holding.labelsAdded)
		{
			if (assay.textLabels != null) for (TextLabel look : assay.textLabels)
				if (label.matchesProperty(look.propURI, look.groupNest) && label.text.equals(look.text)) continue skip;
			assay.textLabels = ArrayUtils.add(assay.textLabels, label.clone());
		}

		return assay;
	}

	// deposits a new holding assay into the system; if the holdingID is defined it is not used: a new one will be created, and the
	// record modified accordingly (and the parameter updated)
	public void depositHolding(Holding holding)
	{
		Document doc = new Document();
		
		holding.holdingID = store.getNextSequence(SEQ_ID_HOLDING);
		doc.put(FLD_HOLDING_ID, holding.holdingID);
		if (holding.assayID > 0) doc.put(FLD_HOLDING_ASSAYID, holding.assayID);
		if (holding.submissionTime != null) doc.put(FLD_HOLDING_SUBMISSIONTIME, holding.submissionTime.getTime());
		doc.put(FLD_HOLDING_CURATORID, holding.curatorID);
		doc.put(FLD_HOLDING_UNIQUEID, holding.uniqueID);
		doc.put(FLD_HOLDING_SCHEMAURI, holding.schemaURI);
		if (holding.schemaBranches != null) doc.put(FLD_HOLDING_SCHEMABRANCHES, DataAssay.serialiseSchemaBranches(holding.schemaBranches));
		if (holding.schemaDuplication != null) doc.put(FLD_HOLDING_SCHEMADUPLICATION, DataAssay.serialiseSchemaDuplication(holding.schemaDuplication));
		doc.put(FLD_HOLDING_DELETEFLAG, holding.deleteFlag);

		doc.put(FLD_HOLDING_TEXT, holding.text);
		doc.put(FLD_HOLDING_ANNOTSADDED, encodeAnnotations(holding.annotsAdded));
		doc.put(FLD_HOLDING_LABELSADDED, encodeTextLabels(holding.labelsAdded));
		doc.put(FLD_HOLDING_ANNOTSREMOVED, encodeAnnotations(holding.annotsRemoved));
		doc.put(FLD_HOLDING_LABELSREMOVED, encodeTextLabels(holding.labelsRemoved));
		
		MongoCollection<Document> coll = store.db.getCollection(COLL_HOLDING);
		coll.insertOne(doc);
	}
	
	// removes the holding item from the database
	public void deleteHolding(long holdingID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_HOLDING);
		Document idx = new Document(FLD_HOLDING_ID, holdingID);
		coll.deleteOne(idx);
	}

	// fetch all of the holding IDs, with no further information about what lies within; guaranteed to be in order
	public long[] fetchHoldings()
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_HOLDING);
		Document proj = new Document(FLD_HOLDING_ID, true);
		Document order = new Document(FLD_HOLDING_ID, 1);
		for (Document doc : coll.find().sort(order).projection(proj)) list.add(doc.getLong(FLD_HOLDING_ID));
		return Util.primLong(list);
	}
	
	// fetch all of the holding IDs for a given curator
	public long[] fetchHoldingsByCurator(String curatorID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_HOLDING);
		Document filter = new Document(FLD_HOLDING_CURATORID, curatorID);
		Document proj = new Document(FLD_HOLDING_ID, true);
		Document order = new Document(FLD_HOLDING_SUBMISSIONTIME, -1);
		List<Long> list = new ArrayList<>();
		for (Document doc : coll.find(filter).sort(order).projection(proj)) list.add(doc.getLong(FLD_HOLDING_ID));
		return Util.primLong(list);
	}
	
	// fetch all of the holding IDs for a given valueURI
	public long[] fetchHoldingsByAnnotation(String valueURI)
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_HOLDING);
		BasicDBList clauses = new BasicDBList();
		clauses.add(new Document(FLD_HOLDING_ANNOTSADDED, new Document("$elemMatch", new Document("$elemMatch", new Document("$eq", valueURI)))));
		clauses.add(new Document(FLD_HOLDING_ANNOTSREMOVED, new Document("$elemMatch", new Document("$elemMatch", new Document("$eq", valueURI)))));
		Document filter = new Document("$or", clauses);
		Document proj = new Document(FLD_HOLDING_ID, true);
		for (Document doc : coll.find(filter).projection(proj)) list.add(doc.getLong(FLD_HOLDING_ID));
		return Util.primLong(list);
	}
	
	// fetch all of the holding IDs corresponding to a given assay (if any)
	public long[] fetchForAssayID(long assayID)
	{
		List<Long> list = new ArrayList<>();
		MongoCollection<Document> coll = store.db.getCollection(COLL_HOLDING);
		Document filter = new Document(FLD_HOLDING_ASSAYID, assayID);
		Document proj = new Document(FLD_HOLDING_ID, true);
		Document order = new Document(FLD_HOLDING_ID, 1);
		for (Document doc : coll.find(filter).sort(order).projection(proj)) list.add(doc.getLong(FLD_HOLDING_ID));
		return Util.primLong(list);
	}
	
	// fetches a specific holding entry in all its glory
	public Holding getHolding(long holdingID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_HOLDING);
		Document filter = new Document(FLD_HOLDING_ID, holdingID);
		for (Document doc : coll.find(filter).limit(1)) return holdingFromDoc(doc);
		return null;
	}
	
	// just pull out the assay ID for a holding bay entry (0 means undefined or not found)
	public long getHoldingAssayID(long holdingID)
	{	
		MongoCollection<Document> coll = store.db.getCollection(COLL_HOLDING);
		Document filter = new Document(FLD_HOLDING_ID, holdingID);
		Document proj = new Document(FLD_HOLDING_ASSAYID, true);
		for (Document doc : coll.find(filter).projection(proj).limit(1)) 
			return doc.containsKey(FLD_HOLDING_ASSAYID) ? doc.getLong(FLD_HOLDING_ASSAYID) : 0;
		return 0;
	}
	
	// stats
	public int countTotal()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_HOLDING);
		return (int)coll.countDocuments();
	}
	
	// ------------ private methods ------------

	// pulls out everything from the source document
	private Holding holdingFromDoc(Document doc)
	{
		Holding holding = new Holding();
		holding.holdingID = doc.getLong(FLD_HOLDING_ID);
		holding.assayID = doc.containsKey(FLD_HOLDING_ASSAYID) ? doc.getLong(FLD_HOLDING_ASSAYID) : 0;
		holding.submissionTime = doc.containsKey(FLD_HOLDING_SUBMISSIONTIME) ? new Date(doc.getLong(FLD_HOLDING_SUBMISSIONTIME)) : null;
		holding.curatorID = doc.getString(FLD_HOLDING_CURATORID);
		holding.uniqueID = doc.getString(FLD_HOLDING_UNIQUEID);
		holding.schemaURI = doc.getString(FLD_HOLDING_SCHEMAURI);
		
		List<?> branches = (List<?>)doc.get(FLD_ASSAY_SCHEMABRANCHES);
		if (branches != null)
		{
			holding.schemaBranches = new SchemaBranch[branches.size()];
			for (int n = 0; n < holding.schemaBranches.length; n++) 
				holding.schemaBranches[n] = DataAssay.parseSchemaBranch((Document)branches.get(n));
		}
		List<?> duplication = (List<?>)doc.get(FLD_ASSAY_SCHEMADUPLICATION);
		if (duplication != null)
		{
			holding.schemaDuplication = new SchemaDuplication[duplication.size()];
			for (int n = 0; n < holding.schemaDuplication.length; n++) 
				holding.schemaDuplication[n] = DataAssay.parseSchemaDuplication((Document)duplication.get(n));
		}
		
		holding.deleteFlag = doc.getBoolean(FLD_HOLDING_DELETEFLAG, false);
		holding.annotsAdded = decodeAnnotations((List<?>)doc.get(FLD_HOLDING_ANNOTSADDED));
		holding.labelsAdded = decodeTextLabels((List<?>)doc.get(FLD_HOLDING_LABELSADDED));
		holding.annotsRemoved = decodeAnnotations((List<?>)doc.get(FLD_HOLDING_ANNOTSREMOVED));
		holding.labelsRemoved = decodeTextLabels((List<?>)doc.get(FLD_HOLDING_LABELSREMOVED));
		holding.text = doc.getString(FLD_HOLDING_TEXT);
		return holding;
	}
	
	// turn an annotation/label list into a DB-ready array
	private BasicDBList encodeAnnotations(Annotation[] annotations)
	{
		BasicDBList dblist = new BasicDBList();
		if (annotations != null) for (Annotation annot : annotations)
		{
			BasicDBList seq = new BasicDBList();
			seq.add(annot.propURI);
			seq.add(annot.valueURI);
			if (annot.groupNest != null) for (String gn : annot.groupNest) seq.add(gn);
			dblist.add(seq);
		}
		return dblist;
	}
	private BasicDBList encodeTextLabels(TextLabel[] textLabels)
	{
		BasicDBList dblist = new BasicDBList();
		if (textLabels != null) for (TextLabel label : textLabels)
		{
			BasicDBList seq = new BasicDBList();
			seq.add(label.propURI);
			seq.add(label.text);
			if (label.groupNest != null) for (String gn : label.groupNest) seq.add(gn);
			dblist.add(seq);
		}
		return dblist;
	}
	
	// unpack annotations/labels from the database format
	private Annotation[] decodeAnnotations(List<?> list)
	{
		Annotation[] annotations = new Annotation[list == null ? 0 : list.size()];
		for (int n = 0; n < annotations.length; n++)
		{
			List<?> seq = (List<?>)list.get(n);
			annotations[n] = new Annotation((String)seq.get(0), (String)seq.get(1));
			if (seq.size() > 2)
			{
				annotations[n].groupNest = new String[seq.size() - 2];
				for (int i = 2; i < seq.size(); i++) annotations[n].groupNest[i - 2] = (String)seq.get(i);
			}
		}
		return annotations;
	}
	private TextLabel[] decodeTextLabels(List<?> list)
	{
		TextLabel[] textLabels = new TextLabel[list == null ? 0 : list.size()];
		for (int n = 0; n < textLabels.length; n++)
		{
			List<?> seq = (List<?>)list.get(n);
			textLabels[n] = new TextLabel((String)seq.get(0), (String)seq.get(1));
			if (seq.size() > 2)
			{
				textLabels[n].groupNest = new String[seq.size() - 2];
				for (int i = 2; i < seq.size(); i++) textLabels[n].groupNest[i - 2] = (String)seq.get(i);
			}
		}
		return textLabels;
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
}
