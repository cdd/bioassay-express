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
	Outlines the template structure and provides stats for how each assignment is populated. Then allows
	drilling down on how the values are modelled.
*/

// content returned from webservice
interface AssignmentTotal
{
	propURI:string;
	groupNest:string[];
	assayIDList:number[];
	countDefined:number;
	countAbsence:number;
	countMismatch:number;
}

// accumulated information for a row (representing an assignment)
interface GridRow
{
	assn:SchemaAssignment;

	divCount:JQuery;
	divSummary:JQuery;
	btnOpen:JQuery;

	assayCount:number;
	countDefined:number;
	countAbsence:number;
	countMismatch:number;
}

/*
	ModelProgress is the enclosing panel, which shows the schema hierarchy on the left. Whenever an assignment is activated, it
	brings up a detail summary immediately to the right, which is handled by the ModelProgressDetail class.
*/

export class ModelProgress
{
	private divMain:JQuery;
	private progress:ProgressBar;
	private divDetail:JQuery;

	private cancelled = false;
	private schema:SchemaSummary;
	private assayIDList:number[]; // all assay IDs using the schema (determined at the beginning)
	private roster:number[]; // assay IDs that have yet to be processed on the server side
	private gridRows:GridRow[] = [];
	private mapRows:Record<string, GridRow> = {}; // prop:group as key
	private activeRow:GridRow = null;
	private detail:ModelProgressDetail = null;

	constructor(private template:TemplateSummary)
	{
	}

	// uses the given domParent (jQuery object) to build the list; domParent is the element into which the whole thing
	// will be rendered
	public render(domParent:JQuery):void
	{
		this.divMain = $('<div></div>').appendTo(domParent);
		this.divMain.css({'white-space': 'nowrap'});

		this.progress = new ProgressBar(500, 15, () =>
		{
			this.cancelled = true;
			this.progress.remove();
		});
		this.progress.render(this.divMain);

		let schemaURI = this.template.schemaURI;
		TemplateManager.ensureTemplates([schemaURI], () =>
		{
			this.schema = TemplateManager.getTemplate(schemaURI);

			let params =
			{
				'schemaURI': schemaURI,
				'withAssayID': true,
				'withUniqueID': false,
				'withCurationTime': false,
			};
			callREST('REST/ListCuratedAssays', params,
				(data:any) =>
				{
					this.assayIDList = Vec.sorted(data.assayIDList);
					this.roster = this.assayIDList.slice(0);

					this.setupGrid();
				},
				() => alert('Fetching search identifiers failed'));
		});
	}

	// ------------ private methods ------------

	private setupGrid():void
	{
		let divSummary = $('<div></div>').appendTo(this.divMain).css({'display': 'inline-block', 'vertical-align': 'top'});
		let grid = $('<div></div>').appendTo(divSummary);
		grid.css('display', 'grid');
		grid.css({'align-items': 'center', 'justify-items': 'left', 'justify-content': 'start'});
		grid.css({'grid-column-gap': '0.5em', 'grid-row-gap': '0.2em'});
		grid.css('grid-template-columns', '[assignment] auto [count] auto [detail] auto [button] auto [end]');
		grid.css({'padding': '0.5em', 'margin': '0 1em 0.5em 0'});
		grid.css({'background-color': '#FCFCFC', 'border': '1px solid #808080'});
		grid.css({'box-shadow': '#C0C0C0 0px 0px 5px'});

		this.divDetail = $('<div></div>').appendTo(this.divMain).css({'display': 'inline-block', 'vertical-align': 'top'});

		let row = 1;
		let makeHeading = (label:string, column:string):void =>
		{
			let div = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': column});
			div.css({'font-weight': 'bold'});
			div.text(label);
		};
		makeHeading('Assignment', 'assignment');
		makeHeading('Count', 'count');
		makeHeading('Detail', 'detail');

		let hier = new SchemaHierarchy(this.schema);

		let renderAssignment = (assn:SchemaHierarchyAssignment, level:number):void =>
		{
			row++;

			let divAssn = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': 'assignment'});
			let divCount = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': 'count'});
			let divDetail = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': 'detail'});
			let divButton = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': 'button'});

			let label = $('<span></span>').appendTo(divAssn).css('margin-left', level + 'em');
			label.text(assn.name);

			let tip = '<p><b>Property</b>: ' + collapsePrefix(assn.propURI) + '</p>';
			if (assn.descr) tip += '<p align="left">' + escapeHTML(assn.descr) + '</p>';
			Popover.click(domLegacy(label), assn.name, tip);

			divCount.css('justify-self', 'end');

			let gridRow:GridRow =
			{
				'assn': assn,
				'divCount': divCount,
				'divSummary': $('<div></div>').appendTo(divDetail),
				'btnOpen': null,
				'assayCount': 0,
				'countDefined': 0,
				'countAbsence': 0,
				'countMismatch': 0,
			};

			if (assn.suggestions == SuggestionType.Full)
			{
				gridRow.btnOpen = $('<button class="btn btn-xs btn-normal"></button>').appendTo(divButton);
				gridRow.btnOpen.append($('<span class="glyphicon glyphicon-chevron-right" style="height: 1.2em;"></span>'));
				gridRow.btnOpen.click(() => this.toggleRow(gridRow));
			}

			this.gridRows.push(gridRow);
			this.mapRows[keyPropGroup(assn.propURI, assn.groupNest)] = gridRow;
		};
		let renderGroup = (group:SchemaHierarchyGroup, level:number):void =>
		{
			for (let assn of group.assignments) renderAssignment(assn, level);
			for (let sub of group.subGroups)
			{
				row++;

				let divAssn = $('<div></div>').appendTo(grid).css({'grid-row': row.toString(), 'grid-column': 'assignment'});
				let label = $('<span></span>').appendTo(divAssn).css({'margin-left': level + 'em', 'text-decoration': 'underline'});
				label.text(sub.name);

				renderGroup(sub, level + 1);
			}
		};

		renderGroup(hier.root, 0);

		this.processNextBatch();
	}

	private processNextBatch():void
	{
		if (this.roster.length == 0 || this.cancelled)
		{
			this.progress.remove();
			return;
		}

		let sz = this.assayIDList.length;
		this.progress.setProgress((sz - this.roster.length) / sz);

		let batch = this.roster.splice(0, Math.min(100, this.roster.length));

		let params =
		{
			'assayIDList': batch
		};
		callREST('REST/TallyCompletion', params,
			(data:any) =>
			{
				const assignments:AssignmentTotal[] = data.assignments;
				for (let assnTot of assignments)
				{
					let row = this.mapRows[keyPropGroup(assnTot.propURI, assnTot.groupNest)];
					if (!row) continue;

					row.assayCount += assnTot.assayIDList.length;
					row.countDefined += assnTot.countDefined;
					row.countAbsence += assnTot.countAbsence;
					row.countMismatch += assnTot.countMismatch;

					this.redrawRow(row);
				}

				this.processNextBatch();
			},
			() => alert('Fetching assay details'));

		setTimeout(() => this.processNextBatch(), 100);
	}

	private toggleRow(row:GridRow):void
	{
		// dull the current row button
		if (this.activeRow)
		{
			this.activeRow.btnOpen.removeClass('btn-action');
			this.activeRow.btnOpen.addClass('btn-normal');
			this.activeRow.btnOpen.empty();
			this.activeRow.btnOpen.append($('<span class="glyphicon glyphicon-chevron-right" style="height: 1.2em;"></span>'));
		}

		// shutdown the task, if any
		if (this.detail)
		{
			this.detail.cancel();
			this.detail = null;
			this.divDetail.empty();
		}

		// if clicked row isn't the active row, make that the one
		if (this.activeRow !== row)
		{
			this.activeRow = row;
			this.activeRow.btnOpen.removeClass('btn-normal');
			this.activeRow.btnOpen.addClass('btn-action');
			this.activeRow.btnOpen.empty();
			this.activeRow.btnOpen.append($('<span class="glyphicon glyphicon-chevron-down" style="height: 1.2em;"></span>'));

			this.detail = new ModelProgressDetail(this.schema, row.assn, this.assayIDList);
			this.detail.delta = row.btnOpen.offset().top - this.divMain.offset().top;
			this.detail.render(this.divDetail);
		}
		else this.activeRow = null;
	}

	private redrawRow(row:GridRow):void
	{
		row.divCount.text(row.assayCount.toString());

		row.divSummary.empty();
		if (row.assayCount > 0)
		{
			const WIDTH = 300, HEIGHT = 25;
			let total = this.assayIDList.length, invScale = 1.0 / total;
			let divBar = $('<div></div>').appendTo(row.divSummary);
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
			makeSeg(row.countDefined, Theme.STRONG_HTML);
			makeSeg(row.countAbsence, Theme.STRONGWEAK_HTML);
			makeSeg(row.countMismatch, 'black');

			let missing = total - row.countDefined - row.countAbsence - row.countMismatch;
			const makeBit = (label:string, count:number, bgcol:string):string =>
			{
				 let blk = '<span style="display: inline-block; width: 2em; height: 1em; background-color: ' + bgcol + ';"></span>';
				 let bit = `<b>${label}</b>: ${count} (${(100 * count / total).toFixed(1)}%)`;
				 return `<div>${blk} ${bit}</div>`;
			};
			let bits =
			[
				makeBit('Defined', row.countDefined, Theme.STRONG_HTML),
				makeBit('Absence', row.countAbsence, Theme.STRONGWEAK_HTML),
				makeBit('Mismatch', row.countMismatch, 'black'),
				makeBit('Blank', missing, 'white'),
			];
			let tip = '<div style="white-space: none;">' + bits.join('\n') + '</div>';
			Popover.hover(domLegacy(divBar), null, tip);
		}
	}
}

/* EOF */ }
