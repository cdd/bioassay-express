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

import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import org.apache.commons.lang3.*;
import org.json.*;
import org.slf4j.*;

/*
	Converting between the DataStore.Assay object and a JSON representation, used for external serialisation and
	communicating with the web front end.
	
	Note that when an assay is serialised to JSON, it includes a variety of additional information that isn't in the raw
	datastructure (e.g. the labels for the annotation URIs, and the full name of the curator): this data does not need to
	be provided when submitting back to the database. Derived information from the raw datastructure is not included in
	serialisation (e.g. NLP fingerprints, curation flag) since it is situation specific, and gets recalculated.
*/

public class AssayJSON
{
	private static Logger logger = LoggerFactory.getLogger(AssayJSON.class);

	public static final String PROP_URI = "propURI";
	public static final String PROP_ABBREV = "propAbbrev";
	public static final String PROP_LABEL = "propLabel";
	public static final String ASSAY_ID = "assayID";
	public static final String UNIQUE_ID = "uniqueID";
	public static final String ANNOTS_REMOVED = "annotsRemoved";
	public static final String ANNOTS_ADDED = "annotsAdded";
	public static final String LABELS_REMOVED = "labelsRemoved";
	public static final String LABELS_ADDED = "labelsAdded";
	public static final String CURATOR_ID = "curatorID";
	public static final String CURATION_TIME = "curationTime";
	public static final String UNIQUEID_PATCH = "uniqueIDPatch";
	public static final String TEXT_PATCH = "textPatch";
	public static final String GROUP_NEST = "groupNest";
	public static final String GROUP_LABEL = "groupLabel";
	public static final String VALUE_URI = "valueURI";
	public static final String VALUE_LABEL = "valueLabel";

	public static final class Options
	{
		public boolean includeText = true;
		public boolean includeHistory = true;
	}
	private static final Options DEFOPT = new Options();

	private AssayJSON()
	{
	}

	// ------------ public methods ------------

	// turns the assay datastructure into a JSON object; always succeeds, unless there is something extra specially gnarly about
	// the argument object
	public static JSONObject serialiseAssay(DataObject.Assay assay) {return serialiseAssay(assay, DEFOPT);}
	public static JSONObject serialiseAssay(DataObject.Assay assay, Options opt)
	{
		if (assay == null) return null;
		JSONObject json = new JSONObject();

		Set<String> dupCheck = new HashSet<>();

		try
		{
			json.put(ASSAY_ID, assay.assayID);
			json.put(UNIQUE_ID, assay.uniqueID);
			if (opt.includeText) json.put("text", assay.text);
			
			json.put("schemaURI", assay.schemaURI);
			
			if (Util.length(assay.schemaBranches) > 0)
			{
				JSONArray jsonBranches = new JSONArray();
				for (DataObject.SchemaBranch branch : assay.schemaBranches) jsonBranches.put(serialiseBranch(branch));
				json.put("schemaBranches", jsonBranches);
			}
			if (Util.length(assay.schemaDuplication) > 0)
			{
				JSONArray jsonDuplication = new JSONArray();
				for (DataObject.SchemaDuplication dupl : assay.schemaDuplication) jsonDuplication.put(serialiseDuplication(dupl));
				json.put("schemaDuplication", jsonDuplication);
			}

			SchemaDynamic schdyn = new SchemaDynamic(Common.getSchema(assay.schemaURI), assay.schemaBranches, assay.schemaDuplication);
			Schema schema = schdyn.getResult();

			JSONArray jsonAnnot = new JSONArray();
			if (assay.annotations != null) for (DataObject.Annotation annot : assay.annotations)
			{
				if (!dupCheck.add(Schema.keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI))) continue;
				jsonAnnot.put(serialiseAnnotation(annot, schema, schdyn));
			}
			if (assay.textLabels != null) for (DataObject.TextLabel label : assay.textLabels)
			{
				jsonAnnot.put(serialiseTextLabel(label, schema));
			}
			json.put("annotations", jsonAnnot);

			JSONArray xrefList = new JSONArray();
			for (int n = 0; n < Util.length(assay.pubchemXRefs); n++)
			{
				JSONObject obj = new JSONObject();
				obj.put("type", assay.pubchemXRefs[n].type);
				obj.put("id", assay.pubchemXRefs[n].id);
				obj.put("comment", assay.pubchemXRefs[n].comment);
				xrefList.put(obj);
			}
			json.put("pubchemXRef", xrefList);
			//json.put("pubchemSource", assay.pubchemSource);

			//boolean touched = assay.touchedTime != null && assay.touchedTime.getTime() > new Date().getTime();
			//json.put("beenTouched", touched);

			if (assay.curationTime != null) json.put(CURATION_TIME, assay.curationTime.getTime());
			json.put(CURATOR_ID, assay.curatorID);
			DataObject.User user = Common.getDataStore().user().getUser(assay.curatorID);
			if (user != null) 
			{
				json.put("curatorName", user.name);
				json.put("curatorEmail", user.email);
			}

			if (opt.includeHistory)
			{
				JSONArray jsonHistory = new JSONArray();
				if (assay.history != null) for (DataObject.History h : assay.history)
				{
					JSONObject obj = new JSONObject();
	
					if (h.curationTime != null) obj.put(CURATION_TIME, h.curationTime.getTime());
	
					obj.put(CURATOR_ID, h.curatorID);
					user = Common.getDataStore().user().getUser(h.curatorID);
					if (user != null) 
					{
						obj.put("curatorName", user.name);
						obj.put("curatorEmail", user.email);
					}

					if (h.uniqueIDPatch != null) obj.put(UNIQUEID_PATCH, h.uniqueIDPatch);
					if (h.textPatch != null) obj.put(TEXT_PATCH, h.textPatch);

					JSONArray jsonAnnotsAdded = new JSONArray();
					JSONArray jsonAnnotsRemoved = new JSONArray();
					if (h.annotsAdded != null) for (DataObject.Annotation annot : h.annotsAdded)
						jsonAnnotsAdded.put(serialiseAnnotation(annot, schema, schdyn));
					if (h.annotsRemoved != null) for (DataObject.Annotation annot : h.annotsRemoved)
						jsonAnnotsRemoved.put(serialiseAnnotation(annot, schema, schdyn));
					obj.put(ANNOTS_ADDED, jsonAnnotsAdded);
					obj.put(ANNOTS_REMOVED, jsonAnnotsRemoved);
	
					JSONArray jsonLabelsAdded = new JSONArray();
					JSONArray jsonLabelsRemoved = new JSONArray();
					if (h.labelsAdded != null) for (DataObject.TextLabel label : h.labelsAdded)
						jsonLabelsAdded.put(serialiseTextLabel(label, schema));
					if (h.labelsRemoved != null) for (DataObject.TextLabel label : h.labelsRemoved)
						jsonLabelsRemoved.put(serialiseTextLabel(label, schema));
					obj.put(LABELS_ADDED, jsonLabelsAdded);
					obj.put(LABELS_REMOVED, jsonLabelsRemoved);
					
					jsonHistory.put(obj);
				}
				json.put("history", jsonHistory);
			}

			json.put("holdingIDList", Common.getDataStore().holding().fetchForAssayID(assay.assayID));
		}
		catch (JSONException ex)
		{
			ex.printStackTrace();
			return null;
		} // shouldn't happen

		return json;
	}

	public static void serialiseCollection(File file, DataObject.Assay[] assayList) throws IOException
	{
		try (OutputStream ostr = new FileOutputStream(file))
		{
			serialiseCollection(ostr, assayList);
		}
	}

	public static void serialiseCollection(OutputStream ostr, DataObject.Assay[] assayList) throws IOException
	{
		if (assayList == null) return;

		ZipOutputStream zip = new ZipOutputStream(ostr);

		int orphan = 0;
		for (DataObject.Assay assay : assayList)
		{
			String code = String.valueOf(++orphan);
			if (assay.assayID > 0) code = "id" + assay.assayID;

			zip.putNextEntry(new ZipEntry("assay_" + code + ".json"));
			JSONObject json = serialiseAssay(assay);
			try
			{
				Writer wtr = new OutputStreamWriter(zip);
				json.write(wtr);
				wtr.flush();
			}
			catch (JSONException ex)
			{
				throw new IOException(ex);
			}
			zip.closeEntry();
		}

		zip.close();
	}

	// turns a string into an assay object; throws an exception if it's not a properly formatted JSON object; otherwise, the
	// return value will be null if invalid, or a properly instantiated object otherwise
	public static DataObject.Assay deserialiseAssay(String rawjson)
	{
		JSONObject json = new JSONObject(new JSONTokener(rawjson));
		return deserialiseAssay(json);
	}

	// turns a JSON object into an assay instance; if the object does not seem to be a serialised assay, will return null; runtime
	// exceptions may result from invalid formatting, but generally it's null-or-success
	public static DataObject.Assay deserialiseAssay(JSONObject json)
	{
		DataObject.Assay assay = new DataObject.Assay();

		try
		{
			assay.assayID = json.optLong(ASSAY_ID, 0);
			assay.uniqueID = json.has(UNIQUE_ID) ? json.getString(UNIQUE_ID) : null;
			assay.text = json.optString("text", "");

			assay.schemaURI = json.optString("schemaURI", null);
			if (Util.isBlank(assay.schemaURI)) return null; // mandatory
			
			JSONArray jsonBranches = json.optJSONArray("schemaBranches");
			if (jsonBranches != null && jsonBranches.length() > 0)
			{
				assay.schemaBranches = new DataObject.SchemaBranch[jsonBranches.length()];
				for (int n = 0; n < assay.schemaBranches.length; n++) assay.schemaBranches[n] = deserialiseBranch(jsonBranches.getJSONObject(n));
			}
			JSONArray jsonDuplication = json.optJSONArray("schemaDuplication");
			if (jsonDuplication != null && jsonDuplication.length() > 0)
			{
				assay.schemaDuplication = new DataObject.SchemaDuplication[jsonDuplication.length()];
				for (int n = 0; n < assay.schemaDuplication.length; n++) assay.schemaDuplication[n] = deserialiseDuplication(jsonDuplication.getJSONObject(n));
			}

			JSONArray jsonAssn = json.optJSONArray("annotations");
			if (jsonAssn == null) return null; // mandatory

			List<DataObject.Annotation> annotList = new ArrayList<>();
			List<DataObject.TextLabel> textList = new ArrayList<>();
			for (int n = 0; n < jsonAssn.length(); n++)
			{
				JSONObject obj = jsonAssn.getJSONObject(n);
				String propURI = obj.getString(PROP_URI);
				String valueURI = obj.optString(VALUE_URI);
				String valueLabel = obj.optString(VALUE_LABEL);
				JSONArray groupNest = obj.optJSONArray(GROUP_NEST);
				if (Util.notBlank(valueURI))
				{
					DataObject.Annotation annot = new DataObject.Annotation();
					annot.propURI = propURI;
					annot.valueURI = valueURI;
					if (groupNest != null) annot.groupNest = groupNest.toStringArray();
					annotList.add(annot);
				}
				else if (Util.notBlank(valueLabel))
				{
					DataObject.TextLabel label = new DataObject.TextLabel();
					label.propURI = propURI;
					label.text = valueLabel;
					if (groupNest != null) label.groupNest = groupNest.toStringArray();
					textList.add(label);
				}
			}
			assay.annotations = annotList.toArray(new DataObject.Annotation[annotList.size()]);
			assay.textLabels = textList.toArray(new DataObject.TextLabel[textList.size()]);

			JSONArray jsonXRef = json.optJSONArray("pubchemXRef");
			List<DataObject.PubChemXRef> xrefList = new ArrayList<>();
			if (jsonXRef != null) for (int n = 0; n < jsonXRef.length(); n++)
			{
				JSONObject obj = jsonXRef.getJSONObject(n);
				DataObject.PubChemXRef xref = new DataObject.PubChemXRef();
				xref.type = obj.getString("type");
				xref.id = obj.getString("id");
				xref.comment = obj.getString("comment");
				xrefList.add(xref);
			}
			assay.pubchemXRefs = xrefList.toArray(new DataObject.PubChemXRef[xrefList.size()]);

			//assay.pubchemSource = json.optString("pubchemSource");

			long curationTime = json.optLong(CURATION_TIME, 0);
			if (curationTime > 0) assay.curationTime = new Date(curationTime);
			assay.curatorID = json.optString(CURATOR_ID);

			JSONArray jsonHistory = json.optJSONArrayEmpty("history");
			List<DataObject.History> historyList = new ArrayList<>();
			for (int n = 0; n < jsonHistory.length(); n++)
			{
				JSONObject obj = jsonHistory.getJSONObject(n);
				DataObject.History hist = new DataObject.History();
				hist.curationTime = new Date(obj.getLong(CURATION_TIME));
				hist.curatorID = obj.optString(CURATOR_ID);
				JSONArray jsonAnnotsAdded = obj.optJSONArrayEmpty(ANNOTS_ADDED);
				JSONArray jsonAnnotsRemoved = obj.optJSONArrayEmpty(ANNOTS_REMOVED);
				for (int i = 0; i < jsonAnnotsAdded.length(); i++)
				{
					JSONObject jsonAnnot = jsonAnnotsAdded.getJSONObject(i);
					DataObject.Annotation annot = new DataObject.Annotation();
					annot.propURI = jsonAnnot.getString(PROP_URI);
					annot.valueURI = jsonAnnot.getString(VALUE_URI);
					JSONArray groupNest = jsonAnnot.optJSONArray(GROUP_NEST);
					if (groupNest != null) annot.groupNest = groupNest.toStringArray();
					hist.annotsAdded = ArrayUtils.add(hist.annotsAdded, annot);
				}
				for (int i = 0; i < jsonAnnotsRemoved.length(); i++)
				{
					JSONObject jsonAnnot = jsonAnnotsRemoved.getJSONObject(i);
					DataObject.Annotation annot = new DataObject.Annotation();
					annot.propURI = jsonAnnot.getString(PROP_URI);
					annot.valueURI = jsonAnnot.getString(VALUE_URI);
					JSONArray groupNest = jsonAnnot.optJSONArray(GROUP_NEST);
					if (groupNest != null) annot.groupNest = groupNest.toStringArray();
					hist.annotsRemoved = ArrayUtils.add(hist.annotsRemoved, annot);
				}
				JSONArray jsonLabelsAdded = obj.optJSONArrayEmpty(LABELS_ADDED);
				JSONArray jsonLabelsRemoved = obj.optJSONArrayEmpty(LABELS_REMOVED);
				for (int i = 0; i < jsonLabelsAdded.length(); i++)
				{
					JSONObject jsonLabel = jsonLabelsAdded.getJSONObject(i);
					DataObject.TextLabel label = new DataObject.TextLabel();
					label.propURI = jsonLabel.getString(PROP_URI);
					label.text = jsonLabel.optString(VALUE_LABEL);
					JSONArray groupNest = jsonLabel.optJSONArray(GROUP_NEST);
					if (groupNest != null) label.groupNest = groupNest.toStringArray();
					hist.labelsAdded = ArrayUtils.add(hist.labelsAdded, label);
				}
				for (int i = 0; i < jsonLabelsRemoved.length(); i++)
				{
					JSONObject jsonLabel = jsonLabelsRemoved.getJSONObject(i);
					DataObject.TextLabel label = new DataObject.TextLabel();
					label.propURI = jsonLabel.getString(PROP_URI);
					label.text = jsonLabel.optString(VALUE_LABEL);
					JSONArray groupNest = jsonLabel.optJSONArray(GROUP_NEST);
					if (groupNest != null) label.groupNest = groupNest.toStringArray();
					hist.labelsRemoved = ArrayUtils.add(hist.labelsRemoved, label);
				}
				historyList.add(hist);
			}
			assay.history = historyList.toArray(new DataObject.History[historyList.size()]);

			assay.isCurated = true; // by definition it is so
		}
		catch (JSONException ex)
		{
			Util.writeln(ex.toString());
			logger.debug("Error deserialising JSON", ex);
			return null;
		}

		return assay;
	}

	// take a ZIP file that may contain some number of assays (each JSON formatted) and unpack all of them
	public static DataObject.Assay[] deserialiseCollection(File file) throws IOException
	{
		try (InputStream istr = new FileInputStream(file))
		{
			return deserialiseCollection(istr);
		}
	}

	public static DataObject.Assay[] deserialiseCollection(InputStream istr) throws IOException
	{
		List<DataObject.Assay> assayList = new ArrayList<>();

		ZipInputStream zip = new ZipInputStream(istr);

		ZipEntry ze = zip.getNextEntry();
		while (ze != null)
		{
			String path = ze.getName();
			if (path.endsWith(".json"))
			{
				try
				{
					JSONObject json = new JSONObject(new JSONTokener(new InputStreamReader(zip)));
					DataObject.Assay assay = deserialiseAssay(json);
					if (assay != null) assayList.add(assay);
				}
				catch (JSONException ex)
				{ /* just skip the file if it's not really JSON */ }
			}

			zip.closeEntry();
			ze = zip.getNextEntry();
		}

		zip.close();

		return assayList.toArray(new DataObject.Assay[assayList.size()]);
	}

	// packages up just one annotation into a JSON object; note: if the schema value is non-null, will provide a hierarchy trail
	public static JSONObject serialiseAnnotation(DataObject.Annotation annot, Schema schema, SchemaDynamic schdyn)
	{
		JSONObject obj = new JSONObject();
		obj.put(PROP_URI, annot.propURI);

		Schema.Assignment[] assnList = schema != null ? schema.findAssignmentByProperty(annot.propURI, annot.groupNest) : null;

		String propLabel = null;
		if (Util.length(assnList) == 0) propLabel = Common.getOntoProps().getLabel(annot.propURI); else propLabel = assnList[0].name;

		SchemaDynamic.SubTemplate subt = null;
		if (schema != null && schdyn != null) subt = schdyn.relativeAssignment(annot.propURI, annot.groupNest);

		obj.put(PROP_LABEL, propLabel);
		obj.put(PROP_ABBREV, ModelSchema.collapsePrefix(annot.propURI));

		String valueLabel = null;
		if (schema != null) 
		{
			if (subt != null)
				valueLabel = Common.getCustomName(subt.schema, annot.propURI, subt.groupNest, annot.valueURI);
			else
				valueLabel = Common.getCustomName(schema, annot.propURI, annot.groupNest, annot.valueURI);
		}
		if (valueLabel == null) valueLabel = Common.getOntoValues().getLabel(annot.valueURI);

		String valueDescr = null;
		if (schema != null) valueDescr = Common.getCustomDescr(schema, annot.propURI, annot.groupNest, annot.valueURI);
		if (valueDescr == null) valueDescr = Common.getOntoValues().getDescr(annot.valueURI);

		obj.put(VALUE_URI, annot.valueURI);
		obj.put(VALUE_LABEL, valueLabel);
		obj.put("valueAbbrev", ModelSchema.collapsePrefix(annot.valueURI));
		obj.put("valueDescr", valueDescr);
		if (annot.groupNest != null) 
		{
			obj.put(GROUP_NEST, annot.groupNest);
			if (Util.length(assnList) > 0) obj.put(GROUP_LABEL, assnList[0].groupLabel());
		}

		// descending hierarchy for the value property, if applicable
		boolean inSchema = false;
		if (schema != null)
		{
			JSONArray valueHier = new JSONArray(), labelHier = new JSONArray();
			if (annot.valueURI != null)
			{
				SchemaTree tree = Common.obtainTree(subt == null ? schema : subt.schema, annot.propURI, subt == null ? annot.groupNest : subt.groupNest);
				SchemaTree.Node node = tree == null ? null : tree.getTree().get(annot.valueURI);
				if (node != null) 
				{
					for (SchemaTree.Node look = node.parent; look != null; look = look.parent) 
					{
						valueHier.put(look.uri);
						labelHier.put(look.label);
					}
					obj.put("altLabels", node.altLabels);
					obj.put("externalURLs", node.externalURLs);
					inSchema = true;
				}
			}
			obj.put("valueHier", valueHier);
			obj.put("labelHier", labelHier);
		}

		if (!inSchema && schema != null && !AssayUtil.ABSENCE_SET.contains(annot.valueURI)) obj.put("outOfSchema", true);
		
		return obj;
	}

	// packages up just one label into a JSON object
	public static JSONObject serialiseTextLabel(DataObject.TextLabel label, Schema schema)
	{
		JSONObject obj = new JSONObject();
		obj.put(PROP_URI, label.propURI);
		obj.put(VALUE_LABEL, label.text);
		obj.put(PROP_ABBREV, ModelSchema.collapsePrefix(label.propURI));
		
		if (label.groupNest != null) obj.put(GROUP_NEST, label.groupNest);

		if (schema == null)
		{
			obj.put(PROP_LABEL, Common.getOntoProps().getLabel(label.propURI));
			if (label.groupNest != null) obj.put(GROUP_NEST, label.groupNest);
		}
		else
		{
			Schema.Assignment[] assnList = schema.findAssignmentByProperty(label.propURI, label.groupNest);
			if (assnList.length == 0)
			{
				obj.put(PROP_LABEL, Common.getOntoProps().getLabel(label.propURI));
			}
			else
			{
				obj.put(PROP_LABEL, assnList[0].name);
				if (label.groupNest != null) obj.put(GROUP_LABEL, assnList[0].groupLabel());
			}
		}

		return obj;
	}
	
	public static JSONObject serialiseBranch(DataObject.SchemaBranch branch)
	{
		JSONObject obj = new JSONObject();
		obj.put("schemaURI", branch.schemaURI);
		obj.put("groupNest", branch.groupNest);
		return obj;
	}
	public static DataObject.SchemaBranch deserialiseBranch(JSONObject obj) throws JSONException
	{
		DataObject.SchemaBranch branch = new DataObject.SchemaBranch();
		branch.schemaURI = obj.getString("schemaURI");
		branch.groupNest = obj.optJSONArrayEmpty("groupNest").toStringArray();
		return branch;
	}

	public static JSONObject serialiseDuplication(DataObject.SchemaDuplication dupl)
	{
		JSONObject obj = new JSONObject();
		obj.put("multiplicity", dupl.multiplicity);
		obj.put("groupNest", dupl.groupNest);
		return obj;
	}
	public static DataObject.SchemaDuplication deserialiseDuplication(JSONObject obj) throws JSONException
	{
		DataObject.SchemaDuplication dupl = new DataObject.SchemaDuplication();
		dupl.multiplicity = obj.getInt("multiplicity");
		dupl.groupNest = obj.optJSONArrayEmpty("groupNest").toStringArray();
		return dupl;
	}

	// ------------ private methods ------------

}
