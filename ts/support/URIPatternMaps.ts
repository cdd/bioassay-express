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
	Manage URIPatternMaps
*/

interface URIPattern extends URIPatternMap
{
	regexp?:RegExp;
}

interface URIPatternMatch
{
	text:string;
	url:string;
	map:URIPattern;
	payload:string;
	groups:string[];
}

interface LinkOptions
{
	separator?:string;
	prefix?:string;
	postfix?:string;
	externalText?:string;
	externalPattern?:string;
}
const defaultLinkOptions:LinkOptions = {'separator': ',', 'prefix': '[', 'postfix': ']'};

export class URIPatternMaps
{
	private maps:URIPattern[];
	constructor(maps:URIPatternMap[] = [])
	{
		this.maps = [...maps];
		for (let map of this.maps) map.regexp = new RegExp(map.matchPrefix);
	}

	public get hasPatterns():boolean
	{
		return this.maps.length > 0;
	}

	public *matches(text:string):Generator<URIPatternMatch, void, unknown>
	{
		if (!text || !this.hasPatterns) return;

		for (let map of this.maps)
		{
			let groups = text.match(map.regexp);
			if (!groups) continue;
			let result:URIPatternMatch =
			{
				'text': text,
				'map': map,
				'groups': groups.slice(1),
				'payload': groups[1],
				'url': text.replace(map.regexp, map.externalURL),
			};
			yield result;
		}
	}

	public *renderLinks(text:string, options:LinkOptions = {}):Generator<JQuery, void, unknown>
	{
		for (let match of this.matches(text))
			yield URIPatternMaps.renderLink(match, options);
	}

	public joinLinks(container:JQuery, text:string, options:LinkOptions = {}):JQuery
	{
		options = {...defaultLinkOptions, ...options};
		let links = [...this.renderLinks(text, options)];
		return joinElements(container, links, options.separator, options.prefix, options.postfix);
	}

	public static renderLink(match:URIPatternMatch, options:LinkOptions = {}):JQuery
	{
		const {url, map, text} = match;
		let {externalText, externalPattern} = options;

		if (!externalPattern && !externalText) externalText = map.label;

		let href = $('<a target="_blank"/>');
		href.attr({'href': url, 'alt': map.label});
		if (externalText)
			href.text(externalText);
		else if (externalPattern)
			href.text(text.replace(map.regexp, externalPattern));
		return href;
	}
}

/* EOF */ }
