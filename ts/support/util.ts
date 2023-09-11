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

///<reference path='../decl/webmolkit-build.d.ts'/>
///<reference path='../decl/bootstrap.d.ts'/>
///<reference path='../decl/DOMpurify.d.ts'/>
///<reference path='svcobj.ts'/>

// convenient remappings from WebMolKit
import wmk = WebMolKit;
import Vec = WebMolKit.Vec;
import pixelDensity = WebMolKit.pixelDensity;
import drawLine = WebMolKit.drawLine;
import escapeHTML = WebMolKit.escapeHTML;
import pathRoundedRect = WebMolKit.pathRoundedRect;
import eventCoords = WebMolKit.eventCoords;
import clone = WebMolKit.clone;
import deepClone = WebMolKit.deepClone;
import orBlank = WebMolKit.orBlank;
import blendRGB = WebMolKit.blendRGB;
import colourCode = WebMolKit.colourCode;
import colourCanvas = WebMolKit.colourCanvas;
import TWOPI = WebMolKit.TWOPI;
import norm_xy = WebMolKit.norm_xy; // eslint-disable-line
import dom = WebMolKit.dom;
import domLegacy = WebMolKit.domLegacy;
import DOM = WebMolKit.DOM;

namespace BioAssayExpress /* BOF */ {

/*
	Miscellaneous utilities for the BAE project.
*/

// defining the base URL, from which REST calls are based; also the optional "stamp" parameter is used when formulating
// GET-style requests, to encourage the browser to cache each URL until the server has been restarted
export let restBaseURL = '', restStamp:string = null;
export function initREST(baseURL:string, stamp?:string):void
{
	restBaseURL = baseURL;
	restStamp = stamp;
}

// determine the base URL from the current locaton. By default, it assumes that the
// URL is on the root level. Use nparent for subfolders
export function getBaseURL(nparent?:number):string
{
	if (!nparent) nparent = 0;
	nparent++;
	let s = window.location.href.split('?')[0];
	for (let i = 0; i < nparent; i++) s = s.substring(0, s.lastIndexOf('/'));
	return s;
}

// convenience function for making a REST-esque call using JQuery
export function callREST(service:string, params:any,
				successFunc:(data:any, textStatus:string, jqXHR:JQueryXHR) => void,
				errorFunc?:(jqXHR:JQueryXHR, textStatus:string, errorThrow:string) => void,
				completeFunc?:(jqXHR:JQueryXHR, textStatus:string) => void):void
{
	/*$.ajax(
	{
		'url': restBaseURL + '/' + service,
		'type': 'POST',
		'data': JSON.stringify(params),
		'contentType': 'application/json;charset=utf-8',
		'dataType': 'json',
		'headers': {'Access-Control-Allow-Origin': '*'},
		'success': successFunc,
		'error': errorFunc,
		'complete': completeFunc
	});*/
	asyncREST(service, params).then(
		(result) =>
		{
			successFunc(result, null, null);
			if (completeFunc) completeFunc(null, null);
		},
		(error) =>
		{
			if (errorFunc) errorFunc(null, null, error.toString());
			if (completeFunc) completeFunc(null, null);
		});
}

// convenience access to REST-esque calls to the BAE server
export async function asyncREST(service:string, params:any):Promise<any>
{
	return new Promise<string>((resolve, reject) =>
	{
		let url = restBaseURL + '/' + service;
		let request = new XMLHttpRequest();
		request.open('POST', url, true);
		request.setRequestHeader('Content-Type', 'application/json;charset=utf-8');
		request.setRequestHeader('Access-Control-Allow-Origin', '*');
		request.responseType = 'json';
		request.onload = () => resolve(request.response);
		request.onerror = (ex) => reject('Failed on URL [' + url + ']: ' + ex);
		request.send(JSON.stringify(params));
	});
}

// convenience function to open a new window using POST
export function openWindowWithPOST(url:string, params:Record<string, string>):void
{
	let form = document.createElement('form');
	form.target = '_blank';
	form.method = 'POST';
	form.enctype = 'application/x-www-form-urlencoded';
	form.action = url;
	form.style.display = 'none';

	for (let key in params)
	{
		let input = document.createElement('input');
		input.type = 'hidden';
		input.name = key;
		input.value = params[key];
		form.appendChild(input);
	}

	document.body.appendChild(form);
	form.submit();
	document.body.removeChild(form);
}

// a somewhat nasty hack to allow text to be copied onto the clipboard: creates a text area, uses its copy abilities, then removes it
export function copyToClipboard(text:string, parent:HTMLElement = null):void
{
	let tmp = document.createElement('textarea');
	tmp.style.fontSize = '12pt';
	tmp.style.border = '0';
	tmp.style.padding = '0';
	tmp.style.margin = '0';
	tmp.style.position = 'fixed';
	tmp.style['left'] = '-9999px';
	tmp.style.top = (window.pageYOffset || document.documentElement.scrollTop) + 'px';
	tmp.setAttribute('readonly', '');

	if (parent == null) parent = document.body;
	parent.appendChild(tmp);

	tmp.value = text;
	tmp.select();
	document.execCommand('copy');

	tmp.parentNode.removeChild(tmp);
}

// turns the more space efficient "column-based" tree format into the more convenient object-based version used internally
export function unlaminateTree(columns:SchemaTreeLaminated):SchemaTreeNode[]
{
	let pfxmap = columns.prefixMap;
	let treeData:SchemaTreeNode[] = [];
	for (let n = 0; n < columns.depth.length; n++)
	{
		let obj = {} as SchemaTreeNode;
		obj.depth = columns.depth[n];
		obj.parent = columns.parent[n];
		obj.name = columns.name[n];
		obj.abbrev = columns.abbrev[n];
		obj.inSchema = columns.inSchema[n];
		obj.provisional = columns.provisional[n];
		obj.schemaCount = columns.schemaCount[n];
		obj.childCount = columns.childCount[n];
		obj.inModel = columns.inModel[n];
		obj.altLabels = columns.altLabels[n];
		obj.externalURLs = columns.externalURLs[n];
		if (columns.axiomApplicable) obj.axiomApplicable = columns.axiomApplicable[n];

		// turn prefixed version into a full URL (if not already)
		obj.uri = obj.abbrev;
		if (!obj.uri.startsWith('http://'))
		{
			let i = obj.abbrev.indexOf(':');
			let pfx = pfxmap[obj.abbrev.substring(0, i + 1)];
			if (pfx) obj.uri = pfx + obj.abbrev.substring(i + 1);
		}

		treeData.push(obj);
	}

	// indicate which nodes in the tree are container terms
	for (let n of columns.containers) treeData[n].isContainer = true;

	return treeData;
}

// global declared elsewhere; see MiscInserts.
declare let PREFIX_MAP:Record<string, string>;

// if the given URI has one of the common prefixes, replace it with the abbreviated version; if none, returns same as input
export function collapsePrefix(uri:string):string
{
	if (!uri) return '';
	for (let pfx in PREFIX_MAP)
	{
		let stem = PREFIX_MAP[pfx];
		if (uri.startsWith(stem)) return pfx + uri.substring(stem.length);
	}
	return uri;
}
export function collapsePrefixes(uriList:string[]):string[]
{
	if (uriList == null) return null;
	return uriList.map((uri) => collapsePrefix(uri));
}

// if the given proto-URI starts with one of the common prefixes, replace it with the actual URI root stem; if none, returns same as input
export function expandPrefix(uri:string):string
{
	if (uri == null) return null;
	for (let pfx in PREFIX_MAP)
	{
		if (uri.startsWith(pfx)) return PREFIX_MAP[pfx] + uri.substring(pfx.length);
	}
	return uri;
}
export function expandPrefixes(uriList:string[]):string[]
{
	if (uriList == null) return null;
	return uriList.map((uri) => expandPrefix(uri));
}

// draws text (using the current font & fill style) rotated 90 degrees anticlockwise, with the given centroid
export function drawRotatedText(ctx:CanvasRenderingContext2D, cx:number, cy:number, txt:string):void
{
	ctx.save();
	ctx.rotate(-0.5 * Math.PI);
	let x = -cy, y = cx;
	let tw = ctx.measureText(txt).width;
	ctx.textBaseline = 'middle';
	ctx.fillText(txt, x - 0.5 * tw, y);
	ctx.restore();
}

// convenient shortcuts comparing assignment group nesting: two separate concepts - "same" is a more literal comparison which
// is appropriate for two assignments within the same template; "compatible" means that they are not mutually exclusive, that
// being the appropriate way to compare assignments not necessarily from the same template
export function sameGroupNest(groupNest1:string[], groupNest2:string[]):boolean
{
	let sz = Vec.arrayLength(groupNest1);
	if (Vec.arrayLength(groupNest2) != sz) return false;
	//for (let n = 0; n < sz; n++) if (!compareGroupURI(groupNest1[n], groupNest2[n])) return false;
	for (let n = 0; n < sz; n++) if (groupNest1[n] != groupNest2[n]) return false;
	return true;
}
export function compatibleGroupNest(groupNest1:string[], groupNest2:string[]):boolean
{
	let sz = Math.min(Vec.arrayLength(groupNest1), Vec.arrayLength(groupNest2));
	//for (let n = 0; n < sz; n++) if (!compareGroupURI(groupNest1[n], groupNest2[n])) return false;
	for (let n = 0; n < sz; n++) if (groupNest1[n] != groupNest2[n]) return false;
	return true;
}
export function samePropGroupNest(propURI1:string, groupNest1:string[], propURI2:string, groupNest2:string[]):boolean
{
	return propURI1 == propURI2 && sameGroupNest(groupNest1, groupNest2);
}
export function compatiblePropGroupNest(propURI1:string, groupNest1:string[], propURI2:string, groupNest2:string[]):boolean
{
	return propURI1 == propURI2 && compatibleGroupNest(groupNest1, groupNest2);
}

// returns true if childNest is the same as parentNest, or is a descendent of it, i.e. childNest.length >= parentNest.length
export function descendentGroupNest(childNest:string[], parentNest:string[]):boolean
{
	if (!childNest) childNest = [];
	if (!parentNest) parentNest = [];
	if (childNest.length < parentNest.length) return false;
	for (let i = 0; i < parentNest.length; i++)
	{
		let j = childNest.length - parentNest.length + i;
		//if (!compareGroupURI(parentNest[i], childNest[j])) return false;
		if (parentNest[i] != childNest[j]) return false;
	}
	return true;
}

// formulates a token key from property & group hierarchy: this is used frequently for stashing assignments in dictionaries
export function keyPropGroup(propURI:string, groupNest:string[]):string
{
	let key = propURI + '::';
	if (groupNest != null) key += groupNest.join('::');
	return key;
}
export function keyPropGroupValue(propURI:string, groupNest:string[], value:string):string
{
	return keyPropGroup(propURI, groupNest) + '::' + value;
}

// useful formatting, primarily for debugging purposes
export function formatAssignment(assn:Partial<SchemaAssignment>):string
{
	return '{' + collapsePrefix(assn.propURI) + ':' + JSON.stringify(collapsePrefixes(assn.groupNest)) + '/' + assn.name + '}';
}
export function formatAnnotation(annot:AssayAnnotation):string
{
	return '{' + collapsePrefix(annot.propURI) + ':' + JSON.stringify(collapsePrefixes(annot.groupNest)) +
			'/' + collapsePrefix(annot.valueURI) + ':' + annot.valueLabel + '}';
}

// pull out a suitable title for an assay;
export function deriveAssayName(assay:AssayDefinition, maxLength:number = 100):string
{
	const URI_HASTITLE = 'http://www.bioassayontology.org/bao#BAO_0002853';
	for (let annot of assay.annotations) if (annot.propURI == URI_HASTITLE)
	{
		// (maybe consider bunching them up if more than one?)
		if (annot.valueLabel) return annot.valueLabel;
	}

	// by default: keeps just enough of the assay text to use as a title
	let title = assay.text ? assay.text.split('\n')[0] : '';
	if (title.length > maxLength) title = title.substring(0, maxLength - 4) + ' ...';

	return title ? title : 'untitled';
}

// makes sure the string has no nefarious JavaScript-hijacking content
export function purifyTextPlainInput(textPrecursor:any):string
{
	if (textPrecursor == null) return null;
	let text = textPrecursor.toString();

	// TODO: is there any sanitisation needed for plain text? if so, insert it here; this function is only called for user input, so it's
	// not necessarily a bad thing be overly aggressive about filtering out naughty content
	return text;
}
export function purifyTextHTMLInput(text:string):string
{
	return DOMPurify.sanitize(text);
}

// opens a new tab with the given URL and POST data
export function launchTabPOST(url:string, postData:string):void
{
	let form = document.createElement('form');
	form.setAttribute('method', 'POST');
	form.setAttribute('action', url);
	form.setAttribute('target', '_blank');
	let hidden = document.createElement('input');
	hidden.setAttribute('type', 'hidden');
	hidden.setAttribute('name', 'data');
	hidden.setAttribute('value', postData);
	form.appendChild(hidden);
	document.body.appendChild(form);
	form.submit();
	form.remove();
}

// parses text and returns jquery object that shows text between ** as bold
// e.g. a **bold** move => <span>a <b>bold</b> move</span>
export function highlightedText(text:string):JQuery
{
	let e = $('<span/>');
	let parts = text.split('**');
	e.append(parts.shift());
	while (parts.length > 0)
	{
		$('<b/>').appendTo(e).append(parts.shift());
		e.append(parts.shift());
	}
	return e;
}

// append array of elements to container adding separator between element
// if provided, add prefix before and postfix after the elements
// the functionality is similar to Java's StringJoiner and useful to render e.g.
// comma separated lists
export function joinElements(container:JQuery, elements:(string | JQuery)[], separator:(string | JQuery), prefix?:string, postfix?:string):JQuery
{
	if (elements.length == 0) return container;
	if (prefix) container.append(prefix);
	for (let idx = 0; idx < elements.length; idx++)
	{
		if (idx > 0)
		{
			let sep = separator;
			if ((sep as JQuery).jquery) sep = (separator as JQuery).clone();
			container.append(sep);
		}
		container.append(elements[idx]);
	}
	if (postfix) container.append(postfix);
	return container;
}

// returns the Levenshtein distance of two strings, whereby 0 means identical, 1 means single permutation, etc.; note
// that the function is case sensitive, so convert beforehand if necessary
export function stringSimilarity(str1:string, str2:string):number
{
	const ch1 = Array.from(str1), sz1 = ch1.length;
	const ch2 = Array.from(str2), sz2 = ch2.length;
	if (sz1 == 0) return sz2;
	if (sz2 == 0) return sz1;

	let levenshteinDistance = (sz1:number, sz2:number):number =>
	{
		let d:number[][] = [];
		for (let i = 0; i <= sz1; i++)
		{
			d.push(Vec.numberArray(0, sz2 + 1));
			d[i][0] = i;
		}
		for (let j = 1; j <= sz2; j++) d[0][j] = j;

		for (let j = 1; j <= sz2; j++) for (let i = 1; i <= sz1; i++)
		{
			let cost = ch1[i - 1] == ch2[j - 1] ? 0 : 1;
			d[i][j] = Math.min(Math.min(d[i - 1][j] + 1, d[i][j - 1] + 1), d[i - 1][j - 1] + cost);
		}
		return d[sz1][sz2];
	};

	let cost = ch1[sz1 - 1] == ch2[sz2 - 1] ? 0 : 1;
	let lev1 = levenshteinDistance(sz1 - 1, sz2) + 1;
	let lev2 = levenshteinDistance(sz1, sz2 - 1) + 1;
	let lev3 = levenshteinDistance(sz1 - 1, sz2 - 1) + cost;

	return Math.min(Math.min(lev1, lev2), lev3);
}

// returns a metric of string similarity that is based on the Levenshtein distance, with some post calibration;
// this is mainly to workaround the short string problem, for which short strings with nothing in common can
// report better similarity than long strings that are almost the same
export function calibratedSimilarity(str1:string, str2:string):number
{
	let sim = stringSimilarity(str1, str2);
	let sz = Math.max(str1.length, str2.length);
	if (sz < 5) sim *= 6 - sz;
	return sim;
}

// returns a similarity measure between two strings based on number of common n-grams
// strings are fragmented into substrings of a given length n (default 2): str1 => X, str2 => Y
// the similarity is then calculated using the Dice coefficient 2*|X and Y| / (|X|-n+1 + |Y|-n+1)
export function fuzzyStringSimilarity(str1:string, str2:string, ngramLength:number = 2, caseSensitive:boolean = false):number
{
	if (!caseSensitive)
	{
		str1 = str1.toLowerCase();
		str2 = str2.toLowerCase();
	}

	if (str1.length < ngramLength || str2.length < ngramLength) return 0;

	// count n-grams in str1
	const map = new Map();
	for (let i = 0; i < str1.length - (ngramLength - 1); i++)
	{
		const substr1 = str1.substr(i, ngramLength);
		map.set(substr1, map.has(substr1) ? map.get(substr1) + 1 : 1);
	}

	// determine number of matching n-grams in str2
	let match = 0;
	for (let j = 0; j < str2.length - (ngramLength - 1); j++)
	{
		const substr2 = str2.substr(j, ngramLength);
		const count = map.has(substr2) ? map.get(substr2) : 0;
		if (count > 0)
		{
			map.set(substr2, count - 1);
			match++;
		}
	}

	// normalize number of matches
	return (match * 2) / (str1.length + str2.length - ((ngramLength - 1) * 2));
}

// common special keycodes
export enum KeyCode
{
	Enter = 13,
	Tab = 9,
	Up = 38,
	Down = 40,
	Escape = 27
}

// number checking convenience methods; these are implemented with explicit regular expressions because library functions tend to
// try to be nice about regional conventions, whereas we want to enforce a narrow subset

const REGEX_INTEGER = /^-?\d+$/;
const REGEX_NUMBER1 = /^-?\d*\.\d+$/;
const REGEX_NUMBER2 = /^-?\d*\.?\d+[eE]-?[\d\.]+$/;

export function validIntegerLiteral(literal:string):boolean
{
	if (literal == null || literal == '') return false;
	return REGEX_INTEGER.test(literal);
}
export function validNumberLiteral(literal:string):boolean
{
	if (literal == null || literal == '') return false;
	return REGEX_INTEGER.test(literal) || REGEX_NUMBER1.test(literal) || REGEX_NUMBER2.test(literal);
}
export function standardizeNumberLiteral(literal:string):string
{
	if (REGEX_INTEGER.test(literal)) return parseInt(literal).toString();
	if (REGEX_NUMBER1.test(literal)) return parseFloat(literal).toString();
	if (REGEX_NUMBER2.test(literal)) return parseFloat(literal).toString();
	return literal;
}

// convert to time to date and represent in ISO-8601 format
export function formatDate(time:number):string
{
	if (time == null) return '';
	let date = new Date();
	date.setTime(time);
	let d = date.getDate(), m = date.getMonth() + 1, y = date.getFullYear();
	return y + '-' + (m < 10 ? '0' : '') + m + '-' + (d < 10 ? '0' : '') + d;
}

// return tuples of the array elements as a generator
type ZipArraysTuple<T0, T1> = [T0, T1, ...any[]];
export function *zipArrays<T0, T1>(array0:T0[], array1:T1[], ...arrays:any[][]):Generator<ZipArraysTuple<T0, T1>, void, unknown>
{
	for (let idx = 0; idx < array0.length; idx++)
	{
		let result:ZipArraysTuple<T0, T1> = [array0[idx], array1[idx]];
		if (arrays) result = [result[0], result[1], ...arrays.map((array) => array[idx])];
		yield result;
	}
}

// return tuples of the array elements as a generator
export function *enumerate<T>(array:(T[] | Generator<T, void, unknown>), start:number = 0):Generator<[number, T], void, unknown>
{
	let idx = start;
	for (let element of array)
		yield [idx++, element as T];
}

// group list by key. key is determined using a function
type GroupKey = (string | number);
export function groupBy<T>(array:T[], calculateKey:(arg0:T) => GroupKey, sort:(-1 | 0 | 1) = 0):Record<GroupKey, T[]>
{
	let grouped:Record<GroupKey, T[]> = {};
	for (let element of array)
	{
		let key = calculateKey(element);
		(grouped[key] = grouped[key] || []).push(element);
	}
	if (sort == 0) return grouped;

	// sort by size
	let keys = Object.keys(grouped);
	keys.sort((k1, k2) => sort * (grouped[k1].length - grouped[k2].length));
	let result:Record<GroupKey, T[]> = {};
	for (let key of keys)
		result[key] = grouped[key];
	return result;
}

/* EOF */ }
