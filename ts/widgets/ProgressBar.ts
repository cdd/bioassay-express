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
	General purpose progress bar, with its own stop button.
*/

export class ProgressBar
{
	private divMain:JQuery;
	private canvas:JQuery;
	private ctx:CanvasRenderingContext2D;
	private btnStopped:JQuery;
	private progress = 0;

	constructor(private width:number, private height:number, private onStopped:() => void)
	{
	}

	// uses the given domParent (jQuery object) to build the list; domParent is the element into which the whole thing 
	// will be rendered
	public render(domParent:JQuery):void
	{
		this.divMain = $('<div></div>').appendTo(domParent);
		this.divMain.css({'display': 'flex', 'justify-content': 'start', 'align-items': 'center', 'margin-bottom': '0.5em'});

		let density = pixelDensity();
		
		this.canvas = $('<canvas></canvas>').appendTo(this.divMain);
		this.canvas.attr('width', this.width * density);
		this.canvas.attr('height', this.height * density);
		this.canvas.css('width', this.width + 'px');
		this.canvas.css('height', this.height + 'px');
		this.ctx = (this.canvas[0] as HTMLCanvasElement).getContext('2d');
		this.ctx.scale(density, density);
		
		this.btnStopped = $('<button class="btn btn-xs btn-action"></button').appendTo(this.divMain);
		this.btnStopped.append('<span class="glyphicon glyphicon-stop"></span>');
		this.btnStopped.click(() => this.onStopped());

		this.redraw();
	}

	// cleanup after itself, i.e. take everything off the page
	public remove():void
	{
		this.divMain.remove();
	}

	// change the meter value and redraw; progress should be between 0..1
	public setProgress(progress:number):void
	{
		if (this.progress == progress) return;
		this.progress = progress;
		this.redraw();
	}

	// ------------ private methods ------------

	private redraw():void
	{
		let ctx = this.ctx, w = 0.5 * this.width, h = 0.5 * this.height;
		
		let density = pixelDensity();
		ctx.save();
		ctx.scale(density, density);
		ctx.clearRect(0, 0, w, h);
		
		let lw = 5, x1 = lw, x2 = w - lw, midY = 0.5 * h;
		ctx.beginPath();
		ctx.moveTo(x1, midY);
		ctx.lineTo(x2, midY);
		ctx.lineWidth = lw - 1;
		ctx.lineCap = 'round';
		ctx.strokeStyle = '#E0E0E0';
		ctx.stroke();
		
		if (this.progress > 0)
		{
			let sw = (x2 - x1) * this.progress;
			ctx.beginPath();
			ctx.moveTo(x1, midY);
			ctx.lineTo(x1 + sw, midY);
			ctx.lineWidth = lw;
			ctx.lineCap = 'round';
			ctx.strokeStyle = Theme.STRONG_HTML;
			ctx.stroke();
		}

		ctx.restore();
	}
}

/* EOF */ }