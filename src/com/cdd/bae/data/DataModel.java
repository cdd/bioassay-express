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

import static com.cdd.bae.data.DataStore.*;

import java.util.*;

import org.bson.*;

import com.mongodb.*;
import com.mongodb.client.*;
import com.mongodb.client.model.*;

/*
	Specialisation based on DataStore: provides access to Bayesian model content only.
*/

public class DataModel
{
	private DataStore store;

	private ModelCache nlpCache;
	private ModelCache corrCache;

	protected static final String TYPE_NLP = "nlp";
	protected static final String TYPE_CORR = "corr";

	protected static class ModelCache
	{
		private DataStore store;

		private String modelType;
		private String seqWatermark;
		private Map<Integer, Model> cache = new HashMap<>();
		private Set<Integer> cacheTargets = null;
		private final Object mutex = new Object();

		public ModelCache(String modelType, DataStore store)
		{
			this.modelType = modelType;
			this.store = store;
			if (modelType.equals(TYPE_NLP)) seqWatermark = SEQ_WATERMARK_MODEL;
			else if (modelType.equals(TYPE_CORR)) seqWatermark = SEQ_WATERMARK_CORR;
		}

		// returns various totals
		public int count()
		{
			Document filter = new Document(FLD_MODEL_TYPE, modelType);
			return (int)store.db.getCollection(COLL_MODEL).countDocuments(filter);
		}

		// return a list of all of the targets of the corresponding type
		public Set<Integer> allTargets()
		{
			synchronized (mutex)
			{
				if (cacheTargets != null) return cacheTargets;
			}
			Set<Integer> all = new HashSet<>();
			MongoCollection<Document> coll = store.db.getCollection(COLL_MODEL);
			Document filter = new Document(FLD_MODEL_TYPE, modelType);
			Document proj = new Document(FLD_MODEL_TARGET, true);
			for (Document doc : coll.find(filter).projection(proj)) all.add(doc.getInteger(FLD_MODEL_TARGET));
			synchronized (mutex)
			{
				cacheTargets = all;
			}
			return all;
		}

		// quick-return methods to see if a model is in there
		public boolean hasModel(int target)
		{
			if (cache.containsKey(target)) return true;
			MongoCollection<Document> coll = store.db.getCollection(COLL_MODEL);
			return coll.countDocuments(targetFilter(target), new CountOptions().limit(1)) > 0;
		}

		// returns the whole model for the corresponding type & target, or null if it does not exist
		public Model getModel(int target)
		{
			long watermark = getWatermark();
			synchronized (mutex)
			{
				Model model = cache.get(target);
				if (model != null && model.watermark == watermark)
				{
					if (model.target < 0) return null; // means not in database
					return model;
				}

				MongoCollection<Document> coll = store.db.getCollection(COLL_MODEL);
				for (Document doc : coll.find(targetFilter(target)))
				{
					model = modelFromDoc(doc);
					cache.put(target, model);
					return model;
				}

				// not found: so temporarily block it
				model = new Model();
				model.watermark = watermark;
				model.target = -1;
				cache.put(target, model);
			}
			return null;
		}

		// adds or replaces the indicated model
		public void submitModel(Model model)
		{
			synchronized (mutex)
			{
				cache.put(model.target, model);
				if (cacheTargets == null) allTargets();
				cacheTargets.add(model.target);
			}

			MongoCollection<Document> coll = store.db.getCollection(COLL_MODEL);

			Document doc = modelToDoc(modelType, model);
			doc.append(FLD_MODEL_ISEXPLICIT, model.isExplicit);

			coll.replaceOne(targetFilter(model.target), doc, new ReplaceOptions().upsert(true));
		}

		// deletion of models
		public void deleteModel(int target)
		{
			synchronized (mutex)
			{
				cache.remove(target);
				if (cacheTargets != null) cacheTargets.remove(target);
			}

			MongoCollection<Document> coll = store.db.getCollection(COLL_MODEL);
			coll.deleteOne(targetFilter(target));
		}

		// blanking of models: this keeps the watermark updated, but removes everything else, so it's somewhat invisible
		public void blankModel(int target, long watermark)
		{
			MongoCollection<Document> coll = store.db.getCollection(COLL_MODEL);
			Document doc = targetFilter(target).append(FLD_MODEL_WATERMARK, watermark);
			coll.replaceOne(targetFilter(target), doc, new ReplaceOptions().upsert(true));
		}

		// fetch targets grouped by watermark, so they can be processed oldest-first
		public SortedMap<Long, List<Integer>> groupByWatermarks()
		{
			MongoCollection<Document> coll = store.db.getCollection(COLL_MODEL);
			Document filter = new Document(FLD_MODEL_TYPE, modelType);
			Document order = new Document(FLD_MODEL_WATERMARK, 1);
			Document proj = new Document(FLD_MODEL_TARGET, true).append(FLD_MODEL_WATERMARK, true);
			SortedMap<Long, List<Integer>> result = new TreeMap<>();
			for (Document doc : coll.find(filter).sort(order).projection(proj))
			{
				Long watermark = doc.getLong(FLD_MODEL_WATERMARK);
				if (!result.containsKey(watermark)) result.put(watermark, new ArrayList<>());
				result.get(watermark).add(doc.getInteger(FLD_MODEL_TARGET));
			}
			return result;
		}

		// watermark controls rebuild of models when NLP fingerprints or annotations have changed
		public long getWatermark() {return store.getSequence(seqWatermark);}
		public long nextWatermark() {return store.getNextSequence(seqWatermark);}

		// returns the watermark for the corresponding kind of model, based on the target (aka annotation fingerprint); returns 0 if there is no model
		public long getModelWatermark(int target)
		{
			MongoCollection<Document> coll = store.db.getCollection(COLL_MODEL);
			Document proj = new Document(FLD_MODEL_WATERMARK, true);
			for (Document doc : coll.find(targetFilter(target)).projection(proj)) return doc.getLong(FLD_MODEL_WATERMARK);
			return 0;
		}

		private Document targetFilter(int target)
		{
			return new Document(FLD_MODEL_TYPE, modelType).append(FLD_MODEL_TARGET, target);
		}
	}

	// ------------ public methods ------------

	public DataModel(DataStore store)
	{
		this.store = store;
		nlpCache = new ModelCache(TYPE_NLP, store);
		corrCache = new ModelCache(TYPE_CORR, store);
	}

	// returns various totals
	public int countModelNLP() {return nlpCache.count();}
	public int countModelCorr() {return corrCache.count();}

	// return a list of all of the targets of the corresponding type
	public Set<Integer> allTargetsNLP() {return nlpCache.allTargets();}
	public Set<Integer> allTargetsCorr() {return corrCache.allTargets();}

	// return information about model watermarks (models are sorted by watermark in ascending order)
	public SortedMap<Long, List<Integer>> groupNLPByWatermarks() {return nlpCache.groupByWatermarks();}
	public SortedMap<Long, List<Integer>> groupCorrByWatermarks() {return corrCache.groupByWatermarks();}

	public boolean hasModelNLP(int target) {return nlpCache.hasModel(target);}
	public boolean hasModelCorr(int target) {return corrCache.hasModel(target);}

	// returns the watermark for the corresponding kind of model, based on the target (aka annotation fingerprint); returns 0 if there is no model
	public long getModelNLPWatermark(int target) {return nlpCache.getModelWatermark(target);}
	public long getModelCorrWatermark(int target) {return corrCache.getModelWatermark(target);}

	// returns the whole model for the corresponding type & target, or null if it does not exist
	public Model getModelNLP(int target) {return nlpCache.getModel(target);}
	public Model getModelCorr(int target) {return corrCache.getModel(target);}

	// deletion of models
	public void deleteModelNLP(int target) {nlpCache.deleteModel(target);}
	public void deleteModelCorr(int target) {corrCache.deleteModel(target);}

	// blanking of models: this keeps the watermark updated, but removes everything else, so it's somewhat invisible
	public void blankModelNLP(int target, long watermark) {nlpCache.blankModel(target, watermark);}
	public void blankModelCorr(int target, long watermark) {corrCache.blankModel(target, watermark);}

	// zap everything
	public void deleteAllModels()
	{
		MongoCollection<Document> coll = store.db.getCollection(COLL_MODEL);
		coll.deleteMany(new Document());
		nextWatermarkNLP();
		nextWatermarkCorr();
	}

	// adds or replaces the indicated model
	public void submitModelNLP(Model model) {nlpCache.submitModel(model);}
	public void submitModelCorr(Model model) {corrCache.submitModel(model);}

	// looks through all of the models that have the indicated property, and accumulates each unique value
	public Set<String> modelledValuesForProperty(String propURI)
	{
		Map<Integer, String> targetToValue = new HashMap<>();
		for (AnnotationFP annot : store.annot().fetchAnnotationFP())
		{
			if (annot.propURI.equals(propURI)) targetToValue.put(annot.fp, annot.valueURI);
		}

		Set<String> values = new HashSet<>();

		Set<Integer> targets = new HashSet<>(nlpCache.allTargets());
		targets.addAll(corrCache.allTargets());
		for (Integer target : targets)
		{
			String valueURI = targetToValue.get(target);
			if (valueURI != null) values.add(valueURI);
		}
		return values;
	}

	// model watermark: for when NLP fingerprints have changed, requiring rebuild
	public long getWatermarkNLP() {return nlpCache.getWatermark();}
	public long nextWatermarkNLP() {return nlpCache.nextWatermark();}

	// correlation watermark: for when annotations have changed, requiring rebuild
	public long getWatermarkCorr() {return corrCache.getWatermark();}
	public long nextWatermarkCorr() {return corrCache.nextWatermark();}

	// ------------ protected methods ------------

	// ------------ private methods ------------

	protected static Model modelFromDoc(Document doc)
	{
		Model model = new Model();
		model.watermark = doc.getLong(FLD_MODEL_WATERMARK);
		model.target = doc.getInteger(FLD_MODEL_TARGET);

		List<?> fplist = (List<?>)doc.get(FLD_MODEL_FPLIST);
		if (fplist != null)
		{
			model.fplist = new int[fplist.size()];
			for (int n = 0; n < model.fplist.length; n++) model.fplist[n] = (Integer)fplist.get(n);
		}

		List<?> contribs = (List<?>)doc.get(FLD_MODEL_CONTRIBS);
		if (contribs != null)
		{
			model.contribs = new float[contribs.size()];
			for (int n = 0; n < model.contribs.length; n++) model.contribs[n] = getRealFloat(contribs.get(n));
		}

		List<?> calib = (List<?>)doc.get(FLD_MODEL_CALIBRATION);
		if (calib != null)
		{
			model.calibLow = getRealFloat(calib.get(0));
			model.calibHigh = getRealFloat(calib.get(1));
		}

		model.isExplicit = doc.getBoolean(FLD_MODEL_ISEXPLICIT, true);

		return model;
	}

	protected static Document modelToDoc(String type, Model model)
	{
		Document doc = new Document(FLD_MODEL_TYPE, type).append(FLD_MODEL_TARGET, model.target)/*.append(FLD_MODEL_SCHEMAURI, model.schemaURI)*/;
		doc.append(FLD_MODEL_WATERMARK, model.watermark);

		BasicDBList dbFPList = new BasicDBList();
		for (int fp : model.fplist) dbFPList.add(fp);
		doc.append(FLD_MODEL_FPLIST, dbFPList);

		BasicDBList dbContribs = new BasicDBList();
		for (double c : model.contribs) dbContribs.add(c);
		doc.append(FLD_MODEL_CONTRIBS, dbContribs);

		BasicDBList dbCalib = new BasicDBList();
		dbCalib.add(model.calibLow);
		dbCalib.add(model.calibHigh);
		doc.append(FLD_MODEL_CALIBRATION, dbCalib);

		doc.append(FLD_MODEL_ISEXPLICIT, model.isExplicit);
		return doc;
	}

	// turns an object into a single precision float, with some tolerance
	private static float getRealFloat(Object obj)
	{
		if (obj instanceof Float) return (Float)obj;
		return ((Number)obj).floatValue(); // will fail gracefully if invalid
	}
}
