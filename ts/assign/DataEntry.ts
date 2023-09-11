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

///<reference path='DataEntryBox.ts'/>

namespace BioAssayExpress /* BOF */ {

/*
	Base class for data entry: provides the ability to create boxes that correspond to contemporary annotations
	within an assay template, and edit them. Should be subclassed in order to provide the actual layout.
*/

export class DataEntry extends DataEntryBoxDelegate
{
	protected boxes:DataEntryBox[] = [];
	protected currentOpen:DataEntryBox = null;

	protected easterEggs:AssayAnnotation[] = [];
	protected easterPropKeys = new Set<string>();
	protected easterValueKeys = new Set<string>();

	protected clonedPropKeys = new Set<string>();
	protected clonedValueKeys = new Set<string>();
	protected clonedLabelKeys = new Set<string>();

	protected cacheAxiomJustif:Record<string, AssaySuggestion> = {}; // key:prop/value/group
	protected cacheAxiomViol:Record<string, AssaySuggestion> = {}; // key:prop/value/group
	protected cacheAxiomAddit:Record<string, AssaySuggestion[]> = {}; // key:prop/group

	constructor(schema:SchemaSummary, assay:AssayDefinition, delegate:AssignmentDelegate)
	{
		super(schema, assay, delegate);

		this.callbackClose = this.actionClose.bind(this);
		this.callbackOpen = this.actionOpen.bind(this);
		this.callbackAcceptAnnot = this.acceptAnnot.bind(this);
		this.callbackRejectAnnot = this.rejectAnnot.bind(this);
		this.callbackAppendLiteral = this.appendLiteralValue.bind(this);
		this.callbackCycleSelectedBox = this.cycleSelectedBox.bind(this);
	}

	// access to current state
	public getAssay():AssayDefinition {return this.assay;}
	public setAssay(assay:AssayDefinition):void
	{
		this.assay = assay;
		this.fillAssignments();
	}

	public fillAssignments():void
	{
		this.updateAxiomEffects();
		for (let box of this.boxes) this.fillContent(box);
		this.delegate.actionRebuiltAssignments();
	}
	public fillOneAssignment(idx:number):void
	{
		let box = this.findBox(idx);
		if (box) this.fillContent(box);
	}
	public clearSelection():void
	{
		if (this.currentOpen != null) this.currentOpen.setOpen(false);
		this.currentOpen = null;
	}

	// if there is a box that's currently open, make it not so
	public closeCurrentBox():void
	{
		if (this.currentOpen) this.actionClose(this.currentOpen);
	}

	// global cache of identifiers changed, so update everything that needs to be
	public updateIdentifiers():void
	{
		for (let n = 0; n < this.schema.assignments.length; n++)
			if (this.schema.assignments[n].suggestions == SuggestionType.ID) this.fillOneAssignment(n);
	}

	// change edit mode status, which likely involves a refresh of the screen display
	public updateEditMode():void
	{
		this.fillAssignments();
	}

	// define the "easter eggs", which are used to highlight particular suggestions as being known correct
	public setEasterEggs(eggs:AssayAnnotation[]):void
	{
		this.easterEggs = eggs;
		this.easterPropKeys.clear();
		this.easterValueKeys.clear();
		for (let annot of eggs)
		{
			this.easterPropKeys.add(keyPropGroup(annot.propURI, annot.groupNest));
			this.easterValueKeys.add(keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI));
		}
	}

	// define the "cloned terms", which are treated similarly to suggestions, except that they go to the top of the list because they're
	// from a previous assay that's probably mostly the same
	public updateClonedTerms():void
	{
		this.clonedPropKeys.clear();
		this.clonedValueKeys.clear();
		this.clonedLabelKeys.clear();
		for (let annot of this.delegate.clonedTerms)
		{
			this.clonedPropKeys.add(keyPropGroup(annot.propURI, annot.groupNest));
			if (annot.valueURI)
				this.clonedValueKeys.add(keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI));
			else
				this.clonedLabelKeys.add(keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueLabel));
		}
	}

	// commit the given annotation, taking care of whatever consequences are appropriate
	public acceptAnnot(annot:AssayAnnotation, shouldPredict:boolean):void
	{
		let removeAnnotation = (list:AssayAnnotation[]):void =>
		{
			if (list) for (let n = list.length - 1; n >= 0; n--)
			{
				let a = list[n];
				if (samePropGroupNest(annot.propURI, annot.groupNest, a.propURI, a.groupNest) &&
					annot.valueURI == a.valueURI) list.splice(n, 1);
			}
		};
		removeAnnotation(this.delegate.extractions);
		removeAnnotation(this.delegate.suggestions);
		removeAnnotation(this.delegate.clonedTerms);

		for (let existing of this.assay.annotations)
		{
			if (samePropGroupNest(existing.propURI, existing.groupNest, annot.propURI, annot.groupNest) &&
				 existing.valueLabel == annot.valueLabel)
			{
				// return immediately as annotation already exists
				return;
			}
		}

		this.delegate.actionStashForUndo();

		// if this is an absence term, start by zapping any other absence terms in the same assignment
		if (annot.valueURI && ALL_ABSENCE_TERMS.includes(annot.valueURI))
		{
			this.assay.annotations = this.assay.annotations.filter((look) =>
				!samePropGroupNest(annot.propURI, annot.groupNest, look.propURI, look.groupNest) ||
											!ALL_ABSENCE_TERMS.includes(look.valueURI));
		}

		// add and cleanup
		this.assay.annotations.push(annot);
		this.assay.annotations = PageAssignment.cleanupAnnotations(this.assay.annotations);

		this.fillAssignments();
		if (shouldPredict) this.delegate.actionPredictAnnotations();
		this.delegate.keyword.changeAnnotations(this.assay.annotations);
	}

	public rejectAnnot(annot:AssayAnnotation, shouldPredict:boolean):void
	{
		for (let n = this.delegate.extractions.length - 1; n >= 0; n--)
		{
			let p = this.delegate.extractions[n];
			if (samePropGroupNest(annot.propURI, annot.groupNest, p.propURI, p.groupNest) && annot.valueURI == p.valueURI)
				this.delegate.extractions.splice(n, 1);
		}
		for (let n = this.delegate.suggestions.length - 1; n >= 0; n--)
		{
			let p = this.delegate.suggestions[n];
			if (samePropGroupNest(annot.propURI, annot.groupNest, p.propURI, p.groupNest) && annot.valueURI == p.valueURI)
				this.delegate.suggestions.splice(n, 1);
		}

		this.delegate.actionStashForUndo();

		for (let n = 0; n < this.assay.annotations.length; n++)
		{
			let existing = this.assay.annotations[n];
			if (samePropGroupNest(existing.propURI, existing.groupNest, annot.propURI, annot.groupNest) &&
				existing.valueLabel == annot.valueLabel)
			{
				this.assay.annotations.splice(n, 1);
				break;
			}
		}

		this.fillAssignments();
		if (shouldPredict) this.delegate.actionPredictAnnotations();
		this.delegate.keyword.changeAnnotations(this.assay.annotations);
	}

	// fetch the list of assignments that are fair game for setting to not-applicable in one big batch
	public getNotApplicableAssnList():SchemaAssignment[]
	{
		let usedAssn = new Set<string>();
		for (let annot of this.assay.annotations) usedAssn.add(keyPropGroup(annot.propURI, annot.groupNest));

		let assnList:SchemaAssignment[] = [];
		for (let box of this.boxes) if (!usedAssn.has(keyPropGroup(box.assn.propURI, box.assn.groupNest))) assnList.push(box.assn);
		return assnList;
	}

	public getAssnList():SchemaAssignment[]
	{
		return this.boxes.map((box) => box.assn);
	}

	// ------------ private/protected methods ------------

	// decides what content goes into a box, and updates it as necessary
	protected fillContent(box:DataEntryBox):void
	{
		let annotations = this.obtainAnnotations(box.assn);
		let suggest = box.assn.suggestions;
		let lines:DataEntryContent[] = [];
		if (suggest == SuggestionType.Field) lines = this.fillFieldAssignment(box, annotations);
		else if (suggest == SuggestionType.ID) lines = this.fillIDAssignment(box, annotations);
		else if (suggest == SuggestionType.URL) lines = this.fillLiteralAssignment(box, annotations); // (doubling up with literal)
		else if (suggest == SuggestionType.String || suggest == SuggestionType.Number || suggest == SuggestionType.Integer ||
				 suggest == SuggestionType.Date) lines = this.fillLiteralAssignment(box, annotations);
		else lines = this.fillTermAssignment(box, annotations);

		box.fill(lines);
	}

	private fillTermAssignment(box:DataEntryBox, annotations:AssayAnnotation[]):DataEntryContent[]
	{
		let lines:DataEntryContent[] = this.createExistingTerms(box, annotations);

		const {assn} = box;

		let suggest:AssaySuggestion[] = [];

		// exclusion sets for already-asserted
		let seenAnnot = new Set<string>(), seenLabel = new Set<string>();
		for (let annot of annotations)
		{
			if (annot.valueURI)
				seenAnnot.add(keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI));
			else
				seenLabel.add(keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueLabel));
		}

		// terms that were carried over from a "cloning" operation get top billing
		if (this.clonedPropKeys.has(keyPropGroup(assn.propURI, assn.groupNest))) for (let t of this.delegate.clonedTerms)
		{
			if (!samePropGroupNest(t.propURI, t.groupNest, assn.propURI, assn.groupNest)) continue;

			if (t.valueURI)
			{
				let key = keyPropGroupValue(t.propURI, t.groupNest, t.valueURI);
				if (seenAnnot.has(key)) continue;
				seenAnnot.add(key);
			}
			else
			{
				let key = keyPropGroupValue(t.propURI, t.groupNest, t.valueLabel);
				if (seenLabel.has(key)) continue;
				seenLabel.add(key);
			}

			let p = deepClone(t) as AssaySuggestion;
			p.combined = Number.MAX_VALUE;
			p.type = AssaySuggestionType.Cloned;
			suggest.push(p);
		}

		// terms that were extracted from the text are considered somewhat authoritative
		if (this.delegate.extractions) for (let x of this.delegate.extractions)
		{
			if (!samePropGroupNest(x.propURI, x.groupNest, assn.propURI, assn.groupNest)) continue;

			let key = keyPropGroupValue(x.propURI, x.groupNest, x.valueURI);
			if (seenAnnot.has(key)) continue;
			seenAnnot.add(key);

			let p = deepClone(x) as AssayTextExtraction;
			p.combined = Number.MAX_VALUE - 1;
			p.type = AssaySuggestionType.Mined;
			suggest.push(p);
		}

		// suggestions from probabilistic models are last
		if (this.delegate.suggestions) for (let p of this.delegate.suggestions)
		{
			if (!samePropGroupNest(p.propURI, p.groupNest, assn.propURI, assn.groupNest)) continue;
			if (this.clonedValueKeys.has(keyPropGroupValue(assn.propURI, assn.groupNest, p.valueURI))) continue;

			let key = keyPropGroupValue(p.propURI, p.groupNest, p.valueURI);
			if (seenAnnot.has(key)) continue;
			seenAnnot.add(key);

			if (p.combined == null || p.combined < 0) p.combined = -1;
			p.type = AssaySuggestionType.Guessed;
			suggest.push(p);
		}

		// sort the suggestions, then convert them into lines
		suggest.sort((p1:AssaySuggestion, p2:AssaySuggestion):number => p2.combined - p1.combined);

		let groupNest = assn.groupNest;
		let blankEaster = this.easterEggs.length > 0 && !this.easterPropKeys.has(keyPropGroup(assn.propURI, groupNest));
		for (let sugg of suggest)
		{
			let line:DataEntryContent = {'type': null, 'annot': sugg};

			let uri = sugg.valueURI;
			let key = keyPropGroupValue(sugg.propURI, sugg.groupNest, uri);
			if (this.easterValueKeys.has(key)) line.isEasterEgg = true; else line.isBlankEaster = blankEaster;

			if (suggest.length == 1 && suggest[0].axiomFiltered)
			{
				line.type = DataEntryContentType.Implied;
				let justif = this.cacheAxiomJustif[keyPropGroupValue(assn.propURI, assn.groupNest, uri)];
				if (justif) line.axiomTriggers = justif.triggers;
			}
			else if (sugg.type == AssaySuggestionType.Cloned) line.type = DataEntryContentType.Cloned;
			else if (sugg.type == AssaySuggestionType.Mined) line.type = DataEntryContentType.TextMined;
			else if (sugg.combined == null) line.type = DataEntryContentType.Searchable;
			else
			{
				line.type = DataEntryContentType.Suggested;
				line.predScore = sugg.combined;
			}

			lines.push(line);
		}

		return lines;
	}
	private fillLiteralAssignment(box:DataEntryBox, annotations:AssayAnnotation[]):DataEntryContent[]
	{
		return [...this.createExistingTerms(box, annotations),
				...this.createClonedLiterals(box, annotations),
				...this.createAdditionalAxioms(box)];
	}
	private fillFieldAssignment(box:DataEntryBox, annotations:AssayAnnotation[]):DataEntryContent[]
	{
		const {assn} = box;

		let lines:DataEntryContent[] =
		[
			...this.createExistingTerms(box, annotations),
			...this.createClonedLiterals(box, annotations),
			...this.createAdditionalAxioms(box)
		];

		let gotColumns = new Set<string>();
		for (let line of lines) if (!line.annot.valueURI) gotColumns.add(line.annot.valueLabel);

		let otherColumns:MeasureColumn[] = [];
		let mapColumn:Record<string, MeasureColumn> = {};
		for (let column of this.delegate.dataColumns)
		{
			mapColumn[column.name] = column;
			if (gotColumns.has(column.name)) continue;
			otherColumns.push(column);
		}
		otherColumns.sort((c1:MeasureColumn, c2:MeasureColumn):number =>
		{
			let p1 = c1.type == MeasureData.TYPE_PRIMARY ? 0 : c1.type == MeasureData.TYPE_MEASUREMENT ? 1 : 2;
			let p2 = c2.type == MeasureData.TYPE_PRIMARY ? 0 : c2.type == MeasureData.TYPE_MEASUREMENT ? 1 : 2;
			if (p1 != p2) return p1 - p2;
			return c1.name.localeCompare(c2.name);
		});

		for (let column of otherColumns)
		{
			let annot:AssayAnnotation = {'propURI': assn.propURI, 'groupNest': assn.groupNest, 'valueLabel': column.name};
			let line:DataEntryContent = {'type': DataEntryContentType.Searchable, 'annot': annot};
			lines.push(line);
		}

		for (let line of lines) line.column = mapColumn[line.annot.valueLabel]; // null if not in accompanying SAR info

		return lines;
	}
	private fillIDAssignment(box:DataEntryBox, annotations:AssayAnnotation[]):DataEntryContent[]
	{
		return [...this.createExistingTerms(box, annotations),
				...this.createClonedLiterals(box, annotations),
				...this.createAdditionalAxioms(box)];
	}

	// common baseline for filling boxes: manufacture annotations that have been asserted already
	private createExistingTerms(box:DataEntryBox, annotations:AssayAnnotation[]):DataEntryContent[]
	{
		const {assn} = box;
		let lines:DataEntryContent[] = [];

		for (let annot of annotations)
		{
			let line:DataEntryContent = {'type': DataEntryContentType.Asserted, 'annot': annot};

			const uri = annot.valueURI, label = annot.valueLabel;
			if (uri)
			{
				let viol = this.cacheAxiomViol[keyPropGroupValue(assn.propURI, assn.groupNest, uri)];
				if (viol)
				{
					line.inViolation = true;
					line.axiomTriggers = viol.triggers;
				}
			}

			lines.push(line);
		}

		return lines;
	}

	// common baseline for literals copied over from original cloned source
	private createClonedLiterals(box:DataEntryBox, annotations:AssayAnnotation[]):DataEntryContent[]
	{
		const {assn} = box;
		let lines:DataEntryContent[] = [];

		for (let annot of this.delegate.clonedTerms)
			if (!annot.valueURI && samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest))
		{
			let line:DataEntryContent = {'type': DataEntryContentType.Cloned, 'annot': annot};
			lines.push(line);
		}

		return lines;
	}

	// manufacture content for "additional" axioms-implied terms, which are typically literals that don't get modelled
	private createAdditionalAxioms(box:DataEntryBox):DataEntryContent[]
	{
		const {assn} = box;
		let additional = this.cacheAxiomAddit[keyPropGroup(assn.propURI, assn.groupNest)];
		if (!additional) return [];

		let lines:DataEntryContent[] = [];
		for (let annot of additional)
		{
			let line:DataEntryContent = {'type': DataEntryContentType.Implied, 'annot': annot};
			line.axiomTriggers = annot.triggers;
			lines.push(line);
		}

		return lines;
	}

	// similar to adding text, but with some validation
	protected appendLiteralValue(box:DataEntryBox, text:string):boolean
	{
		// stash for undo
		if (this.delegate) this.delegate.actionStashForUndo();

		if (text == '') return;

		const assn = this.schema.assignments[box.idx];

		if (assn.suggestions == SuggestionType.Number)
		{
			if (!validNumberLiteral(text))
			{
				alert('Must enter a numeric value, using dots as the decimal separator (e.g. 10.5) or scientific notation (e.g. 1.05E-1).');
				return false;
			}
			text = standardizeNumberLiteral(text);
		}
		else if (assn.suggestions == SuggestionType.Integer)
		{
			if (!validIntegerLiteral(text))
			{
				alert('Number must be an integer.');
				return false;
			}
			text = standardizeNumberLiteral(text);
		}
		else if (assn.suggestions == SuggestionType.Date)
		{
			let match = /^(\d\d\d\d)[-/\.](\d?\d)[-/\.](\d?\d)$/.exec(text);
			if (match == null || parseInt(match[1]) < 1900 || parseInt(match[1]) > 2100 ||
								 parseInt(match[2]) < 1 || parseInt(match[2]) > 12 ||
								 parseInt(match[3]) < 1 || parseInt(match[3]) > 31)
			{
				alert('Enter dates in YYYY-MM-DD format.');
				return false;
			}
		}
		else if (assn.suggestions == SuggestionType.URL)
		{
			if (!text.startsWith('http://') && !text.startsWith('https://'))
			{
				alert('Must be a valid URL.');
				return false;
			}
		}

		let propLabel = '';
		for (let look of this.delegate.schema.assignments)
			if (samePropGroupNest(look.propURI, look.groupNest, assn.propURI, assn.groupNest)) propLabel = look.name;

		this.delegate.actionAppendCustomText(box.idx, text);

		this.fillOneAssignment(box.idx);
		this.delegate.actionRebuiltAssignments();

		return true;
	}

	// lookup by index
	protected findBox(idx:number):DataEntryBox
	{
		for (let box of this.boxes) if (box.idx == idx) return box;
		return null;
	}

	// assignment sections opening & closing
	protected actionClose(box:DataEntryBox):void
	{
		Popover.removeAllPopovers();
		if (box !== this.currentOpen) return;
		box.setOpen(false);
		this.currentOpen = null;
	}
	protected actionOpen(box:DataEntryBox):void
	{
		Popover.removeAllPopovers();
		if (box === this.currentOpen) return;
		if (this.currentOpen) this.currentOpen.setOpen(false);
		this.currentOpen = box;
		if (this.inputText) this.inputText.val('');
		box.setOpen(true);
		if (this.inputText) this.inputText.focus();
	}

	// for a given assignment, extract all of the annotations that apply to it
	protected obtainAnnotations(assn:SchemaAssignment):AssayAnnotation[]
	{
		let annotList:AssayAnnotation[] = [];
		for (let annot of this.assay.annotations)
			if (samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest)) annotList.push(annot);
		return annotList;
	}

	// tab key pressed: move forward or backward; uses box order as the determinant
	protected cycleSelectedBox(dir:number):void
	{
		let pos = -1;
		for (let n = 0; n < this.boxes.length; n++) if (this.boxes[n] === this.currentOpen) {pos = n; break;}
		if (dir < 0) pos = pos <= 0 ? this.boxes.length - 1 : pos - 1;
		else pos = (pos + 1) % this.boxes.length;

		let box = this.boxes[pos];
		if (box.assn)
		{
			this.actionOpen(box);
		}
	}

	// makes sure axiom justification/violation list can be fetched quickly
	private updateAxiomEffects():void
	{
		this.cacheAxiomJustif = {};
		for (let justif of this.delegate.axiomJustifications)
		{
			let key = keyPropGroupValue(justif.propURI, justif.groupNest, justif.valueURI);
			this.cacheAxiomJustif[key] = justif;
		}

		this.cacheAxiomViol = {};
		for (let viol of this.delegate.axiomViolations)
		{
			let key = keyPropGroupValue(viol.propURI, viol.groupNest, viol.valueURI);
			this.cacheAxiomViol[key] = viol;
		}

		this.cacheAxiomAddit = {};
		for (let annot of this.delegate.axiomAdditional)
		{
			let key = keyPropGroup(annot.propURI, annot.groupNest);
			let annotList = this.cacheAxiomAddit[key];
			if (annotList) annotList.push(annot); else this.cacheAxiomAddit[key] = [annot];
		}
	}
}

/* EOF */ }
