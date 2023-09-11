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

import com.cdd.bae.config.*;
import com.cdd.bae.util.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.bson.*;
import org.slf4j.*;

import com.mongodb.*;
import com.mongodb.MongoClient;
import com.mongodb.client.*;
import com.mongodb.client.model.*;

/*
	Provides access to the MongoDB instance that centralises the persistent data for all the concurrent tasks.
	
	Publicly useful functionality is provided by the various specialisation classes, e.g. dataAssay, etc. These are separated based on the
	collections that they provide access to.
*/

public class DataStore implements DataObject
{
	private static final Logger logger = LoggerFactory.getLogger(DataStore.class);

	protected MongoClient mongo = null;
	protected MongoDatabase db = null;

	// ------------ notifications ------------

	public static interface Notifier
	{
		// sent when assay text has been added, changed or deleted, meaning that a fingerprint refresh is necessary
		public void datastoreTextChanged();
		
		// sent when fingerprints for an assay have been modified
		public void datastoreFingerprintsChanged();

		// sent when an assay's annotations have been modified
		public void datastoreAnnotationsChanged();
		
		// sent when measurements may need to be updated
		public void datastoreMeasurementsChanged();
		
		// sent when some action may be required for compounds
		public void datastoreCompoundsChanged();
		
		// sent when compound structures have been updated
		public void datastoreStructuresChanged();
	}
	protected Notifier notifier = null;

	// ------------ pseudo-schema ------------

	// sequences	
	protected static final String COLL_SEQUENCES = "sequences";
	protected static final String SEQ_WATERMARK_NLP = "watermarkNLP"; // incremented with the NLP-to-fingerprint indices are modified
	protected static final String SEQ_WATERMARK_MODEL = "watermarkModel"; // incremented when NLP models need to be updated
	protected static final String SEQ_WATERMARK_CORR = "watermarkCorr"; // incremented when correlation models need to be updated
	protected static final String SEQ_WATERMARK_MEASURE = "watermarkMeasure"; // incremented when measurements need updating
	protected static final String SEQ_WATERMARK_COMPOUND = "watermarkCompound"; // incremented when compounds need updating
	protected static final String SEQ_WATERMARK_ASSAY = "watermarkAssay"; // incremented when assays have changed
	protected static final String SEQ_ID_ASSAY = "assayID"; 
	protected static final String SEQ_ID_COMPOUND = "compoundID";
	protected static final String SEQ_ID_HOLDING = "holdingID";
	protected static final String SEQ_ID_PROVISIONAL = "provisionalID";
	protected static final String SEQ_PROVISIONAL_URI = "provisionalURI";
	protected static final String SEQ_ID_KEYWORDMAP = "keywordmapID";
	protected static final String SEQ_ID_TEMPLATE = "templateID";
	
	// assay collection: where most of the interesting content resides
	protected static final String COLL_ASSAY = "assay";
	protected static final String FLD_ASSAY_ID = "assayID"; // autonumbered ID that is unique in this database
	protected static final String FLD_ASSAY_UNIQUEID = "uniqueID"; // external ID that is expected to be globally unique
	protected static final String FLD_ASSAY_TEXT = "text"; // assay protocol description (string)
	protected static final String FLD_ASSAY_FPLIST = "fplist"; // fingerprints: indices of the NLP fragments (list of integers)
	protected static final String FLD_ASSAY_SCHEMAURI = "schemaURI"; // which schema is used (e.g. common assay template)
	protected static final String FLD_ASSAY_SCHEMABRANCHES = "schemaBranches"; // additional grafted-on schema branches
	protected static final String FLD_ASSAY_SCHEMADUPLICATION = "schemaDuplication"; // duplicate groups
	protected static final String FLD_ASSAY_ANNOTATIONS = "annotations"; // semantic annotations: array of arrays [[predicate, object],...]
	protected static final String FLD_ASSAY_TEXTLABELS = "textLabels"; // non-semantic annotations: array of arrays [[predicate, text],...]
	protected static final String FLD_ASSAY_PUBCHEMSOURCE = "pubchemSource"; // PubChem source (string); only defined if it comes from PubChem
	protected static final String FLD_ASSAY_PUBCHEMXREF = "pubchemXRef"; // pubchem-specific annotations
	protected static final String FLD_ASSAY_ISCURATED = "isCurated"; // flag: if true, the assay is marked as expert human curated
	protected static final String FLD_ASSAY_MEASURECHECKED = "measureCheck"; // flag: if true, measurements have been investigated
	protected static final String FLD_ASSAY_MEASURESTATE = "measureState"; // arbitrary string, used to compare against previous measurement state
	protected static final String FLD_ASSAY_CURATIONTIME = "curationTime"; // time of loading the curated text/assays
	protected static final String FLD_ASSAY_TOUCHEDTIME = "touchedTime"; // a time (maybe in the future) that the assay was "touched"
	protected static final String FLD_ASSAY_CURATORID = "curatorID"; // identifier of the curator (the most recent batch)
	protected static final String FLD_ASSAY_HISTORY = "history"; // stack of deltas, which can be used to roll back the annotations
	
	// used within the history section inside the assay datastructure
	protected static final String FLD_ASSAY_HISTORY_UNIQUEIDPATCH = "uniqueIDPatch";
	protected static final String FLD_ASSAY_HISTORY_TEXTPATCH = "textPatch";
	protected static final String FLD_ASSAY_HISTORY_ANNOTSADDED = "annotsAdded";
	protected static final String FLD_ASSAY_HISTORY_ANNOTSREMOVED = "annotsRemoved";
	protected static final String FLD_ASSAY_HISTORY_LABELSADDED = "labelsAdded";
	protected static final String FLD_ASSAY_HISTORY_LABELSREMOVED = "labelsRemoved";

	// the holding bay: where requested modifications to assays reside
	protected static final String COLL_HOLDING = "holding";
	protected static final String FLD_HOLDING_ID = "holdingID"; // unique ID number
	protected static final String FLD_HOLDING_ASSAYID = "assayID"; // reference to existing assay (null = new record)
	protected static final String FLD_HOLDING_SUBMISSIONTIME = "submissionTime"; // when the request was made
	protected static final String FLD_HOLDING_CURATORID = "curatorID"; // who requested it
	protected static final String FLD_HOLDING_UNIQUEID = "uniqueID"; // globally unique ID (null = no change)
	protected static final String FLD_HOLDING_SCHEMAURI = "schemaURI"; // which schema is used (null = no change)
	protected static final String FLD_HOLDING_SCHEMABRANCHES = "schemaBranches"; // additional grafted-on schema branches (null = no change)
	protected static final String FLD_HOLDING_SCHEMADUPLICATION = "schemaDuplication"; // duplicated groups
	protected static final String FLD_HOLDING_DELETEFLAG = "deleteFlag"; // true if this is a deletion rather than an "upsert"
	protected static final String FLD_HOLDING_TEXT = "text"; // raw text (null = no change)
	protected static final String FLD_HOLDING_ANNOTSADDED = "annotsAdded"; // list of annotations to be added (if not already present)
	protected static final String FLD_HOLDING_LABELSADDED = "labelsAdded"; // list of text labels to be added (if not already present)
	protected static final String FLD_HOLDING_ANNOTSREMOVED = "annotsRemoved"; // list of annotations to be deleted (if present)
	protected static final String FLD_HOLDING_LABELSREMOVED = "labelsRemoved"; // list of text labels to be deleted (if present)

	// mappings between an NLP fragment and a fingerprint index, and the contribution to the latest model
	protected static final String COLL_NLP = "nlp";
	protected static final String FLD_NLP_BLOCK = "block"; // the block text from the NLP analysis
	protected static final String FLD_NLP_FP = "fp"; // fingerprint index

	// NLP fingerprint and correlation models
	protected static final String COLL_MODEL = "model";
	protected static final String FLD_MODEL_WATERMARK = "watermark"; // most recent "data state" to which the model corresponds
	protected static final String FLD_MODEL_TYPE = "type"; // either "nlp" or "corr"
	protected static final String FLD_MODEL_TARGET = "target"; // assignment target whose presence is being modelled
	protected static final String FLD_MODEL_FPLIST = "fplist"; // list of fingerprints pertinent to the model (either NLP or assignment indices)
	protected static final String FLD_MODEL_CONTRIBS = "contribs"; // Bayesian model contributions for each
	protected static final String FLD_MODEL_CALIBRATION = "calibration"; // ROC calibration: [min,max]
	protected static final String FLD_MODEL_ISEXPLICIT = "isExplicit"; // true if used directly; false if came in only due to branches

	// annotation index mapping (for efficiency purposes)
	protected static final String COLL_ANNOT = "annotations";
	protected static final String FLD_ANNOT_PROPURI = "propURI"; // property URI
	protected static final String FLD_ANNOT_VALUEURI = "valueURI"; // value URI
	protected static final String FLD_ANNOT_FP = "fp"; // fingerprint index (used by models)
			
	// measurement groups
	protected static final String COLL_MEASUREMENT = "measurement";
	protected static final String FLD_MEASUREMENT_ASSAYID = "assayID";
	protected static final String FLD_MEASUREMENT_NAME = "name";
	protected static final String FLD_MEASUREMENT_UNITS = "units";
	protected static final String FLD_MEASUREMENT_TYPE = "type";
	protected static final String FLD_MEASUREMENT_COMPOUNDID = "compoundID";
	protected static final String FLD_MEASUREMENT_VALUE = "value";
	protected static final String FLD_MEASUREMENT_RELATION = "relation";

	// chemical compounds
	protected static final String COLL_COMPOUND = "compound";
	protected static final String FLD_COMPOUND_ID = "compoundID";
	protected static final String FLD_COMPOUND_MOLFILE = "molfile";
	protected static final String FLD_COMPOUND_HASHECFP6 = "hashECFP6";
	protected static final String FLD_COMPOUND_PUBCHEMCID = "pubchemCID";
	protected static final String FLD_COMPOUND_PUBCHEMSID = "pubchemSID";
	protected static final String FLD_COMPOUND_VAULTID = "vaultID";
	protected static final String FLD_COMPOUND_VAULTMID = "vaultMID";

	// stashing list of assay-containing files that have already been scanned & loaded
	protected static final String COLL_LOADFILES = "loadfiles";
	protected static final String FLD_LOADFILES_PATH = "path"; // full path on server
	protected static final String FLD_LOADFILES_DATE = "date"; // last modified date (long)
	protected static final String FLD_LOADFILES_SIZE = "size"; // file size (long)
	
	// user records (every unique authentication event)
	protected static final String COLL_USER = "user";
	protected static final String FLD_USER_CURATORID = "curatorID"; // used as the unique key (composed from other entries)
	protected static final String FLD_USER_STATUS = "status"; // explicit user account type; null = default minimal access
	protected static final String FLD_USER_SERVICENAME = "serviceName"; // which authentication service was used
	protected static final String FLD_USER_USERID = "userID"; // user ID provided by the service
	protected static final String FLD_USER_NAME = "userName"; // descriptive name (typically person or institution)
	protected static final String FLD_USER_EMAIL = "email"; // email address, if available
	protected static final String FLD_USER_LASTAUTHENT = "lastAuthent"; // when most recently verified via the underlying service
	protected static final String FLD_USER_PWHASH = "passwordHash"; // password hash, if available
	protected static final String FLD_USER_PWSALT = "passwordSalt"; // password hash, if available
	protected static final String FLD_USER_CURATION_HISTORY = "curation"; // assay identifier of assays curated by user in reverse chronological order

	// collection housing term requests
	protected static final String COLL_PROVISIONAL = "provisional";
	protected static final String FLD_PROVISIONAL_ID = "provisionalID";
	protected static final String FLD_PROVISIONAL_PARENTURI = "parentURI"; // parent of requested term
	protected static final String FLD_PROVISIONAL_LABEL = "label"; // name or label for requested term
	protected static final String FLD_PROVISIONAL_URI = "uri"; // uri for requested term
	protected static final String FLD_PROVISIONAL_DESCRIPTION = "description"; // description for requested term
	protected static final String FLD_PROVISIONAL_EXPLANATION = "explanation"; // explanation for requested term
	protected static final String FLD_PROVISIONAL_PROPOSERID = "proposerID"; // userID of user who requested term
	protected static final String FLD_PROVISIONAL_ROLE = "role"; // intended role of the term
	protected static final String FLD_PROVISIONAL_CREATEDTIME = "createdTime"; // time when this document was created
	protected static final String FLD_PROVISIONAL_MODIFIEDTIME = "modifiedTime"; // time when this document was last modified
	protected static final String FLD_PROVISIONAL_REMAPPEDTO = "remappedTo"; // null if not remapped, otherwise term to which request is remapped
	protected static final String FLD_PROVISIONAL_BRIDGESTATUS = "bridgeStatus"; // status within ontolobridge system
	protected static final String FLD_PROVISIONAL_BRIDGEURL = "bridgeURL"; // which ontolobridge it's connected to, if any
	protected static final String FLD_PROVISIONAL_BRIDGETOKEN = "bridgeToken"; // identifier for the ontolobridge request

	// collection housing keyword mappings
	protected static final String COLL_KEYWORDMAP = "keywordmap";
	protected static final String FLD_KEYWORDMAP_ID = "keywordmapID";
	protected static final String FLD_KEYWORDMAP_SCHEMAURI = "schemaURI";
	protected static final String FLD_KEYWORDMAP_PROPURI = "propURI";
	protected static final String FLD_KEYWORDMAP_GROUPNEST = "groupNest";
	protected static final String FLD_KEYWORDMAP_KEYWORD = "keyword";
	protected static final String FLD_KEYWORDMAP_VALUEURI = "valueURI";
	
	// collection housing templates
	protected static final String COLL_TEMPLATE = "template";
	protected static final String FLD_TEMPLATE_ID = "templateID";
	protected static final String FLD_TEMPLATE_SCHEMAPREFIX = "schemaPrefix";
	protected static final String FLD_TEMPLATE_JSON = "json";

	// specialisations
	private DataAssay dataAssay = null;
	private DataHolding dataHolding = null;
	private DataNLP dataNLP = null;
	private DataModel dataModel = null;
	private DataAnnot dataAnnot = null;
	private DataMeasure dataMeasure = null;
	private DataCompound dataCompound = null;
	private DataMisc dataMisc = null;
	private DataUser dataUser = null;
	private DataProvisional dataProvisional = null;
	private DataKeywordMap dataKeywordMap = null;
	
	private static final int SOCKET_TIMEOUT_MS = 60 * 1000; // no one request may take longer than 60 seconds
	
	// ------------ public methods ------------

	// construct: default values apply, so passing (null,0,null) connects to the localhost with the default port
	// using the "bae" database
	public DataStore(InitParams.Database dbConfig)
	{
		if (dbConfig == null)
		{
			logger.debug("Operating in stateless mode (no database connection)");
			return;
		}
	
		LogTimer timer = new LogTimer(logger, "Connecting to MongoDB");
		ServerAddress server = null;
		if (dbConfig.port > 0)
			server = new ServerAddress(dbConfig.host, dbConfig.port);
		else
			server = new ServerAddress(dbConfig.host);
		timer.report("Before MongoClient ({})");
		
		MongoClientOptions.Builder options = MongoClientOptions.builder();
		options.socketTimeout(SOCKET_TIMEOUT_MS);
		
		if (dbConfig.user != null && dbConfig.password != null)
		{
			MongoCredential credential = MongoCredential.createCredential(dbConfig.user, dbConfig.name, dbConfig.password.toCharArray());
			mongo = new MongoClient(server, credential, options.build());
		}
		else mongo = new MongoClient(server, options.build());
		
		timer.report("Before getDatabase ({})");
		db = mongo.getDatabase(dbConfig.name);
		
		if (isDBAvailable())
		{
			timer.report("Connection to database established ({})");
			forceIndexes();
			timer.report("Force indices ({})");
			setupSequences();
			timer.report("SetupSequences ({})");
		}
		else
		{
			timer.report("Connection to database failed ({})");
		}
		
		setupCollections();
		timer.report("DataStore initialisation done ({})");
	}
	
	// initialise with a previously established connection, 
	public DataStore(MongoClient mongo, MongoDatabase db)
	{
		this.mongo = mongo;
		this.db = db;
		forceIndexes();
		setupSequences();
		setupCollections();
	}
		
	public boolean isDBAvailable()
	{
		if (db == null) return false;
		try
		{
			db.runCommand(new Document("ping", "1").append("maxTimeMS", 100));
			return true;
		}
		catch (Exception e)
		{
			logger.error("Check access to Mongo database", e);
			return false;
		}
	}

	public void setNotifier(Notifier notifier) {this.notifier = notifier;}
	
	// access to specialisations
	public DataAssay assay() {return dataAssay;}
	public DataHolding holding() {return dataHolding;}
	public DataNLP nlp() {return dataNLP;}
	public DataModel model() {return dataModel;}
	public DataAnnot annot() {return dataAnnot;}
	public DataMeasure measure() {return dataMeasure;}
	public DataCompound compound() {return dataCompound;}
	public DataMisc misc() {return dataMisc;}
	public DataUser user() {return dataUser;}
	public DataProvisional provisional() {return dataProvisional;}
	public DataKeywordMap keywordMap() {return dataKeywordMap;}

	// ------------ internal methods ------------

	// makes sure all indexes are properly created
	private void forceIndexes()
	{
		createIndex(COLL_ASSAY + "_" + FLD_ASSAY_ID, COLL_ASSAY, new String[]{FLD_ASSAY_ID}, null, false);
		createIndex(COLL_ASSAY + "_" + FLD_ASSAY_UNIQUEID, COLL_ASSAY, new String[]{FLD_ASSAY_UNIQUEID}, null, false);
		createIndex(COLL_ASSAY + "_" + FLD_ASSAY_CURATIONTIME, COLL_ASSAY, new String[]{FLD_ASSAY_CURATIONTIME}, new int[]{-1}, false);
		
		createIndex(COLL_HOLDING + "_" + FLD_HOLDING_ID, COLL_HOLDING, new String[]{FLD_HOLDING_ID}, null, true);
		createIndex(COLL_HOLDING + "_" + FLD_HOLDING_ASSAYID, COLL_HOLDING, new String[]{FLD_HOLDING_ASSAYID}, null, false);

		createIndex(COLL_ANNOT + "_" + FLD_ANNOT_PROPURI + FLD_ANNOT_VALUEURI, 
					COLL_ANNOT, new String[]{FLD_ANNOT_PROPURI, FLD_ANNOT_VALUEURI}, null, false);
		
		createIndex(COLL_NLP + "_" + FLD_NLP_BLOCK, COLL_NLP, new String[]{FLD_NLP_BLOCK}, null, true);
		createIndex(COLL_NLP + "_" + FLD_NLP_FP, COLL_NLP, new String[]{FLD_NLP_FP}, null, true);
		
		createIndex(COLL_MODEL + "_" + FLD_MODEL_TARGET, COLL_MODEL, new String[]{FLD_MODEL_TARGET}, null, false);
		createIndex(COLL_MODEL + "_" + FLD_MODEL_TYPE + "_" + FLD_MODEL_TARGET, 
					COLL_MODEL, new String[]{FLD_MODEL_TYPE, FLD_MODEL_TARGET}, null, false);
		createIndex(COLL_MODEL + "_" + FLD_MODEL_WATERMARK, COLL_MODEL, new String[]{FLD_MODEL_WATERMARK}, null, false);
					
		createIndex(COLL_MEASUREMENT + "_" + FLD_MEASUREMENT_ASSAYID, COLL_MEASUREMENT, new String[]{FLD_MEASUREMENT_ASSAYID}, null, false);

		createIndex(COLL_COMPOUND + "_" + FLD_COMPOUND_ID, COLL_COMPOUND, new String[]{FLD_COMPOUND_ID}, null, false);
		createIndex(COLL_COMPOUND + "_" + FLD_COMPOUND_PUBCHEMCID, COLL_COMPOUND, new String[]{FLD_COMPOUND_PUBCHEMCID}, null, false);
		createIndex(COLL_COMPOUND + "_" + FLD_COMPOUND_PUBCHEMSID, COLL_COMPOUND, new String[]{FLD_COMPOUND_PUBCHEMSID}, null, false);
		createIndex(COLL_COMPOUND + "_" + FLD_COMPOUND_VAULTMID, COLL_COMPOUND, new String[]{FLD_COMPOUND_VAULTMID}, null, false);

		createIndex(COLL_LOADFILES + "_" + FLD_LOADFILES_PATH, COLL_LOADFILES, new String[]{FLD_LOADFILES_PATH}, null, true);
		
		createIndex(COLL_USER + "_" + FLD_USER_CURATORID, COLL_USER, new String[]{FLD_USER_CURATORID}, null, true);
		
		createIndex(COLL_PROVISIONAL + "_" + FLD_PROVISIONAL_ID, COLL_PROVISIONAL, new String[]{FLD_PROVISIONAL_ID}, null, true);
		createIndex(COLL_PROVISIONAL + "_" + FLD_PROVISIONAL_URI, COLL_PROVISIONAL, new String[]{FLD_PROVISIONAL_URI}, null, false);
		
		createIndex(COLL_KEYWORDMAP + "_" + FLD_KEYWORDMAP_ID, COLL_KEYWORDMAP, new String[]{FLD_KEYWORDMAP_ID}, null, true);
		createIndex(COLL_KEYWORDMAP + "_" + FLD_KEYWORDMAP_SCHEMAURI + FLD_KEYWORDMAP_PROPURI + FLD_KEYWORDMAP_KEYWORD,
					COLL_KEYWORDMAP, new String[]{FLD_KEYWORDMAP_SCHEMAURI, FLD_KEYWORDMAP_PROPURI, FLD_KEYWORDMAP_KEYWORD}, null, false);
					
		createIndex(COLL_TEMPLATE + "_" + FLD_TEMPLATE_ID, COLL_TEMPLATE, new String[]{FLD_TEMPLATE_ID}, null, true);
		createIndex(COLL_TEMPLATE + "_" + FLD_TEMPLATE_SCHEMAPREFIX, COLL_TEMPLATE, new String[]{FLD_TEMPLATE_SCHEMAPREFIX}, null, true);
	}
	
	private void createIndex(String idxName, String collName, String[] fields, int[] ordering, boolean isUnique)
	{
		Document keys = new Document();
		for (int n = 0; n < fields.length; n++) keys.append(fields[n], ordering == null ? 1 : ordering[n]);
		IndexOptions opt = new IndexOptions().unique(isUnique).name(idxName);
		MongoCollection<Document> coll = db.getCollection(collName);
		
		try 
		{
			coll.createIndex(keys, opt);
			return;
		}
		catch (MongoCommandException ex)
		{
			if (ex.getErrorCode() == 85)
			{
				String msg = "Creation of index failed (" + idxName + "): proceeding to recreate";
				logger.warn(msg, ex);
				Util.writeln(msg);
				// fall through to next step...
			}
			else throw ex;
		}
		
		coll.dropIndex(idxName);
		coll.createIndex(keys, opt);
	}
	
	// makes sure all of the sequences are defined as individual documents
	private void setupSequences()
	{
		MongoCollection<Document> coll = db.getCollection(COLL_SEQUENCES);
		String[] seq = new String[]
		{
			SEQ_WATERMARK_NLP,
			SEQ_WATERMARK_MODEL,
			SEQ_WATERMARK_CORR,
			SEQ_WATERMARK_MEASURE,
			SEQ_WATERMARK_COMPOUND,
			SEQ_WATERMARK_ASSAY,
			SEQ_ID_ASSAY,
			SEQ_ID_COMPOUND,
			SEQ_ID_HOLDING,
			SEQ_ID_PROVISIONAL,
			SEQ_PROVISIONAL_URI,
			SEQ_ID_KEYWORDMAP,
			SEQ_ID_TEMPLATE,
		};
		for (int n = 0; n < seq.length; n++) 
		{
			boolean found = false;
			for (Document doc : coll.find(new Document("_id", seq[n]))) {found = true; break;}
			if (found) continue;
		
			Document doc = new Document("_id", seq[n]).append("seq", 1L);
			coll.insertOne(doc);
		}
	}
	
	// instantiate all of the collection-specific instances
	private void setupCollections()
	{
		dataAssay = new DataAssay(this);
		dataHolding = new DataHolding(this);
		dataNLP = new DataNLP(this);
		dataModel = new DataModel(this);
		dataAnnot = new DataAnnot(this);
		dataMeasure = new DataMeasure(this);
		dataCompound = new DataCompound(this);
		dataMisc = new DataMisc(this);
		dataUser = new DataUser(this);
		dataProvisional = new DataProvisional(this);
		dataKeywordMap = new DataKeywordMap(this);
	}	

	// fetches the value of a sequence
	protected long getSequence(String seqID)
	{
		MongoCollection<Document> coll = db.getCollection(COLL_SEQUENCES);
		Document query = new Document("_id", seqID);
		for (Document doc : coll.find(query)) {return (Long)doc.get("seq");}
		return 0;
	}

	// increments a sequence, then returns the new value
	protected long getNextSequence(String seqID)
	{
		MongoCollection<Document> coll = db.getCollection(COLL_SEQUENCES);
		Document query = new Document("_id", seqID);
		Document update = new Document("$inc", new Document("seq", 1));
		Document result = coll.findOneAndUpdate(query, update); // (gets the _old_ value, which is fine)
		return (Long)result.get("seq");
	}
}
