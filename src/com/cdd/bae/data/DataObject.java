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
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.apache.commons.lang3.*;

/*
	Container for the various classes that are used to map to the underlying database collections.
*/

public interface DataObject
{
	// {predicate,object} pairs, with optional additional group nesting
	public static class Annotation
	{
		public String propURI, valueURI;
		public String[] groupNest = null;
		
		public Annotation() {}
		public Annotation(String propURI, String valueURI)
		{
			this.propURI = propURI;
			this.valueURI = valueURI;
		}
		public Annotation(String propURI, String valueURI, String[] groupNest)
		{
			this.propURI = propURI;
			this.valueURI = valueURI;
			this.groupNest = groupNest;
		}
		
		@Override
		public String toString() 
		{
			List<String> s = new ArrayList<>();
			s.add(ModelSchema.collapsePrefix(propURI));
			s.add(ModelSchema.collapsePrefix(valueURI));
			if (groupNest != null) s.addAll(Arrays.asList(ModelSchema.collapsePrefixes(groupNest)));
			return "[" + String.join(",", s) + "]";
		}
		
		public Annotation clone()
		{
			return new Annotation(propURI, valueURI, groupNest == null ? null : groupNest.clone());
		}
		
		// convenience method: returns true if this is compatible, taking into account the group-nest
		public boolean matchesProperty(String propURI, String[] groupNest)
		{
			return Schema.compatiblePropGroupNest(propURI, groupNest, this.propURI, this.groupNest);
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			Annotation other = (Annotation) o;
			return Util.equals(propURI, other.propURI) && Util.equals(valueURI, other.valueURI) && saveEqualsArray(groupNest, other.groupNest);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(propURI, valueURI, Arrays.hashCode(groupNest));
		}
		
		static boolean saveEqualsArray(Object[] a1, Object[] a2)
		{
			if (a1 == null) return a1 == a2;
			return Arrays.equals(a1, a2);		
		}
	}
	
	// {predicate,text} pairs, with optional additional group nesting
	public static class TextLabel
	{
		public String propURI, text;
		public String[] groupNest = null;

		public TextLabel() {}
		public TextLabel(String propURI, String text)
		{
			this.propURI = propURI;
			this.text = text;
		}
		public TextLabel(String propURI, String text, String[] groupNest)
		{
			this.propURI = propURI;
			this.text = text;
			this.groupNest = groupNest;
		}

		@Override
		public String toString() 
		{
			String nest = "";
			if (groupNest != null) for (String uri : groupNest) nest += "," + ModelSchema.collapsePrefix(uri); 
			return "[" + ModelSchema.collapsePrefix(propURI) + ",\"" + text + "\"" + nest + "]";
		}
		
		public TextLabel clone()
		{
			return new TextLabel(propURI, text, groupNest == null ? null : groupNest.clone());
		}
		
		// convenience method: returns true if this is compatible, taking into account the group-nest
		public boolean matchesProperty(String propURI, String[] groupNest)
		{
			if (!propURI.equals(this.propURI)) return false;
			int gsz = Math.min(Util.length(groupNest), Util.length(this.groupNest));
			for (int n = 0; n < gsz; n++) if (!groupNest[n].equals(this.groupNest[n])) return false;
			return true;
		}
		
		@Override
		public boolean equals(Object o)
		{
			if (o == null || getClass() != o.getClass()) return false;
			TextLabel other = (TextLabel) o;
			return Util.equals(propURI, other.propURI) && Util.equals(text, other.text) && saveEqualsArray(groupNest, other.groupNest);
		}

		@Override
		public int hashCode()
		{
			return Objects.hash(propURI, text, Arrays.hashCode(groupNest));
		}
		
		static boolean saveEqualsArray(Object[] a1, Object[] a2)
		{
			if (a1 == null) return a1 == a2;
			return Arrays.equals(a1, a2);		
		}
	}
	
	// reference to a branch schema that is to be grafted on at some point
	public static class SchemaBranch
	{
		public String schemaURI;
		public String[] groupNest;
		
		public SchemaBranch() {}
		public SchemaBranch(String schemaURI, String[] groupNest)
		{
			this.schemaURI = schemaURI;
			this.groupNest = groupNest;
		}
	}
	
	// reference to a group that has been cloned some number of times
	public static class SchemaDuplication
	{
		public int multiplicity;
		public String[] groupNest;
		
		public SchemaDuplication() {}
		public SchemaDuplication(int multiplicity, String[] groupNest)
		{
			this.multiplicity = multiplicity;
			this.groupNest = groupNest;
		}
	}

	// unit of annotation history: who did what and when
	public static class History
	{
		public Date curationTime;
		public String curatorID;
		public String uniqueIDPatch; // new-to-old
		public String textPatch; // new-to-old
		public Annotation[] annotsAdded, annotsRemoved;
		public TextLabel[] labelsAdded, labelsRemoved;
	}
	
	// holds cross-references from PubChem; treat these as freeform, to be interpreted later
	public static class PubChemXRef
	{
		public String type;
		public String id;
		public String comment;
	}
	
	// used to serialise assays for convenience
	public static final class Assay
	{
		// persistent properties of the assay
		public long assayID; // sequence-based unique ID (meaningful only to this database)
		public String uniqueID; // globally unique ID, assembled by the Identifier class
		public String text;
		public String schemaURI;
		public SchemaBranch[] schemaBranches;
		public SchemaDuplication[] schemaDuplication;
		public Annotation[] annotations;
		public TextLabel[] textLabels;
		public PubChemXRef[] pubchemXRefs;
		public Date curationTime, touchedTime;
		public String curatorID;
		public History[] history; // oldest first
		
		// cached features (derived)
		public int[] fplist;
		public boolean isCurated;
		public boolean measureChecked;
		public String measureState;

		@Override // note: makes a one-level-deep copy of sub-objects
		public Assay clone()
		{
			Assay dup = new Assay();
			dup.assayID = assayID;
			dup.uniqueID = uniqueID;
			dup.text = text;
			dup.schemaURI = schemaURI;
			dup.schemaBranches = ArrayUtils.clone(schemaBranches);
			dup.schemaDuplication = ArrayUtils.clone(schemaDuplication);
			dup.annotations = ArrayUtils.clone(annotations);
			dup.textLabels = ArrayUtils.clone(textLabels);
			dup.pubchemXRefs = ArrayUtils.clone(pubchemXRefs);
			dup.curationTime = curationTime;
			dup.touchedTime = touchedTime;
			dup.history = ArrayUtils.clone(history);
			dup.curatorID = curatorID;
			dup.fplist = ArrayUtils.clone(fplist);
			dup.isCurated = isCurated;
			dup.measureChecked = measureChecked;
			dup.measureState = measureState;
			return dup;
		}

		public TextLabel[] getTextLabels(String propURI, String[] groupNest)
		{
			if (textLabels == null) return new TextLabel[0];
			List<TextLabel> labels = new ArrayList<>();
			for (TextLabel textLabel : textLabels)
				if (textLabel.matchesProperty(propURI, groupNest)) labels.add(textLabel);
			return labels.toArray(new TextLabel[0]);
		}

		public Annotation[] getAnnotations(String propURI, String[] groupNest)
		{
			if (annotations == null) return new Annotation[0];
			List<Annotation> annot = new ArrayList<>();
			for (Annotation annotation : annotations)
				if (annotation.matchesProperty(propURI, groupNest)) annot.add(annotation);
			return annot.toArray(new Annotation[0]);
		}
	}

	// assay replacement data stored in the holding bay
	public static final class Holding
	{
		public long holdingID; // sequence-based unique ID
		public long assayID; // 0=none (equivalent to null)
		public Date submissionTime;
		public String curatorID;
		public String uniqueID;
		public String schemaURI;
		public SchemaBranch[] schemaBranches;
		public SchemaDuplication[] schemaDuplication;
		public boolean deleteFlag;
		public String text;
		public Annotation[] annotsAdded, annotsRemoved;
		public TextLabel[] labelsAdded, labelsRemoved;
	}

	// annotation with the corresponding "fingerprint" index tacked onto it
	public static class AnnotationFP extends Annotation
	{
		public int fp;
		public AnnotationFP() {}
		public AnnotationFP(String propURI, String valueURI, int fp)
		{
			super(propURI, valueURI);
			this.fp = fp;
		}
	}

	// description of an instantiated model; the target is an annotation FP index, while the fplist is either NLP or annotation indices 
	// (agnostic to type)
	public static class Model
	{
		public long watermark;
		public int target;
		public int[] fplist;
		public float[] contribs;
		public float calibLow = Float.NaN, calibHigh = Float.NaN;
		public boolean isExplicit;
	}
	
	// a set of measurements that applies to an assay: references compounds and a value for each
	public static final class Measurement
	{
		public String id; // database ID
		public long assayID; // the assay to which the measurements belong
		public String name, units, type;
		
		// one entry each per measurement
		public long[] compoundID; // ID index into compounds
		public Double[] value; // values can be null
		public String[] relation; // typically "=", ">", "<", or some other relational modifier/substitute for missing value
	}
	
	// a compound, with various identifiers, and a molecular structure
	public static final class Compound
	{
		public String id; // database ID
		public long compoundID; // unique identifier
		public String molfile; // MDL Molfile-formatted structure
		public int hashECFP6; // structure hash formed by XOR'ing together all the ECFP6 fingerprints
		public int pubchemCID, pubchemSID; // references into PubChem, often used to locate the entry (0 if not applicable)
		public long vaultID, vaultMID; // vault ID/molecule ID respectively (0 if not applicable)
	}

	// record of a file (from the local file system) having been loaded into the database	
	public static final class LoadedFile
	{
		public String path;
		public long date, size;
	}
	
	// user information
	public static class User
	{
		public String curatorID;
		public String status;
		public String serviceName, userID, name, email;
		public byte[] passwordSalt, passwordHash;
		public Date lastAuthent;
		public long[] curationHistory;
	}
	
	public enum ProvisionalRole
	{
		PRIVATE, // indicates that this is a term that should not leave the internal system
		PUBLIC, // indicates a desire to upgrade the term to a public ontology
		DEPRECATED; // indicates that the term should be deleted
		
		@Override
		public String toString()
		{
			return this.name().toLowerCase();
		}
		public static ProvisionalRole fromString(String str)
		{
			return ProvisionalRole.valueOf(str.toUpperCase());
		}
	}

	public static class Provisional
	{
		public long provisionalID; // sequence-based unique ID
		public String parentURI;
		public String label;
		public String uri;
		public String description;
		public String explanation;
		public String proposerID; // refers to DataUser.curatorID
		public ProvisionalRole role; // null=haven't decided
		public Date createdTime; // created timestamp
		public Date modifiedTime; // last modified timestamp
		public String remappedTo; // null if not approved, otherwise URI of approved term
		public String bridgeStatus; // status within the ontolobridge system, if any (values defined by OntoloBridge service)
		public String bridgeURL; // baseURL of the ontolobridge reference, if it has been submitted
		public String bridgeToken; // then ontolobridge identifier for the request-in-progress
	}
	
	public static class KeywordMap
	{
		public long keywordmapID;
		public String schemaURI;
		public String propURI;
		public String[] groupNest;
		public String keyword;
		public String valueURI;
	}
}
