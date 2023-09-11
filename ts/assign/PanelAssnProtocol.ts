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
	Assignment panel that holds the protocol text for an assay.
*/

export class PanelAssnProtocol
{
	private areaTranslit:JQuery;
	private fullText:JQuery;
	private spanPredict:JQuery;
	private btnPredict:JQuery;
	private btnStop:JQuery;
	private btnEasterEgg:JQuery;
	private lastTranslitHash:string[] = []; // used to block unnecessary re-transliterations

	// ------------ public methods ------------

	constructor(private delegate:AssignmentDelegate, private divColumn:JQuery)
	{
	}

	public render():void
	{
		let divText = $('<div></div>').appendTo(this.divColumn);

		this.fullText = $('<textarea class="assaytext"></textarea>').appendTo(divText);
		this.fullText.attr({'cols': 55, 'rows': 30, 'autocomplete': 'off', 'autocorrect': 'off', 'autocapitalize': 'off', 'spellcheck': 'false'});
		this.fullText.prop('disabled', !this.delegate.editMode);
		this.fullText.change(() => this.delegate.actionChangedFullText());
		this.fullText.keyup(() => this.delegate.actionChangedFullText());
		this.fullText.focusin(() => this.delegate.actionEnterFullText());
		this.fullText.focusout(() => this.delegate.actionExitFullText());

		let divTextButtons = $('<div class="flexbuttons"></div>').appendTo(divText);
		divTextButtons.css('justify-content', 'flex-end');

		this.spanPredict = $('<div></div>').appendTo(divTextButtons);

		this.btnPredict = $('<button class="btn btn-action"></button>').appendTo($('<div></div>').appendTo(divTextButtons));
		this.btnPredict.append('<span class="glyphicon glyphicon-play-circle"></span> Request Suggestions');
		this.btnPredict.click(() => this.delegate.actionPredictAnnotations());

		this.btnStop = $('<button class="btn btn-action"></button>').appendTo($('<div></div>').appendTo(divTextButtons));
		this.btnStop.append('<span class="glyphicon glyphicon glyphicon-pause"></span> Stop');
		this.btnStop.click(() => this.delegate.actionStopPrediction());
		this.btnStop.prop('disabled', true);

		this.btnEasterEgg = $('<button class="btn btn-xs btn-action"></button>').appendTo($('<div></div>').appendTo(divTextButtons));
		this.btnEasterEgg.append('<span class="glyphicon glyphicon glyphicon-sunglasses"></span>');
		this.btnEasterEgg.click(() => this.delegate.actionEnterCheatMode());
		Popover.hover(domLegacy(this.btnEasterEgg), null, 'Activate "easter-egg" mode to evaluate suggestion models.');

		this.areaTranslit = $('<p id="areaTranslit" style="display: none;"></p>').appendTo(divText);
	}

	// access to full text content
	public getFullText():string
	{
		return purifyTextPlainInput(this.fullText.val());
	}
	public setFullText(txt:string):void
	{
		this.fullText.val(txt);
		(this.fullText[0] as HTMLTextAreaElement).setSelectionRange(0, 0);
	}

	// returns true if the main text area owns the focus
	public hasFocus():boolean
	{
		return this.fullText.is(':focus');
	}

	// buttons & hints for prediction happening or not
	public statusBeginPredict():void
	{
		this.btnPredict.prop('disabled', true);
		this.btnStop.prop('disabled', false);
		this.spanPredict.html('<i>Suggesting...</i>');
	}
	public statusEndPredict():void
	{
		this.btnPredict.prop('disabled', false);
		this.btnStop.prop('disabled', true);
		this.spanPredict.html('');
	}
	public setStatusPredict(enable:boolean):void
	{
		this.btnPredict.prop('disabled', !enable);
	}

	public updateEditStatus():void
	{
		this.fullText.prop('disabled', !this.delegate.editMode);
		this.btnEasterEgg.prop('disabled', !this.delegate.editMode);
		this.btnPredict.prop('disabled', !this.delegate.editMode);
	}

	// turn the annotations into text, using boilerplate
	public transliterateText():void
	{
		let assay = this.delegate.assay;
		if (!assay.schemaURI) return;

		// build & compare a term array, to prevent unnecessary regeneration (since this requires a web request)
		let translitHash:string[] = ['*{{' + assay.schemaURI + '}}*'];
		for (let annot of assay.annotations)
		{
			let key = keyPropGroup(annot.propURI, annot.groupNest);
			if (annot.valueURI) key += '::' + annot.valueURI; else key += '**' + annot.valueLabel;
			translitHash.push(key);
		}
		translitHash.sort();
		if (Vec.equals(translitHash, this.lastTranslitHash)) return;
		this.lastTranslitHash = translitHash;

		this.areaTranslit.css('display', 'block');
		this.areaTranslit.text('Transliterating...');

		let params =
		{
			'assayID': assay.assayID,
			'uniqueID': assay.uniqueID,
			'schemaURI': assay.schemaURI,
			'schemaBranches': assay.schemaBranches,
			'schemaDuplication': assay.schemaDuplication,
			'annotations': assay.annotations
		};
		callREST('REST/TransliterateAssay', params,
			(result:any) => this.applyTransliteration(result.html),
			() => this.areaTranslit.text('Transliteration failed.'));
	}

	// shows the extracted text corresponding to a proposed term
	public highlightTextExtraction(extr:AssayTextExtraction):void
	{
		if (!extr) return;

		let text = this.fullText.val().toString();
		if (extr.referenceText == null) return;
		if (extr.referenceText != text)
		{
			// will re-enter once the sync is complete
			this.updateHighlightText(extr, text);
			return;
		}

		let area = this.fullText[0] as HTMLTextAreaElement;
		let currentStart = area.selectionStart;//, currentLength = area.selectionEnd - currentStart;

		let ordered = Vec.idxSort(extr.begin);
		let idx = ordered[0];
		for (let n = 0; n < ordered.length; n++) if (currentStart < extr.begin[ordered[n]]) {idx = ordered[n]; break;}

		// this is unfortunately the least kludgey way to make sure the selection of a textarea is visible
		let position = Math.min(extr.end[idx] + 20, text.length - 1);
		area.blur();
		this.fullText.val(text.substring(0, position));
		area.focus();
		this.fullText.val(text);
		area.setSelectionRange(extr.begin[idx], extr.end[idx]);
	}

	// ------------ private methods ------------

	// apply the results of the transliteration service
	private applyTransliteration(html:string):void
	{
		this.areaTranslit.empty();
		this.areaTranslit.append('<p style="margin: 0.5em 0 0 0"><b>Autogenerated Text</b></p>');

		let div = $('<div></div>').appendTo(this.areaTranslit);
		div.css('max-width', '25em');
		div.css('border', '1px solid #96CAFF');
		div.css('box-shadow', '0 0 5px rgba(66,77,88,0.1)');
		div.css('background-color', '#F8FCFF');
		div.css('padding', '0.5em');
		div.html(html);
	}

	// special case when text is out of sync with the extractions; need to revisit the webservice
	private updateHighlightText(extr:AssayTextExtraction, text:string):void
	{
		let assay = this.delegate.assay;

		// all the extracted contents at the moment get flushed
		let map:Record<string, AssayTextExtraction> = {};
		for (let x of this.delegate.extractions)
		{
			x.begin = [];
			x.end = [];
			x.referenceText = null;
			map[keyPropGroupValue(x.propURI, x.groupNest, x.valueURI)] = x;
		}

		// call the service again; any extractions that are still there get re-synced; anything that disappeared stays null,
		// and new stuff is not added: a proper re-textmine needs to be triggered in order to get the complete sync
		let params =
		{
			'schemaURI': assay.schemaURI,
			'schemaBranches': assay.schemaBranches,
			'schemaDuplication': assay.schemaDuplication,
			'text': text,
			'existing': assay.annotations
		};
		callREST('REST/TextMine', params,
			(data:any) =>
			{
				for (let u of data.extractions as AssayTextExtraction[])
				{
					u.propURI = expandPrefix(u.propURI);
					u.valueURI = expandPrefix(u.valueURI);
					for (let n = 0; n < Vec.arrayLength(u.groupNest); n++) u.groupNest[n] = expandPrefix(u.groupNest[n]);

					let x = map[keyPropGroupValue(u.propURI, u.groupNest, u.valueURI)];
					if (x)
					{
						x.begin = u.begin;
						x.end = u.end;
						x.referenceText = text;
					}
				}
				this.highlightTextExtraction(map[keyPropGroupValue(extr.propURI, extr.groupNest, extr.valueURI)]);
			});
	}
}

/* EOF */ }
