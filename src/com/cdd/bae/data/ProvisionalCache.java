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

import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.*;

/*
	Stores provisional terms in a cache so they can be quickly applied to composed branches.
*/

public class ProvisionalCache
{
	private Map<String, DataObject.Provisional> mapURI = new HashMap<>(); // term URI -> entry (note: use remapped URI)
	private Map<String, DataObject.Provisional[]> mapParent = new HashMap<>(); // parent URI -> list of entries (note: use remapped URI)
	private Map<String, String> remapURI = new HashMap<>(); // original URI -> final formal URI

    // ------------ public methods ------------

	public ProvisionalCache()
	{
	}

	// convenience constructor: create and load
	public static ProvisionalCache loaded()
	{
		var cache = new ProvisionalCache();
		cache.update();
		return cache;
	}
	
	// reload the provisional terms from the database; this is fast enough to call after any modification to the database
	public synchronized void update()
	{
		var store = Common.getDataStore();
		if (!store.isDBAvailable()) return;
	
		mapURI.clear();
		mapParent.clear();
		remapURI.clear();
	
		var allTerms = store.provisional().fetchAllTerms();
		
		// first extract all the remappings (which affect the next part)
		for (var prov : allTerms) if (Util.notBlank(prov.remappedTo))
		{
			remapURI.put(prov.uri, prov.remappedTo);
		}
	
		// go through and process each one
		for (var prov : allTerms)
		{
			String parentURI = remapMaybe(prov.parentURI);
			String termURI = prov.remappedTo != null ? prov.remappedTo : prov.uri;

			mapURI.put(termURI, prov);
			mapParent.put(parentURI, ArrayUtils.add(mapParent.get(parentURI), prov));			
		}
	}
	
	// for a given URI, converts it into the new one if applicable, or returns null if it isn't affected by remapping
	public synchronized String remap(String uri)
	{
		if (uri == null) return null;
		String newURI = remapURI.get(uri);
		if (newURI == null) return null;
		if (!remapURI.containsKey(newURI)) return newURI;
		
		// chained remapping, prevent loops
		Set<String> seenURI = new HashSet<>();
		seenURI.add(uri);
		seenURI.add(newURI);
		uri = newURI;
		while (true)
		{
			newURI = remapURI.get(uri);
			if (newURI == null || seenURI.contains(newURI)) break;
			uri = newURI;
			seenURI.add(uri);
		}
		return uri;
	}
	
	// as above, but returns the original URI if it's not remapped
	public synchronized String remapMaybe(String uri)
	{
		String newURI = remap(uri);
		return newURI == null ? uri : newURI;
	}
	
	// querying info
	public synchronized int numTerms()
	{
		return mapURI.size();
	}
	public synchronized int numRemappings()
	{
		return remapURI.size();
	}
	
	// fetch all the terms
	public synchronized DataObject.Provisional[] getAllTerms()
	{
		return mapURI.values().toArray(new DataObject.Provisional[mapURI.size()]);
	}
	
	// fetch the term based on the URI (note that if the parameter has been remapped to a new URI, it won't be found)
	public synchronized DataObject.Provisional getTerm(String uri)
	{
		return mapURI.get(uri);
	}
	
    // ------------ private methods ------------


}
