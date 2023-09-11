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
	Heavyweight widget that takes a list of assays (columns) and compounds (rows) and produces a grid for
	visualisation purposes.
*/

export interface AssayGridAssay extends AssayDefinition
{
	loaded1:boolean; // loaded primary details (IDs, annotations)
	loaded2:boolean; // loaded secondary details (compound info)
	shortText?:string; // truncated version of the text, approximately line-length
	actives?:number[]; // compound IDs for actives
	inactives?:number[]; // ditto for inactives
	probes?:number[]; // and also for compounds classed as probes
}

export interface AssayGridCompound
{
	loaded:boolean;
	compoundIDList:number[]; // internal compound ID numbers
	pubchemCIDList:number[]; // PubChem compounds, if applicable
	pubchemSIDList:number[]; // PubChem substances, if applicable
	vaultIDList:number[]; // Vault ID & molecule ID, if applicable
	vaultMIDList:number[]; // "
	molfileList:string[];
	fplist?:number[]; // ECFP6 fingerprints; for first compound only (they should all be the same); null=not calc'd, []=something wrong, skip
}

export interface AssayGridModel
{
	assayIDList:number[];
	bayes:wmk.BayesianModel;
	predictions:Record<number, number>; // compound index -> calibrated prediction
	failMsg?:string;
	svg?:string;
}

export interface AssayGridBlock
{
	idx:number[]; // indexes into assays or compounds, depending on axis
	model?:AssayGridModel; // sometimes defined for columns
}

export class AssayGrid
{
	private domParent:JQuery;
	private mainDiv:JQuery;
	private progDiv:JQuery;
	private ctrlDiv:JQuery;
	private areaDiv:JQuery;
	private detailDiv:JQuery;

	private dendroIndex = -1;
	private dendroPop:JQuery = null;

	private assays:AssayGridAssay[] = [];
	private compounds:AssayGridCompound[] = [];
	private cpdIDMap:Record<number, number> = {}; // compoundID:index into compounds

	private axisX:AssayGridBlock[] = []; // assays
	private axisY:AssayGridBlock[] = []; // compounds

	private insetX = 12;
	private insetY = 12;
	private dendroH = 5;
	private szw:number;
	private szh:number;
	private axisCanvas:JQuery;
	private gridCanvas:JQuery;
	private focusCanvas:JQuery;

	private gridActives:number[][][]; // [y][x] = [list of compounds];
	private gridInactives:number[][][]; // ditto
	private gridProbes:number[][][]; // ditto

	private focusX = -1;
	private focusY = -1;
	private focusSticky = false;
	private dragType:string = null;
	private firstX = -1;
	private firstY = -1;
	private lastX = -1;
	private lastY = -1;

	private focusBoxUnitW = 0;
	private focusBoxUnitH = 0;
	private focusBoxXPos:number[] = []; // pairs of {idx,pos}
	private focusBoxYPos:number[] = []; // ditto

	private allCompounds = new Set<number>();

	private btnAssayGroup:JQuery;
	private btnBuildModels:JQuery;
	private assayGroup:SchemaAssignment[] = [];
	private assayTree:TreePattern = null;

	private cancelled = false;
	private progCanvas:JQuery;
	private progPos = 0;
	private progCount = 0;
	private PROGWIDTH = 400;
	private PROGHEIGHT = 10;
	private MINWIDTH = 500; // minimum grid width, to ensure space for the focus popup

	private rosterModels:number[][] = []; // each entry is an assayIDList
	private models:Record<string, AssayGridModel> = {}; // idlistkey-to-model
	private rosterPredictions:number[] = []; // each entry is an index into compounds

	constructor(private schema:SchemaSummary)
	{
	}

	// takes the list of assays and turns it into object instances; if numeric, implies internal assay ID; a prefix of
	// 'AID' means that it's a PubChem ID
	public setAssays(assays:any[]):void
	{
		this.assays = [];
		for (let a of assays)
		{
			let str = a.toString();
			let obj = {'loaded1': false, 'loaded2': false} as AssayGridAssay;
			if (str.startsWith('UID/')) obj.uniqueID = str.substring(4);
			else if (str.startsWith('AID')) obj.uniqueID = 'pubchemAID:' + str.substring(3); // legacy
			else obj.assayID = parseInt(str);
			this.assays.push(obj);
		}
	}

	// takes the list of compounds and turns it into object instances; if numeric, implies internal compound ID; prefixes
	// of 'CID' or 'SID' mean that it's referenced by PubChem instead
	/*public setCompounds(compounds:any[]):void
	{
		this.compounds = [];
		for (let c of compounds)
		{
			let str = c.toString();
			let obj:AssayGridCompound = {'loaded': false, 'compoundIDList': [], 'pubchemCIDList': [], '};
			if (str.startsWith('CID')) obj.pubchemCID = parseInt(str.substring(3));
			if (str.startsWith('SID')) obj.pubchemSID = parseInt(str.substring(3));
			else obj.compoundIDList = [parseInt(str)];
			this.compounds.push(obj);
		}
	}*/

	// defining compounds: each block of compoundIDs is considered to be a distinct group of "the same molecule", albeit maybe with
	// different identifiers and equivalent representations
	public setCompounds(compoundIDGroup:number[][]):void
	{
		this.compounds = [];
		for (let n = 0; n < compoundIDGroup.length; n++)
		{
			let obj:AssayGridCompound =
			{
				'loaded': false,
				'compoundIDList': compoundIDGroup[n],
				'pubchemCIDList': [],
				'pubchemSIDList': [],
				'vaultIDList': [],
				'vaultMIDList': [],
				'molfileList': [],
				//'svgList': []
			};
			this.compounds.push(obj);
			for (let cpdID of compoundIDGroup[n]) this.cpdIDMap[cpdID] = n;
		}
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

		this.progDiv = $('<div></div>').appendTo(this.mainDiv);

		this.ctrlDiv = $('<div></div>').appendTo(this.mainDiv);
		this.ctrlDiv.css('width', '100%');
		this.ctrlDiv.css('vertical-align', 'middle');
		this.ctrlDiv.css('padding-bottom', '0.2em');

		this.areaDiv = $('<div></div>').appendTo(this.mainDiv);
		this.areaDiv.css('left', 0);
		this.areaDiv.css('top', 0);
		this.areaDiv.css('position', 'relative');

		this.detailDiv = $('<div></div>').appendTo(this.mainDiv);
		this.detailDiv.css('vertical-align', 'middle');
		this.detailDiv.css('padding-top', '0.2em');
		this.detailDiv.css('min-height', '30em');

		this.axisCanvas = $('<canvas></canvas>').appendTo(this.areaDiv);
		this.axisCanvas.css('left', 0);
		this.axisCanvas.css('top', 0);
		this.axisCanvas.css('width', 0);
		this.axisCanvas.css('height', 0);
		this.axisCanvas.css('position', 'absolute');

		this.gridCanvas = $('<canvas></canvas>').appendTo(this.areaDiv);
		this.gridCanvas.css('left', 0);
		this.gridCanvas.css('top', 0);
		this.gridCanvas.css('width', 0);
		this.gridCanvas.css('height', 0);
		this.gridCanvas.css('position', 'absolute');

		this.focusCanvas = $('<canvas></canvas>').appendTo(this.areaDiv);
		this.focusCanvas.css('left', 0);
		this.focusCanvas.css('top', 0);
		this.focusCanvas.css('width', 0);
		this.focusCanvas.css('height', 0);
		this.focusCanvas.css('position', 'absolute');
		this.focusCanvas.css('pointer-events', 'none');

		this.gridCanvas.mousemove((event:JQueryMouseEventObject) => this.mouseMove(event));
		this.gridCanvas.mouseout((event:JQueryMouseEventObject) => this.mouseOut(event));
		this.gridCanvas.mousedown((event:JQueryMouseEventObject) => {event.preventDefault(); this.mouseDown(event);});
		this.gridCanvas.mouseup((event:JQueryMouseEventObject) => this.mouseUp(event));
		this.gridCanvas.keydown((event:JQueryKeyEventObject) => this.keyDown(event));

		// need to download the rest of the content first
		//this.downloadSchema();
		this.downloadAxisBlock();
	}

	// ------------ private methods ------------

	// begins the content downloading: start with the schema
	/*private downloadSchema():void
	{
		this.ctrlDiv.text('Fetching schema');

		let params = {}; //{'schemaURI': null, 'locator': null};
		callREST('REST/DescribeSchema', params,
			(data:SchemaSummary) =>
			{
				this.schema = data;
				this.downloadAxisBlock();
			},
			() => this.ctrlDiv.html('<span style="color: red;">Unable to fetch schema information.</span>'));
	}*/

	// downloads a unit of content; if nothing left to download, proceeds to the interactive part
	private downloadAxisBlock():void
	{
		// contemplate compounds first
		let doneCount = 0, roster:number[] = [];
		for (let n = 0; n < this.compounds.length; n++)
		{
			let cpd = this.compounds[n];
			if (cpd.loaded) {doneCount++; continue;}
			roster.push(n);
			if (roster.length >= 100) break;
		}
		if (roster.length > 0)
		{
			this.ctrlDiv.text('Acquiring compounds: ' + doneCount + ' of ' + this.compounds.length);

			let compoundIDList:number[] = [];//, pubchemCIDList:number[] = [], pubchemSIDList:number[] = [];
			let srcidx:number[] = [];
			for (let i of roster)
			{
				let cpd = this.compounds[i];
				cpd.loaded = true; // preemptive
				for (let cpdID of cpd.compoundIDList)
				{
					compoundIDList.push(cpdID);
					srcidx.push(i);
					this.allCompounds.add(cpdID);
				}
			}

			let params = {'compoundIDList': compoundIDList};
			callREST('REST/ListCompounds', params,
				(data:any) =>
				{
					for (let n = 0; n < data.compoundIDList.length; n++)
					{
						let cpd = this.compounds[srcidx[n]];
						cpd.pubchemCIDList.push(data.pubchemCIDList[n]);
						cpd.pubchemSIDList.push(data.pubchemSIDList[n]);
						cpd.vaultIDList.push(data.vaultIDList[n]);
						cpd.vaultMIDList.push(data.vaultMIDList[n]);
						cpd.molfileList.push(data.molfileList[n]);
						//cpd.svgList.push(null);
					}
					this.downloadAxisBlock();
				});
			return;
		}

		// contemplate assays next
		doneCount = 0;
		roster = [];
		for (let n = 0; n < this.assays.length; n++)
		{
			let assay = this.assays[n];
			if (assay.loaded1) {doneCount++; continue;}
			roster.push(n);
			if (roster.length >= 10) break;
		}
		if (roster.length > 0)
		{
			this.ctrlDiv.text('Acquiring assays: ' + doneCount + ' of ' + this.assays.length);

			let assayIDList:number[] = [], uniqueIDList:string[] = [];
			for (let i of roster)
			{
				let assay = this.assays[i];
				assay.loaded1 = true; // preemptive
				assayIDList.push(assay.assayID > 0 ? assay.assayID : 0);
				uniqueIDList.push(assay.uniqueID ? assay.uniqueID : '');
			}

			let params = {'assayIDList': assayIDList, 'uniqueIDList': uniqueIDList, 'withCompounds': false};
			callREST('REST/SummariseAssays', params,
				(data:any[]) =>
				{
					for (let n = 0; n < roster.length; n++)
					{
						let assay = this.assays[roster[n]], result = data[n];
						assay.assayID = result.assayID;
						assay.uniqueID = result.uniqueID;
						assay.shortText = result.shortText;
						assay.annotations = result.annotations;
						assay.actives = [];
						assay.inactives = [];
						assay.probes = [];
					}
					this.downloadAxisBlock();
				});
			return;
		}

		// everything is downloaded: action time
		this.ctrlDiv.empty();

		this.clusterXAssays();
		this.clusterYCompounds();
		this.rebuildContent();
		this.predictMissingActivities();

		this.createProgress();
		this.downloadCompoundBlock();
	}

	// download a block of assays and interrogate the corresponding compounds
	private downloadCompoundBlock():void
	{
		if (this.cancelled) return;

		let doneCount = 0, roster:number[] = [];
		for (let n = 0; n < this.assays.length; n++)
		{
			let assay = this.assays[n];
			if (assay.loaded2) {doneCount++; continue;}
			roster.push(n);
			if (roster.length >= 10) break;
		}
		if (roster.length > 0)
		{
			this.progPos = doneCount;
			this.progCount = this.assays.length;
			this.updateProgress();

			let assayIDList:number[] = [];
			for (let i of roster)
			{
				let assay = this.assays[i];
				assay.loaded2 = true; // preemptive
				assayIDList.push(assay.assayID);
			}
			let compoundIDList:number[] = [];
			for (let cpd of this.compounds) for (let cpdID of cpd.compoundIDList) compoundIDList.push(cpdID);

			let params = {'assayIDList': assayIDList, 'withCompounds': true, 'onlyCompounds': compoundIDList};
			callREST('REST/SummariseAssays', params,
				(data:any[]) =>
				{
					for (let n = 0; n < roster.length; n++)
					{
						let assay = this.assays[roster[n]], result = data[n];
						for (let compoundID of result.actives) if (this.allCompounds.has(compoundID)) assay.actives.push(compoundID);
						for (let compoundID of result.inactives) if (this.allCompounds.has(compoundID)) assay.inactives.push(compoundID);
						for (let compoundID of result.probes) if (this.allCompounds.has(compoundID)) assay.probes.push(compoundID);
					}
					this.deriveContent();
					this.redrawContent();
					this.downloadCompoundBlock();
				});
			return;
		}

		this.removeProgress();
	}

	// redefines the blocks of assays on the X-axis: combining and ordering as appropriate
	private clusterXAssays():void
	{
		// start with grouping
		this.axisX = [];
		if (this.assayGroup.length == 0)
		{
			for (let n = 0; n < this.assays.length; n++) this.axisX.push({'idx': [n]});
		}
		else
		{
			let propGrp = new Set<string>();
			for (let assn of this.assayGroup) propGrp.add(keyPropGroup(assn.propURI, assn.groupNest));
			let mapPos:Record<string, number> = {};

			for (let n = 0; n < this.assays.length; n++)
			{
				let assay = this.assays[n];
				let values:string[] = [];
				let hierarchy = new Set<string>();
				for (let annot of assay.annotations) if (annot.valueURI && propGrp.has(keyPropGroup(annot.propURI, annot.groupNest)))
				{
					values.push(annot.valueURI);
					if (annot.valueHier) for (let uri of annot.valueHier) hierarchy.add(uri);
				}
				for (let uri of hierarchy)
				{
					let i = values.indexOf(uri);
					if (i >= 0) values.splice(i, 1);
				}

				/*let values:string[] = [];
				for (let annot of this.assays[n].annotations) if (propGrp.has(annot[0])) values.push(annot[1]);*/

				// if none, create a singleton
				if (values.length == 0)
				{
					this.axisX.push({'idx': [n]});
					continue;
				}

				// otherwise, lookup the tag and associate that as a key index
				values.sort();
				let tag = values.join('\n'), pos = mapPos[tag];
				if (pos == null)
				{
					mapPos[tag] = this.axisX.length;
					this.axisX.push({'idx': [n]});
				}
				else this.axisX[pos].idx.push(n);
			}
		}

		// now cluster/sort
		this.assayTree = null;
		if (this.assayGroup.length > 0)
		{
			// (note: could use tree pattern to sort on multiple properties...? might be better than nothing...)
			let assn = this.assayGroup[0];
			this.assayTree = new TreePattern();
			for (let n = 0; n < this.axisX.length; n++)
			{
				let branches:string[][] = [];
				for (let idx of this.axisX[n].idx) for (let annot of this.assays[idx].annotations)
				{
					if (!compatiblePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest)) continue;

					let branch = Vec.concat([annot.valueURI], annot.valueHier).reverse();

					for (let look of branches) if (Vec.equals(branch, look)) {branch = null; break;}
					if (branch) branches.push(branch);
				}
				this.assayTree.addNode(branches);
			}
			this.assayTree.cluster();
			this.axisX = Vec.idxGet(this.axisX, this.assayTree.order);
		}
		// (... other kinds of sort/cluster, without the benefit of grouping)
		// (... maybe make the TreePattern capable of sorting a gigantic jumble of stuff, even without a group --> everything...?)

		this.hideDendroPopover();
		this.unpackModels();
	}

	// redefines the blocks of compounds on the Y-axis: combining and ordering as appropriate
	private clusterYCompounds():void
	{
		// !! actual clustering...
		this.axisY = [];
		for (let n = 0; n < this.compounds.length; n++) this.axisY.push({'idx': [n]});
	}

	// completely rebuild the grid and supporting elements (zap everything already extant)
	private rebuildContent():void
	{
		this.areaDiv.css('left', 0);
		this.areaDiv.css('width', 0);
		this.areaDiv.css('height', 0);

		let width = Math.floor(0.95 * this.mainDiv.width());
		let height = Math.floor(0.8 * $(window).height());
		let density = pixelDensity();

		this.szw = Math.max(1, Math.min(10, Math.floor(width * density / this.axisX.length) / density));
		this.szh = Math.max(1, Math.min(10, Math.floor(height * density / this.axisY.length) / density));

		this.focusX = -1;
		this.focusY = -1;
		this.focusSticky = false;
		this.dragType = null;

		this.buildControl();
		this.buildDetail();
		this.updateGrid();
	}

	// control area: buttons that allow modification of the way the grid is displayed
	private buildControl():void
	{
		this.ctrlDiv.empty();

		let table = $('<table></table>').appendTo(this.ctrlDiv);
		let tr = $('<tr></tr>').appendTo(table);

		tr.append('<td>Assays:&nbsp;</td>');
		let tdAssays = $('<td></td>').appendTo(tr);
		this.btnAssayGroup = $('<button class="btn btn-normal">Group By</button>').appendTo(tdAssays);
		this.btnAssayGroup.click(() => this.actionAssayGroup());

		tdAssays.append(' ');

		this.btnBuildModels = $('<button class="btn btn-normal"></button>').appendTo(tdAssays);
		this.btnBuildModels.append('<span class="glyphicon glyphicon-bullhorn" style=\"height: 1.2em;"></span>&nbsp;&nbsp;Build Models');
		this.btnBuildModels.click(() => this.buildModels());
	}

	// detail area: the section underneath the grid that shows what's been selected
	private buildDetail():void
	{
		this.detailDiv.empty();

		if (this.focusX < 0 || this.focusX >= this.axisX.length || this.focusY < 0 || this.focusY >= this.axisY.length) return;

		let assayBlk = this.axisX[this.focusX], cpdBlk = this.axisY[this.focusY];

		let tdTitle = (td:JQuery, txt:string):void =>
		{
			td.css('vertical-align', 'top');
			td.css('font-weight', 'bold');
			td.css('padding-right', '1em');
			td.css('text-align', 'left');
			td.text(txt);
			return;
		};
		let tdUnderline = (td:JQuery):JQuery =>
		{
			td.css('padding', '0.2em');
			td.css('border-bottom', '1px solid #E6EDF2');
			return td;
		};

		// if there is a group by, then show these
		if (this.assayGroup.length > 0)
		{
			let table = $('<table></table>').appendTo(this.detailDiv);

			// fetch the values from the first assay only (since they're all the same, by definition)
			let values:AssayAnnotation[] = [];
			for (let assn of this.assayGroup) for (let annot of this.assays[assayBlk.idx[0]].annotations)
			{
				if (compatiblePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest)) values.push(annot);
			}

			for (let n = 0; n < Math.max(values.length, 1); n++)
			{
				let tr = $('<tr></tr>').appendTo(table);
				let td = $('<td></td>').appendTo(tr);
				if (n == 0) tdTitle(td, 'Group By');

				td = $('<td></td>').appendTo(tr);
				if (n < values.length)
				{
					let span = $('<span></span>').appendTo(td);
					span.css('background-color', '#E6EDF2');
					span.css('border', '1px solid #C0C0C0');
					span.css('padding', '0.2em');
					span.text(values[n].valueURI);
					let opt:PopoverOptions = {'schemaURI': this.schema.schemaURI, 'propURI': values[n].propURI, 'groupNest': values[n].groupNest, 'valueURI': values[n].valueURI};
					(async () =>
					{
						span.text(await Popover.fetchCachedVocabLabel(opt));
					})();
				}
				else td.text('No terms defined.');
			}
		}

		// summary about what has been selected
		let colAssays = this.axisX[this.focusX].idx.length;
		let colActive = 0, colInactive = 0, colProbe = 0;
		let rowActive = 0, rowInactive = 0, rowProbe = 0;
		for (let n = 0; n < this.axisY.length; n++)
		{
			colActive += Vec.arrayLength(this.gridActives[n][this.focusX]);
			colInactive += Vec.arrayLength(this.gridInactives[n][this.focusX]);
			colProbe += Vec.arrayLength(this.gridProbes[n][this.focusX]);
		}
		for (let n = 0; n < this.axisX.length; n++)
		{
			rowActive += Vec.arrayLength(this.gridActives[this.focusY][n]);
			rowInactive += Vec.arrayLength(this.gridInactives[this.focusY][n]);
			rowProbe += Vec.arrayLength(this.gridProbes[this.focusY][n]);
		}

		let paraSummary = $('<p></p>').appendTo(this.detailDiv);
		let summarise = (title:string, heading:string, active:number, inactive:number, probe:number):void =>
		{
			paraSummary.append(' <b>' + title + '</b>. ' + escapeHTML(heading) + ': ');
			let bits:string[] = [];
			if (active > 0) bits.push('<u>' + active + '</u> &times; active');
			if (inactive > 0) bits.push('<u>' + inactive + '</u> &times; inactive');
			if (probe > 0) bits.push('<u>' + probe + '</u> &times; probe');
			if (bits.length == 0) paraSummary.append('nothing. ');
			else paraSummary.append(bits.join(', ') + '. ');
		};
		summarise('Column', colAssays + ' assay' + (colAssays == 1 ? ' has' : 's have'), colActive, colInactive, colProbe);
		summarise('Row', 'Compound affects assays', rowActive, rowInactive, rowProbe);

		// assay(s) first
		let table = $('<table></table>').appendTo(this.detailDiv);
		let assayIDList = new Set<number>();
		for (let n = 0; n < assayBlk.idx.length; n++)
		{
			let tr = $('<tr></tr>').appendTo(table);
			let td = $('<td style="padding: 0.2em;"></td>').appendTo(tr);
			if (n == 0) tdTitle(td, assayBlk.idx.length == 1 ? 'Assay' : 'Assays');

			td = tdUnderline($('<td style="vertical-align: top;"></td>').appendTo(tr));

			let assay = this.assays[assayBlk.idx[n]];
			assayIDList.add(assay.assayID);
			td.text(assay.shortText);

			td = tdUnderline($('<td style="vertical-align: bottom"></td>').appendTo(tr));
			td.css('padding-left', '1em');
			let linkView = $('<a target="_blank"></a>').appendTo(td);
			linkView.attr('href', restBaseURL + '/assign.jsp?assayID=' + assay.assayID);
			linkView.text('View');
			td.append('&nbsp;\u{21F2}');

			td = tdUnderline($('<td style="vertical-align: bottom"></td>').appendTo(tr));
			let [src, id] = UniqueIdentifier.parseKey(assay.uniqueID);
			if (src)
			{
				let linkAID = $('<a target="_blank"></a>').appendTo(td);
				linkAID.attr('href', UniqueIdentifier.composeRefURL(assay.uniqueID));
				linkAID.html(src.shortName + ' ' + id);
			}
		}

		// compound(s) next
		let probes:number[] = [], actives:number[] = [], inactives:number[] = [];
		probes = this.gridProbes[this.focusY][this.focusX]; if (probes == null) probes = [];
		actives = this.gridActives[this.focusY][this.focusX]; if (actives == null) actives = [];
		inactives = this.gridInactives[this.focusY][this.focusX]; if (inactives == null) inactives = [];

		table = $('<table></table>').appendTo($('<p style="padding-top: 1em;"></p>').appendTo(this.detailDiv));

		for (let [tname, cpdIDList] of [['Probe', probes], ['Active', actives], ['Inactive', inactives]])
		{
			if (cpdIDList.length == 0) continue;

			// pull out all assays that have this compound showing in the given state
			let assays:AssayGridAssay[] = [];
			for (let assay of this.assays) if (assayIDList.has(assay.assayID))
			{
				let assayCpd = tname == 'Probe' ? assay.probes :
							tname == 'Active' ? assay.actives :
							tname == 'Inactive' ? assay.inactives : [];
				for (let compoundID of cpdIDList) if (assayCpd.indexOf(compoundID as number) >= 0)
				{
					assays.push(assay);
					break;
				}
			}

			// pull out all compounds (unique by identifier combo) that correspond to this compound ID
			let dup = new Set<string>();
			let molfiles:string[] = [];
			let cidList:number[] = [], sidList:number[] = [], vidList:number[] = [], midList:number[] = [];
			for (let cpd of this.compounds) for (let compoundID of cpdIDList)
				if (cpd.compoundIDList.indexOf(compoundID as number) >= 0)
			{
				for (let n = 0; n < cpd.compoundIDList.length; n++)
				{
					let cid = cpd.pubchemCIDList[n], sid = cpd.pubchemSIDList[n];
					let vid = cpd.vaultIDList[n], mid = cpd.vaultMIDList[n];
					let key = `${cid}:${sid}:${vid}:${mid}`;
					if (dup.has(key)) continue;
					dup.add(key);
					molfiles.push(cpd.molfileList[n]);
					cidList.push(cid);
					sidList.push(sid);
					vidList.push(vid);
					midList.push(mid);
				}
			}

			let tr = $('<tr></tr>').appendTo(table);
			tdTitle($('<td></td>').appendTo(tr), tname + (assays.length == 1 ? ' Assay' : ' Assays'));
			tdTitle($('<td></td>').appendTo(tr), molfiles.length == 1 ? 'Molecule' : 'Molecules');

			tr = $('<tr></tr>').appendTo(table);
			let tdAssay = tdUnderline($('<td style="vertical-align: top;"></td>').appendTo(tr));
			let tdMol = tdUnderline($('<td style="vertical-align: top;"></td>').appendTo(tr));
			tdAssay.css('padding-right', '1em');
			tdMol.css('border-left', '1px solid #E6EDF2');

			this.renderMatchedAssays(tdAssay, assays);
			this.renderMatchedCompounds(tdMol, molfiles, cidList, sidList, vidList, midList);
		}

		if (assayBlk.model && assayBlk.model.bayes && assayBlk.model.bayes.rocAUC > 0)
		{
			let bayes = assayBlk.model.bayes;
			let para = $('<p></p>').appendTo(this.detailDiv);
			para.append('<b>Bayesian Model</b><br>');

			let preds:number[] = [];
			for (let cpdidx of cpdBlk.idx) if (assayBlk.model.predictions[cpdidx] != null) preds.push(assayBlk.model.predictions[cpdidx]);
			if (preds.length > 0)
			{
				para.append('Prediction' + (preds.length == 1 ? '' : 's') + ': ');
				for (let n = 0; n < preds.length; n++)
				{
					if (n > 0) para.append(', ');
					let b = $('<b></b>').appendTo(para);
					if (preds[n] < 0) b.text('<0%');
					else if (preds[n] > 1) b.text('>100%');
					else b.text((preds[n] * 100).toFixed(1) + '%');
				}
				para.append('<br>');
			}

			para.append('Model created from ' + bayes.trainingActives + ' active' + (bayes.trainingActives == 1 ? '' : 's') +
						' of ' + bayes.trainingSize + ' molecules. ROC = ' + bayes.rocAUC.toFixed(4) + '<br>');
			this.drawROCCurve(assayBlk.model);
			para.append(assayBlk.model.svg);
		}
	}

	// blat out a list of assays
	private renderMatchedAssays(parent:JQuery, assays:AssayGridAssay[]):void
	{
		let table = $('<table></table>').appendTo(parent);
		for (let assay of assays)
		{
			let tr = $('<tr></tr>').appendTo(table);

			let tdText = $('<td></td>').appendTo(tr);
			tdText.text(assay.shortText);

			let tdLink = $('<td style="padding-left: 1em; vertical-align: bottom;"></td>').appendTo(tr);
			let linkView = $('<a target="_blank"></a>').appendTo(tdLink);
			linkView.attr('href', restBaseURL + '/assign.jsp?assayID=' + assay.assayID);
			linkView.text('View');
			tdLink.append('&nbsp;\u{21F2}');

			let [src, id] = UniqueIdentifier.parseKey(assay.uniqueID);
			if (src)
			{
				tdLink.append('&nbsp;');
				let linkAID = $('<a target="_blank"></a>').appendTo(tdLink);
				linkAID.attr('href', UniqueIdentifier.composeRefURL(assay.uniqueID));
				linkAID.html(src.shortName + ' ' + id);
			}
		}
	}

	// render a sequence of molecular structures, and corresponding links
	private renderMatchedCompounds(parent:JQuery, molfiles:string[],
								cidList:number[], sidList:number[], vidList:number[], midList:number[]):void
	{
		let table = $('<table></table>').appendTo(parent);
		for (let n = 0; n < molfiles.length; n++)
		{
			let tr = $('<tr></tr>').appendTo(table);

			let tdMol = $('<td style="text-align: center;"></td>').appendTo(tr);
			if (molfiles[n])
			{
				let policy = wmk.RenderPolicy.defaultColourOnWhite();
				let effects = new wmk.RenderEffects();
				let measure = new wmk.OutlineMeasurement(0, 0, policy.data.pointScale);
				try
				{
					let mol = wmk.MoleculeStream.readMDLMOL(molfiles[n]);
					let layout = new wmk.ArrangeMolecule(mol, measure, policy, effects);
					layout.arrange();

					let metavec = new wmk.MetaVector();
					new wmk.DrawMolecule(layout, metavec).draw();
					metavec.normalise();

					let span = $('<span></span>').appendTo(tdMol);
					span.append(metavec.createSVG());
				}
				catch (ex)
				{
					console.log('Molfile parser fail: ' + ex);
					console.log(molfiles[n]);
				}
			}
			else tdMol.text('(structure is absent)'); // (actually these are filtered out)

			let tdLink = $('<td style="padding-left: 1em; vertical-align: middle;"></td>').appendTo(tr);
			let cid = cidList[n], sid = sidList[n], vid = vidList[n], mid = midList[n];

			if (cid > 0)
			{
				let para = $('<p style="margin: 0; white-space: nowrap;"></p>').appendTo(tdLink);
				para.text('PubChem compound ');
				let ahref = $('<a target="_blank"></a>').appendTo(para);
				ahref.attr('href', 'http://pubchem.ncbi.nlm.nih.gov/compound/' + cid);
				ahref.text('CID ' + cid);
			}
			if (sid > 0)
			{
				let para = $('<p style="margin: 0; white-space: nowrap;"></p>').appendTo(tdLink);
				para.text('PubChem substance ');
				let ahref = $('<a target="_blank"></a>').appendTo(para);
				ahref.attr('href', 'http://pubchem.ncbi.nlm.nih.gov/substance/' + sid);
				ahref.text('SID ' + sid);
			}
			if (vid > 0 && mid > 0)
			{
				let vid = vidList[n], mid = midList[n];
				let para = $('<p style="margin: 0; white-space: nowrap;"></p>').appendTo(tdLink);
				para.text('Vault molecule ');
				let ahref = $('<a target="_blank"></a>').appendTo(para);
				ahref.attr('href', 'https://app.collaborativedrug.com/vaults/' + vid + '/molecules/' + mid);
				ahref.text('ID ' + mid);
			}
		}
	}

	// resize the grid according to current spec, and draw on the content
	private updateGrid():void
	{
		this.insetY = this.assayTree ? this.assayTree.maxDepth * this.dendroH : 12;
		let gridW = Math.max(this.insetX + this.szw * this.axisX.length, this.MINWIDTH);
		let gridH = this.insetY + this.szh * this.axisY.length;

		//this.areaDiv.css('left', Math.floor(0.5 * (width - gridW)) + 'px');
		this.areaDiv.css('width', gridW + 'px');
		this.areaDiv.css('height', gridH + 'px');

		this.axisCanvas.css('width', gridW + 'px');
		this.axisCanvas.css('height', gridH + 'px');
		this.gridCanvas.css('width', gridW + 'px');
		this.gridCanvas.css('height', gridH + 'px');
		this.focusCanvas.css('width', gridW + 'px');
		this.focusCanvas.css('height', gridH + 'px');

		let density = wmk.pixelDensity();
		this.axisCanvas.attr('width', gridW * density);
		this.axisCanvas.attr('height', gridH * density);
		this.gridCanvas.attr('width', gridW * density);
		this.gridCanvas.attr('height', gridH * density);
		this.focusCanvas.attr('width', gridW * density);
		this.focusCanvas.attr('height', gridH * density);

		this.deriveContent();
		this.redrawContent();
	}

	// redraws content of the grid, assuming everything is the right size
	private redrawContent():void
	{
		let ctx = (this.axisCanvas[0] as HTMLCanvasElement).getContext('2d');
		let density = wmk.pixelDensity();
		const ncols = this.axisX.length, nrows = this.axisY.length;
		let gridW = this.insetX + this.szw * ncols, gridH = this.insetY + this.szh * nrows;

		ctx.save();
		ctx.scale(density, density);

		ctx.clearRect(0, 0, Math.max(gridW, this.MINWIDTH), gridH);
		ctx.fillStyle = '#FBFBFF';
		ctx.fillRect(this.insetX, this.insetY, gridW - this.insetX, gridH - this.insetY);

		// axis boundary
		ctx.lineWidth = 1;
		ctx.strokeStyle = '#E0E0E0';
		ctx.beginPath();
		ctx.moveTo(this.insetX - 0.5, gridH);
		ctx.lineTo(this.insetX - 0.5, this.insetY - 0.5);
		ctx.lineTo(gridW, this.insetY - 0.5);
		ctx.stroke();

		// axis labels
		ctx.fillStyle = '#C0C0C0';
		ctx.font = '12px sans-serif';
		ctx.textBaseline = 'bottom';
		let xlabel = 'assays', ylabel = 'compounds';

		if (!this.assayTree)
		{
			let tw = ctx.measureText(xlabel).width;
			ctx.fillText(xlabel, this.insetX + 0.5 * (gridW - this.insetX - tw), this.insetY - 2);
		}

		ctx.save();
		ctx.rotate(-0.5 * Math.PI);
		ctx.fillText(ylabel, 0.5 * (gridH - this.insetY - ctx.measureText(ylabel).width) - gridH, this.insetX - 2);
		ctx.restore();

		// if there's an assay tree dendrogram, we draw that instead of the text label
		if (this.assayTree)
		{
			let ptrad = Math.min(this.szw * 0.4, 2), lsz = Math.min(this.szw * 0.3, 1);
			let dendX = (x:number):number => this.insetX + (x + 0.5) * this.szw;
			let dendY = (y:number):number => (y + 0.5) * this.dendroH;

			ctx.beginPath();
			for (let d of this.assayTree.dendrogram) if (d.parent >= 0)
			{
				let p = this.assayTree.dendrogram[d.parent];
				let x1 = dendX(d.x), y1 = dendY(d.y);
				let x2 = dendX(p.x), y2 = dendY(p.y);
				if (x1 == x2)
				{
					// straight line will suffice
					ctx.moveTo(x1, y1);
					ctx.lineTo(x2, y2);
				}
				else
				{
					// disjointed connection
					let midY = y2 + 0.5 * this.dendroH, seg = 0.5 * this.dendroH, dir = x2 > x1 ? 1 : -1;
					ctx.moveTo(x1, y1);
					ctx.lineTo(x1, midY + seg);
					ctx.quadraticCurveTo(x1, midY, x1 + seg * dir, midY);
					ctx.lineTo(x2 - seg * dir, midY);
					ctx.quadraticCurveTo(x2, midY, x2, midY - seg);
					ctx.lineTo(x2, y2);
				}
			}
			ctx.strokeStyle = '#C0C0C0';
			ctx.lineWidth = lsz;
			ctx.stroke();

			for (let d of this.assayTree.dendrogram)
			{
				ctx.beginPath();
				ctx.ellipse(dendX(d.x), dendY(d.y), ptrad, ptrad, 0, 0, 2 * Math.PI, true);
				ctx.fillStyle = d.uri ? '#1362B3' : '#C0C0C0';
				ctx.fill();
			}
		}

		ctx.restore();

		this.redrawGrid();
		this.redrawFocus();
	}

	// redraw just the grid
	private redrawGrid():void
	{
		let ctx = (this.gridCanvas[0] as HTMLCanvasElement).getContext('2d');
		let density = wmk.pixelDensity();
		const ncols = this.axisX.length, nrows = this.axisY.length;
		let gridW = this.insetX + this.szw * ncols, gridH = this.insetY + this.szh * nrows;

		ctx.save();
		ctx.scale(density, density);

		ctx.clearRect(0, 0, Math.max(gridW, this.MINWIDTH), gridH);

		// fill out the grid proper
		for (let y = 0; y < nrows; y++) for (let x = 0; x < ncols; x++)
		{
			if (this.gridProbes[y][x]) ctx.fillStyle = '#00FF00';
			else if (this.gridActives[y][x]) ctx.fillStyle = Theme.STRONG_HTML;
			else if (this.gridInactives[y][x]) ctx.fillStyle = '#C0C0C0';
			else if (this.axisX[x].model)
			{
				let modelPreds = this.axisX[x].model.predictions, preds:number[] = [];
				for (let cpdidx of this.axisY[y].idx) if (modelPreds[cpdidx] != null) preds.push(modelPreds[cpdidx]);
				if (preds.length == 0) continue;
				let value = preds.length == 1 ? preds[0] : Vec.sum(preds) / preds.length;
				value = Math.max(0, Math.min(1, value));

				let rgb = wmk.blendRGB(value, Theme.WEAK_RGB, Theme.STRONG_RGB);
				ctx.fillStyle = wmk.colourCode(rgb);
				ctx.fillRect(this.insetX + (x + 0.2) * this.szw, this.insetY + (y + 0.2) * this.szh, 0.6 * this.szw, 0.6 * this.szh);

				continue;
			}
			else continue;

			ctx.fillRect(this.insetX + x * this.szw, this.insetY + y * this.szh, this.szw, this.szh);
		}

		ctx.restore();
	}

	// redraws just the focus canvas, i.e. the overlay on top of the main grid
	private redrawFocus():void
	{
		let ctx = (this.focusCanvas[0] as HTMLCanvasElement).getContext('2d');
		let density = wmk.pixelDensity();
		const ncols = this.axisX.length, nrows = this.axisY.length;
		let gridW = this.insetX + this.szw * ncols, gridH = this.insetY + this.szh * nrows;

		ctx.save();
		ctx.scale(density, density);

		ctx.clearRect(0, 0, Math.max(gridW, this.MINWIDTH), gridH);
		if (this.focusX < 0 || this.focusX >= ncols || this.focusY < 0 || this.focusY > nrows)
		{
			ctx.restore();
			return;
		}

		// crosshairs
		ctx.strokeStyle = wmk.colourCanvas(0xF00000FF);
		let fx = this.insetX + (this.focusX + 0.5) * this.szw, fy = this.insetY + (this.focusY + 0.5) * this.szh;
		ctx.lineWidth = this.szw;
		wmk.drawLine(ctx, fx, 0, fx, gridH);
		ctx.lineWidth = this.szh;
		wmk.drawLine(ctx, 0, fy, gridW, fy);

		// figure out box size and position
		let bordersz = 2, unitsz = 10, delta = 5, nitems = 2 * delta + 1;
		let boxsz = 2 * bordersz + unitsz * nitems, molH = 0.7 * boxsz;

		// decide where to place the box relative to the cursor
		let hx = Math.max(gridW, this.MINWIDTH) - fx, hy = gridH - fy;
		let szNW = fx * fy, szNE = hx * fy;
		let szSW = fx * hy, szSE = hx * hy;
		let gap = 10;
		let dx = 0, dy = 0;
		if (szNW > szNE && szNW > szSW && szNW > szSE && fx > boxsz + gap + 1 && fy > boxsz + molH + gap + 1) [dx, dy] = [-1, -1];
		else if (szSW > szNE && szSW > szSE && fx > boxsz + gap + 1 && hy > boxsz + molH + gap + 1) [dx, dy] = [-1, 1];
		else if (szNE > szSE && hx > boxsz + gap + 1 && fy > boxsz + molH + gap + 1) [dx, dy] = [1, -1];
		else [dx, dy] = [1, 1];
		let boxX = dx < 0 ? Math.floor(fx) - gap - boxsz : Math.ceil(fx) + gap;
		let boxY = dy < 0 ? Math.floor(fy) - gap - boxsz : Math.ceil(fy) + gap;
		let minX = boxX + bordersz, minY = boxY + bordersz;
		let maxX = boxX + boxsz - bordersz, maxY = boxY + boxsz - bordersz;

		// border and background
		ctx.fillStyle = this.focusSticky ? '#1362B3' : '#E6EDF2';
		ctx.fillRect(boxX, boxY, boxsz, boxsz);
		ctx.fillStyle = 'white';
		ctx.fillRect(boxX + bordersz, boxY + bordersz, unitsz * nitems, unitsz * nitems);

		// draw the encapsulating crosshairs
		let midX1 = minX + delta * unitsz, midY1 = minY + delta * unitsz;
		let midX2 = midX1 + unitsz, midY2 = midY1 + unitsz;
		ctx.fillStyle = '#E6EDF2';
		ctx.fillRect(midX1, minY, unitsz, maxY - minY);
		ctx.fillRect(minX, midY1, maxX - minX, unitsz);
		ctx.lineWidth = 0.5;
		ctx.strokeStyle = '#D0D0D0';
		ctx.beginPath();
		ctx.moveTo(minX, midY1);
		ctx.lineTo(midX1, midY1);
		ctx.lineTo(midX1, minY);
		ctx.moveTo(midX2, minY);
		ctx.lineTo(midX2, midY1);
		ctx.lineTo(maxX, midY1);
		ctx.moveTo(minX, midY2);
		ctx.lineTo(midX1, midY2);
		ctx.lineTo(midX1, maxY);
		ctx.moveTo(midX2, maxY);
		ctx.lineTo(midX2, midY2);
		ctx.lineTo(maxX, midY2);
		ctx.stroke();

		// count up the things, for relative sizing
		let maxnum = 0;
		for (let i = -delta; i <= delta; i++)
		{
			let y = this.focusY + i;
			if (y < 0 || y >= nrows) continue;
			for (let j = -delta; j <= delta; j++)
			{
				let x = this.focusX + j;
				if (x < 0 || x >= ncols) continue;

				let num = Vec.arrayLength(this.gridActives[y][x]) +
						Vec.arrayLength(this.gridInactives[y][x]) +
						Vec.arrayLength(this.gridProbes[y][x]);
				maxnum = Math.max(maxnum, num);
			}
		}

		// now draw them
		for (let i = -delta; i <= delta; i++)
		{
			let y = this.focusY + i;
			if (y < 0 || y >= nrows) continue;
			let py = minY + (i + delta) * unitsz;

			for (let j = -delta; j <= delta; j++)
			{
				let x = this.focusX + j;
				if (x < 0 || x >= ncols) continue;
				let px = minX + (j + delta) * unitsz;

				let actives = Vec.arrayLength(this.gridActives[y][x]);
				let inactives = Vec.arrayLength(this.gridInactives[y][x]);
				let probes = Vec.arrayLength(this.gridProbes[y][x]);
				if (actives + inactives + probes == 0)
				{
					if (!this.axisX[x].model) continue;

					let modelPreds = this.axisX[x].model.predictions, preds:number[] = [];
					for (let cpdidx of this.axisY[y].idx) if (modelPreds[cpdidx] != null) preds.push(modelPreds[cpdidx]);
					if (preds.length == 0) continue;
					let value = preds.length == 1 ? preds[0] : Vec.sum(preds) / preds.length;
					value = Math.max(0, Math.min(1, value));

					let rgb = wmk.blendRGB(value, Theme.WEAK_RGB, Theme.STRONG_RGB);
					ctx.fillStyle = wmk.colourCode(rgb);
					ctx.fillRect(px + 0.25 * unitsz, py + 0.25 * unitsz, 0.5 * unitsz, 0.5 * unitsz);
				}
				else this.drawFocusItem(ctx, px, py, unitsz, maxnum, actives, inactives, probes);
			}
		}

		// draw the molecule, above or below
		let cpdidx = this.axisY[this.focusY].idx; // note: multiple compounds are allowed, but for now just do first one
		if (cpdidx.length > 0) for (let molfile of this.compounds[cpdidx[0]].molfileList) if (molfile)
		{
			try
			{
				let mol = wmk.MoleculeStream.readMDLMOL(molfile);
				let policy = wmk.RenderPolicy.defaultColourOnWhite();
				let effects = new wmk.RenderEffects();
				let measure = new wmk.OutlineMeasurement(0, 0, policy.data.pointScale);
				let layout = new wmk.ArrangeMolecule(mol, measure, policy, effects);
				layout.arrange();
				layout.squeezeInto(0, 0, boxsz, molH, 0);
				let bounds = layout.determineBoundary();
				let mx = minX, my = 0, mw = maxX - minX, mh = bounds[3] - bounds[1];
				if (dy < 0) my = minY - 1 - mh; else my = maxY + 1;
				/*ctx.fillStyle = 'white';
				ctx.globalAlpha = 0.8;
				ctx.fillRect(mx, my, mw, mh);
				ctx.globalAlpha = 1;*/
				layout.squeezeInto(mx, my, mw, mh, 0);

				let metavec = new wmk.MetaVector();
				new wmk.DrawMolecule(layout, metavec).draw();
				metavec.renderContext(ctx);

				break;
			}
			catch (ex)
			{
				console.log('Molfile parser fail: ' + ex);
				console.log(molfile);
			}
		}

		ctx.restore();

		// for posterity: stash the positions of the focus details, so they are clickable
		this.focusBoxUnitW = unitsz;
		this.focusBoxUnitH = unitsz;
		this.focusBoxXPos = [];
		this.focusBoxYPos = [];
		for (let n = -delta; n <= delta; n++)
		{
			this.focusBoxXPos.push(this.focusX + n);
			this.focusBoxXPos.push(minX + (n + delta) * unitsz);
			this.focusBoxYPos.push(this.focusY + n);
			this.focusBoxYPos.push(minY + (n + delta) * unitsz);
		}
	}

	// draw just one focus item
	private drawFocusItem(ctx:CanvasRenderingContext2D, x:number, y:number, sz:number, maxnum:number,
						actives:number, inactives:number, probes:number):void
	{
		let num = actives + inactives + probes;
		if (num == 0 || sz == 0) return;

		let rad = Math.sqrt(num / maxnum * 0.25 * sz * sz);
		let cx = x + 0.5 * sz, cy = y + 0.5 * sz;

		if (actives == num)
		{
			ctx.fillStyle = '#1362B3';
			ctx.beginPath();
			ctx.ellipse(cx, cy, rad, rad, 0, 0, TWOPI, false);
			ctx.fill();
			return;
		}
		if (probes == num)
		{
			ctx.fillStyle = '#00FF00';
			ctx.beginPath();
			ctx.ellipse(cx, cy, rad, rad, 0, 0, TWOPI, false);
			ctx.fill();
			return;
		}
		if (inactives > 0)
		{
			ctx.fillStyle = '#C0C0C0';
			ctx.beginPath();
			ctx.ellipse(cx, cy, rad, rad, 0, 0, TWOPI, false);
			ctx.fill();
		}

		if (actives == 0 && probes == 0) return;
		let theta1 = (actives / num) * TWOPI, theta2 = (probes / num) * TWOPI;
		let theta0 = -0.5 * Math.PI - 0.5 * (theta1 + theta2);
		if (actives > 0)
		{
			ctx.fillStyle = '#1362B3';
			ctx.beginPath();
			ctx.moveTo(cx, cy);
			ctx.ellipse(cx, cy, rad, rad, 0, theta0, theta0 + theta1, false);
			ctx.fill();
		}
		if (actives > 0)
		{
			ctx.fillStyle = '#00FF00';
			ctx.beginPath();
			ctx.moveTo(cx, cy);
			ctx.ellipse(cx, cy, rad, rad, 0, theta0 + theta1, theta0 + theta1 + theta2, false);
			ctx.fill();
		}
	}

	// build the grids for convenient access to whatever compounds are at each position
	private deriveContent():void
	{
		const ncols = this.axisX.length, nrows = this.axisY.length;
		this.gridActives = new Array(nrows);
		this.gridInactives = new Array(nrows);
		this.gridProbes = new Array(nrows);
		for (let y = 0; y < nrows; y++)
		{
			this.gridActives[y] = new Array(ncols);
			this.gridInactives[y] = new Array(ncols);
			this.gridProbes[y] = new Array(ncols);
		}

		let cpdRow:Record<number, number> = {};
		for (let y = 0; y < this.axisY.length; y++) for (let n of this.axisY[y].idx)
		{
			let cpd = this.compounds[n];
			//cpdRow[cpd.compoundID] = y;
			for (let cpdID of cpd.compoundIDList) cpdRow[cpdID] = y;
		}

		for (let x = 0; x < this.axisX.length; x++) for (let n of this.axisX[x].idx)
		{
			let assay = this.assays[n];
			for (let compoundID of assay.actives)
			{
				let y = cpdRow[compoundID];
				if (this.gridActives[y][x] == null) this.gridActives[y][x] = [compoundID];
				else /*if (this.gridActives[y][x].indexOf(compoundID) < 0)*/ this.gridActives[y][x].push(compoundID);
			}
			for (let compoundID of assay.inactives)
			{
				let y = cpdRow[compoundID];
				if (this.gridInactives[y][x] == null) this.gridInactives[y][x] = [compoundID];
				else /*if (this.gridInactives[y][x].indexOf(compoundID) < 0)*/ this.gridInactives[y][x].push(compoundID);
			}
			for (let compoundID of assay.probes)
			{
				let y = cpdRow[compoundID];
				if (this.gridProbes[y][x] == null) this.gridProbes[y][x] = [compoundID];
				else /*if (this.gridProbes[y][x].indexOf(compoundID) < 0)*/ this.gridProbes[y][x].push(compoundID);
			}
		}
	}

	// progress bar: start/update/stop cycle
	private createProgress():void
	{
		this.progDiv.empty();
		let table = $('<table></table>').appendTo(this.progDiv);
		table.css('margin-bottom', '0.5em');
		let td = $('<td valign="center"></td>').appendTo($('<tr></tr>').appendTo(table));

		let btn = $('<button class="btn btn-action"><span class="glyphicon glyphicon-stop"></span></button>').appendTo(td);

		this.progCanvas = $('<canvas></canvas>').appendTo(td);
		this.progCanvas.css('width', this.PROGWIDTH + 'px');
		this.progCanvas.css('height', this.PROGHEIGHT + 'px');

		let density = pixelDensity();
		this.progCanvas.attr('width', this.PROGWIDTH * density);
		this.progCanvas.attr('height', this.PROGHEIGHT * density);

		btn.click(() =>
		{
			this.cancelled = true;
			this.removeProgress();
		});
	}
	private updateProgress():void
	{
		let ctx = (this.progCanvas[0] as HTMLCanvasElement).getContext('2d');
		let density = pixelDensity();

		ctx.save();
		ctx.scale(density, density);

		let width = this.PROGWIDTH, height = this.PROGHEIGHT;
		ctx.clearRect(0, 0, width, height);

		if (this.progCount > 0)
		{
			let lw = 5, bw = width - 2 * lw;
			ctx.lineWidth = lw - 2;
			ctx.strokeStyle = '#E0E0E0';
			ctx.lineCap = 'round';
			drawLine(ctx, lw, 0.5 * height, lw + bw, 0.5 * height);

			ctx.strokeStyle = '#1362B3';
			ctx.lineWidth = lw;
			drawLine(ctx, lw, 0.5 * height, lw + (this.progPos / this.progCount) * bw, 0.5 * height);
		}

		ctx.restore();
	}
	private removeProgress():void
	{
		this.progDiv.empty();
	}

	// open up the assay group selection
	private actionAssayGroup():void
	{
		let dlg = new PickAssignmentDialog(this.schema, true);
		dlg.picked = this.assayGroup;
		dlg.callbackDone = (assnlist:SchemaAssignment[]) => this.changedAssayGroup(assnlist);
		dlg.show();
	}
	private changedAssayGroup(assnlist:SchemaAssignment[]):void
	{
		assnlist.sort();
		if (Vec.equals(assnlist, this.assayGroup)) return;

		this.assayGroup = assnlist;
		this.clusterXAssays();
		this.rebuildContent();

		if (this.assayGroup.length > 0)
		{
			let map:Record<string, string> = {};
			for (let assn of this.schema.assignments) map[keyPropGroup(assn.propURI, assn.groupNest)] = assn.name;
			let tip = '';
			for (let assn of this.assayGroup)
			{
				if (tip.length > 0) tip += '<br>';
				tip += map[keyPropGroup(assn.propURI, assn.groupNest)];
			}

			Popover.hover(domLegacy(this.btnAssayGroup), null, tip);
		}
		else this.btnAssayGroup.popover('disable');
	}

	// converts mouse event to grid-specific coordinates
	private gridFromMouse(event:JQueryMouseEventObject):[number, number]
	{
		let [x, y] = eventCoords(event, this.gridCanvas);
		x = Math.floor((x - this.insetX) / this.szw);
		y = Math.floor((y - this.insetY) / this.szh);
		return [x, y];
	}

	// converts mouse event to a dendrogram index (and x,y positions), or -1 if none
	private dendroFromMouse(event:JQueryMouseEventObject):[number, number, number]
	{
		if (this.assayTree == null) return [-1, null, null];
		let [x, y] = eventCoords(event, this.gridCanvas);
		if (x < this.insetX || y > this.insetY) return [-1, null, null];
		const RADIUS = 0.5 * this.dendroH, RADSQ = RADIUS * RADIUS;
		let closeIdx = -1, closeDSQ = Number.POSITIVE_INFINITY, closeX:number = null, closeY:number = null;
		for (let n = 0; n < this.assayTree.dendrogram.length; n++)
		{
			let dx = this.insetX + (this.assayTree.dendrogram[n].x + 0.5) * this.szw;
			let dy = (this.assayTree.dendrogram[n].y + 0.5) * this.dendroH;
			let dsq = norm_xy(x - dx, y - dy);
			if (dsq <= RADSQ && dsq < closeDSQ) [closeIdx, closeDSQ, closeX, closeY] = [n, dsq, dx, dy];
		}
		return [closeIdx, closeX, closeY];
	}

	// returns the grid index of the item in the focus box under the mouse (if it is "sticky")
	private focusFromMouse(event:JQueryMouseEventObject):[number, number]
	{
		let [x, y] = eventCoords(event, this.gridCanvas);
		let fx = -1, fy = -1;
		for (let n = 0; n < this.focusBoxXPos.length; n += 2)
		{
			let px = this.focusBoxXPos[n + 1];
			if (x >= px && x < px + this.focusBoxUnitW) {fx = this.focusBoxXPos[n]; break;}
		}
		for (let n = 0; n < this.focusBoxYPos.length; n += 2)
		{
			let py = this.focusBoxYPos[n + 1];
			if (y >= py && y < py + this.focusBoxUnitH) {fy = this.focusBoxYPos[n]; break;}
		}
		if (fx < 0 || fy < 0) return [-1, -1]; else return [fx, fy];
	}

	// mouse & keyboard events
	private mouseMove(event:JQueryMouseEventObject):void
	{
		if (this.szw == 0 || this.szh == 0 || this.focusSticky) return;

		let [didx, dx, dy] = this.dendroFromMouse(event);
		if (didx >= 0)
		{
			this.showDendroPopover(didx, dx, dy);
			if (this.focusX >= 0 || this.focusY >= 0)
			{
				this.focusX = -1;
				this.focusY = -1;
				this.redrawFocus();
				this.buildDetail();
			}
			return;
		}
		else this.hideDendroPopover();

		let [x, y] = this.gridFromMouse(event);
		if (this.dragType == null)
		{
			if (x != this.focusX || y != this.focusY)
			{
				this.focusX = x;
				this.focusY = y;
				this.redrawFocus();
				this.buildDetail();
			}
		}
		else
		{
			if (this.dragType == 'down')
			{
				if (x != this.firstX && y != this.firstY)
				{
					this.lastX = x;
					this.lastY = y;
					this.focusX = -1;
					this.focusY = -1;
					this.focusSticky = false;
					this.dragType = 'box';
					this.redrawFocus();
					this.buildDetail();
				}
			}
			else if (this.dragType = 'box')
			{
				this.lastX = x;
				this.lastY = y;
				this.redrawFocus();
				this.buildDetail();
			}
		}
	}
	private mouseOut(event:JQueryMouseEventObject):void
	{
		if (this.dragType != null)
		{
			this.dragType = null;
			this.redrawFocus();
		}
		else if (this.focusSticky && this.dragType == null) {}
		else if (this.focusX != -1 || this.focusY != -1)
		{
			this.focusX = -1;
			this.focusY = -1;
			this.redrawFocus();
			this.buildDetail();
		}
	}
	private mouseDown(event:JQueryMouseEventObject):void
	{
		this.dragType = 'down';
		let [x, y] = this.gridFromMouse(event);
		this.firstX = x;
		this.firstY = y;
	}
	private mouseUp(event:JQueryMouseEventObject):void
	{
		if (this.dragType == 'down' && this.focusX != -1 && this.focusY != -1)
		{
			if (this.focusSticky)
			{
				let [fx, fy] = this.focusFromMouse(event);
				if (fx != -1 && fy != -1)
				{
					this.focusX = fx;
					this.focusY = fy;
					this.dragType = null;
					this.redrawFocus();
					this.buildDetail();
					return;
				}
			}
			this.focusSticky = !this.focusSticky;
			let [x, y] = this.gridFromMouse(event);
			this.focusX = x;
			this.focusY = y;
		}
		else if (this.dragType == 'box')
		{
			// !! do what ??
		}

		this.dragType = null;
		this.redrawFocus();
		this.buildDetail();
	}
	private keyDown(event:JQueryKeyEventObject):void
	{
		//let keyCode = event.keyCode || event.which;
		//console.log('KEY:' + keyCode);
	}

	// showing a popover to display what's going on under a dendrogram node
	private hideDendroPopover():void
	{
		if (this.dendroPop != null) this.dendroPop.hide();
		this.dendroIndex = -1;
	}
	private showDendroPopover(didx:number, dx:number, dy:number):void
	{
		if (didx == this.dendroIndex) return; // let it be
		this.dendroIndex = didx;

		if (this.dendroPop == null) this.dendroPop = $('<div></div>');
		let pop = this.dendroPop;
		let d = this.assayTree.dendrogram[didx];
		if (!d.uri) {pop.hide(); return;}

		pop.css('position', 'absolute');
		pop.css('background-color', '#FFFFFF');
		pop.css('color', 'black');
		pop.css('border', '1px solid black');
		pop.css('border-radius', '5px');
		pop.css('box-shadow', '0 0 5px rgba(66,88,77,0.5');
		pop.css('padding', '0.3em');
		pop.css('min-width', '10em');
		pop.css('max-width', '40em');
		pop.css('min-height', '2em');
		pop.css('pointer-events', 'none');
		pop.appendTo(document.body);

		let wpos = this.gridCanvas.offset();
		let posX = wpos.left + dx + 0.5 * this.dendroH - $(document.body).offset().left;
		let posY = wpos.top + dy + 0.5 * this.dendroH - $(document.body).offset().top;
		pop.css('left', posX + 'px');
		pop.css('top', posY + 'px');

		pop.empty();
		pop.append('<i>' + escapeHTML(collapsePrefix(d.uri)) + '</i><br>');
		let b = $('<b></b>').appendTo(pop);
		let p = $('<p style="margin-bottom: 0;"></p>').appendTo(pop);

		let opt:PopoverOptions = {'schemaURI': this.schema.schemaURI, 'propURI': null, 'groupNest': null, 'valueURI': d.uri};
		(async () =>
		{
			b.text(await Popover.fetchCachedVocabLabel(opt));
			p.text(await Popover.fetchCachedVocabDescr(opt));
		})();

		pop.show();
	}

	// start creating Bayesian models for all of the columns
	private buildModels():void
	{
		this.rosterModels = [];
		for (let blk of this.axisX)
		{
			let assayIDList:number[] = [];
			for (let idx of blk.idx) assayIDList.push(this.assays[idx].assayID);
			Vec.sort(assayIDList);
			if (!this.models[assayIDList.toString()]) this.rosterModels.push(assayIDList);
		}
		this.buildNextModel();
	}

	// looks for the next requested model in the roster, and asks the server to build it
	private buildNextModel():void
	{
		if (this.rosterModels.length == 0) return;
		let assayIDList = this.rosterModels.shift();

		let params = {'assayIDList': assayIDList};
		callREST('REST/BuildBayesian', params,
			(data:any) =>
			{
				let bayes = data.model ? wmk.BayesianModel.deserialise(data.model) : null;
				let model:AssayGridModel = {'assayIDList': assayIDList, 'bayes': bayes, 'predictions': {}, 'failMsg': data.failMsg};
				this.models[assayIDList.toString()] = model;

				if (bayes && bayes.rocAUC > 0)
				{
					this.unpackModels();
					this.predictMissingActivities();
				}

				this.buildNextModel();
			}/*,
			() => alert('Model building failed.')*/);
	}

	// goes through the columns and checks to see if any of them can be hooked up with a model
	private unpackModels():void
	{
		for (let blk of this.axisX) if (!blk.model)
		{
			let assayIDList:number[] = [];
			for (let idx of blk.idx) assayIDList.push(this.assays[idx].assayID);
			Vec.sort(assayIDList);
			blk.model = this.models[assayIDList.toString()];
		}
	}

	// looks for grid cells that can and ought to have a prediction
	private predictMissingActivities():void
	{
		// accumulate the list of compounds that are missing at least one potential prediction opportunity
		let cpdset = new Set<number>();
		for (let blk of this.axisX) if (blk.model)
		{
			if (!blk.model.predictions) blk.model.predictions = Vec.anyArray(null, this.compounds.length);
			for (let n = 0; n < this.compounds.length; n++) if (blk.model.predictions[n] == null)
			{
				let cpd = this.compounds[n];
				if (cpd.fplist == null || cpd.fplist.length > 0) cpdset.add(n);
			}
		}

		this.rosterPredictions = Array.from(cpdset);
		Vec.sort(this.rosterPredictions);

		this.predictNextCompound();
	}

	// if there are any compounds rostered, predicts as much as possible
	private predictNextCompound():void
	{
		if (this.rosterPredictions.length == 0) return;

		let cpdidx = this.rosterPredictions.shift();

		let cpd = this.compounds[cpdidx];
		if (cpd.fplist == null)
		{
			try
			{
				let mol = wmk.MoleculeStream.readMDLMOL(cpd.molfileList[0]);
				cpd.fplist = wmk.CircularFingerprints.create(mol, wmk.CircularFingerprints.CLASS_ECFP6).getUniqueHashes();
			}
			catch (ex)
			{
				// silent failure (e.g. invalid molfile); no prediction will be made
				cpd.fplist = [];
			}
		}

		let anything = false;
		for (let blk of this.axisX) if (blk.model && blk.model.bayes && blk.model.bayes.rocAUC > 0 && !blk.model.predictions[cpdidx])
		{
			let pred = blk.model.bayes.scalePredictor(blk.model.bayes.predictFP(cpd.fplist));
			blk.model.predictions[cpdidx] = pred;
			anything = true;
		}

		// TODO: have a separate overlay just for the 'grid', so it can be redrawn quickly (this call redraws all the labels etc.)
		if (anything) this.redrawGrid();

		setTimeout(() => this.predictNextCompound(), 1);
	}

	// ensures that the SVG rendition of the ROC curve is available
	private drawROCCurve(model:AssayGridModel):void
	{
		if (model.svg) return;

		let vg = new wmk.MetaVector();

		const SIZE = 100;
		vg.drawRect(0, 0, SIZE, SIZE, 0x000000, 1, Theme.WEAK_RGB);
		vg.drawLine(0, SIZE, SIZE, 0, 0x000000, 1);

		let px:number[] = [], py:number[] = [];
		px.push(0);
		py.push(SIZE);
		for (let n = 0; n < model.bayes.rocX.length; n++)
		{
			px.push(model.bayes.rocX[n] * SIZE);
			py.push((1 - model.bayes.rocY[n]) * SIZE);
		}
		px.push(SIZE);
		py.push(0);
		let bx = px.slice(0), by = py.slice(0);
		bx.push(SIZE);
		by.push(SIZE);

		vg.drawPath(bx, by, null, true, -1, 0, 0x40000000 | Theme.STRONG_RGB, false);
		vg.drawPath(px, py, null, false, Theme.STRONG_RGB, 2, -1, false);

		vg.normalise();
		model.svg = vg.createSVG();
	}
}

/* EOF */ }
