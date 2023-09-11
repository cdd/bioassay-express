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
	Used to show how the activity values interact with the chosen threshold, and select values for inclusion
	within the assignment page.
*/

export class ThresholdDialog extends BootstrapDialog
{
	public actionAssign:(op:string, thresh:number) => void = null;

	private btnApply:JQuery;
	private canvas:JQuery;
	private inputThreshold:JQuery = null;

	private operator:string;
	private threshold:number;
	private dataValues:number[] = []; // null=unavailable
	private dataActives:boolean[] = []; // null=unspecified
	private withValues = 0;
	private withoutValues = 0;
	private numActives = 0;
	private numInactives = 0;
	private numUnknown = 0;
	private showLogVals = false;

	private chartBarA:number[] = [];
	private chartBarI:number[] = [];
	private chartBarU:number[] = [];
	private chartMin = 0;
	private chartMax = 0;
	private chartLog = false;
	private ratioRaw:number = null;
	private ratioLog:number = null;

	private COL_ACTIVE = '#1362B3';
	private COL_INACTIVE = '#C0C0C0';
	private COL_UNKNOWN = '#FFFFFF';
	private COL_BACKGROUND = '#E6EDF2';

	// property URIs for the thresholding special deal
	public static URI_FIELD = 'http://www.bioassayontology.org/bao#BAX_0000015';
	public static URI_UNITS = 'http://www.bioassayontology.org/bao#BAO_0002874';
	public static URI_OPERATOR = 'http://www.bioassayontology.org/bao#BAX_0000016';
	public static URI_THRESHOLD = 'http://www.bioassayontology.org/bao#BAO_0002916';

	public static OPERATOR_GREATER = 'http://purl.obolibrary.org/obo/GENEPIO_0001006';
	public static OPERATOR_LESSTHAN = 'http://purl.obolibrary.org/obo/GENEPIO_0001002';
	public static OPERATOR_GREQUAL = 'http://purl.obolibrary.org/obo/GENEPIO_0001005';
	public static OPERATOR_LTEQUAL = 'http://purl.obolibrary.org/obo/GENEPIO_0001003';
	public static OPERATOR_EQUAL = 'http://purl.obolibrary.org/obo/GENEPIO_0001004';

	constructor(private mdata:MeasureData, private field:string, private unitsURI:string, private unitsLabel:string,
				private initOp:string, initThresh:number)
	{
		super('Threshold');

		this.operator = initOp;
		this.threshold = initThresh;

		this.deriveData();
	}

	// ------------ private methods ------------

	// pull the data values out of the raw container
	private deriveData():void
	{
		let column = -1;
		for (let n = 0; n < this.mdata.columns.length; n++) if (this.mdata.columns[n].name == this.field) {column = n; break;}
		if (column < 0) return; // rare, but make it graceful

		for (let cpd of this.mdata.compounds)
		{
			let values:number[] = [];
			for (let measure of cpd.measurements) if (measure.column == column) values.push(measure.value);
			if (values.length == 0) values.push(null);
			for (let n = 0; n < values.length; n++)
			{
				this.dataValues.push(values[n]);
				this.dataActives.push(cpd.isActive);
			}
		}

		// count up totals
		for (let v of this.dataValues) if (v == null) this.withoutValues++; else this.withValues++;
		for (let a of this.dataActives) if (a == null) this.numUnknown++; else if (a) this.numActives++; else this.numInactives++;

		// decide what to do about using -log10 by default, or at all
		for (let v of this.dataValues) if (v != null && v <= 0) this.showLogVals = null; // disallowed
		if (this.showLogVals != null)
		{
			// molar concentrations are generally best shown in log units
			for (let u of ['obo:UO_0000073', 'obo:UO_0000064', 'obo:UO_0000063', 'obo:UO_0000062', 'obo:UO_0000065', 'obo:UO_0000066'])
				if (this.unitsURI == expandPrefix(u)) {this.showLogVals = true; break;}
			// (maybe for other types consider guessing based on disparate orders of magnitude)
		}
	}

	protected populateContent():void
	{
		let pinfo = $('<p></p>').appendTo(this.content);
		let num = this.dataValues.length;

		pinfo.append('Total ' + num + ' compound' + (num == 1 ? '' : 's') + '. ');
		if (this.withValues == num) pinfo.append('All of them have numeric values. ');
		else if (this.withoutValues == num) pinfo.append('None of them have numeric values. ');
		else pinfo.append(this.withValues + (this.withValues == 1 ? ' has a value, ' : ' have values, ') +
						  this.withoutValues + (this.withoutValues == 1 ? ' does' : ' do') + ' not. ');

		pinfo.append(this.numActives + (this.numActives == 1 ? ' is' : ' are') + ' marked as active, ' +
						  this.numInactives + (this.numInactives == 1 ? ' is' : ' are') + ' marked as inactive');
		if (this.numUnknown == 0) pinfo.append('.');
		else pinfo.append(', ' + this.numUnknown + (this.numUnknown == 1 ? ' is' : ' are') + ' unclassified.');

		let table = $('<table></table>').appendTo($('<p align="center"></p>').appendTo(this.content));

		let tr = $('<tr></tr>').appendTo(table);
		let tdKey = $('<td style="text-align: left; vertical-align: middle;"></td>').appendTo(tr);
		let tdCtrl = $('<td style="text-align: right; vertical-align: middle;"></td>').appendTo(tr);

		let addKey = (title:string, col:string):void =>
		{
			let span = $('<span></span>').appendTo(tdKey);
			span.css('display', 'inline-block');
			span.css('width', '2em');
			span.css('height', '0.8em');
			span.css('background-color', col);
			span.css('border', '0.5px solid black');
			tdKey.append(' ' + title + ' ');
		};
		if (this.numActives > 0) addKey('active', this.COL_ACTIVE);
		if (this.numInactives > 0) addKey('inactive', this.COL_INACTIVE);
		if (this.numUnknown > 0) addKey('unknown', this.COL_UNKNOWN);

		if (this.showLogVals != null)
		{
			let chk = $('<input type="checkbox"></input>');
			chk.prop('checked', this.showLogVals);
			chk.click(() => {this.showLogVals = !this.showLogVals; this.redrawCanvas();});

			let label = $('<label></label>').appendTo(tdCtrl);
			label.append(chk);
			label.append(' <font style="font-weight: normal;">-log<sub>10</sub></font>');
		}

		tr = $('<tr></tr>').appendTo(table);
		let tdCanvas = $('<td colspan="2" style="text-align: center;"></td>').appendTo(tr);
		this.canvas = $('<canvas></canvas>').appendTo(tdCanvas);
		let width = Math.floor(0.7 * $(window).width()), height = Math.floor(0.4 * $(window).height());
		this.canvas.css('width', width + 'px');
		this.canvas.css('height', height + 'px');
		let density = pixelDensity();
		this.canvas.attr('width', width * density);
		this.canvas.attr('height', height * density);

		this.redrawCanvas();

		tr = $('<tr></tr>').appendTo(table);
		let tdResult = $('<td colspan="2"></td>').appendTo(tr);
		this.populateResult(tdResult);
	}

	// fills out the line that shows the current threshold selection and permits editing
	private populateResult(parent:JQuery):void
	{
		let para = $('<p style="padding-top: 0.5em;"></p>').appendTo(parent);
		let tr = $('<tr></tr>').appendTo($('<table align="center"></table>').appendTo(para));
		let td:JQuery[] = [];
		for (let n = 0; n < 4; n++) td.push($('<td style="vertical-align: middle; padding-left: 0.5em;"></td>').appendTo(tr));

		td[0].css('font-weight', 'bold');
		td[0].text(this.field);

		let dropGroup = $('<div class="btn-group"></div>').appendTo(td[1]);
		let dropButton = $('<button type="button" class="form-control btn btn-action dropdown-toggle" data-toggle="dropdown">').appendTo(dropGroup);
		let dropTitle = $('<span>Operator</span>').appendTo(dropButton);
		let dropList = $('<ul class="dropdown-menu" role="menu"></ul>').appendTo(dropGroup);
		dropList.css('min-width', '2em');
		dropList.css('max-width', '4em');

		const OPTIONS =
		[
			['&gt;', ThresholdDialog.OPERATOR_GREATER],
			['&lt;', ThresholdDialog.OPERATOR_LESSTHAN],
			['&ge;', ThresholdDialog.OPERATOR_GREQUAL],
			['&le;', ThresholdDialog.OPERATOR_LTEQUAL],
			['=', ThresholdDialog.OPERATOR_EQUAL]
		];
		for (let [symbol, uri] of OPTIONS)
		{
			if (this.operator == uri) dropTitle.html(symbol);

			let li = $('<li></li>').appendTo(dropList);
			let href = $('<a href="#"></a>').appendTo(li);
			href.html(symbol);
			href.click((event:JQueryEventObject) =>
			{
				if (uri != this.operator)
				{
					dropTitle.html(symbol);
					this.operator = uri;
					this.redrawCanvas();
				}
				event.preventDefault();
			});
		}

		if (this.inputThreshold == null)
		{
			this.inputThreshold = $('<input type="text" size="12"></input>');
			if (this.threshold != null) this.inputThreshold.val(this.threshold.toString());
			this.inputThreshold.keyup((event) =>
			{
				if (event.which == 13) this.changeThreshold(purifyTextPlainInput(this.inputThreshold.val()));
			});
		}
		td[2].append(this.inputThreshold);

		td[3].text(this.unitsLabel);
	}

	// redraws all of the graphics; it's actually faster than it looks
	private redrawCanvas():void
	{
		let width = this.canvas.width(), height = this.canvas.height();

		let ctx = (this.canvas[0] as HTMLCanvasElement).getContext('2d');
		ctx.save();
		let density = pixelDensity();
		ctx.scale(density, density);
		ctx.fillStyle = this.COL_BACKGROUND;
		ctx.fillRect(0, 0, width, height);

		ctx.font = '14px sans-serif';

		let topHeight = 20, axisHeight = 20, mainHeight = height - axisHeight - topHeight;
		let unkWidth = 0, chartWidth = width;

		// draw the "without values" as a stack
		if (this.withoutValues > 0)
		{
			let wa = 0, wi = 0, wu = 0;
			for (let n = 0; n < this.dataValues.length; n++) if (this.dataValues[n] == null)
				if (this.dataActives[n] == null) wu++; else if (this.dataActives[n]) wa++; else wi++;

			let full = this.determineCoverageRatio(this.showLogVals), empty = 1 - full;

			let extraEmpty = (full + empty) / full;
			let unkArea = width * mainHeight * this.withoutValues / (this.withoutValues + this.withValues * extraEmpty);

			unkWidth = Math.ceil(unkArea / mainHeight);
			chartWidth = width - unkWidth;

			if (wu > 0)
			{
				ctx.fillStyle = this.COL_UNKNOWN;
				ctx.fillRect(0, topHeight, unkWidth, mainHeight);
			}
			if (wi > 0)
			{
				ctx.fillStyle = this.COL_INACTIVE;
				let h = mainHeight * (wa + wi) / this.withoutValues;
				ctx.fillRect(0, topHeight + mainHeight - h, unkWidth, h);
			}
			if (wa > 0)
			{
				ctx.fillStyle = this.COL_ACTIVE;
				let h = mainHeight * wa / this.withoutValues;
				ctx.fillRect(0, topHeight + mainHeight - h, unkWidth, h);
			}
		}

		// draw the "with values" as a plotted chart
		this.ensureChart(chartWidth, this.showLogVals);
		let chartTotal = this.chartBarA.slice(0);
		for (let n = 0; n < chartWidth; n++) chartTotal[n] += this.chartBarI[n] + this.chartBarU[n];
		let chartExtent = Vec.max(chartTotal), invExtent = 1.0 / chartExtent;

		if (this.numUnknown > 0)
		{
			ctx.beginPath();
			ctx.moveTo(unkWidth, topHeight + mainHeight);
			for (let n = 0; n < chartWidth; n++) ctx.lineTo(unkWidth + n + 1, topHeight + mainHeight * (1 - chartTotal[n] * invExtent));
			ctx.lineTo(width, topHeight + mainHeight);
			ctx.closePath();
			ctx.fillStyle = this.COL_UNKNOWN;
			ctx.fill();
		}

		if (this.numInactives > 0)
		{
			ctx.beginPath();
			ctx.moveTo(unkWidth, topHeight + mainHeight);
			for (let n = 0; n < chartWidth; n++) ctx.lineTo(unkWidth + n + 1, topHeight + mainHeight * (1 - (chartTotal[n] - this.chartBarU[n]) * invExtent));
			ctx.lineTo(width, topHeight + mainHeight);
			ctx.closePath();
			ctx.fillStyle = this.COL_INACTIVE;
			ctx.fill();
		}

		if (this.numActives > 0)
		{
			ctx.beginPath();
			ctx.moveTo(unkWidth, topHeight + mainHeight);
			for (let n = 0; n < chartWidth; n++) ctx.lineTo(unkWidth + n + 1, topHeight + mainHeight * (1 - (chartTotal[n] - this.chartBarU[n] - this.chartBarI[n]) * invExtent));
			ctx.lineTo(width, topHeight + mainHeight);
			ctx.closePath();
			ctx.fillStyle = this.COL_ACTIVE;
			ctx.fill();
		}

		// line boundary
		ctx.beginPath();
		ctx.moveTo(unkWidth, topHeight + mainHeight);
		for (let n = 0; n < chartWidth; n++) ctx.lineTo(unkWidth + n + 1, topHeight + mainHeight * (1 - chartTotal[n] * invExtent));
		ctx.lineWidth = 0.1;
		ctx.strokeStyle = 'black';
		ctx.stroke();

		// now X-axis labels
		let axis = new wmk.AxisLabeller(chartWidth, this.chartMin, this.chartMax);
		axis.textWidth = (str:string):number => ctx.measureText(str).width;
		axis.inverse = this.showLogVals ? (val:number):number => Math.pow(10, -val) : (val:number):number => val;
		axis.calculate();
		for (let notch of axis.notches)
		{
			let nrad = 1, nx = Math.min(width - nrad, Math.max(nrad, notch.pos));
			ctx.fillStyle = 'black';
			ctx.beginPath();
			ctx.ellipse(nx, height - axisHeight + nrad, nrad, nrad, 0, 0, 2 * Math.PI, true);
			ctx.fill();

			let tw = ctx.measureText(notch.label).width;
			let tx = unkWidth + notch.pos - 0.5 * tw;
			tx = Math.max(0, Math.min(width - tw, tx));
			ctx.fillStyle = 'black';
			ctx.textBaseline = 'middle';
			ctx.fillText(notch.label, tx, height - 0.5 * axisHeight);
		}

		// draw where the threshold is currently
		if (this.threshold != null)
		{
			let tv = this.showLogVals ? -Math.log10(this.threshold) : this.threshold;
			let tx = unkWidth + (tv - this.chartMin) * chartWidth / (this.chartMax - this.chartMin);
			let dir = this.operator == ThresholdDialog.OPERATOR_GREATER || this.operator == ThresholdDialog.OPERATOR_GREQUAL ? 1 :
					  this.operator == ThresholdDialog.OPERATOR_LESSTHAN || this.operator == ThresholdDialog.OPERATOR_LTEQUAL ? -1 : 0;
			if (this.showLogVals) dir = -dir;
			let endA = dir < 0 ? unkWidth + 2 : dir > 0 ? width - 2 : null;
			let endI = dir > 0 ? unkWidth + 2 : dir < 0 ? width - 2 : null;

			ctx.lineWidth = 1.5;
			ctx.strokeStyle = 'black';
			drawLine(ctx, tx, topHeight + mainHeight, tx, 0);
			if (dir != 0)
			{
				drawLine(ctx, tx, topHeight, endA - 2 * dir, topHeight);
				ctx.save();
				ctx.setLineDash([1, 3]);
				ctx.strokeStyle = '#808080';
				drawLine(ctx, tx, topHeight, endI + 2 * dir, topHeight);
				ctx.restore();

				const arrowW = 10, arrowH = 4;

				ctx.beginPath();
				ctx.moveTo(endA, topHeight);
				ctx.lineTo(endA - dir * arrowW, topHeight - arrowH);
				ctx.lineTo(endA - dir * arrowW, topHeight + arrowH);
				ctx.closePath();
				ctx.fillStyle = 'black';
				ctx.fill();

				ctx.beginPath();
				ctx.moveTo(endI, topHeight);
				ctx.lineTo(endI + dir * arrowW, topHeight - arrowH);
				ctx.lineTo(endI + dir * arrowW, topHeight + arrowH);
				ctx.closePath();
				ctx.fillStyle = '#808080';
				ctx.fill();

				let twa = ctx.measureText('active').width, twi = ctx.measureText('inactive').width;
				ctx.textBaseline = 'middle';
				ctx.fillStyle = 'black';
				let txa = dir > 0 ? Math.min(tx + 0.5 * (width - tx - twa), width - twa) : Math.max(unkWidth + 0.5 * (tx - unkWidth - twa), 0);
				ctx.fillText('active', txa, 0.5 * topHeight);
				ctx.fillStyle = '#808080';
				let txi = dir < 0 ? Math.min(tx + 0.5 * (width - tx - twi), width - twi) : Math.max(unkWidth + 0.5 * (tx - unkWidth - twi), 0);
				ctx.fillText('inactive', txi, 0.5 * topHeight);
			}
		}

		ctx.restore();

		this.canvas.off('click');
		this.canvas.click((event:JQueryEventObject) =>
		{
			let [x, y] = eventCoords(event, this.canvas);
			if (y < topHeight || y > height - axisHeight || x < unkWidth) return;
			let v = (x - unkWidth) / chartWidth * (this.chartMax - this.chartMin) + this.chartMin;
			if (this.showLogVals) v = Math.pow(10, -v);
			let str = v.toPrecision(4);
			this.inputThreshold.val(str);
			this.changeThreshold(str);
		});
	}

	// change the threshold, and update the display accordingly
	private changeThreshold(value:any):void
	{
		let threshold = parseFloat(value);
		if (threshold == null || threshold == this.threshold) return;
		this.threshold = threshold;
		this.redrawCanvas();
	}

	// action time: inform caller and close
	private doApply():void
	{
		this.changeThreshold(purifyTextPlainInput(this.inputThreshold.val()));
		if (this.operator == null)
		{
			alert('Select an operator before applying.');
			return;
		}
		if (this.threshold == null || !Number.isFinite(this.threshold))
		{
			alert('Select a threshold before applying.');
			return;
		}
		this.actionAssign(this.operator, this.threshold);
		this.dlg.modal('hide');
	}

	// recreates the chart-height datastructure, or does nothing if it is already in the right state; the height is a Gaussian smear of
	// all the available values
	private ensureChart(width:number, logVals:boolean):void
	{
		if (this.chartBarA.length == width && this.chartLog == logVals) return;

		// setup raw materials
		this.chartBarA = Vec.numberArray(0, width);
		this.chartBarI = Vec.numberArray(0, width);
		this.chartBarU = Vec.numberArray(0, width);
		this.chartLog = logVals;
		let chartVals:number[] = [], chartIdx:number[] = [];
		for (let n = 0; n < this.dataValues.length; n++)
		{
			let v = this.dataValues[n];
			if (v == null) continue;
			if (logVals) v = -Math.log10(v);
			chartVals.push(v);
			chartIdx.push(n);
		}
		this.chartMin = Vec.min(chartVals);
		this.chartMax = Vec.max(chartVals);
		let preRange = this.chartMax - this.chartMin;
		this.chartMin -= preRange * 0.02;
		this.chartMax += preRange * 0.02;

		// plot Gaussian blobs for each value (integral = area of 1, span of half max = 1 pixel wide)
		const range = this.chartMax - this.chartMin, invRange = 1.0 / range;
		const c = 0.5, mult = c / (2 * Math.PI), invExp = 2 * c * c;
		for (let n = 0; n < chartIdx.length; n++)
		{
			let v = chartVals[n], b = width * (v - this.chartMin) * invRange;
			let a = this.dataActives[chartIdx[n]];
			let bars = a == null ? this.chartBarU : a ? this.chartBarA : this.chartBarI;
			for (let x = 0; x < width; x++)
			{
				let d = x - b, f = mult * Math.exp(-d * d * invExp);
				bars[x] += f;
			}
		}
	}

	// calculations the fraction of the chart that is occupied (0..1); the rest is blank space
	private determineCoverageRatio(logVals:boolean):number
	{
		if (!logVals && this.ratioRaw != null) return this.ratioRaw;
		if (logVals && this.ratioLog != null) return this.ratioLog;

		let state:any[] = [this.chartBarA, this.chartBarI, this.chartBarU, this.chartMin, this.chartMax, this.chartLog];

		const WIDTH = 100;
		this.ensureChart(WIDTH, logVals);
		let chartTotal = this.chartBarA.slice(0);
		for (let n = 0; n < WIDTH; n++) chartTotal[n] += this.chartBarI[n] + this.chartBarU[n];
		let chartExtent = Vec.max(chartTotal);
		let full = 0, empty = 0;
		for (let n = 0; n < WIDTH; n++) {let h = chartTotal[n]; full += h; empty += chartExtent - h;}

		this.chartBarA = state[0];
		this.chartBarI = state[1];
		this.chartBarU = state[2];
		this.chartMin = state[3];
		this.chartMax = state[4];
		this.chartLog = state[5];

		let ratio = full / (full + empty);
		if (!logVals) this.ratioRaw = ratio; else this.ratioLog = ratio;
		return ratio;
	}
}

/* EOF */ }
