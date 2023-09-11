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
	Using user-entered search text to match up identifiers in the database, and presenting a UI to conveniently select the
	desired assay. For multiple matches of the ID part, uses a popup with a list.
*/

export interface FindIdentifierMatch
{
	assayID:number;
	uniqueID:string;
}

export class FindIdentifier
{
	public input:JQuery;
	private currentText = '';
	private cachedMatches:Record<string, FindIdentifierMatch[]> = {};
	private popup:JQuery = null;

	private shownMatches:FindIdentifierMatch[] = [];
	private shownDivs:JQuery[] = [];
	private selidx = -1;

	// ------------ public methods ------------

	constructor(private pickFunc:(match:FindIdentifierMatch) => void)
	{
	}

	// creates the pieces for convenient viewing
	public install(input:JQuery):void
	{
		this.input = input;

		input.change(() => this.changeText(purifyTextPlainInput(input.val())));
		input.keydown((event:JQueryKeyEventObject) => this.trapSpecialKey(event));
		input.keyup(() => this.changeText(purifyTextPlainInput(input.val())));
		input.focus(() => {if (this.popup != null && this.shownMatches.length > 0) this.popup.show();});
		input.blur(() => {if (this.popup != null) this.popup.hide();});
	}

	// ------------ private methods ------------

	// typed text may have been changed: react if necessary
	private changeText(text:string):void
	{
		if (this.currentText == text) return;
		this.currentText = text;

		if (text == '')
		{
			this.clearContent();
			return;
		}

		let cached = this.cachedMatches[text];
		if (cached) {this.updateResults(cached); return;}

		let params = {'id': text, 'permissive': true};
		callREST('REST/FindIdentifier', params,
			(result:any) =>
			{
				let matches:FindIdentifierMatch[] = result.matches;
				//console.log(text + ': ' + JSON.stringify(matches));
				this.cachedMatches[text] = matches;
				if (this.currentText == text) this.updateResults(matches);
			});
	}

	// update the UI to present the list of proposals
	private updateResults(matches:FindIdentifierMatch[]):void
	{
		if (matches.length == 0)
		{
			this.clearContent(false);
			return;
		}

		if (this.popup == null)
		{
			this.popup = $('<div></div>').appendTo($(document.body));
			this.popup.attr('id', 'findIdentifier');
			this.popup.css('border', '1px solid #96CAFF');
			this.popup.css('background-color', '#F8FCFF');
			this.popup.css('z-index', '100000');
			this.popup.css('position', 'absolute');
			this.popup.css('margin', '0');
			this.popup.css('padding', '0.2em 0 0.2em 0');
			this.popup.mousedown((event) => {event.preventDefault();});
			this.popup.mouseup((event) => {event.preventDefault();});
			this.popup.click((event) => {event.preventDefault();});
		}

		let pos = this.input.offset();
		let x = pos.left + 10, y = pos.top + this.input.height() - 10;

		this.popup.css('left', x + 'px');
		this.popup.css('top', y + 'px');
		this.popup.empty();

		this.shownMatches = [];
		this.shownDivs = [];
		this.selidx = -1;
		let num = Math.min(matches.length, 18);
		for (let n = 0; n < num; n++)
		{
			let div = $('<div></div>').appendTo(this.popup), match = matches[n];
			div.css('width', '100%');
			div.css('padding', '0.4em 0.5em 0.4em 0.5em');

			let blkLine = $('<font></font>').appendTo(div);
			blkLine.css('padding', '0.3em');
			blkLine.css('cursor', 'pointer');

			let [src, id] = UniqueIdentifier.parseKey(match.uniqueID);
			if (src)
				blkLine.html(escapeHTML(src.name) + ' <b><u>' + escapeHTML(id) + '</u></b>');
			else
				blkLine.html('Assay ID <b><u>' + match.assayID + '</u></b>');

			const idx = n;
			blkLine.click(() => this.clickedMatch(idx));

			this.shownMatches.push(match);
			this.shownDivs.push(div);
		}

		this.popup.show();
	}

	// user clicked one of the shown annotations: apply it, then regenerate the options
	private clickedMatch(idx:number):void
	{
		this.pickFunc(this.shownMatches[idx]);
		this.clearContent();
	}

	// clears the text and removes everything from display
	private clearContent(clearText:boolean = true):void
	{
		if (this.popup != null) this.popup.hide();
		if (clearText)
		{
			this.input.val('');
			this.currentText = '';
		}
		this.shownMatches = [];
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
	}

	// if there is a current proposed term selected, activate it (as if clicked upon)
	private selectCurrent():void
	{
		let idx = Math.max(0, this.selidx);
		if (idx >= this.shownMatches.length) return;

		this.pickFunc(this.shownMatches[idx]);
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
}

/* EOF */ }
