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
	Quick axioms: lists the indicated "implied" annotations, which have been narrowed down to one option by axioms.
	These should generally be approved right away, and this is a good place to do it.
*/

export class QuickAxiomDialog extends BootstrapDialog
{
	private chkImplied:JQuery[] = [];
	private ordered:AssaySuggestion[] = [];

	constructor(private schema:SchemaSummary, private implied:AssaySuggestion[], private callbackApply:(subset:AssaySuggestion[]) => void)
	{
		super('Quick Axioms');
	}

	// ------------ private methods ------------

	protected populateContent():void
	{
		let keyImplied:Record<string, AssaySuggestion[]> = {};
		for (let sugg of this.implied)
		{
			let key = keyPropGroup(sugg.propURI, sugg.groupNest);
			let list = keyImplied[key];
			if (list) list.push(sugg); else keyImplied[key] = [sugg];
		}

		let divImplied = $('<div></div>').appendTo(this.content);
		for (let assn of this.schema.assignments) for (let sugg of Vec.safeArray(keyImplied[keyPropGroup(assn.propURI, assn.groupNest)]))
		{
			let div = $('<div></div>').appendTo(divImplied);
			div.css({'padding': '0.3em'});

			let lbl = $('<label></label>').appendTo(div);
			let chk = $('<input type="checkbox"></input>').appendTo(lbl);
			chk.prop('checked', true);

			let blkProp = $('<span></span>').appendTo(lbl);
			blkProp.addClass('weakblue');
			blkProp.css({'border-radius': '5px', 'border': '1px solid black', 'margin': '0 0.5em 0 0.5em', 'padding': '0.3em', 'font-weight': 'bold'});
			blkProp.text(assn.name);

			let blkValue = $('<span></span>').appendTo(lbl);
			if (sugg.valueURI)
			{
				blkValue.addClass('lightgray');
				blkValue.css({'border-radius': '5px', 'border': '1px solid black', 'padding': '0.3em', 'font-weight': 'bold'});
				blkValue.text(sugg.valueLabel);
			}
			else
			{
				blkValue.css({'font-style': 'italic', 'font-weight': 'normal'});
				blkValue.text('"' + sugg.valueLabel + '"');
			}

			this.chkImplied.push(chk);
			this.ordered.push(sugg);
		}

		let divButtons = $('<div></div>').appendTo(this.content);
		divButtons.css({'text-align': 'center'});
		let btnApply = $('<button class="btn btn-action"></button>').appendTo(divButtons);
		btnApply.append('<span class="glyphicon glyphicon-ok-circle"></span> Apply');
		btnApply.click(() => this.doApply());
	}

	private doApply():void
	{
		let subset = this.ordered.filter((e, n) => this.chkImplied[n].prop('checked'));
		this.callbackApply(subset);
		this.hide();
	}
}

/* EOF */ }
