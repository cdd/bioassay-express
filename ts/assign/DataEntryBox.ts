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

///<reference path='../support/svcobj.ts'/>
///<reference path='../support/constants.ts'/>

namespace BioAssayExpress /* BOF */ {

/*
	Helper class for DataEntry, which contains state information about an individual box, which represents an
	editable assignment. A box can be readonly/editable, open/closed, have both asserted & suggested content,
	and be styled based on the assignment content type.
*/

export abstract class DataEntryBoxDelegate
{
	public callbackClose:(box:DataEntryBox) => void;
	public callbackOpen:(box:DataEntryBox) => void;
	public callbackAcceptAnnot:(annot:AssayAnnotation, shouldPredict:boolean) => void;
	public callbackRejectAnnot:(annot:AssayAnnotation, shouldPredict:boolean) => void;
	public callbackAppendLiteral:(box:DataEntryBox, text:string) => boolean;
	public callbackCycleSelectedBox:(dir:number) => void;

	public inputText:JQuery = null; // used to capture search queries and/or literal inputs
	public btnTextAccept:JQuery = null; // side button to action the input content

	constructor(public schema:SchemaSummary, public assay:AssayDefinition, public delegate:AssignmentDelegate)
	{
	}
}

const LITERAL_TYPES = [SuggestionType.String, SuggestionType.Number, SuggestionType.Integer,
					   SuggestionType.Date, SuggestionType.URL];

export const ABSENCE_DETAIL_MAP:Record<string, string[]> =
{
	[ABSENCE_NOTAPPLICABLE]:
		['not applicable',
		'This property does not apply to this assay.'],
	[ABSENCE_NOTDETERMINED]:
		['not determined',
		'The measurement was not made.'],
	[ABSENCE_UNKNOWN]:
		['unknown',
		'There should be a term assigned to this property but the value was not specified in the source information.'],
	[ABSENCE_AMBIGUOUS]:
		['ambiguous',
		'The source information is ambiguous: there are multiple indeterminate possibilities.'],
	[ABSENCE_MISSING]:
		['missing',
		'The information is missing from the source data, presumed to be a communication error.'],
	[ABSENCE_DUBIOUS]:
		['dubious',
		'The source information appears to be dubious, and has been omitted for this reason.'],
	[ABSENCE_REQUIRESTERM]:
		['requires term',
		'An appropriate term could not be found in the underlying ontologies: it may be necessary to create a new one.'],
	[ABSENCE_NEEDSCHECKING]:
		['needs checking',
		'These terms need to be checked by an expert.']
};

// assist for caching buttons by identifier keys, to preserve between refreshing sections
class ReusableButtons
{
	public focusedElement:Element = null; // element that was focused before emptying operation
	public oldAccept?:Record<string, JQuery> = {};
	public oldReject?:Record<string, JQuery> = {};
	public oldShow?:Record<string, JQuery> = {};
	public newAccept?:Record<string, JQuery> = {};
	public newReject?:Record<string, JQuery> = {};
	public newShow?:Record<string, JQuery> = {};

	public ensureAccept(key:string, html:string):JQuery
	{
		let btn = this.oldAccept[key];
		if (!btn) btn = $(html); else {btn.empty(); btn.off();}
		this.newAccept[key] = btn;
		return btn;
	}
	public ensureReject(key:string, html:string):JQuery
	{
		let btn = this.oldReject[key];
		if (!btn) btn = $(html); else {btn.empty(); btn.off();}
		this.newReject[key] = btn;
		return btn;
	}
	public ensureShow(key:string, html:string):JQuery
	{
		let btn = this.oldShow[key];
		if (!btn) btn = $(html); else {btn.empty(); btn.off();}
		this.newShow[key] = btn;
		return btn;
	}
}

export const enum DataEntryContentType
{
	Asserted = 0, // term is part of the assay
	Implied = 1, // manufactured by axioms
	Cloned = 2, // residual from cloning source
	TextMined = 3, // obtained from accompanying text
	Suggested = 4, // ranked probabilistic estimate
	Searchable = 5, // only show if explicitly searched for
}

export interface DataEntryContent
{
	type:DataEntryContentType;
	annot:AssayAnnotation;

	// optional details, some of which are type specific
	inViolation?:boolean; // true if it's a term that violates the axiom rules
	axiomTriggers?:string[]; // the term(s) that caused the axiom effect, if any (note: these are valueURIs; may need to extend...)
	isEasterEgg?:boolean; // special display mode for predicted terms
	isBlankEaster?:boolean; // ditto
	predScore?:number; // prediction score, if applicable
	column?:MeasureColumn; // when its a Field, column information is exposed
}
const DATAENTRYLINE_KEYS = ['type', 'annot', 'inViolation', 'axiomTriggers', 'isEasterEgg', 'predScore'];

export class DataEntryBox
{
	// these are defined when it's an assignment box
	public domProp:JQuery = null;
	public domCheck:JQuery = null;
	public domAssn:JQuery = null;
	public domButton:JQuery = null;

	// buttons: these are preserved in between empty/fill cycles
	public btnNotApplic:JQuery = null;
	public btnCaret:JQuery = null;
	public btnList:JQuery = null;
	public btnTree:JQuery = null;
	public btnText:JQuery = null;
	public btnThresh:JQuery = null;
	public reuseAccept:Record<string, JQuery> = null;
	public reuseReject:Record<string, JQuery> = null;
	public reuseShow:Record<string, JQuery> = null;
	private optionIndex = -1;
	private optionList:JQuery[] = []; // a list of <div> elements that can be highlighted with cursor keys
	private optionButtons:JQuery[] = []; // a corresponding 'accept' button for each of above

	private lines:DataEntryContent[] = []; // all available line content
	private isOpen = false;
	private shownLines:DataEntryContent[] = null; // the lines that are being rendered at the moment; null = first time
	private editMode = false; // whether or not it's in edit mode
	private searchFilter = ''; // note that the input widget is always blank at the start
	private searchLines:DataEntryContent[] = null; // extra content from the search query; URI assignment types only
	private watermarkSearch = 0;

	// ------------ public methods ------------

	constructor(public assn:SchemaAssignment, public idx:number, private delegate:AssignmentDelegate, private dataEntry:DataEntryBoxDelegate)
	{
	}

	// replaces the content of the box with the given set of lines; if the content is exactly the same as what was previously
	// there, nothing will happen; if the new content results in a different visual display, it will be redrawn
	public fill(lines:DataEntryContent[]):void
	{
		// figure out the subset of shown lines, and check if they are different
		let shownLines = this.selectShownLines(lines);
		let looksDifferent = true;
		if (this.editMode != this.delegate.editMode) {}
		else if (this.shownLines && shownLines.length == this.shownLines.length)
		{
			looksDifferent = false;
			outer: for (let n = 0; n < shownLines.length; n++)
			{
				let line1 = shownLines[n] as any, line2 = this.shownLines[n] as any;
				for (let key of DATAENTRYLINE_KEYS) if (JSON.stringify(line1[key]) != JSON.stringify(line2[key]))
				{
					looksDifferent = true;
					break outer;
				}
			}
		}
		this.lines = lines;
		this.shownLines = shownLines;
		this.editMode = this.delegate.editMode;

		if (looksDifferent) this.redraw();
		this.updateExtraButtons(); // (low cost)
	}

	// consider re-filling it, given that the lines haven't changed (e.g. when search filter has modified)
	public refill():void
	{
		this.fill(this.lines);
	}

	// sets whether the box is open or closed; when closed, the display of content is more truncated and the
	// auxiliary buttons are hidden; does nothing if the state is unchanged
	public setOpen(isOpen:boolean):void
	{
		if (this.isOpen == isOpen) return;
		this.isOpen = isOpen;
		this.editMode = this.delegate.editMode;
		this.searchFilter = '';
		this.shownLines = this.selectShownLines(this.lines);
		this.redraw();
		this.updateExtraButtons();
	}

	// move the option selection up or down
	public traverseOption(dir:number):void
	{
		let sz = this.optionList.length;
		if (sz == 0) return;

		if (this.optionIndex >= 0)
		{
			this.optionIndex += dir;
			if (this.optionIndex < -1) this.optionIndex = sz - 1;
			else if (this.optionIndex >= sz) this.optionIndex = 0;
		}
		else this.optionIndex = dir < 0 ? sz - 1 : 0;

		for (let n = 0; n < sz; n++)
		{
			let div = this.optionList[n];
			if (n == this.optionIndex) div.addClass('selectionBorder'); else div.removeClass('selectionBorder');
		}
	}

	// if there's a selection option, activate it
	public traverseAction():void
	{
		if (this.optionIndex >= 0) this.optionButtons[this.optionIndex].click();
	}

	// ------------ private methods ------------

	// considers creating extra button(s) alongside, if certain conditions are met
	private updateExtraButtons():void
	{
		let wantNotApplic = false;
		if (!this.isOpen && this.editMode)
		{
			wantNotApplic = true;
			for (let line of this.lines) if (line.type == DataEntryContentType.Asserted) {wantNotApplic = false; break;}
		}
		if (wantNotApplic)
		{
			if (!this.btnNotApplic)
			{
				this.btnNotApplic = $('<button class="btn btn-xs btn-normal"/>').appendTo(this.domButton);
				this.btnNotApplic.append($('<span class="glyphicon glyphicon-ban-circle" style="height: 1.2em;"/>'));
				this.btnNotApplic.css('margin-left', '0.3em');
				this.btnNotApplic.click(() => {this.delegate.actionNotApplicable(this.assn); return false;});
			}
			else this.btnNotApplic.css('display', 'inline-block');
		}
		else if (this.btnNotApplic) this.btnNotApplic.css('display', 'none');
	}

	// decide which lines are shown, which uses a different formula for closed vs. open
	private selectShownLines(lines:DataEntryContent[]):DataEntryContent[]
	{
		let candidates = lines;

		// if there's a search filter, concatenate unique new content, then filter
		if (this.isOpen)
		{
			let srch = this.searchFilter;
			if (srch || LITERAL_TYPES.indexOf(this.assn.suggestions) >= 0)
			{
				candidates = candidates.slice(0);
				srch = srch.toLowerCase();

				let suggest = this.assn.suggestions;
				if (suggest == SuggestionType.Full || suggest == SuggestionType.Disabled) this.supplementSearchTerms(candidates);
				else if (suggest == SuggestionType.ID) this.supplementSearchID(candidates);
				else if (suggest == SuggestionType.Field) {} // ??
				else this.supplementSearchLiterals(candidates);

				this.supplementAbsence(candidates);

				candidates = candidates.filter((line) =>
				{
					if (line.type == DataEntryContentType.Asserted) return true;
					if (line.annot)
					{
						if (line.annot.valueLabel.toLowerCase().includes(srch)) return true;
						if (line.annot.altLabels) for (let label of line.annot.altLabels)
							if (label.toLowerCase().includes(srch)) return true;
						// free-text doesn't have a valueURI
						if ((line.annot.valueURI) && (line.annot.valueURI.toLowerCase().includes(srch))) return true;
					}
					return false;
				});
			}
		}

		// pick the top 'N' desirable
		let selection:DataEntryContent[] = [];
		const MAX_SHOWN = this.isOpen ? 10 : 1;
		for (let n = 0; n < candidates.length; n++)
		{
			if (candidates[n].type == DataEntryContentType.Asserted) selection.push(candidates[n]);
			else if (n < MAX_SHOWN) selection.push(candidates[n]);
			else break;
		}

		return selection;
	}

	// splice in searchable content from other sources
	private supplementSearchTerms(candidates:DataEntryContent[]):void
	{
		let already = new Set<string>();
		for (let line of candidates) already.add(line.annot.valueURI + '::' + line.annot.valueLabel);
		for (let line of this.searchLines)
		{
			let key = line.annot.valueURI + '::' + line.annot.valueLabel;
			if (already.has(key)) continue;
			candidates.push(line);
		}
	}

	private supplementSearchLiterals(candidates:DataEntryContent[]):void
	{
		let already = new Set<string>();
		for (let line of candidates) already.add(line.annot.valueLabel);

		const {assn} = this;

		// add current date as a suggetion
		if (assn.suggestions == SuggestionType.Date)
		{
			let currentDate = new Date().toISOString().slice(0, 10);
			if (!already.has(currentDate))
			{
				let candidate:DataEntryContent =
				{
					'annot': {'groupNest': assn.groupNest, 'propURI': assn.propURI, 'valueLabel': currentDate, 'valueURI': null},
					'type': DataEntryContentType.Suggested,
				};
				candidates.push(candidate);
				already.add(currentDate);
			}
		}

		let auxiliary:AssaySuggestion[] = [];
		if (this.delegate.literals) for (let annot of this.delegate.literals)
		{
			if (!samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest)) continue;
			if (already.has(annot.valueLabel)) continue;

			if (annot.combined == null || annot.combined < 0) annot.combined = -1;
			let lbl = annot.valueLabel ? annot.valueLabel.toLowerCase() : '';

			if (assn.suggestions == SuggestionType.Number)
			{
				if (!validNumberLiteral(lbl)) continue;
			}
			else if (assn.suggestions == SuggestionType.Integer)
			{
				if (!validIntegerLiteral(lbl)) continue;
			}

			auxiliary.push(annot);
		}
		auxiliary.sort((l1, l2) => l2.combined - l1.combined);
		for (let annot of auxiliary)
		{
			let line:DataEntryContent = {'type': DataEntryContentType.Searchable, 'annot': annot};
			candidates.push(line);
		}
	}
	private supplementSearchID(candidates:DataEntryContent[]):void
	{
		const {assn} = this;
		const {identifiers} = this.delegate;

		if (!identifiers) return;

		let already = new Set<string>();
		for (let line of candidates) already.add(line.annot.valueLabel);

		let idlist:string[] = [];
		for (let key in identifiers) for (let val of identifiers[key])
		{
			let uniqueID = key + val;
			let [src, id] = UniqueIdentifier.parseKey(uniqueID);
			if (!src) continue;
			if (!already.has(uniqueID)) idlist.push(uniqueID);
		}
		idlist.sort();

		for (let uniqueID of idlist)
		{
			let annot:AssayAnnotation = {'propURI': assn.propURI, 'groupNest': assn.groupNest, 'valueLabel': uniqueID};
			let line:DataEntryContent = {'type': DataEntryContentType.Searchable, 'annot': annot};
			candidates.push(line);
		}
	}

	// add in absence terms, which are fair game when there's search text
	private supplementAbsence(candidates:DataEntryContent[]):void
	{
		// absence terms are also eligible, if there's a search term
		let already = new Set<string>();
		for (let line of candidates) already.add(line.annot.valueURI + '::' + line.annot.valueLabel);

		for (let uri of this.delegate.absenceTerms)
		{
			let [label, descr] = ABSENCE_DETAIL_MAP[uri];
			let key = uri + '::' + label;
			if (already.has(key)) continue;
			let annot:AssayAnnotation =
			{
				'propURI': this.assn.propURI,
				'groupNest': this.assn.groupNest,
				'valueURI': uri,
				'valueLabel': label
			};
			candidates.push({'type': DataEntryContentType.Searchable, 'annot': annot});
		}
	}
	// perform the complete redraw
	private redraw():void
	{
		const {delegate, dataEntry, assn} = this;

		// prep the reusable buttons
		let reuse = new ReusableButtons();
		reuse.focusedElement = document.activeElement;
		if (this.reuseAccept) reuse.oldAccept = this.reuseAccept;
		if (this.reuseReject) reuse.oldReject = this.reuseReject;
		if (this.reuseShow) reuse.oldShow = this.reuseShow;

		// manufacture common content (regardless of type)
		const {isOpen, shownLines, lines} = this;
		let isEmpty = lines.length == 0;
		this.optionIndex = -1;
		this.optionList = [];
		this.optionButtons = [];

		if (this.domProp)
		{
			this.domProp.css('color', isOpen ? 'white' : '#313A44');
			this.domProp.css('background-color', isOpen ? '#1362B3' : 'white');
		}

		this.domAssn.empty();
		let divblk = $('<div/>').appendTo(this.domAssn);
		divblk.addClass(isOpen ? 'annot-block-sel' : 'annot-block');

		// check open/close button
		this.domCheck.empty();
		let span = $('<span/>').appendTo(this.domCheck);
		span.css('display', 'inline-block');
		span.css('font-size', 0); // stops it cranking up the height for no reason
		span.append('&nbsp;');
		if (!this.editMode)
		{
			this.btnCaret = null;
		}
		else if (isOpen)
		{
			this.btnCaret = (this.btnCaret ? this.btnCaret : $('<button class="btn btn-xs btn-action"/>')).appendTo(span);
			this.btnCaret.empty();
			this.btnCaret.off();
			this.btnCaret.append($('<span class="glyphicon glyphicon-chevron-down" style="height: 1.2em;"/>'));
			this.btnCaret.click(() => dataEntry.callbackClose(this));
		}
		else
		{
			this.btnCaret = (this.btnCaret ? this.btnCaret : $('<button class="btn btn-xs btn-normal"/>')).appendTo(span);
			this.btnCaret.empty();
			this.btnCaret.off();
			this.btnCaret.append($('<span class="glyphicon glyphicon-chevron-right" style="height: 1.2em;"/>'));
			this.btnCaret.click(() => dataEntry.callbackOpen(this));
		}
		span.append('&nbsp;');

		if (isOpen)
		{
			this.createSearchInput(divblk, reuse);
		}

		for (let line of shownLines) this.createLine(divblk, line, reuse);

		if (isOpen)
		{
			let para = $('<p/>').appendTo(this.domAssn);
			para.css({'vertical-align': 'center', 'text-align': 'right', 'margin-top': '0.5em', 'margin-bottom': '0'});
			if (shownLines.length > 1 && shownLines.length < lines.length)
			{
				para.append('<font style="color: #808080;">(' + (lines.length - shownLines.length) + ' more)</font>');
			}
			para.append('&nbsp;');
			this.createAssignmentButtons(para);
		}

		if (shownLines.length == 0 && !isOpen)
		{
			let div = $('<div class="annot-prop">&nbsp;</div>').appendTo(divblk);
		}

		// aesthetic post-processing
		let children = divblk.children();
		children.css('box-sizing', 'border-box');
		if (this.editMode && assn.mandatory && !isOpen)
		{
			let nothing = !lines.find((line) => line.type == DataEntryContentType.Asserted);
			if (nothing) children.css('border', '2px solid #1362B3');
		}
		divblk.children().first().css({'border-top-left-radius': '3px', 'border-top-right-radius': '3px'});
		divblk.children().last().css({'border-bottom-right-radius': '3px', 'border-bottom-left-radius': '3px'});
		for (let n = 1; n < children.length; n++) $(children[n]).css('border-top', 'none');

		this.reuseAccept = reuse.newAccept;
		this.reuseReject = reuse.newReject;
		this.reuseShow = reuse.newShow;
	}

	// create the DOM object for the line, with all of its nuances
	private createLine(parent:JQuery, line:DataEntryContent, reuse:ReusableButtons):void
	{
		const {assn, isOpen, dataEntry} = this;
		const annot = line.annot, uri = annot.valueURI, label = annot.valueLabel;
		const isFaintNA = !isOpen && uri == ABSENCE_NOTAPPLICABLE;
		const isField = assn.suggestions == SuggestionType.Field;
		const isNumeric = assn.suggestions == SuggestionType.Number || assn.suggestions == SuggestionType.Integer;
		const isURL = assn.suggestions == SuggestionType.URL, isID = assn.suggestions == SuggestionType.ID;
		const isAsserted = line.type == DataEntryContentType.Asserted;

		let title:string = null, content:string = null;

		let overDiv = $('<div/>').appendTo(parent);
		overDiv.addClass(isAsserted ? 'annot-conf' : 'annot-prop');
		let div = $('<div/>').appendTo(overDiv).addClass('annot-high');
		let span = $('<span/>').appendTo(div).addClass('right');

		if (line.type == DataEntryContentType.Asserted && annot.valueURI && this.delegate.editMode &&
			annot.valueURI != ABSENCE_NOTAPPLICABLE && ALL_ABSENCE_TERMS.includes(annot.valueURI))
			overDiv.css('background-color', Theme.ISSUE_HTML);

		let appendButton = (btn:JQuery, captureKeyboard:boolean):void =>
		{
			div.append('&nbsp;');
			div.append(btn);
			if (isOpen && captureKeyboard)
			{
				this.optionList.push(div);
				this.optionButtons.push(btn);
			}
		};
		let makeAcceptButton = (reuseKey:string):void =>
		{
			if (!this.editMode) return;

			let btnAccept = reuse.ensureAccept(reuseKey, '<button class="btn btn-xs btn-action"/>');
			appendButton(btnAccept, true);
			btnAccept.append($('<span class="glyphicon glyphicon-ok" style="height: 1.2em;"/>'));
			btnAccept.click(() =>
			{
				dataEntry.callbackAcceptAnnot(line.annot, true);
				this.dataEntry.callbackClose(this);
				return false;
			});
		};

		if (isAsserted)
		{
			if (uri)
			{
				[title, content] = Popover.popoverOntologyTerm(uri, label, null, annot.altLabels, annot.externalURLs);
			}
			else if (isID)
			{
				title = '<b>Identifier</b>';
				content = escapeHTML(label);
				let [src, id] = UniqueIdentifier.parseKey(label);
				if (src) content += '<br><i>' + src.name + ' ' + escapeHTML(id) + '</i>';
			}
			else if (isField)
			{
				if (line.column)
				{
					title = '<b>' + escapeHTML(line.column.name) + '</b>';
					content = 'Type: <b>' + escapeHTML(line.column.type) + '</b><br>';
					content += 'Units: <b>' + escapeHTML(line.column.units) + '</b>';
				}
			}
			else if (!isURL)
			{
				title = '<b>Text Annotation</b>';
				content = label;
			}

			if (isURL && label && (label.startsWith('http://') || label.startsWith('https://')))
			{
				let ahref = $('<a target="_blank"/>').appendTo(span);
				ahref.attr('href', label);
				ahref.text(label);
			}
			else if (isFaintNA)
			{
				let faint = $('<span>n/a</span>').appendTo(span);
				faint.css('color', '#A0A0A0');
			}
			else if (uri || isField)
			{
				let style = '';
				if (annot.outOfSchema) style = ' style="color: #804000;"';
				let blk = $(label ? '<b' + style + '></b>' : '<i' + style + '></i>').appendTo(span);
				blk.text(label ? label : collapsePrefix(uri));
			}
			else if (isID)
			{
				let [src, id] = UniqueIdentifier.parseKey(label);
				if (src)
				{
					let ahref = $('<a target="_blank"/>').appendTo(span);
					ahref.attr('href', 'assign.jsp?uniqueID=' + label);
					ahref.text(src.shortName + ' ' + id);
				}
				else
				{
					span.text(label);
					span.css('color', '#804000');
				}
			}
			else
			{
				if (isNumeric && !isNaN(label as any)) span.text(label); else span.text('"' + label + '"');
			}

			if (line.inViolation)
			{
				let spanGlyph = $('<span class="glyphicon glyphicon glyphicon-fire" style="height: 1.2em; color: #FF0000;"/>');
				appendButton(spanGlyph, false);
				let html = this.describeTrigger('<p>Annotation is in violation of axiomatic rules.</p>', line.axiomTriggers);
				Popover.hover(domLegacy(spanGlyph), null, html);
			}

			let badnum = false;
			if (uri) {} // sometimes URIs are found in literal fields, esp. "not applicable"
			else if (assn.suggestions == SuggestionType.Integer) badnum = !validIntegerLiteral(label);
			else if (assn.suggestions == SuggestionType.Number) badnum = !validNumberLiteral(label);
			if (badnum)
			{
				let spanGlyph = $('<span class="glyphicon glyphicon glyphicon-fire" style="height: 1.2em; color: #FF0000;"/>');
				appendButton(spanGlyph, false);
				let html = 'Number is not formatted properly.';
				Popover.hover(domLegacy(spanGlyph), null, html);
			}

			if (this.editMode && !isFaintNA)
			{
				let reuseKey = 'V:' + uri + '/L:' + label;
				let btnReject = reuse.ensureReject(reuseKey, '<button class="btn btn-xs btn-action"/>');
				$('<span class="glyphicon glyphicon-remove" style="height: 1.2em;"/>').appendTo(btnReject);
				btnReject.click(() =>
				{
					dataEntry.callbackRejectAnnot(line.annot, true);
					return false;
				});
				appendButton(btnReject, true);

				if (isOpen && !isField && !uri)
				{
					reuseKey = 'E:' + keyPropGroupValue(assn.propURI, assn.groupNest, label);
					let btnEdit = reuse.ensureReject(reuseKey, '<button class="btn btn-xs btn-action"/>');
					$('<span class="glyphicon glyphicon-edit" style="height: 1.2em;"/>').appendTo(btnEdit);
					btnEdit.click(() => this.delegate.actionLookupText(this.idx, label, true));
					appendButton(btnEdit, false);
				}

			}
		}
		else // the various "un-asserted" types
		{
			const isImplied = line.type == DataEntryContentType.Implied;
			const isCloned = line.type == DataEntryContentType.Cloned;
			const isTextMined = line.type == DataEntryContentType.TextMined;
			const isSuggested = line.type == DataEntryContentType.Suggested;
			const isSearchable = line.type == DataEntryContentType.Searchable;

	 		// decide what to display in the popover

			let abbrev = collapsePrefix(uri);
			if (uri)
			{
				title = '<b>' + (label ? escapeHTML(label) : abbrev) + '</b>';
				content = '';
				if (label && abbrev)
				{
					content += 'Abbrev: <i>' + abbrev + '</i>';
					let externalLinks = Popover.uriPatternMaps && Popover.uriPatternMaps.joinLinks($('<span/>'), abbrev);
					if (externalLinks) content += ' ' + externalLinks.html();
				}
				content += Popover.CACHE_BRANCH_CODE;
				content += Popover.CACHE_DESCR_CODE;
			}

			if (isImplied) content += '<i>(implied term: awaiting confirmation)</i>';
			else if (isCloned) content += '<i>(from cloning: present in original assay)</i>';
			else if (isField)
			{
				if (line.column)
				{
					title = '<b>' + escapeHTML(line.column.name) + '</b>';
					content = 'Type: <b>' + escapeHTML(line.column.type) + '</b><br>';
					content += 'Units: <b>' + escapeHTML(line.column.units) + '</b>';
				}
			}
			else if (isTextMined) content += '<i>(from text mining)</i>';
			else if (line.isEasterEgg) content += '<i>(easter-egg: correct suggestion)</i>';
			else if (line.isBlankEaster) content += '<i>(easter-egg: unwanted suggestion)</i>';
			else if (isSuggested) content += '<i>(suggested term: awaiting confirmation)</i>';
			else if (isSearchable) content += '<i>(searched term: awaiting confirmation)</i>';

			// create the label payload

			if (isID)
			{
				let [src, id] = UniqueIdentifier.parseKey(label);
				if (src)
				{
					let ahref = $('<a target="_blank"/>').appendTo(span);
					ahref.attr('href', 'assign.jsp?uniqueID=' + label);
					ahref.text(src.shortName + ' ' + id);
				}
				else
				{
					span.text(label);
					span.css('color', '#804000');
				}
			}
			else
			{
				let text = label ? escapeHTML(label) : abbrev;

				if (!uri) span.append('"');
				let font = $('<font style="color: #808080;"/>').appendTo(span);
				font.html(text);
				if (!uri) span.append('"');

				if (isImplied || isCloned)
				{
					font.css('color', '#808080');
					font.css('text-decoration', 'underline');
				}
				else if (isTextMined)
				{
					font.css('color', Theme.NORMAL_HTML);
					font.css('font-weight', 'bold');
					overDiv.css('background', 'linear-gradient(to left, #E0F0FF, #A1F9BF');
				}
				else if (isSuggested || isSearchable)
				{
					if (line.isEasterEgg)
					{
						font.css('color', '#808080');
						font.css('text-decoration', 'underline');
					}
					else if (line.isBlankEaster)
					{
						font.css('text-decoration', 'line-through');
					}
					else if (line.predScore == null)
					{
						font.css('font-style', 'italic');
					}
				}
			}

			// create informational icons for some cases

			if (isImplied)
			{
				let spanGlyph = $('<span class="glyphicon glyphicon-heart" style="height: 1.2em; color: #1362B3;"/>');
				appendButton(spanGlyph, false);
				Popover.hover(domLegacy(spanGlyph), null, '');
				let html = this.describeTrigger('<p>Strongly implied by rules based on existing annotations.</p>', line.axiomTriggers);
				Popover.hover(domLegacy(spanGlyph), null, html);
			}
			if (isCloned)
			{
				let spanGlyph = $('<span class="glyphicon glyphicon-tags" style="height: 1.2em; color: #808080;"/>');
				appendButton(spanGlyph, false);
				spanGlyph.css({'margin-left': '0.5em', 'margin-right': '0.5em'});
				Popover.hover(domLegacy(spanGlyph), null, '');
				let html = 'Cloned annotation from original assay.';
				Popover.hover(domLegacy(spanGlyph), null, html);
			}

			// create the type-specific buttons

			let reuseKey = 'S:' + uri + '::' + label;

			if (isImplied || isCloned || isSuggested || isSearchable)
			{
				makeAcceptButton(reuseKey);
			}
			else if (isTextMined)
			{
				let btnShow = reuse.ensureShow(reuseKey, '<button class="btn btn-xs btn-action"/>');
				appendButton(btnShow, false);
				btnShow.append($('<span class="glyphicon glyphicon-search" style="height: 1.2em;"/>'));
				btnShow.click(() => {this.delegate.actionHighlightTextExtraction(line.annot as AssayTextExtraction); return false;});

				makeAcceptButton(reuseKey);

				if (this.editMode)
				{
					let btnReject = reuse.ensureReject(reuseKey, '<button class="btn btn-xs btn-action"/>');
					appendButton(btnReject, false);
					btnReject.append($('<span class="glyphicon glyphicon-remove" style="height: 1.2em;"/>'));
					btnReject.click(() =>
					{
						dataEntry.callbackRejectAnnot(line.annot, true);
						return false;
					});
				}
			}
		}

		// help popover, when appropriate
		if (title)
		{
			const {schema, assay} = dataEntry;
			let [schemaURI, groupNest] = TemplateManager.relativeBranch(assn.propURI, assn.groupNest,
													schema.schemaURI, assay.schemaBranches, assay.schemaDuplication);
			let popopt = {'schemaURI': schemaURI, 'propURI': assn.propURI, 'groupNest': groupNest, 'valueURI': uri};
			Popover.click(domLegacy(span), title, content, popopt);
		}
	}

	// add in the search/text entry widget
	private createSearchInput(parent:JQuery, reuse:ReusableButtons):void
	{
		const {dataEntry} = this;

		let suggest = this.assn.suggestions;
		if (suggest == SuggestionType.Field) return; // no search for this one

		let input = dataEntry.inputText;
		if (!input) input = dataEntry.inputText = $('<input type="text"/>');
		input.removeClass('search-term literal-term'); // will re-add just one of these
		input.off();

		if (suggest == SuggestionType.ID)
		{
			input.addClass('search-term');
			input.attr('placeholder', 'enter ID code');
			input.keyup(() => this.reapplySearch(purifyTextPlainInput(input.val())));
			input.keydown((event:JQueryKeyEventObject) =>
			{
				let keyCode = event.keyCode || event.which;
				if (keyCode == KeyCode.Tab)
				{
					dataEntry.callbackCycleSelectedBox(event.shiftKey ? -1 : 1);
					event.stopPropagation();
					event.preventDefault();
				}
				else if (keyCode == KeyCode.Enter) this.traverseAction();
				else if (keyCode == KeyCode.Up) this.traverseOption(-1);
				else if (keyCode == KeyCode.Down) this.traverseOption(1);
			});

			let para = $('<p style="margin: 0;"/>').appendTo(parent);
			para.append(input);
			if (input[0] === reuse.focusedElement) input.focus();
		}
		else if (suggest == SuggestionType.URL || suggest == SuggestionType.Date ||
				 suggest == SuggestionType.String || suggest == SuggestionType.Number || suggest == SuggestionType.Integer)
		{
			input.addClass('literal-term');

			if (suggest == SuggestionType.URL) input.attr('placeholder', 'enter URL');
			else if (suggest == SuggestionType.Number) input.attr('placeholder', 'enter number');
			else if (suggest == SuggestionType.Integer) input.attr('placeholder', 'enter integer');
			else if (suggest == SuggestionType.Date) input.attr('placeholder', 'enter date YYYY-MM-DD');
			else input.attr('placeholder', 'enter text');

			input.keyup(() =>
			{
				let txt = purifyTextPlainInput(input.val());
				this.reapplySearch(txt);
				dataEntry.btnTextAccept.prop('disabled', !txt);
			});
			input.keydown((event:JQueryKeyEventObject) =>
			{
				let keyCode = event.keyCode || event.which;
				if (keyCode == KeyCode.Enter)
				{
					let txt = purifyTextPlainInput(input.val());
					if (!txt || this.optionIndex > -1) this.traverseAction();
					else if (dataEntry.callbackAppendLiteral(this, txt)) dataEntry.callbackClose(this);
				}
				else if (keyCode == KeyCode.Tab)
				{
					dataEntry.callbackCycleSelectedBox(event.shiftKey ? -1 : 1);
					event.stopPropagation();
					event.preventDefault();
				}
				else if (keyCode == KeyCode.Up) this.traverseOption(-1);
				else if (keyCode == KeyCode.Down) this.traverseOption(1);
			});

			if (!dataEntry.btnTextAccept)
			{
				dataEntry.btnTextAccept = $('<button class="btn btn-xs btn-action"/>').css('margin', '2px');
				dataEntry.btnTextAccept.append('<span class="glyphicon glyphicon-ok" style="height: 1.2em;"/>');
			}

			let lastText = purifyTextPlainInput(input.val());
			dataEntry.btnTextAccept.prop('disabled', !lastText);
			dataEntry.btnTextAccept.off();
			dataEntry.btnTextAccept.click(() =>
			{
				let txt = purifyTextPlainInput(input.val());
				if (dataEntry.callbackAppendLiteral(this, txt)) dataEntry.callbackClose(this);
			});

			let table = $('<table style="margin: 0; width: 100%;"/>').appendTo(parent);
			let tr = $('<tr/>').appendTo(table);
			tr.css('vertical-align', 'middle');
			let td1 = $('<td/>').appendTo(tr), td2 = $('<td/>').appendTo(tr);
			td1.append(input);
			td2.append('&nbsp;');
			td2.appendTo(tr).append(dataEntry.btnTextAccept);

			if (input[0] === reuse.focusedElement) input.focus();
		}
		else // URI term type
		{
			input.addClass('search-term');
			input.attr('placeholder', 'enter search text');
			input.keyup(() => this.reapplySearch(purifyTextPlainInput(input.val())));
			input.keydown((event:JQueryKeyEventObject) =>
			{
				let keyCode = event.keyCode || event.which;
				if (keyCode == KeyCode.Tab)
				{
					dataEntry.callbackCycleSelectedBox(event.shiftKey ? -1 : 1);
					event.stopPropagation();
					event.preventDefault();
				}
				else if (keyCode == KeyCode.Enter)
				{
					let text = input.val().toString().trim();
					if (!text || suggest.length > 0)
					{
						this.traverseAction();
					}
					else if (confirm('The text does not match any available annotations. Would you like to create a non-semantic ' +
									 'text label [' + text + ']?'))
					{
						this.delegate.actionAppendCustomText(this.idx, text);
					}
				}
				else if (keyCode == KeyCode.Up) this.traverseOption(-1);
				else if (keyCode == KeyCode.Down) this.traverseOption(1);
			});

			let para = $('<p style="margin: 0;"/>').appendTo(parent);
			para.append(input);
			if (input[0] === reuse.focusedElement) input.focus();
		}
	}

	// add in buttons underneath the main block
	private createAssignmentButtons(parent:JQuery):void
	{
		let ensureButton = (btn:JQuery):JQuery => btn ? btn : $('<button class="btn btn-normal"/>');

		// absence button
		if (Vec.arrayLength(this.delegate.absenceTerms))
		{
			this.createAbsenceDropdown(parent);
			parent.append('&nbsp;');
		}

		// list dialog button
		this.btnList = ensureButton(this.btnList).appendTo(parent);
		this.btnList.empty();
		this.btnList.off();
		this.btnList.append('<span class="glyphicon glyphicon-list" style="height: 1.2em;"></span> List');
		this.btnList.click(() => this.delegate.actionLookupList(this.idx, this.recommendValue()));
		parent.append('&nbsp;');

		// tree selection dialog is only relevant for URI-type assignments
		if (this.assn.suggestions == SuggestionType.Full || this.assn.suggestions == SuggestionType.Disabled)
		{
			this.btnTree = ensureButton(this.btnTree).appendTo(parent);
			this.btnTree.empty();
			this.btnTree.off();
			this.btnTree.append('<span class="glyphicon glyphicon-tree-conifer" style="height: 1.2em;"></span> Tree');
			this.btnTree.click(() => this.delegate.actionLookupTree(this.idx, this.recommendValue()));
			parent.append('&nbsp;');
		}

		// most types have a fallback text option, which can be used to create a label and bypasses validation options
		if (!this.isThreshold())
		{
			this.btnText = ensureButton(this.btnText).appendTo(parent);
			this.btnText.empty();
			this.btnText.off();
			this.btnText.append('<span class="glyphicon glyphicon-text-background" style="height: 1.2em;"></span> Text');
			this.btnText.click(() =>
			{
				let input = this.dataEntry.inputText;
				let txt = input ? input.val().toString() : '';
				this.delegate.actionLookupText(this.idx, txt, false);
			});
		}
		else
		{
			// special deal: threshold dialog
			this.btnThresh = ensureButton(this.btnThresh).appendTo(parent);
			this.btnThresh.empty();
			this.btnThresh.off();
			this.btnThresh.append('<span class="glyphicon glyphicon-equalizer" style="height: 1.2em;"></span> Threshold');
			this.btnThresh.click(() => this.delegate.actionLookupThreshold(this.idx));
		}
	}

	// change the search parameters, and update the display accordingly; the first time, this will issue a webservice request
	private reapplySearch(srchText:string):void
	{
		if (srchText == this.searchFilter) return;
		this.searchFilter = srchText;

		// for miscellaneos types, the search materials are relatively static: use what's there
		if (this.assn.suggestions != SuggestionType.Full && this.assn.suggestions != SuggestionType.Disabled)
		{
			this.refill();
			return;
		}

		// for URI-term types: fetch a portion of the branch; this is done on a recurring basis to avoid downloading the
		// whole thing, which can get quite big

		// NOTE: there's currently redundancy between the search parameter passed to the webservice and the post-filtering
		// done after the results come back; but note that the latter is also applied to other searchable suggestion-like
		// entities, so it may be valid as-is

		if (!srchText)
		{
			this.searchLines = [];
			this.refill();
			return;
		}

		let watermark = ++this.watermarkSearch;

		let params:any =
		{
			'keywords': srchText,
			'schemaURI': this.dataEntry.assay.schemaURI,
			'schemaBranches': this.dataEntry.assay.schemaBranches,
			'schemaDuplication': this.dataEntry.assay.schemaDuplication,
			'propURI': this.assn.propURI,
			'groupNest': this.assn.groupNest,
			'annotations': [] // leave this blank, because it's OK to have duplicates
		};

		callREST('REST/KeywordMatch', params,
			(proposals:AssayAnnotation[]) =>
			{
				if (watermark != this.watermarkSearch) return;
				this.searchLines = [];
				for (let annot of proposals)
				{
					let line:DataEntryContent = {'type': DataEntryContentType.Searchable, 'annot': annot};
					this.searchLines.push(line);
				}
				this.refill();
			});
	}

	// decides on a text value that is provided/in progress from the user
	private recommendValue():string
	{
		if (LITERAL_TYPES.indexOf(this.assn.suggestions) >= 0)
		{
			let input = this.dataEntry.inputText;
			if (input) return purifyTextPlainInput(input.val());
		}
		return '';
	}

	// returns true if the assignment index is ready and willing to be used with the threshold selectiondialog
	protected isThreshold():boolean
	{
		if (this.assn.propURI != ThresholdDialog.URI_THRESHOLD) return false;

		// there has to be a field defined, otherwise it won't work
		let groupNest = this.assn.groupNest;
		for (let annot of this.dataEntry.assay.annotations) if (annot.valueLabel != null)
			if (samePropGroupNest(annot.propURI, annot.groupNest, ThresholdDialog.URI_FIELD, groupNest)) return true;
		return false;
	}

	// composes an HTML snippet which explains that an annotation is triggered by other annotations via axioms; includes both
	// violations and implied singletons
	private describeTrigger(msgHTML:string, triggers:string[]):string
	{
		let html = msgHTML;

		if (Vec.arrayLength(triggers) == 0) return html;

		let triggerAnnot:AssayAnnotation[] = [];
		for (let annot of this.delegate.assay.annotations) if (triggers.indexOf(annot.valueURI) >= 0) triggerAnnot.push(annot);

		if (triggerAnnot.length > 0) html += 'Caused by:<br/>';
		for (let annot of triggerAnnot)
		{
			html += '<b>' + escapeHTML(annot.propLabel) + '</b>';
			html += ' \u{2192} ';
			html += '<b>' + escapeHTML(annot.valueLabel) + '</b>';
			html += '<br/>';
		}

		return html;
	}

	// creates a dropdown button that offers up the available absence terms for quick assignment; this is agnostic to the
	// assignment type
	private createAbsenceDropdown(parent:JQuery):void
	{
		let divDrop = $('<div></div>').appendTo(parent).addClass('dropdown').css('display', 'inline-block');

		let btnAbsence = $('<button class="btn btn-normal"/>').appendTo(divDrop);
		btnAbsence.addClass('dropdown-toggle');
		btnAbsence.attr({'data-toggle': 'dropdown', 'aria-haspopup': 'true', 'aria-expanded': 'false'});
		btnAbsence.append('<span class="glyphicon glyphicon-off" style="height: 1.2em;"></span>');
		btnAbsence.append(' <span class="caret"></span>');

		divDrop.click((event) =>
		{
			btnAbsence.dropdown('toggle');
			event.preventDefault();
			return false;
		});

		let already = new Set<string>();
		const {assn, dataEntry} = this;
		let actualContent = false;
		for (let annot of this.delegate.assay.annotations)
			if (samePropGroupNest(assn.propURI, assn.groupNest, annot.propURI, annot.groupNest))
		{
			if (annot.valueURI && ALL_ABSENCE_TERMS.includes(annot.valueURI))
				already.add(annot.valueURI);
			else
				actualContent = true;
		}

		let ul = $('<ul class="dropdown-menu dropdown-menu-left"/>').appendTo(divDrop);
		for (let uri of this.delegate.absenceTerms)
		{
			let [label, descr] = ABSENCE_DETAIL_MAP[uri];
			let inactive = already.has(uri);
			if (uri == ABSENCE_NOTAPPLICABLE && actualContent) inactive = true;

			let li = $('<li/>').appendTo(ul);
			let href = $('<a class="dropdown-item" href="#"/>').appendTo(li);
			if (inactive) href.css('color', '#808080'); else href.css('font-weight', 'bold');
			href.text(label);
			href.click(() =>
			{
				btnAbsence.dropdown('toggle');
				if (!inactive)
				{
					let annot:AssayAnnotation =
					{
						'propURI': assn.propURI,
						'groupNest': assn.groupNest,
						'valueURI': uri,
						'valueLabel': label,
					};
					dataEntry.callbackAcceptAnnot(annot, true);
					this.dataEntry.callbackClose(this);
				}
				return false;
			});
		}
	}
}

/* EOF */ }
