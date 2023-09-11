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
	Adds functionality to a text input control, turning it into a way to search for assay assignments by typing in parts
	of the property/value labels. This is useful because it does not require prior identification of a particular section
	within the template.
*/

interface KeywordMatch
{
	first:AssayAnnotationProposal[]; // result that is potentially filtered
	second?:AssayAnnotationProposal[]; // if filtered query returns nothing, this will contain unfiltered results
}

export class GlobalKeywordBar
{
	public input:JQuery;
	private currentText = '';
	private cachedProps:Record<string, KeywordMatch> = {};
	private popup:JQuery = null;
	private existingAnnots:AssayAnnotation[] = [];

	private shownAnnots:AssayAnnotation[] = [];
	private shownDivs:JQuery[] = [];
	private selidx = -1;

	private selectionTree:any[] = null;

	private activityCount = 0;

	// ------------ public methods ------------

	constructor(private schemaURI:string, private pickFunc:(annot:AssayAnnotation) => void)
	{
	}

	// notify that the "current annotations" have changed: this affects the keyword prediction, and also causes
	// a cache flush
	public changeAnnotations(annotations:AssayAnnotation[]):void
	{
		this.existingAnnots = annotations;
		this.cachedProps = {};
	}

	// need to know: everything changes when the schema gets switched
	public changeSchema(schemaURI:string):void
	{
		this.schemaURI = schemaURI;
		this.cachedProps = {};
		//this.updateContent();
	}

	// call this when the keywords are being used to select from terms that are already being used (i.e. when searching for content
	// rather than annotating new content); as per the API call later, null = all terms fair game; [] = any term in use; [..] = tree winnowing
	public setSelectionTree(selectionTree:any[]):void
	{
		this.selectionTree = selectionTree;
		this.cachedProps = {};
	}

	// creates the pieces for convenient viewing
	public install(input:JQuery):void
	{
		this.input = input;

		input.change(() => this.changeText(purifyTextPlainInput(input.val())));
		input.keydown((event:JQueryKeyEventObject) => this.trapSpecialKey(event));
		input.keyup(() => this.changeText(purifyTextPlainInput(input.val())));
		input.focus(() =>
		{
			Popover.removeAllPopovers();
			if (this.currentText == null || this.currentText.length <= 0) this.shownAnnots = [];
			if (this.popup != null && this.shownAnnots.length > 0) this.popup.show();
		});
		input.blur(() =>
		{
			if (this.popup != null) this.popup.hide();
		});
	}

	public setEnabled(enabled:boolean):void
	{
		this.input.prop('disabled', !enabled);
	}

	// ------------ private methods ------------

	// typed text may have been changed: react if necessary
	private changeText(text:string):void
	{
		if (this.currentText == text) return;
		this.currentText = text;

		if (text == '')
		{
			if (this.popup != null) this.popup.hide();
			return;
		}

		this.updateContent();
	}

	// usually called when the user types something
	private updateContent():void
	{
		let text = this.currentText;
		let cached = this.cachedProps[text];
		if (cached) {this.updateResults(cached); return;}

		let annotations:string[][] = [];
		for (let annot of this.existingAnnots) if (annot.valueURI != null)
		{
			let seq = [annot.propURI, annot.valueURI];
			if (annot.groupNest) for (let uri of annot.groupNest) seq.push(uri);
			annotations.push(seq);
		}

		(async () =>
		{
			this.offsetActivity(1);
			let params =
			{
				'keywords': text,
				'schemaURI': this.schemaURI,
				'annotations': annotations,
				'select': this.selectionTree
			};
			try
			{
				let proposals = await asyncREST('REST/KeywordMatch', params) as AssayAnnotationProposal[];
				this.cachedProps[text] = {'first': proposals};
				if (this.currentText == text) this.updateResults(this.cachedProps[text]);

				if (proposals.length == 0 && this.selectionTree)
				{
					let params =
					{
						'keywords': text,
						'schemaURI': this.schemaURI,
						'annotations': annotations,
					};
					proposals = await asyncREST('REST/KeywordMatch', params) as AssayAnnotationProposal[];
					this.cachedProps[text].second = proposals;
					if (this.currentText == text) this.updateResults(this.cachedProps[text]);
				}
			}
			finally
			{
				this.offsetActivity(-1);
			}
		})();
	}

	// update the UI to present the list of proposals
	private updateResults(proposals:KeywordMatch):void
	{
		if (this.popup == null)
		{
			this.popup = $('<div/>').appendTo($(document.body));
			this.popup.css({'border': '1px solid #96CAFF', 'background-color': '#F8FCFF'});
			this.popup.css({'z-index': '1000', 'position': 'absolute', 'padding': '0.2em 1em'});
			this.popup.mousedown((event) => event.preventDefault());
			this.popup.mouseup((event) => event.preventDefault());
			this.popup.click((event) => event.preventDefault());
		}

		let pos = this.input.offset();
		let leftMargin = pos.left - this.input.position().left;
		let x = pos.left + 10, y = pos.top + this.input.height() - 10;
		let x2 = leftMargin + 10;

		this.popup.css({'left': x + 'px', 'right': x2 + 'px', 'top': y + 'px'});
		this.popup.empty();

		if (proposals.first.length == 0)
		{
			this.updateNoResults(proposals);
			return;
		}

		this.shownAnnots = [];
		this.shownDivs = [];
		this.selidx = -1;

		// grouping preserves the order in proposals - this means for-in loop over keys
		// will give the group with the highest priority element first (ES6)
		let grouped:Record<string, AssayAnnotationProposal[]> = {};
		for (let p of proposals.first)
		{
			let key = '::'.concat(...(p.groupLabel || []), p.propURI);
			(grouped[key] = grouped[key] || []).push(p);
		}

		const MAX_RESULTS = 20;
		let groupCSS = {'border-bottom': '1px solid #96CAFF', 'width': '100%'};
		let itemsCSS = {'padding-bottom': '0.25em', 'padding-left': '1em'};
		let itemCSS = {'white-space': 'nowrap'};
		let labelCSS = {'cursor': 'pointer', 'overflow': 'hidden', 'white-space': 'nowrap', 'width': '10%', 'text-overflow': 'ellipsis'};

		let container = $('<div/>').appendTo(this.popup);
		container.css({'display': 'flex', 'flex-direction': 'column', 'align-items': 'start'});

		for (let key in grouped)
		{
			let group = grouped[key];
			let annot = group[0];
			let assignment = $('<div/>').appendTo(container).css(groupCSS);
			if (annot.groupLabel) for (let groupLabel of annot.groupLabel)
			{
				$('<span/>').appendTo(assignment).text(groupLabel);
				assignment.append(' / ');
			}
			$('<span/>').appendTo(assignment).append($(Popover.displayOntologyProp(annot).elHTML));
			let shown = Math.min(MAX_RESULTS, group.length);
			let ofInfo = '';
			if (group.length >= MAX_RESULTS) ofInfo = `of ${proposals.first.length == 100 ? 'at least' : ''} ${group.length}`;
			assignment.append(` (${shown} ${ofInfo} result${group.length == 1 ? '' : 's'})`);

			let div = $('<div/>').appendTo(container).css(itemsCSS);
			let sep = null;
			for (let annot of group.slice(0, MAX_RESULTS))
			{
				if (sep) div.append(sep);
				let spanItem = $('<span/>').appendTo(div).css(itemCSS);

				this.shownAnnots.push(annot);
				const idx = this.shownAnnots.length - 1;
				let spanLabel = $('<span/>').appendTo(spanItem).css(labelCSS);
				spanLabel.click(() => this.clickedAnnotation(idx));
				spanLabel.hover(() => spanLabel.addClass('hoverUnderline'), () => spanLabel.removeClass('hoverUnderline'));
				spanLabel.append(highlightedText(annot.highlightLabel));

				if (annot.highlightAltLabel)
					spanItem.append(' (').append(highlightedText(annot.highlightAltLabel)).append(')');

				let content = '<i>' + escapeHTML(collapsePrefix(annot.valueURI)) + '</i><br/>';
				content += Popover.CACHE_BRANCH_CODE;
				if (annot.altLabels)
					content += `Other labels: ${annot.altLabels.sort(Intl.Collator().compare).join(', ')}`;
				content += Popover.CACHE_DESCR_CODE;
				let opt = {'schemaURI': this.schemaURI, 'propURI': annot.propURI, 'groupNest': annot.groupNest, 'valueURI': annot.valueURI};
				Popover.click(domLegacy(spanItem), annot.valueLabel, content, opt);

				sep = ', ';
			}
		}

		this.popup.show();
	}

	private updateNoResults(proposals:KeywordMatch):void
	{
		let div = $('<div/>').appendTo(this.popup);
		div.css({'font-weight': 'bold'});
		if (!this.selectionTree)
		{
			div.append('No matching terms found.');
		}
		else if (!proposals.second)
		{
			div.append('No matching terms found in existing annotations. Checking all available terms...');
		}
		else if (proposals.second.length == 0)
		{
			div.append('No matching terms found in all available terms.');
		}
		else
		{
			div.append('No matching terms found in existing annotations. Unused existing terms are:');
			div = $('<div/>').appendTo(this.popup);
			let matchingTerms = proposals.second.map((proposal) => highlightedText(proposal.highlightLabel));
			joinElements(div, matchingTerms, ', ');
		}
		this.popup.show();
	}

	// user clicked one of the shown annotations: apply it, then regenerate the options
	private clickedAnnotation(idx:number):void
	{
		let cached = this.cachedProps[this.currentText];
		if (cached == null) return;

		this.pickFunc(this.shownAnnots[idx]);

		/* ... keep the dialog open? not sure if it's better to do this or just clear it out...
		// remove the clicked item from the list (for immediacy)
		proposals = cached.slice(0);
		proposals.splice(idx, 1);
		this.updateResults(proposals);

		// update the content from the service (may or may not be different from just deleting one)
		this.cachedProps = {};
		this.currentText = '';
		this.changeText(purifyTextInput(this.input.val()));*/
		this.clearContent();
	}

	// clears the text and removes everything from display
	private clearContent():void
	{
		// close information popovers
		$('.popover').remove();
		if (this.popup != null) this.popup.hide();
		this.input.val('');
		this.currentText = '';
		this.shownAnnots = [];
		this.shownDivs = [];
		this.selidx = -1;
	}

	// certain keystrokes need to be intercepted
	private trapSpecialKey(event:JQueryKeyEventObject):void
	{
		let keyCode = event.keyCode || event.which;

		if (keyCode == KeyCode.Escape) this.clearContent();
		else if (keyCode == KeyCode.Enter) this.selectCurrent();
		else if (keyCode == KeyCode.Up) this.moveCurrent(-1);
		else if (keyCode == KeyCode.Down) this.moveCurrent(1);
		else return;

		event.preventDefault();
	}

	// if there is a current proposed term selected, activate it (as if clicked upon)
	private selectCurrent():void
	{
		if (this.selidx < 0 || this.selidx >= this.shownAnnots.length) return;

		this.pickFunc(this.shownAnnots[this.selidx]);
		this.clearContent();
	}

	// advance the currently selected term forward or backward
	private moveCurrent(dir:number):void
	{
		let sz = this.shownDivs.length;
		if (this.selidx < 0) this.selidx = dir < 0 ? sz - 1 : 0;
		else if (dir < 0) this.selidx = this.selidx > 0 ? this.selidx - 1 : sz - 1;
		else if (dir > 0) this.selidx = this.selidx < sz - 1 ? this.selidx + 1 : 0;

		for (let n = 0; n < sz; n++)
		{
			let bg = n == this.selidx ? '#1362B3' : 'transparent';
			this.shownDivs[n].css('background-color', bg);
		}
	}

	// increase/decrease the visual indication regarding whether something's grinding
	private offsetActivity(delta:number):void
	{
		this.activityCount += delta;
		let img = this.activityCount == 0 ? 'images/keyword.svg' : 'images/keyword_busy.svg';
		this.input.css('background-image', 'url(' + img + ')');
	}
}

/* EOF */ }
