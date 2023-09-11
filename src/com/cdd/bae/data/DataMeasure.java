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
import com.cdd.bao.util.*;

import static com.cdd.bae.data.DataStore.*;

import java.util.*;

import org.apache.commons.lang3.*;
import org.bson.*;
import org.bson.types.*;

import com.mongodb.*;
import com.mongodb.client.*;

/*
	Specialisation based on DataStore: provides access to groups of measurement data.
*/

public class DataMeasure
{
	private DataStore store;
	
	// commonly used string types (for Measurement::type); not necessarily limited to these
	public static final String TYPE_ACTIVITY = "activity"; // boolean 1=active, 0=active
	public static final String TYPE_PROBE = "probe"; // presence implies probeness
	public static final String TYPE_PRIMARY = "primary"; // numeric measurement used to determine activity
	public static final String TYPE_MEASUREMENT = "measurement"; // general, i.e. need to dig deeper to decide what it is specifically

	protected static final int LIMIT = 100000;

	// ------------ public methods ------------

	public DataMeasure(DataStore store)
	{
		this.store = store;
	}

	// returns true if it is possible to fetch anything
	public boolean isAnything()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_MEASUREMENT);
		Document proj = new Document("_id", true);
		for (Document doc : coll.find().projection(proj).limit(1)) return true;
		return false;
	}

	// fetches a single measurement, given its database ID	
	public Measurement getMeasurement(String id)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_MEASUREMENT);
		Document filter = new Document("_id", new ObjectId(id));
		for (Document doc : coll.find(filter).limit(1)) return measurementFromDoc(doc);
		return null;
	}
	
	// fetch all the measurements that belong to a given assay; note that it internally does some shenanigans for
	// measurements that got split up into smaller blocks: they get recombined using name as the primary key
	public Measurement[] getMeasurements(long assayID) {return getMeasurements(assayID, null);}
	
	public Measurement[] getMeasurements(long assayID, String[] types)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_MEASUREMENT);
		Document filter = new Document(FLD_MEASUREMENT_ASSAYID, assayID);
		if (Util.length(types) > 0)
		{
			BasicDBList typelist = new BasicDBList();
			for (String t : types) typelist.add(t);
			filter.append(FLD_MEASUREMENT_TYPE, new Document("$in", typelist));
		}
		List<Measurement> results = new ArrayList<>();
		Map<String, Integer> nameIndex = new HashMap<>();
		for (Document doc : coll.find(filter))
		{
			Measurement measure = measurementFromDoc(doc);
			int idx = nameIndex.getOrDefault(measure.name, -1);
			if (idx < 0)
			{
				nameIndex.put(measure.name, results.size());
				results.add(measure);
			}
			else
			{
				Measurement current = results.get(idx);
				current.compoundID = ArrayUtils.addAll(current.compoundID, measure.compoundID);
				current.value = ArrayUtils.addAll(current.value, measure.value);
				current.relation = ArrayUtils.addAll(current.relation, measure.relation);
			}
		}
		return results.toArray(new Measurement[results.size()]);
	}
	
	// updates the content of a given measurement; if the id is defined will update the existing record; if null,
	// will add a new one; note that calling this function with more than ~300K measurements runs the risk of bumping
	// into the document size limit; use appendMeasurements(..) if this is a possible problem
	public void updateMeasurement(Measurement measure)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_MEASUREMENT);

		Document doc = new Document();
		doc.append(FLD_MEASUREMENT_ASSAYID, measure.assayID);
		doc.append(FLD_MEASUREMENT_NAME, measure.name);
		doc.append(FLD_MEASUREMENT_UNITS, measure.units);
		doc.append(FLD_MEASUREMENT_TYPE, measure.type);

		BasicDBList compoundID = new BasicDBList(), value = new BasicDBList(), relation = new BasicDBList();
		for (int n = 0; n < measure.compoundID.length; n++)
		{
			compoundID.add(measure.compoundID[n]);
			value.add(measure.value[n]);
			relation.add(measure.relation[n]);
		}

		doc.append(FLD_MEASUREMENT_COMPOUNDID, compoundID);
		doc.append(FLD_MEASUREMENT_VALUE, value);
		doc.append(FLD_MEASUREMENT_RELATION, relation);

		if (measure.id != null)
		{
			Document idx = new Document("_id", new ObjectId(measure.id));
			coll.updateOne(idx, new Document("$set", doc));
		}
		else
		{
			coll.insertOne(doc);
			measure.id = doc.getObjectId("_id").toHexString();
		}
	}
	
	// usually equivalent to calling updateMeasurement(..) with the id undefined; if the number of items is
	// above a certain block size, it will be split up into smaller units, and these will be returned
	public Measurement[] appendMeasurements(Measurement measure)
	{
		final int sz = measure.compoundID.length;

		if (sz <= LIMIT)
		{
			// this assumes that the measurements were cleared in the database
			measure.id = null;
			updateMeasurement(measure);
			return new Measurement[]{measure};
		}
		
		List<Measurement> blocks = new ArrayList<>();
		for (int n = 0; n < sz; n += LIMIT)
		{
			int end = Math.min(n + LIMIT, sz);
			Measurement blk = new Measurement();
			blk.assayID = measure.assayID;
			blk.name = measure.name;
			blk.units = measure.units;
			blk.type = measure.type;
			blk.compoundID = Arrays.copyOfRange(measure.compoundID, n, end);
			blk.value = Arrays.copyOfRange(measure.value, n, end);
			blk.relation = Arrays.copyOfRange(measure.relation, n, end);
			updateMeasurement(blk);
			blocks.add(blk);
		}
		
		return blocks.toArray(new Measurement[blocks.size()]);
	}
	
	// remove just one
	public void deleteMeasurement(String id)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_MEASUREMENT);
		Document idx = new Document("_id", new ObjectId(id));
		coll.deleteOne(idx);
	}
	
	// remove all measurements for an assay
	public void deleteMeasurementsForAssay(long assayID)
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_MEASUREMENT);
		Document idx = new Document(FLD_MEASUREMENT_ASSAYID, assayID);
		coll.deleteMany(idx);
	}

	// returns the total number of measurement datapoints
	public int countMeasurements()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_MEASUREMENT);
		return (int)coll.countDocuments();
	}

	// returns the number of assay identifiers, i.e. number of assays that have measurements
	public int countUniqueAssays()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_MEASUREMENT);
		Document proj = new Document(FLD_MEASUREMENT_ASSAYID, true);
		Set<Long> unique = new HashSet<>();
		for (Document doc : coll.find().projection(proj)) unique.add(doc.getLong(FLD_MEASUREMENT_ASSAYID));
		return unique.size();
		
		/* ... this seems to be even slower and more prone to timeout
		String reduce = String.join("\n", new String[]
		{
			"function(doc, prev)",
			"{",
			"  var assayID = doc." + FLD_MEASUREMENT_ASSAYID + ";",
			"  prev[assayID] = true;",
			"}"
		});
		Document group = new Document("ns", COLL_MEASUREMENT);
		group.append("initial", new Document()).append("$reduce", reduce);
		
		Document result = store.db.runCommand(new Document("group", group));
		List<Document> retvals = (List<Document>)result.get("retval");
		Document props = retvals.get(0);
		return props.size();*/
	}
	
	// for a given assay, counts the number of compounds that are associated with it by way of measurements
	public int countCompounds(long assayID)
	{
		// NOTE: might be possible to speed up the implementation by using internal javascript, but still have to collect
		// the IDs to remote nondegenerates... may be a bit slow either ways
	
		MongoCollection<Document> coll = store.db.getCollection(COLL_MEASUREMENT);
		Document filter = new Document(FLD_MEASUREMENT_ASSAYID, assayID);
		// note: adding {type=activity/primary} restriction cuts down the time taken to count the molecules, but there may be
		// cases where an assay will have measurements of other types but not these... consider this a low priority bug
		List<String> types = Arrays.asList(new String[]{TYPE_ACTIVITY, TYPE_PRIMARY});
		filter.append(FLD_MEASUREMENT_TYPE, new Document("$in", types));
		Document proj = new Document(FLD_MEASUREMENT_COMPOUNDID, true);

		Set<Long> uniqueCompounds = new HashSet<>();
		for (Document doc : coll.find(filter).projection(proj))
		{
			List<?> listCompound = doc.get(FLD_MEASUREMENT_COMPOUNDID, List.class);
			for (Object cpdID : listCompound) uniqueCompounds.add((Long)cpdID);
		}
		return uniqueCompounds.size();
	}

	// measurement watermark: when measurements need to be updated (e.g. acquiring the full record and extracting)
	public long getWatermarkMeasure() {return store.getSequence(SEQ_WATERMARK_MEASURE);}
	public long nextWatermarkMeasure() {return store.getNextSequence(SEQ_WATERMARK_MEASURE);}

	// ------------ private methods ------------

	private Measurement measurementFromDoc(Document doc)
	{
		Measurement measure = new Measurement();
		measure.id = doc.getObjectId("_id").toHexString();
		measure.assayID = doc.getLong(FLD_MEASUREMENT_ASSAYID);
		measure.name = doc.getString(FLD_MEASUREMENT_NAME);
		measure.units = doc.getString(FLD_MEASUREMENT_UNITS);
		measure.type = doc.getString(FLD_MEASUREMENT_TYPE);
		
		List<?> listCompound = doc.get(FLD_MEASUREMENT_COMPOUNDID, List.class);
		measure.compoundID = new long[listCompound.size()];
		for (int n = 0; n < listCompound.size(); n++) measure.compoundID[n] = (Long)listCompound.get(n);
		
		List<?> listValue = doc.get(FLD_MEASUREMENT_VALUE, List.class);
		measure.value = new Double[listValue.size()];
		for (int n = 0; n < listValue.size(); n++) measure.value[n] = (Double)listValue.get(n);

		List<?> listRelation = doc.get(FLD_MEASUREMENT_RELATION, List.class);
		measure.relation = new String[listRelation.size()];
		for (int n = 0; n < listRelation.size(); n++) measure.relation[n] = (String)listRelation.get(n);

		return measure;
	}
}
