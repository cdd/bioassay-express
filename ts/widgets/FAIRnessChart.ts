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
	Plots FAIRness of the assays vs. time. FAIR = Findable, Accessible, Interoperable, Reusable. This can be estimated approximately by
	counting the proportions of assignments within the template that have been filled out with at least one value.
*/

interface AssignmentTotal
{
	propURI:string;
	groupNest:string[];
	assayIDList:number[];
}

export class FAIRnessChart
{
	private divMain:JQuery;
	private progress:ProgressBar;
	private canvasHistory:JQuery;
	private canvasCurrent:JQuery;

	private cancelled = false;
	private assayIDList:number[]; // all assay IDs (determined at the beginning)
	private roster:number[]; // assay IDs that have yet to be processed on the server side

	private days = new Map<number, Map<number, number>>(); // {tick: {assayID: fairScore}}
	private assignments:Record<string, AssignmentTotal> = {}; // index by prop/group key

	private historyDragging = false;
	private historyPos:number = null;
	private zoomDragging = false;
	private zoomStart:number = null;
	private zoomEnd:number = null;

	private fontSize = 15;

	constructor(private template:TemplateSummary, private startTime:number, private query:string,
				private width:number, private heightHistory:number, private heightCurrent:number)
	{
	}

	// uses the given domParent (jQuery object) to build the list; domParent is the element into which the whole thing will
	// be rendered
	public render(domParent:JQuery):void
	{
		this.divMain = $('<div/>').appendTo(domParent);

		this.progress = new ProgressBar(this.width, 15, () =>
		{
			this.cancelled = true;
			this.progress.remove();
		});
		this.progress.render(this.divMain);

		let density = pixelDensity();

		let setupCanvas = (height:number):JQuery =>
		{
			let canvas = $('<canvas/>').appendTo(this.divMain);
			canvas.attr({'width': this.width * density, 'height': height * density});
			canvas.css({'width': this.width + 'px', 'height': height + 'px'});
			let ctx = (canvas[0] as HTMLCanvasElement).getContext('2d');
			ctx.scale(density, density);
			ctx.fillStyle = '#F0F0F0';
			ctx.fillRect(0, 0, this.width, height);
			return canvas;
		};

		this.canvasHistory = setupCanvas(this.heightHistory);
		this.canvasHistory.mousedown(this.historyMouseDown.bind(this));
		this.canvasHistory.mouseup(this.historyMouseUp.bind(this));
		this.canvasHistory.mousemove(this.historyMouseMove.bind(this));

		this.canvasCurrent = setupCanvas(this.heightCurrent);
		this.canvasCurrent.mousedown(this.currentMouseDown.bind(this));
		this.canvasCurrent.mouseup(this.currentMouseUp.bind(this));
		this.canvasCurrent.mousemove(this.currentMouseMove.bind(this));

		let response = (data:any):void =>
		{
			this.assayIDList = Vec.sorted(data.assayIDList);
			this.roster = this.assayIDList.slice(0);

			this.processNextBatch();
		};

		if (!this.query)
		{
			let params =
			{
				'schemaURI': this.template.schemaURI,
				'withAssayID': true,
				'withUniqueID': false,
				'withCurationTime': false,
				'cutoffTime': this.startTime
			};
			callREST('REST/ListCuratedAssays', params, response, () => alert('Fetching identifiers failed'));
		}
		else
		{
			let params =
			{
				'schemaURI': this.template.schemaURI,
				'query': this.query
			};
			callREST('REST/ListQueryAssays', params, response, () => alert('Fetching identifiers failed'));
		}
	}

	// ------------ private methods ------------

	private processNextBatch():void
	{
		if (this.roster.length == 0 || this.cancelled)
		{
			this.progress.remove();
			this.maybeBlank();
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
				const days = data.days;
				const assignments:AssignmentTotal[] = data.assignments;

				// append the totals-per day into the current mapping; note that the object id's had to be serialised as strings
				for (let [strTick, content] of Object.entries(days))
				{
					let tick = parseInt(strTick);

					// special deal: in some legacy leftover cases the time value may be set to 0, so just backroll it to
					// just before the BAE project started
					if (tick == 0) tick = new Date(2016, 0, 1).getTime();

					let assayScores = this.days.get(tick);
					if (!assayScores) this.days.set(tick, assayScores = new Map<number, number>());
					for (let [strAssay, fairScore] of Object.entries(content)) assayScores.set(parseInt(strAssay), fairScore);
				}

				for (let assn of assignments)
				{
					let key = keyPropGroup(assn.propURI, assn.groupNest);
					let current = this.assignments[key];
					if (current)
						current.assayIDList = Vec.concat(current.assayIDList, assn.assayIDList);
					else
						this.assignments[key] = assn;
				}

				this.redrawHistoryChart();
				this.redrawCurrentChart();

				this.processNextBatch();
			},
			() => alert('Fetching assay history failed'));
	}

	// redraw the history graph based on the information we have so far
	private redrawHistoryChart():void
	{
		let dayTicks = Array.from(this.days.keys());
		if (dayTicks.length <= 1) return;
		Vec.sort(dayTicks);
		let firstDay = Vec.first(dayTicks), lastDay = Vec.last(dayTicks);
		let dayRange = lastDay - firstDay, invDayRange = 1.0 / dayRange;

		let dayAssayCount:number[] = Vec.numberArray(0, dayTicks.length);
		let dayFAIRTotal:number[] = Vec.numberArray(0, dayTicks.length);
		let allAssayID = new Set<number>();
		let allAssayFAIR = new Map<number, number>(); // {assayID: fairscore}
		for (let n = 0; n < dayTicks.length; n++)
		{
			let assayScores = this.days.get(dayTicks[n]);
			for (let [assayID, fairScore] of assayScores.entries())
			{
				allAssayID.add(assayID);
				allAssayFAIR.set(assayID, fairScore);
			}
			dayAssayCount[n] = allAssayID.size;
			for (let fairScore of allAssayFAIR.values()) dayFAIRTotal[n] += fairScore;
		}

		let maxAssayCount = Vec.max(dayAssayCount); // highest # of assays (usually the most recent)
		let maxFAIRScore = maxAssayCount * Object.keys(this.assignments).length; // theoretical maximum
		let invAssayCount = 1.0 / maxAssayCount, invFAIRScore = 1.0 / maxFAIRScore;

		// draw the content

		let ctx = (this.canvasHistory[0] as HTMLCanvasElement).getContext('2d');
		const {width, 'heightHistory': height} = this;
		ctx.clearRect(0, 0, width, height);

		ctx.font = `${this.fontSize}px sans-serif`;

		let markAssaysText = maxAssayCount.toString(), markAssaysWidth = ctx.measureText(markAssaysText).width;
		let markMaxText = '100%', markMaxWidth = ctx.measureText(markMaxText).width;
		let bestY = Vec.last(dayFAIRTotal) * invFAIRScore;
		let markBestText = (bestY * 100).toFixed(1) + '%', markBestWidth = ctx.measureText(markBestText).width;
		let insetX1 = Math.max(markAssaysWidth + 5, this.fontSize + 10);
		let insetX2 = Math.max(markMaxWidth, markBestWidth) + 5;
		let insetY = this.fontSize + 5;

		// fill the main area
		ctx.fillStyle = Theme.WEAK_HTML;
		let mainW = width - insetX1 - insetX2, mainH = height - insetY - 0.5 * this.fontSize;
		let x0 = insetX1, y0 = height - insetY;
		ctx.fillRect(x0, y0 - mainH, mainW, mainH);

		// plot assay count
		ctx.beginPath();
		for (let n = 0; n < dayTicks.length; n++)
		{
			let x = x0 + (dayTicks[n] - firstDay) * mainW * invDayRange;
			let y = y0 - dayAssayCount[n] * invAssayCount * mainH;
			if (n == 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
		}
		ctx.save();
		ctx.strokeStyle = 'black';
		ctx.lineWidth = 1;
		ctx.setLineDash([1, 2]);
		ctx.stroke();
		ctx.restore();

		// plot FAIRness score
		ctx.beginPath();
		for (let n = 0; n < dayTicks.length; n++)
		{
			let x = x0 + (dayTicks[n] - firstDay) * mainW * invDayRange;
			let y = y0 - dayFAIRTotal[n] * invFAIRScore * mainH;
			if (n == 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
		}
		ctx.strokeStyle = Theme.STRONG_HTML;
		ctx.lineWidth = 2;
		ctx.stroke();

		// if interactive position, include that
		let histX = this.historyPos;
		if (histX >= x0 && histX < x0 + mainW)
		{
			ctx.beginPath();
			ctx.moveTo(histX, y0);
			ctx.lineTo(histX, y0 - mainH);
			ctx.strokeStyle = 'black';
			ctx.lineWidth = 2;
			ctx.stroke();

			let tick = (histX - x0) / (mainW * invDayRange) + firstDay;
			let count = this.interpolate(dayTicks, dayAssayCount, tick);
			let fair = this.interpolate(dayTicks, dayFAIRTotal, tick);

			let date = new Date(tick), year = date.getFullYear(), month = date.getMonth() + 1, day = date.getDate();
			let fraction = fair / (count * Object.keys(this.assignments).length);
			let textLines =
			[
				year + '-' + (month < 10 ? '0' : '') + month + '-' + (day < 10 ? '0' : '') + day,
				Math.round(count) + ' assays',
				(fraction * 100).toFixed(1) + '% FAIR'
			];

			ctx.fillStyle = 'black';
			ctx.textBaseline = 'alphabetic';
			let onLeft = histX > x0 + 0.5 * mainW;
			let ty = y0 - 0.6 * mainH;
			for (let text of textLines)
			{
				let tx = onLeft ? histX - ctx.measureText(text).width - 3 : histX + 3;
				ctx.fillText(text, tx, ty);
				ty += this.fontSize * 1.5;
			}
		}

		// draw axes
		ctx.beginPath();
		ctx.moveTo(x0, y0 - mainH);
		ctx.lineTo(x0, y0);
		ctx.lineTo(x0 + mainW, y0);
		ctx.lineTo(x0 + mainW, y0 - mainH);
		ctx.strokeStyle = 'black';
		ctx.lineWidth = 1;
		ctx.stroke();

		// draw the vertical marks & Y labels
		ctx.beginPath();
		ctx.moveTo(x0, y0 - mainH);
		ctx.lineTo(x0 - 3, y0 - mainH);
		ctx.moveTo(x0 + mainW, y0 - mainH);
		ctx.lineTo(x0 + mainW + 3, y0 - mainH);
		ctx.moveTo(x0 + mainW, y0 - mainH * bestY);
		ctx.lineTo(x0 + mainW + 3, y0 - mainH * bestY);
		ctx.stroke();

		ctx.textBaseline = 'middle';
		ctx.fillStyle = 'black';
		ctx.fillText(markAssaysText, x0 - 4 - markAssaysWidth, y0 - mainH);
		/* drawing 100% is actually misleading
		ctx.fillText(markMaxText, x0 + mainW + 4, y0 - mainH);*/
		ctx.fillStyle = Theme.STRONG_HTML;
		ctx.fillText(markBestText, x0 + mainW + 4, y0 - mainH * bestY);

		// draw the year labels
		let [yearTicks, yearLabels] = this.determineYears(firstDay, lastDay);
		for (let n = 0; n < yearTicks.length; n++)
		{
			let x = x0 + (yearTicks[n] - firstDay) * mainW * invDayRange;
			let txt = yearLabels[n];

			ctx.beginPath();
			ctx.moveTo(x, y0);
			ctx.lineTo(x, y0 + (txt ? 4 : 2));
			ctx.stroke();

			if (yearLabels[n])
			{
				ctx.textBaseline = 'top';
				ctx.fillStyle = 'black';
				let w = ctx.measureText(txt).width;
				ctx.fillText(txt, x - 0.5 * w, y0 + 4);
			}
		}

		// draw rotated Y-axis labels
		ctx.save();
		ctx.rotate(-0.5 * Math.PI); // pointing up
		ctx.textBaseline = 'middle';

		let renderRotatedText = (txt:string, x:number, y:number, col:string, font:string):void =>
		{
			[x, y] = [-y, x];
			ctx.font = font;
			let tw = ctx.measureText(txt).width;
			ctx.fillStyle = col;
			ctx.fillText(txt, x - 0.5 * tw, y);
		};

		renderRotatedText('# assays', x0 - 0.5 * insetX1, y0 - 0.5 * mainH, 'black', `${this.fontSize * 1.3}px sans-serif`);
		renderRotatedText('FAIRness', x0 + mainW + 0.5 * insetX2, y0 - 0.5 * mainH, Theme.STRONG_HTML, `${this.fontSize * 1.3}px sans-serif`);
		ctx.restore();
	}

	// returns the year intervals (and corresponding labels) between a given range of date intervals
	private determineYears(firstDay:number, lastDay:number):[number[], string[]]
	{
		let ticks:number[] = [];
		let labels:string[] = [];

		let curYear = new Date().getFullYear();
		let byMonth = curYear == new Date(firstDay).getFullYear();
		const CALMONTH = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

		for (let year = curYear; year >= 2015; year--)
		{
			let tick = new Date(year, 0, 1, 0, 0, 0, 0).getTime();
			if (tick >= firstDay && tick <= lastDay)
			{
				ticks.push(tick);
				labels.push(year.toString());
			}

			// add sub-ticks for the months
			for (let n = byMonth ? 0 : 1; n < 12; n++)
			{
				let tick = new Date(year, n, 1, 0, 0, 0, 0).getTime();
				if (tick >= firstDay && tick <= lastDay)
				{
					ticks.push(tick);
					labels.push(byMonth ? CALMONTH[n] : null);
				}
			}

			if (tick < firstDay) break;
		}

		return [ticks, labels];
	}

	// for a scattergraph-style list of point pairs (order by x-axis), returns an interpolated value of y corresponding to x
	private interpolate(xval:number[], yval:number[], x:number):number
	{
		for (let n = xval.length - 2; n >= 0; n--) if (x >= xval[n])
		{
			let fr = 0;
			if (xval[n] != xval[n + 1]) fr = (x - xval[n]) / (xval[n + 1] - xval[n]);
			return yval[n] + fr * (yval[n + 1] - yval[n]);
		}
		return yval[yval.length - 1];
	}

	// redraw the current distribution graph based on the information we have so far
	private redrawCurrentChart():void
	{
		let mapAssayFAIR = new Map<number, number>(); // {assayID: fairScore} where score is the most recent
		for (let tick of Vec.sorted(Array.from(this.days.keys())))
		{
			for (let [assayID, fairScore] of this.days.get(tick)) mapAssayFAIR.set(assayID, fairScore);
		}

		let fair100 = Object.keys(this.assignments).length; // the score that corresponds to 100% FAIR

		const {width, 'heightCurrent': height} = this;

		// measure it up and start drawing

		let ctx = (this.canvasCurrent[0] as HTMLCanvasElement).getContext('2d');
		ctx.clearRect(0, 0, width, height);

		ctx.font = `${this.fontSize}px sans-serif`;

		let insetX1 = this.fontSize + 10, insetX2 = 1;
		let insetY = 3 * this.fontSize;
		let mainW = width - insetX1 - insetX2, mainH = height - insetY;
		let x0 = insetX1, y0 = height - insetY;

		// fill the main area
		ctx.fillStyle = Theme.WEAK_HTML;
		ctx.fillRect(x0, y0 - mainH, mainW, mainH);

		// prep the intensity heights for rendering
		let plotW = Math.floor(mainW);
		let intensity = Vec.numberArray(1E-10, plotW);
		const offset = fair100 * 0.01, range = fair100 + 2 * offset, invRange = 1.0 / range;
		const c = 0.1, mult = c / (2 * Math.PI), invExp = 2 * c * c;
		let plotGaussian = (fairScore:number):void =>
		{
			let b = plotW * (fairScore + offset) * invRange;
			for (let x = 0; x < plotW; x++)
			{
				let d = x - b, f = mult * Math.exp(-d * d * invExp);
				intensity[x] += f;
			}
		};
		for (let fairScore of mapAssayFAIR.values()) plotGaussian(fairScore);
		Vec.mulBy(intensity, 1.0 / Vec.max(intensity));

		// draw under-area
		ctx.beginPath();
		ctx.moveTo(x0, y0);
		for (let n = 0; n < plotW; n++)
		{
			let x = x0 + n, y = y0 - 1 - intensity[n] * (mainH - 2);
			ctx.lineTo(x, y);
		}
		ctx.lineTo(x0 + mainW, y0);
		ctx.closePath();
		ctx.fillStyle = Theme.STRONG_HTML;
		ctx.fill();

		// draw upper curve
		ctx.beginPath();
		for (let n = 0; n < plotW; n++)
		{
			let x = x0 + n, y = y0 - 1 - intensity[n] * (mainH - 2);
			if (x == 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
		}
		ctx.strokeStyle = 'black';
		ctx.lineWidth = 1;
		ctx.stroke();

		// draw axes
		ctx.beginPath();
		ctx.moveTo(x0, y0);
		ctx.lineTo(x0 + mainW, y0);
		ctx.strokeStyle = 'black';
		ctx.lineWidth = 1;
		ctx.stroke();

		// draw the score labels
		for (let percent = 0; percent <= 100; percent += 10)
		{
			let txt = percent == 0 || percent == 50 || percent == 100 ? percent.toString() + '%' : '';
			let x = x0 + plotW * (percent * fair100 * 0.01 + offset) * invRange;
			ctx.beginPath();
			ctx.moveTo(x, y0);
			ctx.lineTo(x, y0 + (txt ? 4 : 2));
			ctx.stroke();

			if (txt)
			{
				ctx.textBaseline = 'top';
				ctx.fillStyle = 'black';
				let w = ctx.measureText(txt).width, dw = ctx.measureText(txt.substring(0, txt.length - 1)).width;
				ctx.fillText(txt, Math.max(0, Math.min(width - w, x - 0.5 * dw)), y0 + 4);
			}
		}
		ctx.font = `${this.fontSize * 1.3}px sans-serif`;
		ctx.fillStyle = Theme.STRONG_HTML;
		ctx.textBaseline = 'alphabetic';
		let xlabel = 'FAIRness', xlabelWidth = ctx.measureText(xlabel).width;
		ctx.fillText(xlabel, x0 + 0.5 * mainW, height - this.fontSize * 0.3);

		// draw rotated Y-axis labels
		ctx.save();
		ctx.rotate(-0.5 * Math.PI); // pointing up
		ctx.textBaseline = 'middle';

		let renderRotatedText = (txt:string, x:number, y:number, col:string, font:string):void =>
		{
			[x, y] = [-y, x];
			ctx.font = font;
			let tw = ctx.measureText(txt).width;
			ctx.fillStyle = col;
			ctx.fillText(txt, x - 0.5 * tw, y);
		};

		renderRotatedText('frequency', x0 - 0.5 * insetX1, y0 - 0.5 * mainH, 'black', `${this.fontSize * 1.3}px sans-serif`);
		ctx.restore();

		// draw the zoomed detail, if any
		if (this.zoomStart != null)
		{
			let numAssays = mapAssayFAIR.size;
			let zoom1 = Math.max(0, Math.floor(Math.min(this.zoomStart, this.zoomEnd) - x0));
			let zoom2 = Math.min(plotW - 1, Math.ceil(Math.max(this.zoomStart, this.zoomEnd) - x0));
			if (zoom2 > zoom1) this.drawDetailZoom(ctx, numAssays, intensity, mainW, mainH, zoom1, zoom2);
		}
	}

	// draw the inset which shows a magnified subset of the whole current-content chart
	private drawDetailZoom(ctx:CanvasRenderingContext2D, numAssays:number, intensity:number[],
						   mainW:number, mainH:number, zoom1:number, zoom2:number):void
	{
		const {width, 'heightCurrent': height} = this;
		let x0 = width - mainW;

		if (this.zoomDragging)
		{
			ctx.beginPath();
			ctx.moveTo(x0 + zoom1, mainH + 2);
			ctx.lineTo(x0 + zoom1, mainH + 5);
			ctx.lineTo(x0 + zoom2, mainH + 5);
			ctx.lineTo(x0 + zoom2, mainH + 2);
			ctx.strokeStyle = Theme.STRONG_HTML;
			ctx.lineWidth = 1;
			ctx.stroke();
		}

		let maxIntens = 0;
		for (let n = zoom1; n <= zoom2; n++) maxIntens = Math.max(maxIntens, intensity[n]);
		let maxMainH = maxIntens * mainH, invIntens = 1 / (maxIntens + 1E-5);
		//let zoomH = Math.max(0.5 * mainH, mainH - maxMainH - 6); (dynamic sizing)
		let zoomH = mainH - 8;
		let y0 = zoomH + 2;
		let scaledY = Vec.numberArray(0, mainW);
		for (let n = zoom1; n <= zoom2; n++) scaledY[n] = y0 - 1 - intensity[n] * invIntens * (zoomH - 2);

		// draw the outline inset box
		ctx.fillStyle = 'white';
		ctx.fillRect(x0 + zoom1, y0 - zoomH, zoom2 - zoom1, zoomH);
		ctx.strokeStyle = 'black';
		ctx.lineWidth = 1;
		ctx.strokeRect(x0 + zoom1, y0 - zoomH, zoom2 - zoom1, zoomH);

		// show how many assays (approx.) are in the subset
		let subsetTotal = 0;
		for (let n = zoom1; n <= zoom2; n++) subsetTotal += intensity[n];
		let subsetAssays = numAssays * subsetTotal / Vec.sum(intensity);
		ctx.fillStyle = 'black';
		ctx.textBaseline = 'top';
		ctx.font = `${this.fontSize}px sans-serif`;
		let subsetText = subsetAssays.toFixed(0);
		let subsetWidth = ctx.measureText(subsetText).width;

		// try to find a good place for it, ideally inside the box
		let sx = x0 + zoom1 + 1, sy = y0 - zoomH + 2;
		let foundSpot = true;
		for (let n = zoom1, m = Math.min(zoom1 + Math.ceil(subsetWidth) + 1, zoom2); n < m; n++)
			if (scaledY[n] < y0 - zoomH + this.fontSize) {foundSpot = false; break;}
		if (!foundSpot)
		{
			sx = x0 + zoom2 - Math.ceil(subsetWidth) - 1;
			foundSpot = true;
			for (let n = Math.max(zoom1, zoom2 - Math.ceil(subsetWidth) - 1); n < zoom2; n++)
				if (scaledY[n] < y0 - zoomH + this.fontSize) {foundSpot = false; break;}
		}
		if (!foundSpot)
		{
			let sw1 = x0 + zoom1, sw2 = mainW - zoom2;
			if (sw1 > sw2 && sw1 > subsetWidth + 2) sx = x0 + zoom1 - subsetWidth - 2;
			else if (sw2 > subsetWidth + 2) sx = x0 + zoom2 + 2;
			else sx = x0;
			ctx.fillStyle = colourCanvas(0x80FFFFFF);
			ctx.fillRect(sx - 1, sy - 1, subsetWidth + 2, this.fontSize + 2);
		}

		ctx.fillStyle = 'black';
		ctx.fillText(subsetText, sx, sy);

		// draw under-area
		ctx.beginPath();
		ctx.moveTo(x0 + zoom1, y0);
		for (let n = zoom1; n <= zoom2; n++)
		{
			let x = x0 + n;
			ctx.lineTo(x, scaledY[n]);
		}
		ctx.lineTo(x0 + zoom2, y0);
		ctx.closePath();
		ctx.fillStyle = Theme.STRONG_HTML;
		ctx.fill();

		// draw upper curve
		ctx.beginPath();
		for (let n = zoom1; n <= zoom2; n++)
		{
			let x = x0 + n;
			if (x == 0) ctx.moveTo(x, scaledY[n]); else ctx.lineTo(x, scaledY[n]);
		}
		ctx.strokeStyle = 'black';
		ctx.lineWidth = 1;
		ctx.stroke();
	}

	// called at the end: if there's not enough information to make a display, replace the graph with a
	// corresponding message
	private maybeBlank():void
	{
		if (this.days.size > 1) return;

		this.divMain.empty();
		let para = $('<p/>').appendTo(this.divMain);
		para.text('There isn\'t enough historical assay data to plot a chart.');
	}

	// mouse interaction for the current content detail
	private historyMouseDown(event:JQueryMouseEventObject):void
	{
		let [x, y] = eventCoords(event, this.canvasHistory);
		if (x < 0 || x >= this.width) return;
		this.historyDragging = true;
		this.historyPos = x;
		this.redrawHistoryChart();
		event.preventDefault();
	}
	private historyMouseUp(event:JQueryMouseEventObject):void
	{
		this.historyDragging = false;
		this.redrawHistoryChart();
		event.preventDefault();
	}
	private historyMouseMove(event:JQueryMouseEventObject):void
	{
		event.preventDefault();
		if (!this.historyDragging) return;
		let [x, y] = eventCoords(event, this.canvasCurrent);
		if (x < 0 || x > this.width)
			this.historyPos = null;
		else
			this.historyPos = x;
		this.redrawHistoryChart();
	}

	// if there's any zoom action, make it stop
	private currentStopZoom():void
	{
		let refresh = this.zoomStart != null;
		this.zoomDragging = false;
		this.zoomStart = this.zoomEnd = null;
		if (refresh) this.redrawCurrentChart();
	}

	// mouse interaction for the current content detail
	private currentMouseDown(event:JQueryMouseEventObject):void
	{
		let [x, y] = eventCoords(event, this.canvasCurrent);
		if (x < 0 || x >= this.width) return;
		this.zoomDragging = true;
		this.zoomStart = this.zoomEnd = x;
		this.redrawCurrentChart();
		event.preventDefault();
	}
	private currentMouseUp(event:JQueryMouseEventObject):void
	{
		this.zoomDragging = false;
		this.redrawCurrentChart();
		event.preventDefault();
	}
	private currentMouseMove(event:JQueryMouseEventObject):void
	{
		event.preventDefault();
		if (!this.zoomDragging) return;
		let [x, y] = eventCoords(event, this.canvasCurrent);
		if (x < 0 || x > this.width || Math.abs(x - this.zoomStart) > this.width * 0.9)
		{
			this.currentStopZoom();
			return;
		}
		if (x == this.zoomEnd) return;
		this.zoomEnd = x;
		this.redrawCurrentChart();
	}
}

/* EOF */ }
