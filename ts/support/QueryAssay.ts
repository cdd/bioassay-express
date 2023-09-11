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

namespace BioAssayExpress /* BOF */
{

/*
	Tools for analyzing the concise query line format (cf. QueryAssay.java)
*/

const enum QueryAssayMatch
{
	Branch,
	Exact,
	NotBranch,
	NotExact,
}

class QueryAssayValue
{
	public uri:string;
	public match:number; // one of MatchEnums

	// Only implementing one constructor for now
	//
	//	constructor(uri:string);
	constructor(uri:string, match:number)
	{
		this.uri = uri;
		this.match = match;
	}
}

export const SPECIAL_EMPTY = 'EMPTY';
export const SPECIAL_WITHTEXT = 'WITHTEXT';

export class QueryAssay
{
	public matchTerms:Record<string, QueryAssayValue[]> = {}; // propURI -> values

	constructor(public qstr:string)
	{
		let parts:string[] = this.divideParts(qstr, ';');

		for (let part of parts)
		{
			if (part.length > 0)
			{
				if (part.startsWith('(') && part.endsWith(')')) this.extractTerms(part);
				else if (part.startsWith('[') && part.endsWith(']')) this.extractLiterals(part);
				else throw new Error("Unexpected part '" + part + "'");
			}
		}
	}

	// Quick port of divideParts
	// TODO: Implement the original divideParts which actually...
	// splits the overall query into parts, with a given separators; honours escape characters and bracket levels
	public divideParts(qstr:string, sep:string):string[]
	{
		let parts:string[] = qstr.split(sep);
		return parts;
	}

	public addTerm(propURI:string, value:QueryAssayValue):void
	{
		let vlist:QueryAssayValue[] = this.matchTerms[propURI];
		if (vlist == null)
		{
			this.matchTerms[propURI] = [value];
		}
		else
		{
			vlist.push(value);
		}
	}

	public getPropURIKeys():string[]
	{
		let keys:string[] = [];
		for (let key in this.matchTerms)
		{
			keys.push(key);
		}

		return keys;
	}

	// ------------ private methods ------------

	private extractTerms(part:string):void
	{
		// first drop the first and last character and then split the string by '='
		let partSubstring = part.substr(1, part.length - 2);
		let propval:string[] = this.divideParts(partSubstring, '=');

		if (propval.length != 2) throw 'Part has to start with (propURI=values...): ' + part;

		let propURI:string = expandPrefix(this.unescape(propval[0]));

		for (let vraw of this.divideParts(propval[1], ','))
		{
			let vstr = this.unescape(vraw);
			let match = QueryAssayMatch.Branch;

			if (vstr.startsWith('*')) {match = QueryAssayMatch.Branch; vstr = vstr.substr(1);}
			else if (vstr.startsWith('@')) {match = QueryAssayMatch.Exact; vstr = vstr.substr(1);}
			else if (vstr.startsWith('!*')) {match = QueryAssayMatch.NotBranch; vstr = vstr.substr(2);}
			else if (vstr.startsWith('!@')) {match = QueryAssayMatch.NotExact; vstr = vstr.substr(2);}

			let valueURI = expandPrefix(vstr);

			if (!valueURI.startsWith('http://') && !valueURI.startsWith('https://') && (valueURI != 'WITHTEXT') && (valueURI != 'EMPTY')) throw 'Invalid URI: [' + valueURI + ']';

			this.addTerm(propURI, new QueryAssayValue(valueURI, match));
		}
	}

	private extractLiterals(part:string):void
	{
		// ...
	}

	/*

	// pulls out literals (ID numbers)
	private void extractLiterals(String part) throws Fail
	{
		for (String bit : part.substring(1, part.length() - 1).split(","))
		{
			try
			{
				//if (bit.startsWith("AID")) literalPubChemAID.add(Integer.parseInt(bit.substring(3)));
				if (bit.startsWith("UID/")) literalUniqueID.add(bit.substring(4));
				else literalAssayID.add(Long.parseLong(bit));
			}
			catch (NumberFormatException ex) {throw new Fail("Invalid literal: " + bit);}
		}
	}
	*/

	// TODO: implement the optimized version in QueryAssay.java
	private unescape(str:string):string
	{
		return str.replace('\\', '');
	}
}

/* EOF */ }
