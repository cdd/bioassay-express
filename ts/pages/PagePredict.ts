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
	Supporting functionality for the predict page.
*/

interface PagePredictPartition
{
	assayIDList:number[];
	description:string; // text description of what's in there
}

interface PagePredictModel
{
	assayIDList:number[];
	bayes:wmk.BayesianModel;
	failMsg?:string;
	svg?:string;
}

interface PagePredictCol
{
	idx:number;
	title:string;
	partition:PagePredictPartition;
}

interface PagePredictRow
{
	tr:JQuery;
	mol:wmk.Molecule;
	tdStruct:JQuery;
	svg?:string;
	predictions:Record<number, number>; // idlist-to-calibration prediction
	fplist?:number[];
	tdPred?:JQuery[];
}

export class PagePredict
{
	private divAssays:JQuery;
	private divMolecules:JQuery;
	private tableMolecules:JQuery;
	private trColumns:JQuery;

	private modelCols:PagePredictCol[] = [];
	private moleculeRows:PagePredictRow[] = [];
	private MAX_ROWS = 100;

	private rosterAssays:number[] = [];
	private assayPartitions:PagePredictPartition[] = []; // partitioned-out assay groups (if no group-by, all singletons)
	private assayGroup:SchemaAssignment[] = []; // assignments to partion by
	private assayDef:Record<number, AssayDefinition> = {}; // assayID-to-details

	private rosterModels:PagePredictCol[] = [];
	private models:Record<string, PagePredictModel> = {}; // idlist-to-model

	private isSketching = false;

	constructor(private assayIDList:number[], private query:string, private schema:SchemaSummary)
	{
	}

	// create the baseline objects so that the user can get things started
	public build():void
	{
		let content = $('#content');
		content.empty();

		content.append('<h1>Assay Models</h1>');
		this.divAssays = $('<div></div>').appendTo(content);
		this.divAssays.html('<p>Acquiring assay list...</p>');

		content.append('<h1>Molecules</h1>');
		this.divMolecules = $('<div></div>').appendTo(content);

		this.buildUpload();

		this.acquireAssays();

		// pasting: captures the menu/hotkey form
		document.addEventListener('paste', (event:ClipboardEvent):boolean =>
		{
			if (this.isSketching) return true;
			let wnd = window as any;
			let handled = false, txt:string = null;
			if (wnd.clipboardData && wnd.clipboardData.getData) txt = wnd.clipboardData.getData('Text');
			else if (event.clipboardData && event.clipboardData.getData) txt = event.clipboardData.getData('text/plain');

			if (this.pasteContent(txt)) handled = true;
			if (handled) event.preventDefault();
			return !handled;
		});

		// dragging
		document.addEventListener('dragover', (event:DragEvent) =>
		{
			event.stopPropagation();
			event.preventDefault();
			event.dataTransfer.dropEffect = 'copy';
		});
		document.addEventListener('drop', (event:DragEvent) =>
		{
			event.stopPropagation();
			event.preventDefault();
			let items = event.dataTransfer.items;
			for (let n = 0; n < items.length; n++)
			{
				if (items[n].kind == 'string')
				{
					items[n].getAsString((str:string) => this.pasteContent(str));
					return;
				}
				if (items[n].kind == 'file')
				{
					let file = items[n].getAsFile();
					let reader = new FileReader();
					reader.onload = (event) => this.pasteContent(reader.result.toString());
					try {reader.readAsText(file);}
					catch (e) {} // silent failure if not a text file
				}
			}
		});
	}

	// ------------ private methods ------------

	// builds a page that takes compounds & assay details
	private buildUpload():void
	{
		let para = $('<p></p>').appendTo(this.divMolecules);
		para.text('Drag/paste molecules, ');

		let btnDraw = $('<button class="btn btn-action"></button>').appendTo(para);
		btnDraw.append('<span class="glyphicon glyphicon-edit" style=\"height: 1.2em;"></span>&nbsp;Draw');
		btnDraw.click(() => this.drawMolecule());

		para.append(' or ');

		let btnExample = $('<button class="btn btn-action"></button>').appendTo(para);
		btnExample.append('<span class="glyphicon glyphicon-glass" style=\"height: 1.2em;"></span>&nbsp;Example');
		btnExample.click(() => this.insertExample());

		this.tableMolecules = $('<table></table>').appendTo(this.divMolecules);

		this.updatePredictionInputs();
	}

	// updates help text/button state depending on whether the prediction is ready to roll
	private updatePredictionInputs():void
	{
		/*let msg:string = null;
		if (!this.widgetCompounds.hasContent() && !this.widgetAssay.hasContent())
			msg = 'Provide compounds and assay properties to carry out the prediction. Compounds can be entered by ' +
				  'dragging or pasting standard formats (e.g. Molfile or SDfile). Assays can be specified using the same ' +
				  'JSON format that is used by the annotation page (via either copying to clipboard or downloading). ' +
				  'Alternatively, just click on either box to use a prepared demonstration example.';
		else if (!this.widgetCompounds.hasContent())
			msg = 'Provide one more compounds for prediction.';
		else if (!this.widgetAssay.hasContent())
			msg = 'Provide assay details for prediction.';

		if (msg == null)
		{
			this.paraPredHelp.empty();
			this.btnPredict.prop('disabled', false);
		}
		else
		{
			this.paraPredHelp.text(msg);
			this.btnPredict.prop('disabled', true);
		}*/
	}

	// make sure the assays are all defined, or get it one step closer
	private acquireAssays():void
	{
		if (this.assayIDList == null && this.query != null)
		{
			let params = {'query': this.query};
			callREST('REST/ListQueryAssays', params,
				(data:any) =>
				{
					this.assayIDList = data.assayIDList;
					this.acquireAssays();
				},
				() => this.divAssays.text('Fetching query assays failed.'));
			return;
		}

		if (this.assayIDList != null)
		{
			this.rosterAssays = this.assayIDList.slice(0);
			this.loadNextAssay();
		}
	}
	private loadNextAssay():void
	{
		if (this.rosterAssays.length == 0)
		{
			this.assayPartitions = this.partitionAssays();
			this.redrawAssayModels();
			return;
		}

		let assayID = this.rosterAssays.shift();
		let params = {'assayID': assayID, 'countCompounds': false};
		callREST('REST/GetAssay', params,
			(assay:AssayDefinition) =>
			{
				this.assayDef[assayID] = assay;
				this.divAssays.empty();
				let total = this.assayIDList.length, num = total - this.rosterAssays.length;
				this.divAssays.append('<p>Loading assays (' + num + ' of ' + total + ')</p>');
				this.loadNextAssay();
			});
	}

	// given that the assays have been loaded, and there may or may not be some grouping/model building, re-displays what's available
	private redrawAssayModels():void
	{
		this.divAssays.empty();

		// button row

		let paraBtn = $('<p></p>').appendTo(this.divAssays);
		paraBtn.css('margin-top', '0.5em');

		let btnBuild = $('<button class="btn btn-action"></button>').appendTo(paraBtn);
		btnBuild.append('<span class="glyphicon glyphicon-bullhorn" style=\"height: 1.2em;"></span>&nbsp;&nbsp;Build');
		btnBuild.click(() => this.buildModels());

		paraBtn.append(' ');

		let btnGroup = $('<button class="btn btn-normal">Group By</button>').appendTo(paraBtn);
		btnGroup.click(() =>
		{
			let dlg = new PickAssignmentDialog(this.schema, true);
			dlg.picked = this.assayGroup;
			dlg.callbackDone = (assnlist:SchemaAssignment[]) => this.changedAssayGroup(assnlist);
			dlg.show();
		});

		// table of assay groups and models built therefrom

		let table = $('<table></table>').appendTo($('<p></p>').appendTo(this.divAssays));
		let colLimit = Math.floor(0.4 * $(window).width());

		for (let n = 0; n < this.assayPartitions.length; n++)
		{
			let ptn = this.assayPartitions[n];
			let model = this.models[ptn.assayIDList.toString()];

			// first line

			let tr = $('<tr></tr>').appendTo(table);
			if (n > 0) tr.css('border-top', '1px solid ' + Theme.WEAK_HTML);

			let tdNum = $('<td></td>').appendTo(tr);
			let span = $('<span></span>').appendTo(tdNum);
			span.css('background-color', Theme.STRONG_HTML);
			span.css('color', 'white');
			span.css('padding', '0.3em');
			span.text((n + 1).toString());

			let tdAssays = $('<td rowspan="2"></td>').appendTo(tr);
			tdAssays.css('padding', '0 0.5em 0 0.5em');
			tdAssays.css('max-width', colLimit + 'px');
			for (let assayID of ptn.assayIDList)
			{
				let ahref = $('<a target="_blank"></a>').appendTo(tdAssays);
				ahref.attr('href', 'assign.jsp?assayID=' + assayID);
				let [src, id] = UniqueIdentifier.parseKey(this.assayDef[assayID].uniqueID);
				ahref.text(src ? src.shortName + ' ' + id : assayID);

				tdAssays.append(' ');
			}

			let tdTitle = $('<td></td>').appendTo(tr);
			tdTitle.attr('colspan', 3);
			tdTitle.css('font-style', 'italic');
			tdTitle.css('vertical-align', 'top');
			tdTitle.text(ptn.description);

			// second line

			tr = $('<tr></tr>').appendTo(table);
			tr.append('<td></td>');

			let tdROC = $('<td></td>').appendTo(tr);
			if (model && model.bayes && model.bayes.rocAUC > 0)
			{
				tdROC.css('text-align', 'center');
				if (!model.svg) model.svg = this.renderROCSVG(model.bayes);
				tdROC.append(model.svg);
			}

			let tdStats = $('<td></td>').appendTo(tr);
			tdStats.css('padding-left', '0.5em');
			if (!model) {}
			else if (model.failMsg) tdStats.text(model.failMsg);
			else tdStats.append('ROC ' + model.bayes.rocAUC.toFixed(4));

			if (model && model.bayes) tdStats.append('<br>Actives&nbsp;' + model.bayes.trainingActives + '&nbsp;of&nbsp;' + model.bayes.trainingSize);

			if (model && model.bayes && model.bayes.rocAUC > 0)
			{
				const idx = n + 1, bayes = model.bayes;
				tdStats.append('<br>');

				let btnDownload = $('<button class="btn btn-action"></button>').appendTo(tdStats);
				btnDownload.append('<span class="glyphicon glyphicon-save" style=\"height: 1.2em;"></span>&nbsp;Download');
				btnDownload.click(() => this.downloadBayesian(idx, bayes));
			}

			for (let td of [tdNum, tdAssays, tdROC, tdStats]) td.css('vertical-align', 'top');
		}
	}

	// returns the assays as-requested, grouped as appropriate
	private partitionAssays():PagePredictPartition[]
	{
		let partitions:PagePredictPartition[] = [];

		if (this.assayGroup.length == 0)
		{
			for (let assayID of this.assayIDList)
			{
				let assay = this.assayDef[assayID];
				let ptn:PagePredictPartition = {'assayIDList': [assayID], 'description': deriveAssayName(assay, 100)};
				partitions.push(ptn);
			}
		}
		else
		{
			let propGrp = new Set<string>();
			for (let assn of this.assayGroup) propGrp.add(keyPropGroup(assn.propURI, assn.groupNest));
			let mapPos:Record<string, number> = {};

			for (let assayID of this.assayIDList)
			{
				let assay = this.assayDef[assayID];
				let values:string[] = [], labels:string[] = [];
				let hierarchy = new Set<string>();
				for (let annot of assay.annotations) if (annot.valueURI && propGrp.has(keyPropGroup(annot.propURI, annot.groupNest)))
				{
					values.push(annot.valueURI);
					labels.push(annot.valueLabel);
					if (annot.valueHier) for (let uri of annot.valueHier) hierarchy.add(uri);
				}
				for (let uri of hierarchy)
				{
					let i = values.indexOf(uri);
					if (i >= 0) {values.splice(i, 1); labels.splice(i, 1);}
				}

				// if none, create a singleton
				if (values.length == 0)
				{
					let assay = this.assayDef[assayID];
					let ptn:PagePredictPartition = {'assayIDList': [assayID], 'description': deriveAssayName(assay, 100)};
					partitions.push(ptn);
				}

				// otherwise, lookup the tag and associate that as a key index
				values.sort();
				let tag = values.join('\n'), pos = mapPos[tag];
				if (pos == null)
				{
					mapPos[tag] = partitions.length;
					labels.sort();
					let ptn:PagePredictPartition = {'assayIDList': [assayID], 'description': labels.join(', ')};
					partitions.push(ptn);
				}
				else partitions[pos].assayIDList.push(assayID);
			}
		}

		return partitions;
	}

	// for each group, ensures that a model is available - from the server, or cached
	private buildModels():void
	{
		this.rosterModels = [];
		this.modelCols = [];
		for (let n = 0; n < this.assayPartitions.length; n++)
		{
			let ptn = this.assayPartitions[n], key = ptn.assayIDList.toString();
			let col:PagePredictCol = {'idx': n, 'title': (n + 1).toString(), 'partition': ptn};
			let model = this.models[key];
			if (model)
			{
				if (model.bayes && model.bayes.rocAUC > 0) this.modelCols.push(col);
			}
			else this.rosterModels.push(col);
		}
		this.buildNextModel();
	}

	// if there are more models waiting to be built, handles the next one
	private buildNextModel():void
	{
		if (this.rosterModels.length == 0) return;
		let col = this.rosterModels.shift();

		let params = {'assayIDList': col.partition.assayIDList};
		callREST('REST/BuildBayesian', params,
			(data:any) =>
			{
				let bayes = data.model ? wmk.BayesianModel.deserialise(data.model) : null;
				let model:PagePredictModel = {'assayIDList': col.partition.assayIDList, 'bayes': bayes, 'failMsg': data.failMsg};
				this.models[col.partition.assayIDList.toString()] = model;

				if (bayes && bayes.rocAUC > 0)
				{
					this.modelCols.push(col);
					this.modelCols.sort((c1:PagePredictCol, c2:PagePredictCol):number => c1.idx - c2.idx);
					this.rebuildColumns();
					this.renderNextMolecule();
				}

				this.redrawAssayModels();
				this.buildNextModel();
			},
			() => alert('Model building failed.'));
	}

	// produce an SVG version of the ROC curve
	private renderROCSVG(model:wmk.BayesianModel):string
	{
		let vg = new wmk.MetaVector();

		const SIZE = 100;
		vg.drawRect(0, 0, SIZE, SIZE, 0x000000, 1, Theme.WEAK_RGB);
		vg.drawLine(0, SIZE, SIZE, 0, 0x000000, 1);

		let px:number[] = [], py:number[] = [];
		px.push(0);
		py.push(SIZE);
		for (let n = 0; n < model.rocX.length; n++)
		{
			px.push(model.rocX[n] * SIZE);
			py.push((1 - model.rocY[n]) * SIZE);
		}
		px.push(SIZE);
		py.push(0);
		let bx = px.slice(0), by = py.slice(0);
		bx.push(SIZE);
		by.push(SIZE);

		vg.drawPath(bx, by, null, true, -1, 0, 0x40000000 | Theme.STRONG_RGB, false);
		vg.drawPath(px, py, null, false, Theme.STRONG_RGB, 2, -1, false);

		vg.normalise();
		return vg.createSVG();
	}

	// start sketching a new blank molecule
	private drawMolecule(mol:wmk.Molecule = null):void
	{
		this.isSketching = true;
		let dlg = new wmk.EditCompound(mol ? mol : new wmk.Molecule());
		//dlg.defineClipboard(new wmk.ClipboardProxyWeb());
		dlg.onSave(() =>
		{
			this.insertMolecule(dlg.getMolecule());
			this.renderNextMolecule();
			dlg.close();
		});
		dlg.onClose(() => this.isSketching = false);
		dlg.open();
	}

	// insert a demo molecule
	private insertExample():void
	{
		let mol = wmk.MoleculeStream.readMDLMOL(UploadCompounds.DEMO_UPLOADCOMPOUND);
		this.insertMolecule(mol);
		this.renderNextMolecule();
	}

	// parses incoming text (paste or drag) and returns true if something was done with it
	private pasteContent(txt:string):boolean
	{
		if (!txt) return false;

		try
		{
			let ds = wmk.DataSheetStream.readXML(txt);
			if (ds != null) {this.insertDataSheet(ds); return true;}
		}
		catch (e) {}

		try
		{
			let ds = new wmk.MDLSDFReader(txt).parse();
			if (ds != null) {this.insertDataSheet(ds); return true;}
		}
		catch (e) {}

		let mol = wmk.MoleculeStream.readUnknown(txt);
		if (mol)
		{
			this.insertMolecule(mol);
			this.renderNextMolecule();
			return true;
		}
		return false;
	}

	// recreates the table of molecules
	private rebuildMolecules(alwaysHeading:boolean = false):void
	{
		this.tableMolecules.empty();
		this.trColumns = null;
		if (alwaysHeading || this.moleculeRows.length > 0)
		{
			this.trColumns = $('<tr></tr>').appendTo(this.tableMolecules);
			this.rebuildColumns();
		}
	}

	// columns have changed, so update the display
	private rebuildColumns():void
	{
		if (!this.trColumns) return;

		this.trColumns.empty();

		this.trColumns.css('border-bottom', '1px solid ' + Theme.WEAK_HTML);
		this.trColumns.append('<td></td>');
		this.trColumns.append('<td>Structure</td>');

		for (let col of this.modelCols)
		{
			let td = $('<td></td>').appendTo(this.trColumns);
			td.css('text-align', 'center');
			let span = $('<span></span>').appendTo(td);
			span.css('background-color', Theme.STRONG_HTML);
			span.css('color', 'white');
			span.css('padding', '0.3em');
			span.text(col.title);
		}

		for (let row of this.moleculeRows) if (row.tdPred)
		{
			for (let td of row.tdPred) td.remove();
			row.tdPred = null;
		}
	}

	// looks for the next molecule that's missing a rendered structure
	private renderNextMolecule():void
	{
		let row:PagePredictRow = null;
		for (let look of this.moleculeRows) if (!look.svg || !look.tdPred) {row = look; break;}
		if (!row) return;

		if (!row.svg)
		{
			let policy = wmk.RenderPolicy.defaultColourOnWhite();
			let effects = new wmk.RenderEffects();
			let measure = new wmk.OutlineMeasurement(0, 0, policy.data.pointScale);
			let layout = new wmk.ArrangeMolecule(row.mol, measure, policy, effects);
			layout.arrange();
			let metavec = new wmk.MetaVector();
			new wmk.DrawMolecule(layout, metavec).draw();
			metavec.normalise();
			row.svg = metavec.createSVG();

			$(row.svg).appendTo(row.tdStruct);
		}

		if (row.tdPred == null)
		{
			if (!row.fplist) row.fplist = wmk.CircularFingerprints.create(row.mol, wmk.CircularFingerprints.CLASS_ECFP6).getUniqueHashes();
			row.tdPred = [];
			for (let col of this.modelCols)
			{
				let td = $('<td></td>').appendTo(row.tr);
				td.css('padding', '0.2em');
				td.css('text-align', 'center');
				td.css('vertical-align', 'middle');
				let model = this.models[col.partition.assayIDList.toString()];
				let pred = model.bayes.scalePredictor(model.bayes.predictFP(row.fplist));

				let span = $('<span></span>').appendTo(td);
				span.css('display', 'inline-block');
				span.css('width', '15px');
				span.css('height', '15px');

				let capped = Math.max(0, Math.min(1, pred));
				let rgb = blendRGB(capped, Theme.WEAK_RGB, Theme.STRONG_RGB);
				span.css('background-color', colourCode(rgb));

				let title = 'Model ' + col.title;
				let text = col.partition.description + '<br>';
				text += '<b>Bayesian prediction</b>: ';
				if (pred < 0) text += '&lt;0%';
				else if (pred > 1) text += '&gt;100%';
				else text += (capped * 100).toFixed(1) + '%';

				Popover.hover(domLegacy(span), null, text);

				row.tdPred.push(td);
			}
		}

		setTimeout(() => this.renderNextMolecule(), 1);
	}

	// append a single molecule to the list
	private insertMolecule(mol:wmk.Molecule):void
	{
		if (this.moleculeRows.length == 0) this.rebuildMolecules(true);

		let tr = $('<tr></tr>').appendTo(this.tableMolecules);

		let tdButton = $('<td></td>').appendTo(tr);

		let btnEdit = $('<button type="button" class="btn btn-xs btn-normal"></button>').appendTo(tdButton);
		btnEdit.append('<span class="glyphicon glyphicon-pencil" style="width: 1.2em; height: 1.2em;"></span>');

		tdButton.append(' ');

		let btnDelete = $('<button type="button" class="btn btn-xs btn-normal"></button>').appendTo(tdButton);
		btnDelete.append('<span class="glyphicon glyphicon-remove" style="width: 1.2em; height: 1.2em;"></span>');

		let tdStruct = $('<td></td>').appendTo(tr);
		tdStruct.css('text-align', 'center');
		tdStruct.css('padding', '0.5em');

		let row:PagePredictRow = {'tr': tr, 'mol': mol, 'tdStruct': tdStruct, 'predictions': {}};
		this.moleculeRows.push(row);
		while (this.moleculeRows.length > this.MAX_ROWS) this.moleculeRows.shift().tr.remove();

		btnEdit.click(() => this.drawMolecule(mol.clone()));
		btnDelete.click(() => this.deleteRow(row));
	}

	// append a collection of molecules to the list
	private insertDataSheet(ds:wmk.DataSheet):void
	{
		for (let r = 0; r < ds.numRows; r++) for (let c = 0; c < ds.numCols; c++) if (ds.colType(c) == wmk.DataSheetColumn.Molecule)
		{
			let mol = ds.getMolecule(r, c);
			if (mol) this.insertMolecule(mol);
		}
		this.renderNextMolecule();
	}

	// remove row from list, and from the UI
	private deleteRow(row:PagePredictRow):void
	{
		row.tr.remove();
		for (let n = this.moleculeRows.length - 1; n >= 0; n--) if (this.moleculeRows[n] === row) this.moleculeRows.splice(n, 1);
	}

	// assay groupby has changed, so reorganise everything
	private changedAssayGroup(assnlist:SchemaAssignment[]):void
	{
		this.assayGroup = assnlist;
		this.assayPartitions = this.partitionAssays();
		this.redrawAssayModels();
		this.buildNextModel();
	}

	// triggers insta-download of a Bayesian model
	private downloadBayesian(idx:number, bayes:wmk.BayesianModel):void
	{
		// todo: come up with a more meaningful filename identifier, and also fill in the metadata fields
		let str = bayes.serialise();
		let fn = 'model_' + idx + '.bayesian';

		let a = window.document.createElement('a');
		a.href = window.URL.createObjectURL(new Blob([str], {'type': 'application/octet-stream'}));
		a.download = fn;

		document.body.appendChild(a);
		a.click();
		document.body.removeChild(a);
	}
}

/* EOF */ }
