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
	Datastructure and functionality for the bulk import operation.
*/

export const enum ImportOperationRole
{
	Ignore = 0,
	ID,
	Text,
	AppendText,
	Assignment,
	Literal,
	DeleteAssignment,
	DeleteLiteral,
	SplitColumn
}

export interface ImportOperationColumn
{
	name:string;
	role:ImportOperationRole;
	destination?:any; // one of: UniqueIDSource or {name:} or {name:,uri:}
	mappedPropURI?:string; // previously associated assignment...
	mappedGroupNest?:string[]; // ... reuse as necessary
}

export interface ImportOperationCell
{
	value:string;
	destination?:any;
}

interface ImportOperationRoster
{
	assay:AssayDefinition;
	comparedAssay:boolean;
}

export class ImportOperation
{
	public lines:string[] = null; // raw incoming content
	public firstRowTitles = true;
	public columnSep:string = null;
	public escapeChar:string = null;
	public rawMatrix:string[][] = null;
	public columns:ImportOperationColumn[] = [];
	public matrix:ImportOperationCell[][] = []; // [row][col]

	private importRoster:ImportOperationRoster[] = [];

	private assayCache = new Map<string, AssayDefinition>(); // uniqueID-to-cached assays to display existing annotations

	constructor(private cacheAssnList:Record<string, SchemaValue[]>,
				private updateMatrix:() => void,
				private changedColumn:(colidx:number) => void,
				private getTemplate:() => TemplateSummary,
				private changeTemplate:(schemaURI:string) => boolean)
	{
	}

	// takes the incoming text (from whatever source) and tries to turn it into a meaningful regular table; fails if that's not possible
	// for any reason; will try to guess the basic parameters, like separator character & escaping method
	public interpretText(txt:string):boolean
	{
		if (!txt) return false;

		// first see if the text is JSON-formatted
		if (txt.charAt(0) == '{' || txt.charAt(0) == '[')
		{
			try
			{
				let json = JSON.parse(txt);
				if (Array.isArray(json)) return this.parseJSONArray(json);
				if (typeof json == 'object') return this.parseJSONObject(json);
			}
			catch (ex) {}
		}

		// proceed under the assumption of tabular
		let lines = txt.split(/\r?\n/);
		if (lines.length == 1) lines = txt.split(/\r/);
		for (let n = lines.length - 1; n >= 0; n--)
		{
			lines[n] = lines[n].replace(/\s*$/, ''); // any extra gunk on the end has to go
			if (!lines[n]) lines.splice(n, 1); // delete blanks
		}
		if (lines.length < 1) return false;

		let raw:string[][] = null, ncols = 0, useSep = '', useEsc = '';
		for (let relaxCols of [false, true]) for (let trySep of ['\t', ',', ';']) for (let tryEsc of [null, "'", '"'])
		{
			let [tryRaw, tryCols] = this.parseRaw(lines, trySep, tryEsc, relaxCols);
			if (tryRaw && (!raw || tryCols > ncols)) [raw, ncols, useSep, useEsc] = [tryRaw, tryCols, trySep, tryEsc];
		}
		if (!raw || ncols <= 1) return false;

		for (let cols of raw) while (cols.length < ncols) cols.push('');

		this.lines = lines; // the slightly processed version
		this.columnSep = useSep;
		this.escapeChar = useEsc;

		// simple method: if there's any column for which all values are "ID like" except the first one, then default to title
		this.firstRowTitles = false;

		let regex1 = /^\d+$/, regex2 = /^[A-Za-z]+\d+$/; // two common ID number patterns
		let looksLikeID = (str:string):boolean => regex1.test(str) || regex2.test(str);

		outer: for (let c = 0; c < ncols; c++)
		{
			if (looksLikeID(raw[0][c])) continue; // column name shouldn't be ID-like
			for (let r = 1; r < raw.length; r++) if (!looksLikeID(orBlank(raw[r][c]))) continue outer;
			this.firstRowTitles = true;
			break;
		}

		this.rawMatrix = raw;

		return true;
	}

	// recreates the header columns & matrix content based on the available information (can overwrite the secondary settings)
	public rebuildMatrix():void
	{
		let raw = this.rawMatrix;
		let nrows = raw.length, ncols = raw[0].length;

		this.columns = [];
		for (let n = 0; n < ncols; n++)
		{
			let name = this.firstRowTitles ? raw[0][n] : 'Column ' + (n + 1);
			this.columns.push({'name': name, 'role': ImportOperationRole.Ignore});
		}

		this.matrix = [];
		for (let r = this.firstRowTitles ? 1 : 0; r < nrows; r++)
		{
			let row:ImportOperationCell[] = [];
			for (let c = 0; c < ncols; c++) row.push({'value': raw[r][c]});
			this.matrix.push(row);
		}
	}

	// consults the webservice to see if any column names have been mapped previously
	public autoMapColumns():void
	{
		let colidx:number[] = [], keywordList:string[] = [];
		for (let n = 0; n < this.columns.length; n++) if (!this.columns[n].destination)
		{
			colidx.push(n);
			keywordList.push(this.columns[n].name);
		}
		if (colidx.length == 0) return;

		let params =
		{
			'schemaURI': this.getTemplate().schemaURI,
			'keywordList': keywordList
		};
		callREST('REST/AutomapKeywords', params,
			(result:any) =>
			{
				let assignments:any[] = result.assignments;
				for (let n = 0; n < colidx.length; n++) if (assignments[n])
				{
					let col = this.columns[colidx[n]];
					col.mappedPropURI = assignments[n].propURI;
					col.mappedGroupNest = assignments[n].groupNest;
				}
			});
	}

	// a column has just been mapped to an assignment, where because it's a URI that corresponds to the schema tree, or because the
	// auto-mapping keywords are able to dredge something out from a prevous assignment
	public autoAssignWhenPossible(colidx:number, assn:SchemaAssignment):void
	{
		let anyURI = false;
		for (let n = 0; n < this.matrix.length; n++)
		{
			let cell = this.matrix[n][colidx];
			if (cell.destination || !cell.value) continue;
			if (cell.value.startsWith('http://') || expandPrefix(cell.value).startsWith('http://'))
			{
				anyURI = true;
				break;
			}
		}

		if (anyURI)
		{
			let schema = this.getTemplate();
			let key = schema.schemaURI + '::' + keyPropGroup(assn.propURI, assn.groupNest);
			let values = this.cacheAssnList[key];
			if (!values)
			{
				let params = {'schemaURI': schema.schemaURI, 'propURI': assn.propURI, 'groupNest': assn.groupNest};
				callREST('REST/GetPropertyList', params, (result:any) =>
				{
					this.cacheAssnList[key] = result.list;
					this.performAutoAssignURI(colidx, result.list);
					this.requestAutoAssignMapping(colidx);
				});
			}
			else
			{
				this.performAutoAssignURI(colidx, values);
				this.requestAutoAssignMapping(colidx);
			}
		}
		else this.requestAutoAssignMapping(colidx);
	}

	// given the title of a column, see if any of the available assignments are a good match, and return the best
	public guessAssignmentBestMatch(col:ImportOperationColumn, assnList:SchemaAssignment[]):SchemaAssignment
	{
		if (!col) return null;

		let bestScore = 5; // set the "no result" score: 5 permutations is relatively strict
		let bestAssn:SchemaAssignment = null;

		let txt = col.name.toLowerCase();
		for (let assn of assnList)
		{
			if (samePropGroupNest(col.mappedPropURI, col.mappedGroupNest, assn.propURI, assn.groupNest)) return assn;
			let score = calibratedSimilarity(txt, assn.name.toLowerCase());
			if (score < bestScore) [bestScore, bestAssn] = [score, assn];
		}
		return bestAssn;
	}

	// given an incoming literal to be mapped an assignment, see which one is most similar
	public guessValueBestMatch(txt:string, assn:SchemaAssignment, callback:(value:SchemaValue) => void):void
	{
		// ensure that the list of values is
		let schema = this.getTemplate();
		let key = schema.schemaURI + '::' + keyPropGroup(assn.propURI, assn.groupNest);
		let values = this.cacheAssnList[key];
		if (!values)
		{
			let params = {'schemaURI': schema.schemaURI, 'propURI': assn.propURI, 'groupNest': assn.groupNest};
			callREST('REST/GetPropertyList', params, (result:any) =>
			{
				if (!result.list)
				{
					callback(null);
					return;
				}
				this.cacheAssnList[key] = result.list;
				this.guessValueBestMatch(txt, assn, callback);
			});
			return;
		}

		// see which one matches best
		let bestScore = 10; // set the "no result" score of 10 permutations, i.e. worse than that doesn't count
		let bestValue:SchemaValue = null;

		for (let value of values)
		{
			let score = calibratedSimilarity(txt, value.name);
			if (score < bestScore) [bestScore, bestValue] = [score, value];
			if (value.altLabels) for (let label of value.altLabels)
			{
				score = calibratedSimilarity(txt, label) + 0.5; // add a fudge because we prefer raw labels
				if (score < bestScore) [bestScore, bestValue] = [score, value];
			}
		}
		callback(bestValue);
	}

	// obtaining existing assays from the cache: either return null if not loaded, or provide a callback and wait; note that
	// if the uniqueID isn't in there, the result will be empty: check to see if assayID is null
	public getCachedAssay(uniqueID:string):AssayDefinition
	{
		return this.assayCache.get(uniqueID);
	}
	public obtainCachedAssay(uniqueID:string, callback:(assay:AssayDefinition) => void):void
	{
		let assay = this.assayCache.get(uniqueID);
		if (assay) {callback(assay); return;}

		let params = {'uniqueID': uniqueID, 'countCompounds': false, 'blankAbsent': true};
		callREST('REST/GetAssay', params,
			(assay:AssayDefinition) =>
			{
				this.assayCache.set(uniqueID, assay);
				callback(assay);
			});
	}

	// see if there's a cached assay that corresponds to the index for a given row, if any
	public assayForRow(rowidx:number):AssayDefinition
	{
		let colidx = this.columns.findIndex((col) => col.role == ImportOperationRole.ID);
		if (colidx < 0) return null;
		let src:UniqueIDSource = this.columns[colidx].destination;
		let value = this.matrix[rowidx][colidx].value;
		let uniqueID = src ? src.prefix + value : value;
		return this.getCachedAssay(uniqueID);
	}

	// checks to see if the row already has an annotation, typically for the purposes of not re-asserting the instance
	public hasAnnotationAlready(rowidx:number, annot:AssayAnnotation):boolean
	{
		let assay = this.assayForRow(rowidx);
		if (!assay || !assay.annotations) return;
		return !!assay.annotations.find((look) =>
		{
			if (!samePropGroupNest(look.propURI, look.groupNest, annot.propURI, annot.groupNest)) return false;
			if (annot.valueURI)
				return annot.valueURI == look.valueURI;
			else
				return !look.valueURI && annot.valueLabel == look.valueLabel;
		});
	}

	// ------------ private methods ------------

	// take an array of lines, and considers the hypothesis that a particular separator/escape code can be used to split these into
	// a nicely formed matrix; returns valid results if and only if the columns are uniform
	private parseRaw(lines:string[], sep:string, esc:string, relaxCols:boolean):[string[][], number]
	{
		let raw:string[][] = [], ncols = 0;

		for (let line of lines)
		{
			let cols:string[] = esc ? this.splitWithEscape(line, sep, esc) : line.split(sep);

			if (relaxCols) ncols = Math.max(ncols, cols.length);
			else if (cols == null || (ncols > 0 && ncols != cols.length)) return [null, null]; // not happening

			raw.push(cols);
			ncols = cols.length;
		}

		return [raw, ncols];
	}

	// splits up a line according to the given separator, with respect for escape codes; can return null if overtly broken; note that when
	// escape codes are requested, they are considered optional
	private splitWithEscape(line:string, sep:string, esc:string):string[]
	{
		let cols:string[] = [], current = '', isEscaped = false;
		for (let n = 0; n < line.length; n++)
		{
			let ch = line.charAt(n);
			if (!isEscaped && ch == sep) {cols.push(current); current = '';}
			else if (ch == esc)
			{
				if (isEscaped && n < line.length - 1 && line.charAt(n + 1) != sep) return null; // can't do this in the middle of a value
				isEscaped = !isEscaped;
				current += ch;
			}
			else current += ch;
		}
		cols.push(current);

		if (esc) for (let n = 0; n < cols.length; n++) if (cols[n].startsWith(esc))
		{
			if (cols[n].length <= 1 || !cols[n].endsWith(esc)) return null; // nope!
			cols[n] = cols[n].substring(1, cols[n].length - 1);
		}

		return cols;
	}

	// take a single JSON-defined assay and convert it into the column-based datastructure; will return true if it seems legit, but there
	// are lots of server calls that will be executed subsequently
	private parseJSONObject(assay:AssayDefinition):boolean
	{
		if (!assay.uniqueID && Vec.arrayLength(assay.annotations) == 0 && !assay.text) return false; // nothing we can use
		return this.parseJSONArray([assay]);
	}

	// take multiple JSON-defined assay and convert it into the column-based datastructure; returns true quickly if it seems legit, while
	// racking up a series of server call requests that are needed to process the content
	private parseJSONArray(assayList:AssayDefinition[]):boolean
	{
		let anything = false;
		for (let assay of assayList) if (assay.uniqueID || Vec.arrayLength(assay.annotations) > 0 && assay.text) {anything = true; break;}
		if (!anything) return false;

		// change template to the first valid instance
		for (let assay of assayList) if (assay.schemaURI && this.changeTemplate(assay.schemaURI)) break;

		this.lines = null;
		this.rawMatrix = null;
		this.columns = [];
		this.matrix = [];

		this.importRoster = [];
		for (let assay of assayList)
		{
			if (assay.annotations == null) assay.annotations = [];
			this.importRoster.push({'assay': assay, 'comparedAssay': false});
		}
		this.processNextRoster();

		return true;
	}

	// process one unit of procedural action for a "rostered" assay: if there is enough content available on the server, the page will
	// be updated right away; otherwise it will make a server call and then come back for another round
	private processNextRoster():void
	{
		if (this.importRoster.length == 0) return;
		let roster = this.importRoster[0], assay = roster.assay;

		// make sure we have a full template description available
		let schemaURI = assay.schemaURI ? assay.schemaURI : this.getTemplate().schemaURI;
		let schema = TemplateManager.getTemplate(schemaURI); // note: should switch to use TemplateManager.graftTemplates(..) at some point
		if (!schema)
		{
			TemplateManager.ensureTemplates([schemaURI], () =>
			{
				// if the requests schema failed to manifest itself in the cache, this is an error condition; in this case we can
				// quietly remove the assay from the roster
				if (!TemplateManager.getTemplate(schemaURI)) this.importRoster.shift();

				this.processNextRoster();
			});
			return;
		}

		// if there's a uniqueID field, lookup the existing assay and see how similar
		if (assay.uniqueID && !roster.comparedAssay)
		{
			let params = {'uniqueID': assay.uniqueID, 'countCompounds': false, 'blankAbsent': true};
			callREST('REST/GetAssay', params,
				(priorAssay:AssayDefinition) =>
				{
					if (priorAssay.uniqueID) this.makeAssayDelta(assay, priorAssay);
					roster.comparedAssay = true;
					this.processNextRoster();
				});
			return;
		}

		let deleteAnnotations:AssayAnnotation[] = Vec.safeArray((assay as any).deleteAnnotations);

		// go through all annotations and make sure there's a cached property list for each one of them
		for (let annot of Vec.concat(assay.annotations, deleteAnnotations))
		{
			let key = schema.schemaURI + '::' + keyPropGroup(annot.propURI, annot.groupNest);
			if (!this.cacheAssnList[key])
			{
				let params = {'schemaURI': schema.schemaURI, 'propURI': annot.propURI, 'groupNest': annot.groupNest};
				callREST('REST/GetPropertyList', params,
					(result:any) =>
					{
						this.cacheAssnList[key] = result.list;
						this.processNextRoster();
					},
					() =>
					{
						this.cacheAssnList[key] = []; // error: fail silently
						this.processNextRoster();
					});
				return;
			}
		}

		let mtxrow:ImportOperationCell[] = Vec.anyArray(null, this.columns.length);
		this.matrix.push(mtxrow);

		let appendColumn = (col:ImportOperationColumn):number =>
		{
			this.columns.push(col);
			for (let row of this.matrix) row.push({'value': null});
			return this.columns.length - 1;
		};

		// if there's a valid uniqueID, make sure it is represented
		if (assay.uniqueID)
		{
			let [src, id] = UniqueIdentifier.parseKey(assay.uniqueID);
			if (src && id)
			{
				let colidx = this.columns.findIndex((col) => col.role == ImportOperationRole.ID);
				if (colidx < 0) colidx = appendColumn({'name': 'ID', 'role': ImportOperationRole.ID, 'destination': src});
				mtxrow[colidx] = {'value': id};
			}
		}

		// if there's text, get it in there
		if (assay.text)
		{
			let colidx = this.columns.findIndex((col) => col.role == ImportOperationRole.Text);
			if (colidx < 0) colidx = appendColumn({'name': 'Text', 'role': ImportOperationRole.Text});
			mtxrow[colidx] = {'value': assay.text};
		}

		let prepareAnnotation = (annot:AssayAnnotation, role:ImportOperationRole):number =>
		{
			// find a column that matches the assignment & type, and honour duplication index
			let assnKey = keyPropGroup(annot.propURI, annot.groupNest) + '::' + role, assnCount = assnIdx[assnKey];
			assnIdx[assnKey] = assnCount = assnCount == null ? 1 : assnCount + 1;
			let colidx = -1;
			for (let n = 0, count = 0; n < this.columns.length; n++)
			{
				let col = this.columns[n];
				if (col.role != role) continue;
				let assn:SchemaAssignment = col.destination;
				if (!samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, annot.groupNest)) continue;
				count++;
				if (count == assnCount) {colidx = n; break;}
			}
			if (colidx < 0)
			{
				let assn = {'propURI': annot.propURI, 'groupNest': annot.groupNest, 'name': '?'};
				for (let look of schema.assignments)
					if (samePropGroupNest(assn.propURI, assn.groupNest, look.propURI, look.groupNest)) {assn = look; break;}
				colidx = appendColumn({'name': assn.name, 'role': role, 'destination': assn});
			}
			return colidx;
		};

		// separate loops for adding/deleting assignments & literals

		let assnIdx:Record<string, number> = {};
		for (let annot of assay.annotations) if (annot.valueURI)
		{
			let colidx = prepareAnnotation(annot, ImportOperationRole.Assignment);
			let label = collapsePrefix(annot.valueURI);
			for (let value of this.cacheAssnList[schema.schemaURI + '::' + keyPropGroup(annot.propURI, annot.groupNest)])
				if (value.uri == annot.valueURI) {label = value.name; break;}

			mtxrow[colidx] = {'value': label, 'destination': {'name': label, 'uri': annot.valueURI}};
		}

		assnIdx = {};
		for (let annot of assay.annotations) if (!annot.valueURI)
		{
			let colidx = prepareAnnotation(annot, ImportOperationRole.Literal);
			mtxrow[colidx] = {'value': annot.valueLabel, 'destination': {'name': annot.valueLabel}};
		}

		assnIdx = {};
		for (let annot of deleteAnnotations) if (annot.valueURI)
		{
			let colidx = prepareAnnotation(annot, ImportOperationRole.DeleteAssignment);
			let label = collapsePrefix(annot.valueURI);
			for (let value of this.cacheAssnList[schema.schemaURI + '::' + keyPropGroup(annot.propURI, annot.groupNest)])
				if (value.uri == annot.valueURI) {label = value.name; break;}

			mtxrow[colidx] = {'value': label, 'destination': {'name': label, 'uri': annot.valueURI}};
		}

		assnIdx = {};
		for (let annot of deleteAnnotations) if (!annot.valueURI)
		{
			let colidx = prepareAnnotation(annot, ImportOperationRole.DeleteLiteral);
			mtxrow[colidx] = {'value': annot.valueLabel, 'destination': {'name': annot.valueLabel}};
		}

		// it's ready, so can render the row and move on to the next one

		for (let n = 0; n < mtxrow.length; n++) if (!mtxrow[n]) mtxrow[n] = {'value': null};

		this.updateMatrix();

		this.importRoster.shift();
		setTimeout(() => this.processNextRoster(), 1);
	}

	// converts the assay parameter into a "delta" relative to the assay that's already present; will also manufacture an
	// extension field for annotations that are to be deleted rather than added
	private makeAssayDelta(assay:AssayDefinition, priorAssay:AssayDefinition):void
	{
		let makeKey = (annot:AssayAnnotation):string =>
		{
			let base = keyPropGroup(annot.propURI, annot.groupNest);
			return base + (annot.valueURI ? '::' + annot.valueURI : '**' + annot.valueLabel);
		};
		let keysNew = new Set<string>(), keysOld = new Set<string>();
		for (let annot of assay.annotations) keysNew.add(makeKey(annot));
		for (let annot of priorAssay.annotations) keysOld.add(makeKey(annot));

		// any annotations that are already in the old list can be deleted
		for (let n = assay.annotations.length - 1; n >= 0; n--)
			if (keysOld.has(makeKey(assay.annotations[n]))) assay.annotations.splice(n, 1);

		// any annotations in the old list but not the new are requesting a deletion
		let deleteAnnotations:AssayAnnotation[] = [];
		for (let annot of priorAssay.annotations)
			if (!keysNew.has(makeKey(annot))) deleteAnnotations.push(annot);
		(assay as any).deleteAnnotations = deleteAnnotations;

		// text: keep only if different
		if (assay.text == priorAssay.text) assay.text = null;
	}

	// do the lookup, from URI auto-assign
	private performAutoAssignURI(colidx:number, termList:any[]):void
	{
		let uriToName:Record<string, string> = {};
		for (let term of termList) uriToName[term.uri] = term.name;

		let anything = false;
		for (let n = 0; n < this.matrix.length; n++)
		{
			let cell = this.matrix[n][colidx];
			if (cell.destination || !cell.value) continue;
			let uri = expandPrefix(cell.value);
			let name = uriToName[uri];
			if (name)
			{
				cell.destination = {'uri': uri, 'name': name};
				anything = true;
			}
		}

		if (anything) this.changedColumn(colidx);
	}

	// see if there are any mappings that can be auto-assigned
	private requestAutoAssignMapping(colidx:number):void
	{
		// see which keywords have yet to be assigned
		let keywordList:string[] = [];
		for (let n = 0; n < this.matrix.length; n++)
		{
			let cell = this.matrix[n][colidx];
			if (cell.destination == null && cell.value && keywordList.indexOf(cell.value) < 0) keywordList.push(cell.value);
		}
		if (keywordList.length == 0) return;

		// fetch the options, if any
		let assn:SchemaAssignment = this.columns[colidx].destination;
		let params =
		{
			'schemaURI': this.getTemplate().schemaURI,
			'propURI': assn.propURI,
			'groupNest': assn.groupNest,
			'keywordList': keywordList
		};
		callREST('REST/AutomapKeywords', params,
			(result:any) =>
			{
				let values:any[] = result.values;
				let keyToURI:Record<string, string> = {}, keyToLabel:Record<string, string> = {};
				for (let n = 0; n < keywordList.length; n++) if (values[n])
				{
					keyToURI[keywordList[n]] = values[n].valueURI;
					keyToLabel[keywordList[n]] = values[n].valueLabel;
				}
				if (!$.isEmptyObject(keyToURI)) this.performAutoAssignMapping(colidx, keyToURI, keyToLabel);
			});
	}

	// when at least one mapping is available, apply it to the data
	private performAutoAssignMapping(colidx:number, keyToURI:Record<string, string>, keyToLabel:Record<string, string>):void
	{
		for (let n = 0; n < this.matrix.length; n++)
		{
			let cell = this.matrix[n][colidx], col = this.columns[colidx];
			if (cell.destination != null) continue;
			let uri = keyToURI[cell.value], label = keyToLabel[cell.value];
			if (!uri) continue;
			let annot:AssayAnnotation =
			{
				'valueURI': uri,
				'propURI': col.destination.propURI,
				'groupNest': col.destination.groupNest
			};
			if (!this.hasAnnotationAlready(n, annot)) cell.destination = {'uri': uri, 'name': label ? label : cell.value};
		}
		this.changedColumn(colidx);
	}
}

/* EOF */ }
