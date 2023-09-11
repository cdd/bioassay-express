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
	BarGraph: a widget for creating several particular kinds of graphs, which are bespoke to the needs of this project (i.e. don't
	try to make it too generic).
*/

export class BarGraph
{
	private domParent:JQuery;
	private canvas:JQuery;

	private bars:number[];
	private errors:number[];
	private barLabels:string[];
	private xlabel:string;
	private ylabel:string;
	private horLines:number[];

	constructor(private width:number, private height:number)
	{
	}

	// uses the given domParent (jQuery object) to build the list; domParent is the element into which the whole thing
	// will be rendered
	public render(domParent:JQuery):void
	{
		this.domParent = domParent;

		let density = pixelDensity();

		this.canvas = $('<canvas></canvas>').appendTo(domParent);
		this.canvas.attr('width', this.width * density);
		this.canvas.attr('height', this.height * density);
		this.canvas.css('width', this.width + 'px');
		this.canvas.css('height', this.height + 'px');
		let ctx = (this.canvas[0] as HTMLCanvasElement).getContext('2d');
		ctx.scale(density, density);

		ctx.fillStyle = '#F0F0F0';
		ctx.fillRect(0, 0, this.width, this.height);
	}

	// provide the bar heights (array of numbers): this defines the size of the X- and Y-axes
	public setBars(bars:number[]):void
	{
		this.bars = bars;
	}

	// provide errors for each of the bars
	public setErrors(errors:number[]):void
	{
		this.errors = errors;
	}

	// provide labels for the bars (otherwise just index)
	public setBarLabels(barLabels:string[]):void
	{
		this.barLabels = barLabels;
	}

	// provide labels for the axes
	public setAxes(xlabel:string, ylabel:string):void
	{
		this.xlabel = xlabel;
		this.ylabel = ylabel;
	}

	// specify Y-positions for which horizontal lines should be drawn
	public setHorizontalLines(horLines:number[]):void
	{
		this.horLines = horLines;
	}

	// redraws the graph, to reflect the most recently defined content
	public redraw():void
	{
		let bars = this.bars, w = this.width, h = this.height;
		let ctx = (this.canvas[0] as HTMLCanvasElement).getContext('2d');
		ctx.fillStyle = '#F0F0F0';
		ctx.fillRect(0, 0, w, h);

		let extW = bars.length;
		let extH = 0, extV = 0;
		for (let n = 0; n < bars.length; n++)
		{
			let err = this.errors ? this.errors[n] : 0;
			extH = Math.max(extH, bars[n] + err);
			extV = Math.max(extV, bars[n]);
		}
		if (this.horLines != null) for (let n = 0; n < this.horLines.length; n++) extH = Math.max(extH, this.horLines[n]);
		if (extW == 0 || extH == 0) return;

		// reserve axis label sizes
		let txt0 = '0';
		let txtX = this.barLabels ? this.barLabels[this.barLabels.length - 1] : extW.toString();
		let txtY = extV == Math.round(extV) ? extV.toString() : extV.toPrecision(5);
		ctx.font = '15px serif';
		let insetX = ctx.measureText(txtY).width + 2, insetY = 17;
		if (this.ylabel != null) insetX = Math.max(insetX, 18);

		let bw = Math.min(50, (w - insetX) / extW);

		ctx.fillStyle = 'black';
		let notchY = (h - insetY) * (1 - extV / extH);
		ctx.textBaseline = 'top';
		ctx.fillText(txtY, insetX - 2 - ctx.measureText(txtY).width, Math.max(0, notchY - 8));
		ctx.lineWidth = 1;
		ctx.strokeStyle = 'black';
		ctx.beginPath();
		ctx.moveTo(insetX, notchY);
		ctx.lineTo(insetX - 2, notchY);
		ctx.stroke();

		ctx.textBaseline = 'bottom';
		ctx.fillText(txt0, insetX - ctx.measureText(txt0).width - 2, h);
		ctx.fillText(txtX, Math.max(0, Math.min(w - ctx.measureText(txtX).width, insetX + (bars.length - 0.5) * bw - 0.5 * ctx.measureText(txt0).width)), h);

		if (this.xlabel != null)
		{
			let lw = ctx.measureText(this.xlabel).width;
			ctx.textBaseline = 'bottom';
			ctx.fillText(this.xlabel, insetX + 0.5 * (w - insetX - lw), h);
		}
		if (this.ylabel != null)
		{
			let lw = ctx.measureText(this.ylabel).width;
			ctx.save();
			ctx.textBaseline = 'bottom';
			ctx.rotate(-0.5 * Math.PI);
			ctx.fillText(this.ylabel, insetY + 0.5 * (h - notchY - insetY - lw) - h, insetX - 2);
			ctx.restore();
		}

		// draw horizontal lines, if any
		if (this.horLines != null) for (let n = 0; n < this.horLines.length; n++)
		{
			let posY = (h - insetY) * (1 - this.horLines[n] / extH);
			ctx.beginPath();
			ctx.moveTo(insetX, posY);
			ctx.lineTo(w, posY);
			ctx.strokeStyle = '#808080';
			ctx.lineWidth = 1;
			ctx.stroke();
		}

		// draw each bar
		for (let n = 0; n < bars.length; n++)
		{
			let bh = (h - insetY) * (bars[n] / extH);
			ctx.fillStyle = '#404040';
			ctx.fillRect(insetX + (n + 0.1) * bw, h - insetY - bh, 0.8 * bw, bh);

			let err = this.errors ? this.errors[n] * (h - insetY) / extH : 0;
			if (err > 0)
			{
				let cx = insetX + (n + 0.5) * bw, cy = h - insetY - bh;
				ctx.lineWidth = 1;

				let y1 = Math.max(cy - err, 1);
				ctx.beginPath();
				ctx.moveTo(cx, cy);
				ctx.lineTo(cx, y1);
				ctx.moveTo(cx - 0.3 * bw, y1);
				ctx.lineTo(cx + 0.3 * bw, y1);
				ctx.strokeStyle = '#404040';
				ctx.stroke();

				let y2 = Math.min(cy + err, h - insetY);
				ctx.beginPath();
				ctx.moveTo(cx, cy);
				ctx.lineTo(cx, y2);
				if (cy + err < h - insetY)
				{
					ctx.moveTo(cx - 0.3 * bw, y2);
					ctx.lineTo(cx + 0.3 * bw, y2);
				}
				ctx.strokeStyle = '#FFFFFF';
				ctx.stroke();
			}
		}

		// draw axes
		ctx.beginPath();
		ctx.moveTo(insetX, 0);
		ctx.lineTo(insetX, h - insetY);
		ctx.lineTo(w, h - insetY);
		ctx.lineWidth = 2;
		ctx.strokeStyle = 'black';
		ctx.stroke();
	}

	// ------------ private methods ------------

}

/* EOF */ }