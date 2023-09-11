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
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.data.DataObject.Annotation;
import com.cdd.bae.data.DataObject.Assay;
import com.cdd.bae.util.*;
import com.cdd.bao.template.*;
import com.cdd.bao.template.Schema.*;
import com.cdd.bao.template.SchemaVocab.*;
import com.cdd.bao.util.*;

import java.util.*;
import java.util.regex.*;

import org.apache.commons.collections4.*;
import org.apache.commons.lang3.*;
import org.apache.commons.text.StringEscapeUtils;
import org.json.*;

/*
	General purpose utilities for assay data, which span several functional areas (e.g. schema & database).
*/

public class AssayUtil
{
	// commonly used URIs that are sufficiently special to justify being hardcoded
	public static final String URI_ABSENCE = ModelSchema.PFX_BAT + "Absence";
	public static final String URI_NOTAPPLICABLE = ModelSchema.PFX_BAT + "NotApplicable";
	public static final String URI_NOTDETERMINED = ModelSchema.PFX_BAT + "NotDetermined";
	public static final String URI_UNKNOWN = ModelSchema.PFX_BAT + "Unknown";
	public static final String URI_AMBIGUOUS = ModelSchema.PFX_BAT + "Ambiguous";
	public static final String URI_MISSING = ModelSchema.PFX_BAT + "Missing";
	public static final String URI_DUBIOUS = ModelSchema.PFX_BAT + "Dubious";
	public static final String URI_REQUIRESTERM = ModelSchema.PFX_BAT + "RequiresTerm";
	public static final String URI_NEEDSCHECKING = ModelSchema.PFX_BAT + "NeedsChecking";
	public static final String[] ABSENCE_TERMS =
	{
		URI_NOTAPPLICABLE,
		URI_NOTDETERMINED,
		URI_UNKNOWN,
		URI_AMBIGUOUS,
		URI_MISSING,
		URI_DUBIOUS,
		URI_REQUIRESTERM,
		URI_NEEDSCHECKING,
	};
	public static final Set<String> ABSENCE_SET = new HashSet<>(Arrays.asList(ABSENCE_TERMS));
	public static final Map<String, String> ABSENCE_LABEL = new HashMap<>(), ABSENCE_DESCR = new HashMap<>();
	static
	{
		ABSENCE_LABEL.put(URI_ABSENCE, "absence");
		ABSENCE_DESCR.put(URI_ABSENCE, "A group of annotations that explain why an annotation is missing, when it should not be.");
		
		ABSENCE_LABEL.put(URI_NOTAPPLICABLE, "not applicable");
		ABSENCE_DESCR.put(URI_NOTAPPLICABLE, "This property does not apply to this assay.");
		
		ABSENCE_LABEL.put(URI_NOTDETERMINED, "not determined");
		ABSENCE_DESCR.put(URI_NOTDETERMINED, "The measurement was not made.");
		
		ABSENCE_LABEL.put(URI_UNKNOWN, "unknown");
		ABSENCE_DESCR.put(URI_UNKNOWN, "There should be a term assigned to this property but the value was not " +
									   "specified in the source information.");
		
		ABSENCE_LABEL.put(URI_AMBIGUOUS, "ambiguous");
		ABSENCE_DESCR.put(URI_AMBIGUOUS, "The source information is ambiguous: there are multiple indeterminate possibilities.");
		
		ABSENCE_LABEL.put(URI_MISSING, "missing");
		ABSENCE_DESCR.put(URI_MISSING, "The information is missing from the source data, presumed to be a communication error.");
		
		ABSENCE_LABEL.put(URI_DUBIOUS, "dubious");
		ABSENCE_DESCR.put(URI_DUBIOUS, "The source information appears to be dubious, and has been omitted for this reason.");
		
		ABSENCE_LABEL.put(URI_REQUIRESTERM, "requires term");
		ABSENCE_DESCR.put(URI_REQUIRESTERM, "An appropriate term could not be found in the underlying ontologies: " +
											"it may be necessary to create a new one.");
		
		ABSENCE_LABEL.put(URI_NEEDSCHECKING, "needs checking");
		ABSENCE_DESCR.put(URI_NEEDSCHECKING, "These terms need to be checked by an expert.");
	}

	public static final String URI_ASSAYTITLE = ModelSchema.PFX_BAO + "BAO_0002853";

	// works over the annotations and labels, making corrections where necessary: most importantly the groupNest property,
	// which can routinely get out of sync as the schema hierarchy gets rearranged; note that it does not guarantee that everything
	// is valid, it just fixes what can be fixed
	public static void conformAnnotations(Assay assay)
	{
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) 
		{
			return; // schema may have been deleted
			//throw new IllegalArgumentException("Cannot find schema associated with assay: " + assay.schemaURI);
		}

		schema = SchemaDynamic.compositeSchema(schema, assay.schemaBranches, assay.schemaDuplication);
		
		SchemaVocab schemaVocab = Common.getSchemaVocab();
		if (schemaVocab == null) throw new IllegalArgumentException("Cannot find SchemaVocab; conforming annotations failed for assay with URI: " + assay.schemaURI);
		
		conformAnnotations(assay, schema, schemaVocab);
	}
	public static void conformAnnotations(Assay assay, Schema schema, SchemaVocab schemaVocab)
	{
		if (assay.annotations == null) assay.annotations = new Annotation[0];
		if (assay.textLabels == null) assay.textLabels = new TextLabel[0];
	
		List<Annotation> annotList = new ArrayList<>();
		List<TextLabel> labelList = new ArrayList<>();
		
		Map<String, StoredRemapTo> remappings = schemaVocab.getRemappings();
		conformToRemap(assay, remappings);
		
		Set<String> dupAnnots = new HashSet<>(), dupLabels = new HashSet<>();
		
		if (assay.annotations != null) for (Annotation annot : assay.annotations)
		{
			annot.groupNest = conformGroupNest(annot.propURI, annot.groupNest, schema);
			if (!dupAnnots.add(Schema.keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI))) continue;
			annotList.add(annot);
		}
		if (assay.textLabels != null) for (TextLabel label : assay.textLabels)
		{
			label.groupNest = conformGroupNest(label.propURI, label.groupNest, schema);
			if (!dupLabels.add(Schema.keyPropGroupValue(label.propURI, label.groupNest, label.text))) continue;
			labelList.add(label);
		}
		
		assay.annotations = annotList.toArray(new Annotation[annotList.size()]);
	}

	// ensures that any stale URIs get remapped to their contemporary replacement values
	public static void conformToRemap(Assay assay, Map<String, StoredRemapTo> remappings)
	{
		if (MapUtils.isEmpty(remappings)) return;

		for (DataObject.Annotation annot : assay.annotations)
		{
			annot.valueURI = remapIfNecessary(annot.valueURI, remappings);
			annot.propURI = remapIfNecessary(annot.propURI, remappings);
		}
	}

	// include transliterated previews, if any
	public static JSONArray transliteratedPreviews(DataObject.Assay assay)
	{
		JSONArray list = new JSONArray();
		
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) return list;
		
		String[] todo = Common.getTransliteration().getPreviews(assay.schemaURI);
		todo = ArrayUtils.insert(0, todo, (String[])null); // insert the default at the beginning
		for (String preview : todo)
		{
			Transliteration.Boilerplate boiler = preview == null ? Common.getTransliteration().getBoilerplate(assay.schemaURI) :
												Common.getTransliteration().getBoilerplatePreview(assay.schemaURI, preview);
			if (boiler == null) continue;

			JSONObject obj = new JSONObject();
			obj.put("title", preview);

			BoilerplateScript script = new BoilerplateScript(boiler, schema, assay);
			try 
			{
				script.inscribe();
				obj.put("html", script.getHTML());
			}
			catch (DetailException ex) 
			{
				obj.put("html", "Transliteration error: " + StringEscapeUtils.escapeHtml4(ex.getMessage()));
			}
			
			list.put(obj);
		}
		
		return list;
	}

	// return set of container terms for the given assignment
	public static Set<String> enumerateContainerTerms(Schema.Assignment assn)
	{
		Set<String> containers = new HashSet<>();
		for (Schema.Value v : assn.values)
		{
			if (v.spec == Specify.CONTAINER) containers.add(v.uri);
		}
		return containers;
	}

	// ------------ private methods ------------

	// checks to see if the hierarchy that is implied by groupNest/propURI is fully formed and non-degenerate, and if so, returns
	// the same thing unmodified; if not, will try to find the correct place for it, using all available clues - usually there is
	// enough information to pin it down, but if not, it will pick the first one (i.e. it is the responsibility of data curators
	// down the line to fix it if it's wrong)
	protected static String[] conformGroupNest(String propURI, String[] groupNest, Schema schema)
	{
		Schema.Assignment[] assnList = schema.findAssignmentByProperty(propURI);
		
		if (assnList.length == 0) return groupNest;
		else if (assnList.length == 1) return assnList[0].groupNest();

		int bestScore = Integer.MIN_VALUE;
		String[] bestNest = null;
		
		for (int n = 0; n < assnList.length; n++)
		{
			String[] lookNest = assnList[n].groupNest();
			if (Arrays.equals(groupNest, lookNest)) return groupNest; // short circuit
			
			int score = groupNestCompatibilityScore(groupNest, lookNest);
			
			if (score <= bestScore) continue;
			bestScore = score;
			bestNest = lookNest;
		}
	
		return bestNest;
	}
	
	// for two groupNest arrays, returns a score indicating how compatible they are; higher is better; the first parameter is assumed to be
	// "what it is now" (probably wrong) and the second is "from the template (which may be used to replace it)
	protected static int groupNestCompatibilityScore(String[] nest1, String[] nest2)
	{
		final int sz1 = ArrayUtils.getLength(nest1), sz2 = ArrayUtils.getLength(nest2);
		if (sz1 == 0 || sz2 == 0) return 0;
		
		int score = 0;
		
		outer: for (int n1 = 0, n2 = 0; n1 < sz1 && n2 < sz2;)
		{
			if (nest1[n1].equals(nest2[n2])) 
			{
				score += sz1 - n1 + sz2 - n2;
				n1++; 
				n2++; 
				continue;
			}
			for (int i = 1; n1 + i < sz1 || n2 + i < sz2; i++)
			{
				int i1 = n1 + i, i2 = n2 + i;
				if (i2 < sz2 && nest1[n1].equals(nest2[i2]))
				{
					score += sz1 - n1 + sz2 - n2 - i;
					n2 = i2 + 1;
					n1++;
					continue outer;
				}
				else if (i1 < sz1 && nest1[i1].equals(nest2[n2]))
				{
					score += sz1 - n1 + sz2 - n2 - i;
					n1 = i2 + 1;
					n2++;
					continue outer;
				}
			}
			n1++;
			n2++;
		}
		
		return score;
	}
		
	// remap named annotation if there are any remappings applicable to the annotation
	private static String remapIfNecessary(String uri, Map<String, StoredRemapTo> remappings)
	{
		// quick-outs: almost all cases have 0 or 1 remappings
		StoredRemapTo remap1 = remappings.get(uri);
		if (remap1 == null) return uri;

		StoredRemapTo remap2 = remappings.get(remap1.toURI);
		if (remap2 == null) return remap1.toURI;

		// there are 2 or more, so have to establish a tail to protect against the ouroboros catastrophic fail
		Set<String> alreadySeen = new HashSet<>();
		alreadySeen.add(uri);
		alreadySeen.add(remap1.toURI);
		alreadySeen.add(remap2.toURI);
		uri = remap2.toURI;

		// keep remapping until nothing left, or eating the tail
		while (true)
		{
			StoredRemapTo remapN = remappings.get(uri);
			if (remapN == null) return uri;
			if (alreadySeen.contains(remapN.toURI)) throw new RuntimeException("Cycle detected in remappings with sequence [" + alreadySeen + "].");
			alreadySeen.add(remapN.toURI);
			uri = remapN.toURI;
		}
	}
	
	// number checking convenience methods; these are implemented with explicit regular expressions because library functions tend to
	// try to be nice about regional conventions, whereas we want to enforce a narrow subset
	
	private static final Pattern REGEX_INTEGER = Pattern.compile("^-?\\d+$");
	private static final Pattern REGEX_NUMBER1 = Pattern.compile("^-?\\d*\\.\\d+$");
	private static final Pattern REGEX_NUMBER2 = Pattern.compile("^-?\\d*\\.?\\d+[eE]-?[\\d\\.]+$");
	
	public static boolean validIntegerLiteral(String literal)
	{
		return REGEX_INTEGER.matcher(literal).matches();
	}
	public static boolean validNumberLiteral(String literal)
	{
		return REGEX_INTEGER.matcher(literal).matches() ||
			   REGEX_NUMBER1.matcher(literal).matches() ||
			   REGEX_NUMBER2.matcher(literal).matches();
	}

	// in contrast to validNumberLiteral, standardNumberLiteral is more restrictive and used for schema check only
	private static final Pattern REGEX_NONSTANDARD_NUMBER1 = Pattern.compile("^-?0\\d+.*$");
	private static final Pattern REGEX_NONSTANDARD_NUMBER2 = Pattern.compile("^-?\\..*$");
	public static boolean standardNumberLiteral(String literal)
	{
		if (!validNumberLiteral(literal)) return false;
		return !REGEX_NONSTANDARD_NUMBER1.matcher(literal).matches() &&
				!REGEX_NONSTANDARD_NUMBER2.matcher(literal).matches();
	}
}
