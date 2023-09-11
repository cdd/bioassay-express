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

namespace BioAssayExpress /* BOF */ {

/*
	Provides some key functionality for handling templates, such as fetching/caching the underlying definitions,
	and grafting branches onto base templates.
*/

const REGEX_GROUP_INDEXED = /^(.*)\@(\d+)$/;

export class TemplateManager
{
	private static cache:Record<string, SchemaSummary> = {};

	constructor()
	{
	}

	// fetches the definition for a template; returns nil if not already in the cache
	public static getTemplate(schemaURI:string):SchemaSummary
	{
		return this.cache[schemaURI];
	}

	// if there's a template already loaded into memory, might as well bootstrap it into the cache
	public static cacheTemplate(schema:SchemaSummary):void
	{
		this.cache[schema.schemaURI] = schema;
	}

	// fetches any templates that are not already in the cache; will not perform any fetching if they are all present; if
	// the callback is provided, will call it when the fetching the cache is as updated
	public static ensureTemplates(schemaList:string[], callback?:() => void):void
	{
		(async () =>
		{
			for (let schemaURI of schemaList)
			{
				try
				{
					let schema = await asyncREST('REST/DescribeSchema', {'schemaURI': schemaURI});
					this.cache[schemaURI] = schema as SchemaSummary;
				}
				catch (e)
				{
					alert(`Failed to fetch schema ${schemaURI}`);
				}
			}
			if (callback) callback();
		})();
	}

	// takes a starting template reference and branches to attach, and asks the server for a glued-together version
	// NOTE: currently not cached, though it could be
	public static graftTemplates(schemaURI:string, branches:SchemaBranch[], duplication:SchemaDuplication[],
								 callback:(schema:SchemaSummary) => void):void
	{
		let params = {'schemaURI': schemaURI, 'schemaBranches': branches, 'schemaDuplication': duplication};
		callREST('REST/DescribeSchema', params,
			(schema:SchemaSummary) => callback(schema),
			() => alert('Failed to graft schema ' + schemaURI));
	}
	public static async asyncGraftTemplates(schemaURI:string,
						branches:SchemaBranch[], duplication:SchemaDuplication[]):Promise<SchemaSummary>
	{
		let params = {'schemaURI': schemaURI, 'schemaBranches': branches, 'schemaDuplication': duplication};
		return await asyncREST('REST/DescribeSchema', params);
	}

	// given information about a schema which may contain sub-branches, checks to see if the propURI/groupNest belongs on one of
	// these branches; if so, returns the [schemaURI, groupNest] that points to the branch schema; if not, just returns the
	// same [schemaURI, groupNest]
	public static relativeBranch(propURI:string, groupNest:string[],
								 schemaURI:string, branches:SchemaBranch[], duplication:SchemaDuplication[]):[string, string[]]
	{
		if (Vec.arrayLength(groupNest) == 0) return [schemaURI, groupNest];
		if (Vec.arrayLength(branches) == 0) return [schemaURI, TemplateManager.baselineGroup(groupNest)];

		skip: for (let branch of branches) if (groupNest.length >= Vec.arrayLength(branch.groupNest))
		{
			let d = groupNest.length - Vec.arrayLength(branch.groupNest);
			for (let n = 0; n < Vec.arrayLength(branch.groupNest); n++) if (branch.groupNest[n] != groupNest[n + d]) continue skip;
			return [branch.schemaURI, TemplateManager.baselineGroup(groupNest.slice(0, d))];
		}
		return [schemaURI, TemplateManager.baselineGroup(groupNest)];
	}

	// sometimes the annotation [propURI,groupNest..] can get out of sync with the underlying template; this is typically only
	// an issue when the template is changed, since harmonisation is done at the server end; other cases when it has any effect
	// are likely to be caused by a bug
	public static harmoniseAnnotations(schema:SchemaSummary, annotations:AssayAnnotation[]):void
	{
		let propGroupKeys = new Set<string>();
		for (let assn of schema.assignments) propGroupKeys.add(keyPropGroup(assn.propURI, assn.groupNest));
		let idx:number[] = [];
		for (let annot of annotations)
		{
			if (propGroupKeys.has(keyPropGroup(annot.propURI, annot.groupNest))) continue;

			// there's no literal match, so try to find the groupNest pair that matches most deeply, and with suffixes
			// trimmed off if necessary (i.e. the @1, @2, etc)
			let bestAssn:SchemaAssignment = null, bestScore = -1;
			for (let assn of schema.assignments) if (assn.propURI == annot.propURI)
			{
				if (bestScore < 0) {bestAssn = assn; bestScore = 0;}
				let sz = Math.min(Vec.arrayLength(assn.groupNest), Vec.arrayLength(annot.groupNest));
				for (let n = 0; n < sz; n++)
				{
					if (assn.groupNest[n] != annot.groupNest[n]) break;
					let score = n + 1;
					if (score > bestScore) {bestAssn = assn; bestScore = score;}
				}
				for (let n = 0, score = 0; n < sz; n++)
				{
					let g1 = assn.groupNest[n], g2 = annot.groupNest[n];
					if (g1 == g2) score++;
					else
					{
						let sfx1 = g1.indexOf('@'), sfx2 = g2.indexOf('@');
						if (sfx1 >= 0) g1 = g1.substring(0, sfx1);
						if (sfx2 >= 0) g2 = g2.substring(0, sfx2);
						if (g1 != g2) break;
						score += 0.5;
					}
					if (score > bestScore) {bestAssn = assn; bestScore = score;}
				}
			}
			if (bestAssn) annot.groupNest = bestAssn.groupNest;
		}
	}

	// ------------ branch/duplicated content helpers ------------

	// variation of above, where an un-suffixed URI matches any suffixed URI
	public static comparePermissiveGroupURI(uri1:string, uri2:string):boolean
	{
		if (uri1 == uri2) return true;
		if (!uri1 || !uri2) return false;
		let short1 = uri1.match(REGEX_GROUP_INDEXED), short2 = uri2.match(REGEX_GROUP_INDEXED);
		if (short1 && short2) return false; // they both have suffixes, and they weren't the same, so stop
		return (short1 ? short1[1] : uri1) == (short2 ? short2[1] : uri2);
	}

	// appends the group duplication suffix; it is permissive: if there is one already, it'll be stripped first
	public static appendSuffixGroupURI(uri:string, dupidx:number):string
	{
		return this.removeSuffixGroupURI(uri) + '@' + dupidx;
	}

	// returns just the baseline URI, with any group duplication suffix removed
	public static removeSuffixGroupURI(uri:string):string
	{
		if (!uri) return null;
		let match = uri.match(REGEX_GROUP_INDEXED);
		return match ? match[1] : uri;
	}

	// separate the baseline URI from the group duplication index; the latter will be 0 if none
	public static decomposeSuffixGroupURI(uri:string):[string, number]
	{
		if (!uri) return [uri, 0];
		let split = uri.match(REGEX_GROUP_INDEXED);
		if (!split) return [uri, 0];
		return [split[1], parseInt(split[2])];
	}

	// compares two groupNest arrays, but for the top item (entry #0) which ignores the group duplication suffix
	public static compareBaselineGroupNest(groupNest1:string[], groupNest2:string[]):boolean
	{
		let sz1 = Vec.arrayLength(groupNest1), sz2 = Vec.arrayLength(groupNest2);
		if (sz1 == 0 && sz2 == 0) return true;
		if (sz1 != sz2) return false;
		if (this.removeSuffixGroupURI(groupNest1[0]) != this.removeSuffixGroupURI(groupNest2[0])) return false;
		for (let n = 1; n < sz1; n++) if (groupNest1[n] != groupNest2[n]) return false;
		return true;
	}

	// if the top level group (groupNest[0]) has a duplication index suffix, strips it off, and returns a copied array; else
	// returns self; this can then be used as the base indicator for all of the groups, regardless of their index
	public static baselineGroup(groupNest:string[]):string[]
	{
		if (Vec.isBlank(groupNest)) return groupNest;
		let match = groupNest[0].match(REGEX_GROUP_INDEXED);
		if (match)
		{
			groupNest = groupNest.slice(0);
			groupNest[0] = match[1];
		}
		return groupNest;
	}

	// given a group for which the top level (groupNest[0]) refers generally to all duplications, appends (or replaces)
	// the suffix for the given duplication index; returns a cloned array
	public static groupSuffix(groupNest:string[], dupidx:number):string[]
	{
		if (Vec.isBlank(groupNest)) return groupNest;
		groupNest = groupNest.slice(0);
		groupNest[0] = this.removeSuffixGroupURI(groupNest[0]) + '@' + dupidx;
		return groupNest;
	}

	// ------------ private methods ------------
}

/* EOF */ }
