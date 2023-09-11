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
	Provides functionality for interpreting the unique identifier strings. The actual content is inserted into the web page
	via the MiscInserts java class, as a short array of dictionaries.
*/

export interface UniqueIDSource
{
	name:string;
	shortName:string;
	prefix:string;
	baseURL:string;
	baseRegex:string;
	defaultSchema:string;
}

declare var UNIQUE_IDENTIFIER_SOURCES:UniqueIDSource[];

export class UniqueIdentifier
{
	public static sources():UniqueIDSource[]
	{
		return UNIQUE_IDENTIFIER_SOURCES;
	}

	// splits a key into its constituents; returns null if the prefix is not recognised
	public static parseKey(key:string):[UniqueIDSource, string]
	{
		if (!key) return [null, null];
		for (let src of UNIQUE_IDENTIFIER_SOURCES) if (key.startsWith(src.prefix)) return [src, key.substring(src.prefix.length)];
		return [null, null];
	}

	// recombine into a unique string to use as a key
	public static makeKey(src:UniqueIDSource, id:string):string
	{
		if (!src) return null;
		return src.prefix + id;
	}

	// return a string representation of the identifier
	public static composeUniqueID(key:string):string
	{
		let [src, id] = UniqueIdentifier.parseKey(key);
		if (src && key) return src.name + ' ' + id;
		return key;
	}

	// returns a URL that refers to the original thing
	public static composeRefURL(key:string):string
	{
		let [src, id] = UniqueIdentifier.parseKey(key);
		if (src == null || !src.baseURL) return null;

		// the "base regex" can be used to trim off superfluous fluff
		if (src.baseRegex && id)
		{
			let groups = id.match(new RegExp(src.baseRegex));
			if (groups) id = groups[1];
		}

		return src.baseURL + id;
	}
}

/* EOF */ }
