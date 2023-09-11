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
	The companion to ModelProgress, which displays specific detailed model progress information for one assignment.
*/

const enum HitMiss
{
	TP = 'TP', // true positive
	TN = 'TN', // true negative
	FP = 'FP', // false positive
	FN = 'FN', // false negative
}

interface DetailNode
{
	depth:number;
	parent:number;
	uri:string;
	name:string;
	count:number;
}

// accumulated information for a row (representing a value within an assignment)
interface GridRow
{
	node:DetailNode;

	divStatus:JQuery;
	divPercent:JQuery;
	btnOpen:JQuery;

	assaysTP:number[]; // assay IDs leading to a true positive
	assaysTN:number[]; // assay IDs leading to a true negative
	assaysFP:number[]; // assay IDs leading to a false positive
	assaysFN:number[]; // assay IDs leading to a false negative
}

export class ModelProgressDetail
{
	public delta = 0; // optional # pixels to offset by

	private divMain:JQuery;
	private progress:ProgressBar;

	private cancelled = false;
	private assayIDList:number[]; // all assay IDs involved in this assignment
	private roster:number[]; // assay IDs that need a "self prediction"
	private gridRows:GridRow[] = [];
	private mapRows:Record<string, GridRow> = {}; // valueURI as key

	constructor(private schema:SchemaSummary, private assn:SchemaAssignment, private allAssayID:number[])
	{
	}

	// creates the content and begins the series of requests to evaluate the models
	public render(domParent:JQuery):void
	{
		this.divMain = $('<div></div>').appendTo(domParent);

		if (this.delta > 0)
		{
			let gap = $('<div></div>').appendTo(this.divMain);
			gap.css('height', this.delta + 'px');
		}

		let hier = new SchemaHierarchy(this.schema);
		let seq = [this.assn.name];
		for (let group = hier.findGroupNest(this.assn.groupNest); group && group.parent; group = group.parent) seq.unshift(group.name);

		let divTitle = $('<div></div>').appendTo(this.divMain);
		for (let n = 0; n < seq.length; n++)
		{
			if (n > 0) divTitle.append(' \u{2192} ');
			$('<b></b>').appendTo(divTitle).text(seq[n]);
		}

		this.progress = new ProgressBar(300, 15, () =>
		{
			this.cancelled = true;
			this.progress.remove();
		});
		this.progress.render(this.divMain);

		let divDetail = $('<div></div>').appendTo(this.divMain).css('display', 'inline-block');

		let params =
		{
			'schemaURI': this.schema.schemaURI,
			'propURI': this.assn.propURI,
			'groupNest': this.assn.groupNest,
		};
		callREST('REST/GetPropertyTree', params,
			(data:any) =>
			{
				let treeData = unlaminateTree(data.tree);
				this.fetchAnnotations(divDetail, treeData);
			});
	}

	public cancel():void
	{
		this.cancelled = true;
	}

	// ------------ private methods ------------

	// given that the branch structure is now known, find out which assays belong to each one
	private fetchAnnotations(dom:JQuery, treeData:SchemaTreeNode[]):void
	{
		if (this.cancelled) return;

		let select =
		{
			'propURI': this.assn.propURI,
			'groupNest': this.assn.groupNest,
			'valueURIList': treeData.map((node:SchemaTreeNode):string => node.uri),
		};
		let params =
		{
			'schemaURI': this.schema.schemaURI,
			'select': [select]
		};

		callREST('REST/SelectionTree', params,
			(data:any) =>
			{
				let detailData:DetailNode[] = data.treeList[0];
				this.assayIDList = data.matchesAssayID;
				dom.empty();
				this.renderTree(dom, detailData);
			});
	}

	// sufficient data exists to render the tree outline
	private renderTree(dom:JQuery, treeData:DetailNode[]):void
	{
		let grid = $('<div></div>').appendTo(dom);
		grid.css('display', 'grid');
		grid.css({'align-items': 'center', 'justify-items': 'left', 'justify-content': 'start'});
		grid.css({'grid-column-gap': '0.5em', 'grid-row-gap': '0.2em'});
		grid.css('grid-template-columns', '[annotation] auto [count] auto [status] auto [percent] auto [button] auto [end]');
		grid.css({'padding': '0.5em', 'margin': '0.5em 1em 0.5em 0'});
		grid.css({'background-color': '#FCFCFC', 'border': '1px solid #808080'});
		grid.css({'box-shadow': '#C0C0C0 0px 0px 5px'});

		let row = 1;
		let makeHeading = (label:string, column:string):void =>
		{
			let div = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': column});
			div.css({'font-weight': 'bold'});
			div.text(label);
		};
		makeHeading('Annotation', 'annotation');
		makeHeading('Count', 'count');
		makeHeading('Success Rate', 'status');
		makeHeading('Recall', 'percent');

		for (let n = 0; n < treeData.length; n++)
		{
			let node = treeData[n];

			row++;
			let divAnnot = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': 'annotation'});
			let divCount = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': 'count'});
			let divStatus = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': 'status'});
			let divPercent = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': 'percent'});
			let divButton = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': 'button'});

			if (node.depth > 0)
			{
				divAnnot.css('padding-left', node.depth + 'em');
				divAnnot.append('\u{21B3}');
			}

			let label = $('<span></span>').appendTo(divAnnot);
			label.text(node.name);

			let content = '';
			content += Popover.CACHE_DESCR_CODE;
			let popopt =
			{
				'schemaURI': this.schema.schemaURI,
				'propURI': this.assn.propURI,
				'groupNest': this.assn.groupNest,
				'valueURI': node.uri
			};
			Popover.click(domLegacy(label), node.name, content, popopt);

			divCount.css('justify-self', 'end');
			if (node.count > 0) divCount.text(node.count.toString());

			divPercent.css('justify-self', 'end');

			let btnOpen = $('<button class="btn btn-xs btn-normal"></button>').appendTo(divButton).css('visibility', 'hidden');
			btnOpen.append($('<span class="glyphicon glyphicon-triangle-right" style="height: 1.2em;"></span>'));
			btnOpen.click(() => this.investigateRow(gridRow));

			let gridRow:GridRow =
			{
				'node': node,
				'divStatus': divStatus,
				'divPercent': divPercent,
				'btnOpen': btnOpen,
				'assaysTP': [],
				'assaysTN': [],
				'assaysFP': [],
				'assaysFN': [],
			};

			this.gridRows.push(gridRow);
			this.mapRows[node.uri] = gridRow;
		}

		this.roster = this.assayIDList.slice(0);
		this.modelNextAssay();
	}

	// fetch prediction information for the next assay
	private modelNextAssay():void
	{
		if (this.cancelled) return;
		if (this.roster.length == 0)
		{
			this.progress.remove();
			// (finalise?)
			return;
		}

		let sz = this.assayIDList.length;
		this.progress.setProgress((sz - this.roster.length) / sz);

		let batch = this.roster.splice(0, Math.min(100, this.roster.length));
		let params =
		{
			'assayID': batch,
			'assignments': [{'propURI': this.assn.propURI, 'groupNest': this.assn.groupNest}],
		};
		callREST('REST/SelfSuggest', params,
			(data:any) =>
			{
				for (let result of data)
				{
					let assayID:number = result.assayID;
					let suggestions:AssaySuggestion[] = result.suggestions;
					let valueURIList:string[] = result.valueURIList;
					let mapTruth = this.truthType(suggestions, valueURIList);

					//console.log('assay='+assayID+' suggest='+JSON.stringify(suggestions)+' values='+JSON.stringify(valueURIList)+
					//	' truth='+JSON.stringify(truth));

					for (let [valueURI, truth] of Object.entries(mapTruth))
					{
						let row = this.mapRows[expandPrefix(valueURI)];
						if (!row) continue;
						if (truth == HitMiss.TP) row.assaysTP.push(assayID);
						else if (truth == HitMiss.TN) row.assaysTN.push(assayID);
						else if (truth == HitMiss.FP) row.assaysFP.push(assayID);
						else if (truth == HitMiss.FN) row.assaysFN.push(assayID);
					}
				}

				for (let row of this.gridRows) this.redrawRowResults(row);
				this.modelNextAssay();
			},
			() => alert('Error making predictions'));
	}

	// given a list of suggestions with a prediction score, and a list of values that have been asserted to be present, returns
	// a truth type for each of the possible values
	private truthType(suggestions:AssaySuggestion[], valueURIList:string[]):Record<string, HitMiss>
	{
		let mapTruth:Record<string, HitMiss> = {};

		let rankedValues = suggestions.map((s) => s.valueURI);
		rankedValues = Vec.idxGet(rankedValues, Vec.idxSort(suggestions.map((s) => -s.combined)));
		let beenAsserted = new Set<string>(valueURIList);

		// consider each modelled value in turn: if the value is asserted and has the highest rank, then that's a true
		// positive; note that in cases where there are multiple assertions, best results are incurred by having them
		// consecutively at the top of the stack, independent of order
		for (let n = 0; n < rankedValues.length; n++)
		{
			let valueURI = rankedValues[n];
			let isAsserted = beenAsserted.has(valueURI);
			let isHighest = isAsserted;
			for (let i = n - 1; i >= 0; i--) if (!beenAsserted.has(rankedValues[i])) {isHighest = false; break;}

			if (isAsserted)
				mapTruth[valueURI] = isHighest ? HitMiss.TP : HitMiss.FN;
			else
				mapTruth[valueURI] = isHighest ? HitMiss.FP : HitMiss.TN;
		}

		return mapTruth;
	}

	// update the display of model successes given that results are available
	private redrawRowResults(row:GridRow):void
	{
		let tp = row.assaysTP.length, tn = row.assaysTN.length;
		let fp = row.assaysFP.length, fn = row.assaysFN.length;
		let total = tp + tn + fp + fn;
		if (total == 0) return;

		row.divStatus.empty();

		const WIDTH = 200, HEIGHT = 25;
		let invScale = 1.0 / total;

		let divBar = $('<div></div>').appendTo(row.divStatus);
		divBar.css({'position': 'relative', 'width': (WIDTH + 2) + 'px', 'height': (HEIGHT + 2) + 'px'});
		divBar.css({'background-color': 'white', 'border': '1px solid black'});

		let cumulX = 0;
		let makeSeg = (size:number, bg:string):JQuery =>
		{
			if (size == 0) return null;
			let div = $('<div></div>').appendTo(divBar);
			div.css({'position': 'absolute', 'top': 0, 'height': HEIGHT + 'px', 'background': bg});
			div.css({'left': (cumulX * WIDTH * invScale) + 'px', 'width': (size * WIDTH * invScale) + 'px'});
			cumulX += size;
			return div;
		};
		makeSeg(tp, Theme.STRONG_HTML);
		makeSeg(tn, Theme.STRONGWEAK_HTML);

		const makeBit = (label:string, count:number, bgcol:string):string =>
		{
			let blk = '<span style="display: inline-block; width: 2em; height: 1em; background-color: ' + bgcol + ';"></span>';
			let bit = `<b>${label}</b>: ${count} (${(100 * count / total).toFixed(1)}%)`;
			return `<div>${blk} ${bit}</div>`;
		};
		let bits =
		[
			'<p><span style="border: 1px solid black; padding: 0.2em;">' + row.node.name + '</span></p>',
			makeBit('True Positives', tp, Theme.STRONG_HTML),
			makeBit('True Negatives', tn, Theme.STRONGWEAK_HTML),
			makeBit('False Positives', fp, 'white'),
			makeBit('False Negatives', fn, 'white'),
			'<div><b>Total Annotations</b>: <u> ' + total + '</u></div>',
		];
		let tip = '<div style="white-space: none;">' + bits.join('\n') + '</div>';
		Popover.hover(domLegacy(divBar), null, tip);

		row.divPercent.empty();
		if (tp + fn > 0)
		{
			let recall = 100 * tp / (tp + fn);
			let spanPercent = $('<span/>').appendTo(row.divPercent);
			spanPercent.text(recall.toFixed(1) + '%');
			Popover.hover(domLegacy(spanPercent), null, tip);
		}

		// (maybe check just in case *all* assays have a value?)
		row.btnOpen.css('visibility', 'visible');
	}

	// brings up a dialog that hunts for assays that would likely improve the model
	private investigateRow(row:GridRow):void
	{
		let dlg = new ModelProgressSelector(this.schema, this.assn, row.node.uri, row.node.name, this.assayIDList, this.allAssayID);
		dlg.show();
	}
}

/* EOF */ }
