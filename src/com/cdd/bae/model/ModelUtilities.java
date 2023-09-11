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

package com.cdd.bae.model;

import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

/*
	Collection of utilities for model building
*/

public class ModelUtilities
{
	public static final String SEP = "::";

	private static final Object mutex = new Object();

	// create missing fingerprints
	public static void updateAnnotationFP()
	{
		DataStore store = Common.getDataStore();
		Map<String, Integer> annotToTarget;
		Map<String, SchemaTree> treeCache = new HashMap<>();

		// method is called from parallel threads 
		synchronized (mutex)
		{
			annotToTarget = getAnnotToFP();
			int highTarget = 0;
			for (Integer fp : annotToTarget.values()) highTarget = Math.max(highTarget, fp);

			// identify novel annotations
			long[] assayIDList = store.assay().fetchAllCuratedAssayID();
			for (long assayID : assayIDList)
			{
				DataObject.Assay assay = null;
				try
				{
					assay = store.assay().getAssay(assayID);
				}
				catch (Exception ex) { /* ignore */ }
				
				if (assay == null || Util.length(assay.annotations) == 0) continue;
				Schema schema = Common.getSchema(assay.schemaURI);
				if (schema == null) continue;

				// map assignment fingerprint (target) indices, and create anything not already extant
				for (DataObject.Annotation a : assay.annotations)
				{
					boolean want = false;
					for (Schema.Assignment assn : schema.findAssignmentByProperty(a.propURI))
						if (assn.suggestions == Schema.Suggestions.FULL)
					{
						want = true;
						break;
					}
					if (!want) continue;

					String cacheKey = schema.getSchemaPrefix() + "::" + Schema.keyPropGroup(a.propURI, a.groupNest);
					SchemaTree tree = null;
					if (treeCache.containsKey(cacheKey))
						tree = treeCache.get(cacheKey);
					else
						treeCache.put(cacheKey, tree = Common.obtainTree(schema, a.propURI, a.groupNest));
					if (tree == null) continue; // ugh
					
					for (String valueURI : tree.expandAncestors(a.valueURI))
					{
						String key = a.propURI + SEP + valueURI;
						Integer val = annotToTarget.get(key);
						if (val == null)
						{
							val = ++highTarget;
							annotToTarget.put(key, val);
							store.annot().addAssnFingerprint(a.propURI, valueURI, val);
						}
					}
				}
			}
		}
	}

	// create bi-directional map between annotations and target FP
	public static void getTargetAnnotMaps(Map<String, Integer> annotToTarget, Map<Integer, AnnotationFP> targetToAnnot)
	{
		for (AnnotationFP a : Common.getDataStore().annot().fetchAnnotationFP())
		{
			String key = a.propURI + SEP + a.valueURI;
			annotToTarget.put(key, a.fp);
			targetToAnnot.put(a.fp, a);
		}
	}

	//--- private and protected methods --------
	protected static Map<String, Integer> getAnnotToFP()
	{
		Map<String, Integer> annotToFP = new HashMap<>();
		for (AnnotationFP annotFP : Common.getDataStore().annot().fetchAnnotationFP())
		{
			String key = annotFP.propURI + SEP + annotFP.valueURI;
			annotToFP.put(key, annotFP.fp);
		}
		return annotToFP;
	}
}
