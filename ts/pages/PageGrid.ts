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
	Supporting functionality for the grid page.
*/

export class PageGrid
{
	private divAssays:JQuery;
	private divCompounds:JQuery;
	private divCompoundFreq:JQuery;
	private divCompoundProbe:JQuery;
	private divCompoundQuery:JQuery;
	private divProgress:JQuery;
	private divGrid:JQuery;
	private compoundSource = 'frequent';
	private maxCompounds = 100;
	private chkProbesOnly:JQuery = null;
	private molSimilar:wmk.Molecule = null;
	private widgetStructure:JQuery = null;
	private btnCompounds:JQuery = null;
	private grid:AssayGrid = null;

	// looking for compounds corresponding to the assay list
	private searchRunning = false;
	private acquisition:CompoundAcquire = null;
	private PROGR_W = 500;
	private PROGR_H = 40;
	private canvasProgress:JQuery = null;
	private textProgress:JQuery = null;

	// obtained from the CompoundAcquire instance
	private compounds:number[][] = [];
	private hashes:number[] = [];
	private score:number[] = [];

	constructor(private assayIDList:number[], private query:string, private schema:SchemaSummary)
	{
	}

	// create the baseline objects so that the user can get things started
	public build():void
	{
		let content = $('#content');

		content.append('<h1>Assays</h1>');
		this.divAssays = $('<div style="padding-left: 2em;"></div>').appendTo(content);

		content.append('<h1>Compounds</h1>');
		this.divCompounds = $('<div style="padding-left: 2em;"></div>').appendTo(content);
		this.divProgress = $('<div align="center"></div>').appendTo(content);

		content.append('<h1>Grid</h1>');
		this.divGrid = $('<div></div>').appendTo(content);

		this.buildAssays();
		this.buildCompounds();
		this.buildGrid();

		if (this.assayIDList == null && this.query != null) this.runQuery();

		// pasting: captures the menu/hotkey form
		document.addEventListener('paste', (e:any):boolean =>
		{
			let wnd = window as any;
			let handled = false;
			if (wnd.clipboardData && wnd.clipboardData.getData) handled = this.pasteText(wnd.clipboardData.getData('Text'));
			else if (e.clipboardData && e.clipboardData.getData) handled = this.pasteText(e.clipboardData.getData('text/plain'));
			if (handled) e.preventDefault();
			return !handled;
		});
	}

	// ------------ private methods ------------

	// create the content for selecting assays,
	private buildAssays():void
	{
		this.divAssays.empty();
		if (this.assayIDList == null && this.query != null) return; // (means its loading currently)

		let para = $('<p></p>').appendTo(this.divAssays);

		let tr = $('<tr></tr>').appendTo($('<table></table>').appendTo(para));
		($('<td style="padding-right: 0.5em;"></td>').appendTo(tr)).append('Query: ');
		let line = $('<input type="text" size="80"></input>').appendTo($('<td></td>').appendTo(tr));
		line.val(this.query);
		let bclass = this.assayIDList == null && this.query == null ? 'btn-action' : 'btn-normal';
		let btnQuery = $('<button class="btn ' + bclass + '"></button>').appendTo($('<td style="padding-left: 0.5em;"></td>').appendTo(tr));
		btnQuery.append('<span class="glyphicon glyphicon-search"></span> Search');
		Popover.hover(domLegacy(btnQuery), null, 'Use the query string to select assays.');
		btnQuery.click(() =>
		{
			this.query = purifyTextPlainInput(line.val());
			this.runQuery();
		});

		para.append('Assays: ');
		para.append('<b>' + (this.assayIDList == null ? '?' : this.assayIDList.length) + '</b>');
	}

	private buildCompounds():void
	{
		this.divCompounds.empty();

		// create the category buttons
		let div = $('<div class="btn-group" data-toggle="buttons"></div>').appendTo(this.divCompounds);
		div.css('margin', '0.5em 0 0.5em 0');
		let lblFreq = $('<label class="btn btn-radio"></label>').appendTo(div);
		let lblProbe = $('<label class="btn btn-radio"></label>').appendTo(div);
		let lblQuery = $('<label class="btn btn-radio"></label>').appendTo(div);
		lblFreq.append('<input type="radio" name="options" autocomplete="off">Frequent Hitters</input>');
		lblProbe.append('<input type="radio" name="options" autocomplete="off">Selectivity</input>');
		lblQuery.append('<input type="radio" name="options" autocomplete="off">Query Structures</input>');
		if (this.compoundSource == 'frequent')
		{
			lblFreq.addClass('active');
			lblFreq.find('input').prop('checked', true);
		}
		else if (this.compoundSource == 'selective')
		{
			lblProbe.addClass('active');
			lblProbe.find('input').prop('checked', true);
		}
		else // 'query'
		{
			lblQuery.addClass('active');
			lblQuery.find('input').prop('checked', true);
		}

		this.divCompoundFreq = $('<div></div>').appendTo(this.divCompounds);
		this.divCompoundProbe = $('<div></div>').appendTo(this.divCompounds);
		this.divCompoundQuery = $('<div></div>').appendTo(this.divCompounds);
		this.updateCompoundSource();
		lblFreq.click(() => {this.compoundSource = 'frequent'; this.updateCompoundSource();});
		lblProbe.click(() => {this.compoundSource = 'selective'; this.updateCompoundSource();});
		lblQuery.click(() => {this.compoundSource = 'similarity'; this.updateCompoundSource();});

		// create max# compounds
		let paraMax = $('<p></p>').appendTo(this.divCompounds);
		paraMax.append('Maximum compounds: ');
		div = $('<div class="btn-group" data-toggle="buttons"></div>').appendTo(paraMax);
		div.css('margin', '0.5em 0 0.5em 0');
		let lblFew = $('<label class="btn btn-radio"></label>').appendTo(div);
		let lblSome = $('<label class="btn btn-radio"></label>').appendTo(div);
		let lblMany = $('<label class="btn btn-radio"></label>').appendTo(div);
		lblFew.append('<input type="radio" name="options" autocomplete="off">Few</input>');
		lblSome.append('<input type="radio" name="options" autocomplete="off">Some</input>');
		lblMany.append('<input type="radio" name="options" autocomplete="off">Many</input>');

		Popover.hover(domLegacy(lblFew), null, 'Select up to 20 compounds for the grid.');
		Popover.hover(domLegacy(lblSome), null, 'Select up to 100 compounds for the grid.');
		Popover.hover(domLegacy(lblMany), null, 'Select up to 1000 compounds for the grid.');

		if (this.maxCompounds == 20)
		{
			lblFew.addClass('active');
			lblFew.find('input').prop('checked', true);
		}
		else if (this.maxCompounds == 100)
		{
			lblSome.addClass('active');
			lblSome.find('input').prop('checked', true);
		}
		else // 1000
		{
			lblMany.addClass('active');
			lblMany.find('input').prop('checked', true);
		}
		lblFew.click(() => this.maxCompounds = 20);
		lblSome.click(() => this.maxCompounds = 100);
		lblMany.click(() => this.maxCompounds = 1000);

		// frequent hitter parameters

		// (none)

		// probelikeness parameters

		// (none)

		// query parameters

		let paraProbes = $('<p></p>').appendTo(this.divCompoundQuery);
		if (this.chkProbesOnly == null)
		{
			this.chkProbesOnly = $('<input type="checkbox"></input>');
			this.chkProbesOnly.prop('checked', false);
		}
		let label = $('<label></label>').appendTo(paraProbes);
		label.append(this.chkProbesOnly);
		label.append(' <font style="font-weight: normal;">Only select probe compounds</font>');

		let paraMol = $('<p></p>').appendTo(this.divCompoundQuery);
		let trSim = $('<tr></tr>').appendTo($('<table></table>').appendTo(paraMol));
		trSim.append('<td valign="top">Rank by similarity to:</td>');

		if (this.widgetStructure == null) this.widgetStructure = $('<canvas></canvas>');

		$('<td style="padding-left: 1em;"></td>').appendTo(trSim).append(this.widgetStructure);
		this.widgetStructure.click(() => {if (wmk.MolUtil.isBlank(this.molSimilar)) this.pasteText(UploadCompounds.DEMO_UPLOADCOMPOUND);});
		this.widgetStructure[0].addEventListener('dragover', (event) =>
		{
			event.stopPropagation();
			event.preventDefault();
			event.dataTransfer.dropEffect = 'copy';
		});
		this.widgetStructure[0].addEventListener('drop', (event) =>
		{
			event.stopPropagation();
			event.preventDefault();
			this.dropInto(event.dataTransfer);
		});
		let btnEdit = $('<button class="btn btn-action"></button>').appendTo($('<td style="padding-left: 0.5em;"></td>').appendTo(trSim));
		btnEdit.append('<span class="glyphicon glyphicon-edit"></span> Edit');
		btnEdit.click(() => this.editStructure());

		this.redrawMolecule();

		let ncpd = this.compounds.length;
		if (ncpd > 0) this.divCompounds.append('<p>Compounds found: <b>' + ncpd + '</b></p>');

		if (Vec.arrayLength(this.assayIDList) > 0)
		{
			let paraButtons = $('<p align="center"></p>').appendTo(this.divCompounds);
			let bclass = this.compounds.length == 0 ? 'btn-action' : 'btn-normal';
			this.btnCompounds = $('<button class="btn ' + bclass + '"></button>').appendTo(paraButtons);
			this.btnCompounds.append('<span class="glyphicon glyphicon-search"></span> Search');
			Popover.hover(domLegacy(this.btnCompounds), null, 'Obtain compounds for the selected assays, under these conditions.');
			this.btnCompounds.click(() => this.compoundSearch());
		}
	}

	private buildGrid():void
	{
		this.divGrid.empty();

		if (Vec.arrayLength(this.assayIDList) > 0 && Vec.arrayLength(this.compounds) > 0)
		{
			let paraButtons = $('<p align="center" style="margin: 0.5em;"</p>').appendTo(this.divGrid);
			let btnBuild = $('<button class="btn btn-action"></button>').appendTo(paraButtons);
			btnBuild.append('<span class="glyphicon glyphicon-search"></span> Build Grid');
			Popover.hover(domLegacy(btnBuild), null, 'Construct the grid using these assays and compounds.');
			btnBuild.click(() =>
			{
				this.grid = new AssayGrid(this.schema);
				this.grid.setAssays(this.assayIDList);
				this.grid.setCompounds(this.compounds);
				this.grid.render(this.divGrid);
			});
		}
		else if (Vec.arrayLength(this.assayIDList) == 0)
		{
			this.divGrid.text('Select assays and compounds first, then build the grid.');
		}
		else if (Vec.arrayLength(this.compounds) == 0)
		{
			this.divGrid.text('Select compounds to go with the assays, then build the grid.');
		}
	}

	// use the query to obtain the assay list
	private runQuery():void
	{
		this.divAssays.text('Querying list of assays...');

		let params = {'query': this.query};
		callREST('REST/ListQueryAssays', params,
			(data:any) =>
			{
				this.assayIDList = data.assayIDList;
				this.buildAssays();
				this.buildCompounds();
			},
			() => this.divAssays.text('Fetching query assays failed.'));
	}

	// redisplay where compounds are coming from
	private updateCompoundSource():void
	{
		this.divCompoundFreq.css('display', this.compoundSource == 'frequent' ? 'block' : 'none');
		this.divCompoundProbe.css('display', this.compoundSource == 'selective' ? 'block' : 'none');
		this.divCompoundQuery.css('display', this.compoundSource == 'similarity' ? 'block' : 'none');
	}

	// start looking for compounds (or stop, if it's in progress)
	private compoundSearch():void
	{
		if (!this.searchRunning) this.compoundSearchStart(); else this.compoundSearchStop();
	}
	private compoundSearchStart():void
	{
		if (this.compoundSource == 'frequent')
		{
			this.acquisition = new CompoundAcquireFrequent(this.assayIDList, this.maxCompounds, false);
		}
		else if (this.compoundSource == 'selective')
		{
			this.acquisition = new CompoundAcquireSelectivity(this.assayIDList, this.maxCompounds);
		}
		else if (this.compoundSource == 'similarity')
		{
			//let maxCpd = parseInt(this.lineMaxCompounds.val());
			let similarTo:string = null;
			if (this.molSimilar != null && this.molSimilar.numAtoms > 0)
			{
				let molwtr = new wmk.MDLMOLWriter(this.molSimilar);
				similarTo = molwtr.write();
			}
			let probes = this.chkProbesOnly.prop('checked');
			this.acquisition = new CompoundAcquireSimilarity(this.assayIDList, this.maxCompounds, similarTo, probes);
		}

		this.acquisition.callbackResults = () => this.updateSearchProgress();
		this.acquisition.callbackFinished = () => this.compoundSearchStop();

		this.btnCompounds.html('<span class="glyphicon glyphicon-search"></span> Stop</span>');
		this.divProgress.empty();
		this.canvasProgress = $('<canvas></canvas>').appendTo(this.divProgress);
		this.canvasProgress.css('width', this.PROGR_W + 'px');
		this.canvasProgress.css('height', this.PROGR_H + 'px');
		let density = pixelDensity();
		this.canvasProgress.attr('width', this.PROGR_W * density);
		this.canvasProgress.attr('height', this.PROGR_H * density);

		this.textProgress = $('<p></p>').appendTo(this.divProgress);

		this.searchRunning = true;
		this.updateSearchProgress();
		this.acquisition.start();
	}
	private compoundSearchStop():void
	{
		this.searchRunning = false;
		this.acquisition.stop();
		this.compounds = this.acquisition.compounds;
		this.hashes = this.acquisition.hashes;
		this.score = this.acquisition.score;

		let ncpd = this.compounds.length;
		this.textProgress.text(ncpd + ' compound' + (ncpd == 1 ? '' : 's'));

		this.btnCompounds.html('<span class="glyphicon glyphicon-search"></span> Search</span>');
		this.divProgress.empty();
		this.canvasProgress = null;
		this.textProgress = null;
		this.buildCompounds();
		this.buildGrid();
	}

	// update progress indicator for searching
	private updateSearchProgress():void
	{
		let width = this.PROGR_W, height = this.PROGR_H;

		let ctx = (this.canvasProgress[0] as HTMLCanvasElement).getContext('2d');
		let density = pixelDensity();
		ctx.save();
		ctx.scale(density, density);
		ctx.clearRect(0, 0, width, height);

		let lw = 5, x1 = lw, x2 = width - lw, midY = 0.25 * height;
		ctx.beginPath();
		ctx.moveTo(x1, midY);
		ctx.lineTo(x2, midY);
		ctx.lineWidth = lw - 1;
		ctx.lineCap = 'round';
		ctx.strokeStyle = '#E0E0E0';
		ctx.stroke();

		if (this.compoundSource == 'selective')
		{
			let midX = 0.5 * (x1 + x2);
			ctx.beginPath();
			ctx.moveTo(midX, 0.1 * height);
			ctx.lineTo(midX, 0.4 * height);
			ctx.lineWidth = 1;
			ctx.stroke();
		}

		let fraction = this.acquisition.progressFraction();

		if (fraction > 0)
		{
			let sw = (x2 - x1) * fraction;
			ctx.beginPath();
			ctx.moveTo(x1, midY);
			ctx.lineTo(x1 + sw, midY);
			ctx.lineWidth = lw;
			ctx.lineCap = 'round';
			ctx.strokeStyle = '#4040FF';
			ctx.stroke();
		}

		let bars = this.acquisition.progressBars();
		if (bars != null)
		{
			ctx.fillStyle = '#F8F8F8';
			ctx.fillRect(0, 0.5 * height, width, 0.5 * height);
			let sw = Math.min(1, width / bars.length);
			for (let n = 0; n < bars.length; n++)
			{
				let h = bars[n] * 0.5 * height, y = height - h;
				ctx.fillStyle = n % 2 == 0 ? '#4040FF' : '#40FFFF';
				ctx.fillRect(n * sw, y, sw, h);
			}
		}

		ctx.restore();

		this.textProgress.text(this.acquisition.progressText());
	}

	// bring up the sketcher to edit the query structure
	private editStructure():void
	{
		let dlg = new wmk.EditCompound(this.molSimilar == null ? new wmk.Molecule() : this.molSimilar);
		//this.isSketching = true;
		dlg.onSave(() =>
		{
			this.molSimilar = dlg.getMolecule();
			this.redrawMolecule();
			dlg.close();
		});
		//dlg.onClose(() => this.isSketching = false);
		dlg.open();
	}

	// pasting: maybe it's a molecule; returns true if it was
	private pasteText(txt:string):boolean
	{
		let mol = wmk.MoleculeStream.readNative(txt);
		if (mol == null)
		{
			try {mol = wmk.MoleculeStream.readMDLMOL(txt);}
			catch (ex) {}
		}
		if (mol == null) return false;

		this.molSimilar = mol;
		this.redrawMolecule();

		return true;
	}

	// something was dragged into the molecule query area
	private dropInto(transfer:DataTransfer):void
	{
		let items = transfer.items, files = transfer.files;

		//console.log('DROP-INTO: items=' +  items.length + ', files=' + files.length);

		for (let n = 0; n < items.length; n++)
		{
			if (items[n].type.startsWith('text/plain'))
			{
				items[n].getAsString((str:string) =>
				{
					if (!this.pasteText(str)) console.log('Dragged data is not a recognized molecule: ' + str);
				});
				return;
			}

			//console.log('ITEMS['+n+']: ' + items[n].kind+',type='+items[n].type);
		}
		for (let n = 0; n < files.length; n++)
		{
			if (files[n].name.endsWith('.el') || files[n].name.endsWith('.mol'))
			{
				let reader = new FileReader();
				reader.onload = (event) =>
				{
					let str = reader.result.toString();
					if (!this.pasteText(str)) console.log('Dragged file is not a recognized molecule: ' + str);
				};
				reader.readAsText(files[n]);
				return;
			}

			//console.log('DRAGFILE['+n+']: ' + files[n].name+',sz='+files[n].size+',type='+files[n].type);
		}
	}

	// refreshes the displayed molecule
	private redrawMolecule():void
	{
		let metavec:wmk.MetaVector = null;
		let width = 150, height = 80;

		if (this.molSimilar != null && this.molSimilar.numAtoms > 0)
		{
			let policy = wmk.RenderPolicy.defaultColourOnWhite();
			let effects = new wmk.RenderEffects();
			let measure = new wmk.OutlineMeasurement(0, 0, policy.data.pointScale);
			let layout = new wmk.ArrangeMolecule(this.molSimilar, measure, policy, effects);
			layout.arrange();
			let bounds = layout.determineBoundary();
			width = Math.ceil(bounds[2] - bounds[0]) + 6;
			height = Math.ceil(bounds[3] - bounds[1]) + 6;
			layout.squeezeInto(0, 0, width, height, 0);

			metavec = new wmk.MetaVector();
			new wmk.DrawMolecule(layout, metavec).draw();
		}

		this.widgetStructure.css('width', width + 'px');
		this.widgetStructure.css('height', height + 'px');
		let density = pixelDensity();
		this.widgetStructure.attr('width', width * density);
		this.widgetStructure.attr('height', height * density);

		let ctx = (this.widgetStructure[0] as HTMLCanvasElement).getContext('2d');
		ctx.save();
		ctx.scale(density, density);
		ctx.clearRect(0, 0, width, height);

		ctx.fillStyle = '#E6EDF2';
		ctx.fill(pathRoundedRect(0, 0, width, height, 5));

		if (metavec == null)
		{
			ctx.font = '12px sans-serif';
			let txt = 'Paste or Drag Molecule', tw = ctx.measureText(txt).width;
			ctx.fillStyle = '#313A44';
			ctx.textBaseline = 'middle';
			ctx.fillText(txt, 0.5 * (width - tw), 0.5 * height);
		}
		else metavec.renderContext(ctx);

		ctx.restore();
	}
}

/* EOF */ }