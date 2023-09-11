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

export class MatchKeywordBar
{
	public input:JQuery;
	private currentText = '';
	private cachedProps:Record<string, AssayAnnotation[]> = {};
	private popup:JQuery = null;

	private shownAnnots:AssayAnnotation[] = [];
	private shownDivs:JQuery[] = [];

	// ------------ public methods ------------

	constructor(private schemaURI:string)
	{
	}

	// need to know: everything changes when the schema gets switched
	public changeSchema(schemaURI:string):void
	{
		this.schemaURI = schemaURI;
		this.cachedProps = {};
		//this.updateContent();
	}

	// creates the pieces for convenient viewing
	public install(input:JQuery):void
	{
		this.input = input;
		this.bindInputHandlers();
	}

	public bindInputHandlers():void
	{
		this.input.change(() => this.changeText(purifyTextPlainInput(this.input.val())));
		this.input.keydown((event:JQueryKeyEventObject) => this.trapSpecialKey(event));
		this.input.keyup(() => this.changeText(purifyTextPlainInput(this.input.val())));
		this.input.focus(() =>
		{
			Popover.removeAllPopovers();
			if (this.currentText == null || this.currentText.length <= 0) this.shownAnnots = [];
			if (this.popup != null && this.shownAnnots.length > 0) this.popup.show();
		});
		this.input.blur(() =>
		{
			if (this.popup != null) this.popup.hide();
		});
	}

	// ------------ private methods ------------

	// typed text may have been changed: react if necessary
	private changeText(text:string):void
	{
		if (this.currentText == text) return;
		this.currentText = text;

		if (text == '') return;

		this.updateContent();
	}

	// usually called when the user types something
	private updateContent():void
	{
		let text = this.currentText;
		let cached = this.cachedProps[text];
		if (cached) {this.updateResults(cached); return;}

		let params =
		{
			'keywords': text,
			'schemaURI': this.schemaURI,
			'annotations': [] as string[][],
			'select': null as any
		};
		callREST('REST/KeywordMatch', params,
			(proposals:AssayAnnotation[]) =>
			{
				this.cachedProps[text] = proposals;
				if (this.currentText == text) this.updateResults(proposals);
			},
			() => {});
	}

	// update the UI to present the list of proposals
	private updateResults(proposals:AssayAnnotation[]):void
	{
		if (proposals.length == 0) return;

		if (this.popup == null)
		{
			this.popup = $('<div></div>').appendTo($(document.body));
			this.popup.css('border', '1px solid #96CAFF');
			this.popup.css('background-color', '#F8FCFF');
			this.popup.css('z-index', '100000');
			this.popup.css('position', 'absolute');
			this.popup.css('margin', '0');
			this.popup.css('padding', '0.2em 0 0.2em 0');
			this.popup.css('width', 'fit-content');
			this.popup.css('min-width', '340px');
			this.popup.css('height', '415px');
			this.popup.css('block', 'block');
			this.popup.mousedown((event) => event.preventDefault());
			this.popup.mouseup((event) => event.preventDefault());
			this.popup.click((event) => event.preventDefault());
		}

		let pos = this.input.offset();
		let leftMargin = pos.left - this.input.position().left;
		let x = pos.left, y = pos.top + this.input.height() - 10;
		let x2 = leftMargin + 10;

		this.popup.css('left', (x - 16) + 'px');
		this.popup.css('right', x2 + 'px');
		this.popup.css('top', y + 'px');
		this.popup.empty();

		let h2 = $('<h2></h2>').appendTo(this.popup);
		h2.text('Compare with these existing annotations:');
		h2.css({'margin': '5px 10px 8px 10px', 'padding': '0px 0px 3px 0px', 'border-top': '0px', 'color': '#1362B3'});

		this.shownAnnots = [];
		this.shownDivs = [];
		let num = Math.min(proposals.length, 10); // at most 10 suggestions
		for (let n = 0; n < num; n++)
		{
			let div = $('<div></div>').appendTo(this.popup), annot = proposals[n];
			div.css('width', '100%');
			div.css('padding', '0.4em 0.5em 0.4em 0.5em');
			//div.css('margin-bottom', n < num - 1 ? '0.8em' : '0.3em');

			if (annot.groupLabel) for (let groupLabel of annot.groupLabel)
			{
				let blkGroup = $('<font></font>').appendTo(div);
				blkGroup.css('background', 'white');
				blkGroup.css('border-radius', 5);
				blkGroup.css('border', '1px solid black');
				blkGroup.css('padding', '0.3em');
				blkGroup.text(groupLabel);

				div.append('&nbsp;');
			}

			let blkProp = $('<font></font>').appendTo(div);
			blkProp.addClass('weakblue');
			blkProp.css('border-radius', 5);
			blkProp.css('border', '1px solid black');
			blkProp.css('padding', '0.3em');
			blkProp.append($(Popover.displayOntologyProp(annot).elHTML));

			div.append('&nbsp;');

			let vlabel = annot.valueLabel;
			if (vlabel.length > 100) vlabel = vlabel.substring(0, 100) + '...';

			let blkValue = $('<font></font>').appendTo(div);
			blkValue.addClass('lightgray');
			blkValue.css('border-radius', 5);
			blkValue.css('border', '1px solid black');
			blkValue.css('padding', '0.3em');
			blkValue.append($(Popover.displayOntologyValue(annot).elHTML));

			this.shownAnnots.push(proposals[n]);
			this.shownDivs.push(div);
		}

		this.popup.show();
	}

	// certain keystrokes need to be intercepted
	private trapSpecialKey(event:JQueryKeyEventObject):void
	{
		let keyCode = event.keyCode || event.which;

		if (keyCode == KeyCode.Escape)
		{
			// remove popup, if any, showing similar annotations
			if (this.popup != null)
			{
				this.popup.hide();
				this.shownAnnots = [];
				this.shownDivs = [];
				event.stopPropagation();
			}
		}
		else return;

		event.preventDefault();
	}
}

/* EOF */ }
