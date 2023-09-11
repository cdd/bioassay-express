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
	Popup dialog for selecting assays that are likely to be candidates for improving the model for a particular
	schema/assignment/value.
*/

interface AssayPrediction
{
	assayID:number;
	uniqueID:string;
	suggestion:AssaySuggestion;
}

export class ModelProgressSelector extends BootstrapDialog
{
	private withoutAssayID:number[] = []; // assays that are blank for this assignment, which are fair game
	private roster:number[]; // blank assays needing to be predicted
	private predictions:AssayPrediction[] = [];

	private cancelled = false;
	private progress:ProgressBar;
	private domHigh:JQuery;
	private domMedium:JQuery;
	private domLow:JQuery;

	constructor(private schema:SchemaSummary, private assn:SchemaAssignment, private valueURI:string, private valueLabel:string,
				private withAssayID:number[], private allAssayID:number[])
	{
		super('Selection');

		let excl = new Set<number>(withAssayID);
		for (let assayID of allAssayID) if (!excl.has(assayID)) this.withoutAssayID.push(assayID);
	}

	protected populateContent():void
	{
		let hier = new SchemaHierarchy(this.schema);
		let seq = [this.assn.name];
		for (let group = hier.findGroupNest(this.assn.groupNest); group && group.parent; group = group.parent) seq.unshift(group.name);
		seq.push(this.valueLabel);

		let divTitle = $('<div></div>').appendTo(this.content);
		for (let n = 0; n < seq.length; n++)
		{
			if (n > 0) divTitle.append(' \u{2192} ');
			let blk = $('<b></b>').appendTo(divTitle);
			if (n == seq.length - 1) blk.css('color', Theme.STRONG_HTML);
			blk.text(seq[n]);
		}

		let paraHelp = $('<p></p>').appendTo(this.content);
		paraHelp.append('Selecting assays for which the assignment is currently blank. Those with predicted estimates ');
		paraHelp.append('at certain boundaries are likely to provide high value for purposes of model improvement. ');
		paraHelp.append('Assays with no annotation: <u>' + this.withoutAssayID.length + '</u>');

		let divProgress = $('<div></div>').appendTo(this.content).css('text-align', 'center');
		this.progress = new ProgressBar(500, 15, () =>
		{
			this.cancelled = true;
			this.progress.remove();
			divProgress.remove();
		});
		this.progress.render($('<div></div>').appendTo(divProgress).css('display', 'inline-block'));

		let divTable = $('<div></div>').appendTo(this.content);
		let table = $('<table></table>').appendTo(divTable).css('margin', '0 auto');
		let tr1 = $('<tr></tr>').appendTo(table), tr2 = $('<tr></tr>').appendTo(table);
		$('<th></th>').appendTo(tr1).css({'text-align': 'left', 'padding': '0 0.5em'}).text('High');
		$('<th></th>').appendTo(tr1).css({'text-align': 'left', 'padding': '0 0.5em'}).text('Medium');
		$('<th></th>').appendTo(tr1).css({'text-align': 'left', 'padding': '0 0.5em'}).text('Low');
		this.domHigh = $('<td></td>').appendTo(tr2).css({'vertical-align': 'top', 'padding': '0 0.5em'});
		this.domMedium = $('<td></td>').appendTo(tr2).css({'vertical-align': 'top', 'padding': '0 0.5em'});
		this.domLow = $('<td></td>').appendTo(tr2).css({'vertical-align': 'top', 'padding': '0 0.5em'});
	}

	protected onShown():void
	{
		if (this.withoutAssayID.length == 0) return;

		this.roster = this.withoutAssayID.slice(0);
		this.sampleNextBatch();
	}
	protected onHidden():void
	{
		this.cancelled = true;
	}

	// ------------ private methods ------------

	// pick the next few assays to make an estimation
	private sampleNextBatch():void
	{
		if (this.cancelled) return;
		if (this.roster.length == 0)
		{
			this.progress.remove();
			// (finalise)
			return;
		}

		let sz = this.withoutAssayID.length;
		this.progress.setProgress((sz - this.roster.length) / sz);

		let batch = this.roster.splice(0, Math.min(100, this.roster.length));

		let params =
		{
			'assayID': batch,
			'assignments': [{'propURI': this.assn.propURI, 'groupNest': this.assn.groupNest}],
			'valueURIList': [this.valueURI],
		};
		callREST('REST/SelfSuggest', params,
			(data:any) =>
			{
				for (let result of data)
				{
					let pred:AssayPrediction =
					{
						'assayID': result.assayID,
						'uniqueID': result.uniqueID,
						'suggestion': null
					};
					let suggestions:AssaySuggestion[] = result.suggestions;
					for (let sugg of suggestions) if (expandPrefix(sugg.valueURI) == this.valueURI) pred.suggestion = sugg;
					this.predictions.push(pred);
				}

				this.refreshResults();
				this.sampleNextBatch();
			},
			() => alert('Error making predictions'));
	}

	private refreshResults():void
	{
		let rankPred = this.predictions.slice(0);
		rankPred.sort((p1, p2) => p1.suggestion.combined - p2.suggestion.combined); // low to high

		const MAXBIN = 10; // up to this many in each column

		this.domHigh.empty();
		this.domMedium.empty();
		this.domLow.empty();

		let appendPred = (dom:JQuery, pred:AssayPrediction):void =>
		{
			let p = $('<p></p>').appendTo(dom);
			let span = $('<span></span>').appendTo(p);

			let [src, id] = UniqueIdentifier.parseKey(pred.uniqueID);
			if (src)
				span.text(src.shortName + ' ' + id);
			else
				span.text('#' + pred.assayID);
			let link = pred.uniqueID ? restBaseURL + '/assign.jsp?uniqueID=' + encodeURIComponent(pred.uniqueID) :
									   restBaseURL + '/assign.jsp?assayID=' + pred.assayID;

			span.addClass('pseudoLink');
			span.click(() => window.open(link, '_blank'));
		};

		// shave off high & low
		for (let n = 0; n < MAXBIN && rankPred.length > 0 && rankPred[rankPred.length - 1].suggestion.combined > 0.5; n++)
			appendPred(this.domHigh, rankPred.pop());
		for (let n = 0; n < MAXBIN && rankPred.length > 0 && rankPred[0].suggestion.combined < 0.5; n++)
			appendPred(this.domLow, rankPred.shift());

		// sort by distance to the middle
		rankPred = Vec.idxGet(rankPred, Vec.idxSort(rankPred.map((p) => Math.max(0.5 - p.suggestion.combined))));
		for (let n = 0; n < MAXBIN && n < rankPred.length; n++) appendPred(this.domMedium, rankPred[n]);
	}
}

/* EOF */ }
