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
	Heavyweight widget that creates a table showing molecules & measurements for one or more assays. Handles data acquisition,
	molecule display, view switching and customisation, and downloading of customised results.
*/

const enum MeasureTableViewType
{
	Grid = 1,
	Panel // TODO! the idea is to present compounds in rows, with the good stuff on the right, as a toggle option
}

export class MeasureTable
{
	private domParent:JQuery;
	private mainDiv:JQuery;
	private navDiv:JQuery;
	private downDiv:JQuery;
	private areaDiv:JQuery;
	private gridLines:JQuery;
	private viewType = MeasureTableViewType.Grid;
	private sortOrder = -1;
	private showValues = false;

	private btnFirst:JQuery;
	private btnPrev:JQuery;
	private btnNext:JQuery;
	private btnLast:JQuery;
	private txtPos:JQuery;
	private btnTag:JQuery;
	private btnSort:JQuery;
	private chkValues:JQuery;
	private btnSDfile:JQuery;
	private spanSize:JQuery;

	// defines the dimension & shape of the grid display
	private colWidth:number[] = [];
	private rowHeight:number[] = [];
	private gridSpan:JQuery[][] = []; // [y][x] objects placed on top of the grid
	private firstIndex = 0; // index of the molecule at the beginning of the grid/list

	private policy = wmk.RenderPolicy.defaultColourOnWhite();
	private layoutCache:Record<string, wmk.ArrangeMolecule> = {}; // compoundID -> rendering

	constructor(public data:MeasureData)
	{
		this.policy.data.pointScale = 10;
	}

	// uses the given domParent (jQuery object) to build the tree. domParent is the element into which the whole thing will
	// be rendered; the creator{Func/Params} pair is a closure for providing the new object that should be rendered inline,
	// to represent the node entry; it should generally be enclosed within a <span>
	public render(domParent:JQuery):void
	{
		this.domParent = domParent;

		domParent.empty();

		this.mainDiv = $('<div></div>').appendTo(domParent);
		this.mainDiv.css('width', '100%');

		this.navDiv = $('<div></div>').appendTo(this.mainDiv);
		this.navDiv.css('width', '100%');
		this.navDiv.css('vertical-align', 'middle');
		this.navDiv.css('padding-bottom', '0.2em');

		this.areaDiv = $('<div></div>').appendTo(this.mainDiv);
		this.areaDiv.css('left', 0);
		this.areaDiv.css('top', 0);
		this.areaDiv.css('position', 'relative');

		this.downDiv = $('<div></div>').appendTo(this.mainDiv);
		this.downDiv.css('vertical-align', 'middle');
		this.downDiv.css('padding-top', '0.2em');

		this.gridLines = $('<canvas></canvas>').appendTo(this.areaDiv);
		this.gridLines.css('left', 0);
		this.gridLines.css('top', 0);
		this.gridLines.css('position', 'relative');

		this.buildNavigation();
		this.buildDownload();
		this.updateLayout();
	}

	// ------------ private methods ------------

	private buildNavigation():void
	{
		this.navDiv.empty();

		this.btnFirst = $('<button class="btn btn-normal"><span class="glyphicon glyphicon-fast-backward"></span></button>').appendTo(this.navDiv);
		this.navDiv.append(' ');
		this.btnPrev = $('<button class="btn btn-normal"><span class="glyphicon glyphicon-step-backward"></span></button>').appendTo(this.navDiv);
		this.navDiv.append(' ');
		this.btnNext = $('<button class="btn btn-normal"><span class="glyphicon glyphicon-step-forward"></span></button>').appendTo(this.navDiv);
		this.navDiv.append(' ');
		this.btnLast = $('<button class="btn btn-normal"><span class="glyphicon glyphicon-fast-forward"></span></button>').appendTo(this.navDiv);
		this.navDiv.append(' ');

		this.txtPos = $('<input type="text" size="8"></input>').appendTo(this.navDiv);
		this.navDiv.append(' of ');
		this.spanSize = $('<span></span>').appendTo(this.navDiv);
		this.navDiv.append(' ');

		this.btnTag = $('<button class="btn btn-normal"><span class="glyphicon glyphicon-tags"></span></button>').appendTo(this.navDiv);

		this.btnFirst.click(() => this.stepPage('first'));
		this.btnPrev.click(() => this.stepPage('prev'));
		this.btnNext.click(() => this.stepPage('next'));
		this.btnLast.click(() => this.stepPage('last'));
		this.txtPos.keyup((event) => {if (event.which == 13) {this.gotoNumber(purifyTextPlainInput(this.txtPos.val()));}});
		this.btnTag.click(() => this.openTagging());

		this.navDiv.append(' ');
		this.btnSort = $('<button class="btn btn-normal"><span class="glyphicon glyphicon-arrow-down"></button>').appendTo(this.navDiv);
		this.btnSort.click(() => this.changeSort());

		this.navDiv.append(' ');
		this.chkValues = $('<input type="checkbox" id="checkShowValues"></input>').appendTo(this.navDiv);
		this.chkValues.change(() => this.toggleShowValues());
		this.navDiv.append(' ');
		let label = $('<label for="checkShowValues" style="font-weight: normal;"></label>').appendTo(this.navDiv);
		label.text('Show Values');
	}

	private buildDownload():void
	{
		this.downDiv.empty();

		this.btnSDfile = $('<button class="btn btn-normal"></button>').appendTo(this.downDiv);
		this.btnSDfile.append('<span class="glyphicon glyphicon-download-alt"></span> Download as SDfile');
		this.btnSDfile.click(() => this.startDownload());
	}

	// hard rebuild of all the content
	private updateLayout():void
	{
		if (this.viewType == MeasureTableViewType.Grid) this.updateGrid(); else this.updatePanel();
	}

	// hard rebuild for grid-style view
	private updateGrid():void
	{
		this.areaDiv.css('left', 0);
		this.areaDiv.css('width', 0);
		this.areaDiv.css('height', 0);
		let width = this.mainDiv.width();

		const IDEALSZ = 100;
		let ncols = Math.floor((width - 1) / IDEALSZ);
		let nrows = Math.min(5, Math.ceil(this.data.compounds.length / ncols));
		if (nrows == 1) ncols = Math.min(ncols, this.data.compounds.length);

		// TODO: # of compounds is a factor, don't assume lots
		this.colWidth = Vec.numberArray(IDEALSZ, ncols);
		this.rowHeight = Vec.numberArray(IDEALSZ, nrows);

		this.replaceGridSpan();
		this.redrawBackground();
		this.data.reorderCompounds(this.sortOrder);
		this.redrawGridCompounds();

		this.rosterCompounds();
	}

	// hard rebuild for panel-style view
	private updatePanel():void
	{
		// (panel tbd)
	}

	// removes all objects in the grid (i.e. takes them off the page)
	private removeGridSpan():void
	{
		for (let line of this.gridSpan) for (let span of line) if (span != null) span.remove();
		this.gridSpan = [];
	}

	// clears out the existing grid, then rebuilds it with empty values for the current dimensions
	private replaceGridSpan():void
	{
		this.removeGridSpan();
		this.gridSpan = [];
		for (let n = 0; n < this.rowHeight.length; n++) this.gridSpan.push(Vec.anyArray(null, this.colWidth.length));
	}

	// re-renders the background grid pattern
	private redrawBackground():void
	{
		let gridW = Vec.sum(this.colWidth) + 1, gridH = Vec.sum(this.rowHeight) + 1;

		//this.areaDiv.css('left', Math.floor(0.5 * (width - gridW)) + 'px');
		this.areaDiv.css('width', gridW + 'px');
		this.areaDiv.css('height', gridH + 'px');

		this.gridLines.css('width', gridW + 'px');
		this.gridLines.css('height', gridH + 'px');

		let density = pixelDensity();
		this.gridLines.attr('width', gridW * density);
		this.gridLines.attr('height', gridH * density);
		let ctx = (this.gridLines[0] as HTMLCanvasElement).getContext('2d');
		ctx.scale(density, density);

		ctx.clearRect(0, 0, gridW, gridH);
		ctx.fillStyle = '#FBFBFF';
		ctx.fillRect(0, 0, gridW, gridH);

		ctx.strokeStyle = '#CCD9E8';
		ctx.lineWidth = 1;
		for (let n = 0, x = 0.5; n <= this.colWidth.length; n++)
		{
			drawLine(ctx, x, 0, x, gridH);
			if (n < this.colWidth.length) x += this.colWidth[n];
		}
		for (let n = 0, y = 0.5; n <= this.rowHeight.length; n++)
		{
			drawLine(ctx, 0, y, gridW, y);
			if (n < this.rowHeight.length) y += this.rowHeight[n];
		}
	}

	// assuming the basic layout is the same, redraws the entries
	private redrawContent():void
	{
		if (this.viewType == MeasureTableViewType.Grid) this.redrawGridCompounds(); else this.redrawPanelCompounds();
	}

	// soft rebuild for grid-style view
	private redrawGridCompounds():void
	{
		this.txtPos.val((this.firstIndex + 1).toString());
		this.spanSize.text(this.data.compounds.length.toString());

		let count = this.colWidth.length * this.rowHeight.length;

		let x:number[] = [1], y:number[] = [1];
		for (let n = 0; n < this.colWidth.length; n++) x.push(x[n] + this.colWidth[n]);
		for (let n = 0; n < this.rowHeight.length; n++) y.push(y[n] + this.rowHeight[n]);

		for (let n = 0; n < count; n++)
		{
			let idx = this.firstIndex + n;
			if (idx >= this.data.compounds.length) continue;
			let cpd = this.data.compounds[idx];

			// if the object is already rendered, just move it to the appropriate position
			let col = n % this.colWidth.length, row = Math.floor(n / this.colWidth.length);
			if (this.gridSpan[row][col] != null)
			{
				let span = this.gridSpan[row][col];
				span.css('left', x[col] + 'px');
				span.css('top', y[row] + 'px');
				continue;
			}

			if (wmk.MolUtil.isBlank(cpd.mol)) continue;

			// ensure that the layout is in the cache
			let layout = this.layoutCache[cpd.compoundID];
			if (layout == null)
			{
				let measure = new wmk.OutlineMeasurement(0, 0, this.policy.data.pointScale);
				let effects = new wmk.RenderEffects();
				layout = new wmk.ArrangeMolecule(this.data.compounds[idx].mol, measure, this.policy, effects);
				layout.arrange();
				this.layoutCache[cpd.compoundID] = layout;
			}

			// generate the SVG representation and shove it into a span
			let isActive = cpd.isActive;
			let metavec = new wmk.MetaVector();
			new wmk.DrawMolecule(layout, metavec).draw();
			metavec.normalise();
			let w = this.colWidth[col] - 1, h = this.rowHeight[row] - 1;
			metavec.transformIntoBox(new wmk.Box(1, 1, w - 2, h - 2 - (isActive ? 2 : 0)));
			metavec.width = w;
			metavec.height = h;

			if (isActive == true) metavec.drawRect(0, h - 2, w, 2, wmk.MetaVector.NOCOLOUR, 0, 0x1362B3);

			if (this.showValues)
			{
				let vtxt = cpd.primaryValue == null ? '?' : cpd.primaryValue.toPrecision(4);
				let vsz = 8;
				let wad = layout.getMeasure().measureText(vtxt, vsz);
				metavec.drawRect(w - wad[0], 0, wad[0] + 2, wad[1] + wad[2] + 2, wmk.MetaVector.NOCOLOUR, 0, 0x40FFFFFF);
				metavec.drawText(w - 1 - wad[0], 1 + wad[1], vtxt, vsz, 0x000000);
			}

			let span = $('<span></span>').appendTo(this.areaDiv);
			span.css('position', 'absolute');
			span.css('left', x[col] + 'px');
			span.css('top', y[row] + 'px');
			span.css('width', w + 'px');
			span.css('height', h + 'px');
			span.html(metavec.createSVG());

			span.mouseenter(() => span.css('background-color', 'rgba(0,0,0,0.1)'));
			span.mouseleave(() => span.css('background-color', 'transparent'));
			span.click(() => this.openCompound(idx));

			this.gridSpan[row][col] = span;
		}
	}

	// soft rebuild for panel-style view
	private redrawPanelCompounds():void
	{
		// (panel TODO)
	}

	// for the current page, looks for any missing compounds
	private rosterCompounds():void
	{
		let pagesz = this.viewType == MeasureTableViewType.Grid ? this.colWidth.length * this.rowHeight.length : this.rowHeight.length;
		let fetch:number[] = [];
		for (let n = 0; n < pagesz; n++)
		{
			let idx = this.firstIndex + n;
			if (idx < this.data.compounds.length && this.data.compounds[idx].mol == null) fetch.push(idx);
		}
		if (fetch.length > 0) this.data.fetchCompounds(fetch, () => this.redrawContent());
	}

	// jump forward or backward
	private stepPage(offset:string):void
	{
		let newFirst = this.firstIndex;
		let pagesz = this.viewType == MeasureTableViewType.Grid ? this.colWidth.length * this.rowHeight.length : this.rowHeight.length;

		if (offset == 'first') newFirst = 0;
		else if (offset == 'last') newFirst = Math.max(0, this.data.compounds.length - pagesz);
		else if (offset == 'prev') newFirst = Math.max(0, this.firstIndex - pagesz);
		else if (offset == 'next') newFirst = Math.max(0, Math.min(this.data.compounds.length - pagesz, this.firstIndex + pagesz));

		if (newFirst == this.firstIndex) return;

		// TODO: re-use existing content rather than recreating...

		this.firstIndex = newFirst;
		this.replaceGridSpan();
		this.redrawContent();
		this.rosterCompounds();
	}

	// jump to a specific position
	private gotoNumber(val:string):void
	{
		let pos = parseInt(val);
		if (!(pos > 0)) return;
		pos--;
		let pagesz = this.viewType == MeasureTableViewType.Grid ? this.colWidth.length * this.rowHeight.length : this.rowHeight.length;
		pos = Math.max(0, Math.min(this.data.compounds.length - pagesz, pos));
		if (pos == this.firstIndex) return;

		this.firstIndex = pos;
		this.replaceGridSpan();
		this.redrawContent();
		this.rosterCompounds();
	}

	// toggles the sort order to the next state
	private changeSort():void
	{
		this.sortOrder = -this.sortOrder;
		let glyph = this.sortOrder < 0 ? 'glyphicon-arrow-down' : 'glyphicon-arrow-up';

		this.btnSort.html('<span class="glyphicon ' + glyph + '"></span>');
		this.data.reorderCompounds(this.sortOrder);
		this.replaceGridSpan();
		this.redrawContent();
		this.rosterCompounds();
	}

	// change whether or not to show measurement value overlay
	private toggleShowValues():void
	{
		this.showValues = !this.showValues;
		this.replaceGridSpan();
		this.redrawContent();
	}

	// brings up detail view for one compound
	private openCompound(idx:number):void
	{
		new MeasureDialog(this.data, idx).show();
	}

	// brings up the "tagging" dialog
	private openTagging():void
	{
		new MeasureDialog(this.data, -1).show();
	}

	private startDownload():void
	{
		let url = restBaseURL + '/servlet/DownloadCompounds/compounds.sdf.gz?assays=';
		for (let n = 0; n < this.data.assayIDList.length; n++)
		{
			if (n > 0) url += ',';
			url += this.data.assayIDList[n];
		}

		let ntags = 0;
		for (let column of this.data.columns) if (column.tagName != null)
		{
			url += '&a' + ntags + '=' + column.assayID;
			url += '&o' + ntags + '=' + encodeURIComponent(column.name);
			url += '&t' + ntags + '=' + encodeURIComponent(column.tagName);
			ntags++;
		}
		url += '&ntags=' + ntags;

		window.location.href = url;
	}
}

/* EOF */ }
