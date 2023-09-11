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

package com.cdd.bae.rest;

import com.cdd.bae.config.authentication.Authentication.*;
import com.cdd.bae.data.*;
import com.cdd.bae.rest.RESTException.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.json.*;

/*
	Searching: provides the serverside part of the dance. It starts with the client asking for a list of all eligible identifiers. The client then
	proceeds to send batches of these in for evaluation, and each one is scored accordingly with a similarity metric. The client has all the information
	necessary to handle progress, and present a subset of the best results as they come in. Also, if the client stops sending requests for any reason,
	the server is only on the hook for finishing the most recent batch.
	
	The similarity metric is composed so that 1 = the same, 0 = nothing in common at all, and anything in between is related to some extent.
	
	Parameters:
		assayIDList: empty = fetch and return list of eligible assays
					 array of strings = evaluate similarity for each
		search: search parameters, [{propURI,valueURI,groupNest},..]
		threshold: similarity value must be >= threshold, otherwise is not returned
		maxResults: if specified, this many most-similar results are returned
		curatedOnly: if true, assayIDs will be limited to those with the curation flag on
		countCompounds: (optional) if true, the # compounds will be counted and returned
		translitPreviews: (optional) if true, any available preview-type transliterations will be included
					 
	Results:
		assayIDList: (if null parameter) list of numbers
		results: (if identifiers provided) 
			serialised assay, plus:
			similarity
			countCompounds (if requested)
			translitPreviews (if requested)
*/

public class Search extends RESTBaseServlet 
{
	private static final long serialVersionUID = 1L;

	private static final class Result
	{
		double similarity;
		DataObject.Assay assay;
	}

	// ------------ public methods ------------

	@Override
	protected JSONObject processRequest(JSONObject input, Session session) throws RESTException
	{
		DataStore store = Common.getDataStore();

		JSONObject results = new JSONObject();
		JSONArray assayIDList = input.optJSONArray("assayIDList");
		if (assayIDList == null || assayIDList.length() == 0)
		{
			boolean curatedOnly = input.optBoolean("curatedOnly", false);
		
			// grab all identifiers with annotations; start with the curated ones: these go at the front
			// insert order is preserved by LinkedHashSet
			Set<Long> idlist = new LinkedHashSet<>();
			for (long assayID : store.assay().fetchAssayIDCuratedWithAnnotations()) idlist.add(assayID);
			if (!curatedOnly) for (long assayID : store.assay().fetchAssayIDWithAnnotations()) idlist.add(assayID);
			results.put("assayIDList", new ArrayList<>(idlist));
		}
		else
		{
			JSONArray searchList = input.optJSONArray("search");
			double threshold = input.optDouble("threshold", 0);
			int maxResults = input.optInt("maxResults", 0);
			if (maxResults > 100) throw new RESTException("The 'maxResults' parameter is too high.", HTTPStatus.BAD_REQUEST);

			boolean countCompounds = input.optBoolean("countCompounds", false);
			boolean translitPreviews = input.optBoolean("translitPreviews", false);

			if (assayIDList.length() > 250 && maxResults == 0) throw new RESTException("Maximum search size exceeded.", HTTPStatus.BAD_REQUEST);
			if (searchList == null || searchList.length() == 0) throw new RESTException("Must provide search parameter.", HTTPStatus.BAD_REQUEST);
			
			long[] idlist = new long[assayIDList.length()];
			for (int n = 0; n < idlist.length; n++) idlist[n] = assayIDList.getLong(n);

			List<DataObject.Annotation> search = new ArrayList<>();
			for (int n = 0; n < searchList.length(); n++)
			{
				JSONObject annot = searchList.getJSONObject(n);
				String propURI = annot.getString("propURI"), valueURI = annot.getString("valueURI");
				String[] groupNest = annot.optJSONArrayEmpty("groupNest").toStringArray();
				search.add(new DataObject.Annotation(propURI, valueURI, groupNest));
			}

			Result[] compare = performComparisons(idlist, search.toArray(new DataObject.Annotation[search.size()]), threshold, store);
			
			if (maxResults > 0)
			{
				Arrays.sort(compare, (c1, c2) -> Double.compare(c2.similarity, c1.similarity));
				if (compare.length > maxResults) compare = Arrays.copyOf(compare, maxResults);
			}
			
			JSONArray compareList = new JSONArray();
			AssayJSON.Options opt = new AssayJSON.Options();
			opt.includeHistory = false;
			for (int n = 0; n < compare.length; n++)
			{
				DataObject.Assay assay = compare[n].assay;
				JSONObject jsonResult = AssayJSON.serialiseAssay(assay, opt);
				jsonResult.put("similarity", compare[n].similarity);
				if (countCompounds) jsonResult.put("countCompounds", store.measure().countCompounds(assay.assayID));
				if (translitPreviews) jsonResult.put("translitPreviews", AssayUtil.transliteratedPreviews(assay));
				
				compareList.put(jsonResult);
			}
			results.put("results", compareList);
		}
		return results;
	}

	// ------------ private methods ------------

	private Result[] performComparisons(long[] assayIDList, DataObject.Annotation[] search, double threshold, DataStore store)
	{
		List<Result> results = new ArrayList<>();

		for (long assayID : assayIDList)
		{
			Result r = new Result();
			r.assay = store.assay().getAssay(assayID);
			if (r.assay == null) continue; // could've been deleted in the meanwhile
			if (r.assay.annotations == null || r.assay.annotations.length == 0) continue; // or modified

			r.similarity = 0;
			for (DataObject.Annotation annot : search) r.similarity += compareProperty(annot, r.assay);
			r.similarity /= search.length;

			if (r.similarity == 0 || r.similarity < threshold) continue;
			
			results.add(r);
		}
		
		return results.toArray(new Result[results.size()]);
	}
	
	protected double compareProperty(DataObject.Annotation srch, DataObject.Assay assay)
	{
		Schema schema = Common.getSchema(assay.schemaURI);
		if (schema == null) return 0;
		
		double sim = 0;
		for (Schema.Assignment assn : schema.findAssignmentByProperty(srch.propURI, srch.groupNest))
		{
			SchemaTree tree = Common.obtainTree(schema, assn);
			if (tree == null) continue;
			for (DataObject.Annotation annot : assay.annotations) if (annot.matchesProperty(srch.propURI, srch.groupNest))
			{
				// fast-out if they are identical
				if (srch.valueURI.equals(annot.valueURI)) return 1;
				
				// trace the annotation URI up the branch hierarchy, and see if it hits the search URI, i.e. the search URI encapsulates the
				// annotation value, which is worth some points
				
				SchemaTree.Node node = tree.getTree().get(annot.valueURI);
				if (node != null)
				{
					int dist = 2;
					node = node.parent;
					while (node != null)
					{
						dist++;
						if (node.uri.equals(srch.valueURI)) 
						{
							sim = Math.max(sim, 1.0 / dist);
							break;
						}
						node = node.parent;
					}
				}			
			}
		}
		
		return sim;
	}
}
