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

package com.cdd.bae.model.dictionary;

import com.cdd.bae.data.*;
import com.cdd.bae.model.hankcs.*;
import com.cdd.bao.template.*;

import java.util.*;

import org.apache.commons.lang3.*;

/*
	Information about an annotation acquired by text extraction.
*/

public class ScoredHit
{
	public SchemaTree.Node hit;
	public double score; // higher is better
	public int count;
	public int[] begin = null;
	public int[] end = null;

	public ScoredHit(SchemaTree.Node hit)
	{
		this(hit, 0.0);
	}

	public ScoredHit(SchemaTree.Node hit, double score)
	{
		this.hit = hit;
		this.score = score;
		this.count = 0;
	}

	@Override
	public String toString()
	{
		String uri = hit.uri;
		uri = uri.substring(uri.lastIndexOf('/') + 1);
		if (uri.contains("#")) uri = uri.substring(uri.lastIndexOf('#') + 1);
		StringJoiner sj = new StringJoiner(" ", "[", "]");
		if (begin != null) for (int i = 0; i < begin.length; i++)
			sj.add(begin[i] + "," + end[i]);
		return String.format("%s %d (%s, %.2f, %d, %s)", Common.getOntoValues().getLabel(hit.uri), hit.depth, uri, score, count, sj.toString());
	}

	@Override
	public boolean equals(Object o)
	{
		if (o == null || getClass() != o.getClass()) return false;
		ScoredHit other = (ScoredHit)o;
		return hit.uri.equals(other.hit.uri);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(hit.uri);
	}

	public void addHit(AhoCorasickDoubleArrayTrie<SchemaTree.Node>.Hit<SchemaTree.Node> hit, double score)
	{
		this.score += score;
		count += 1;
		begin = ArrayUtils.add(begin, hit.begin);
		end = ArrayUtils.add(end, hit.end);
	}

	public void clearMapping()
	{
		begin = null;
		end = null;
	}

	public void addMappings(ScoredHit scoredHit)
	{
		begin = ArrayUtils.addAll(begin, scoredHit.begin);
		end = ArrayUtils.addAll(end, scoredHit.end);
	}

}
