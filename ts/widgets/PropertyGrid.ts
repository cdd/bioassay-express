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
	Heavyweight widget that takes a number of assays and plots the individual items along the X-axis (by id), and
	arranges the tree of annotation terms down the Y-axis in a tree-like form.
*/

let CACHE_SCHEMA:Record<string, SchemaSummary> = {}; // id is schemaURI
let CACHE_ASSIGN:Record<string, SchemaTreeNode[]> = {}; // id is 'schemaURI::propURI::groupNest'

interface PropertyGridCategory
{
	name:string;
	uri:string;
	descr:string;
	column:number;
	row:number;
	height:number;
	assignment?:SchemaAssignment; // only defined for the non-group categories, i.e. the assignments
}

interface PropertyGridText
{
	txt:string;
	name:string;
	uri:string;
	descr:string;
	column:number;
	row:number;
	tw:number;
	x:number;
	y:number;
	// recommended boundary
	x1:number;
	x2:number;
	y1:number;
	y2:number;
}

//a prediction (within the assay definition)
const enum GridItemType
{
	Nothing = 0,
	Implied = 1,
	Absence = 2,
	Explicit = 3,
	Literal = 4
}

const TEXT_ANNOTATION = 'TEXT_ANNOTATION';
const ABBREV_LITERAL = 'bat:Literal';

export class PropertyGrid
{
	public showAssignments:SchemaAssignment[] = null; // optional: if set, restricts the assignments shown
	public queryAssignments:SchemaAssignment[] = []; // assignments pertaining to the query, which should be indicated somehow
	public queryAnnotations:AssayAnnotation[] = []; // annotations pertaining to the query - indicate more strongly than above

	public clickedAssay:(assayID:number) => void = null;
	public doubleClickedAssay:(assayID:number) => void = null;

	public delegate:ExplorerDelegate;

	public showHierarchy = true;
	public selectedAssayIDList:number[] = [];

	private mainDiv:JQuery;
	private middleDiv:JQuery;
	private gridSVG:JQuery;
	private overSVG:JQuery;
	private popupDiv:JQuery;
	private popupCol:number = null;
	private popupRow:number = null;

	private schema:SchemaSummary = null; // needed for the property grid: will get updated once when necessary
	private treeNodes:SchemaTreeNode[] = []; // ... nodes for the composite tree
	private treeProps:string[] = []; // composite tree: property URI dividers
	private treeGroups:string[][] = [];
	private treeLocators:string[] = [];
	private categoryCols = 0;
	private categories:PropertyGridCategory[] = [];
	private textBlocks:PropertyGridText[][] = [];

	private watermarkDescr = 0;

	// grid layout information; cells are always accessed by [row][col]
	private gridLevel:GridItemType[][] = null;
	private gridLabel:string[][][] = null;
	private blockWidth:number;
	private rowWidth:number;
	private rowHeight:number;
	private colWidth:number;
	private colHeight:number;

	private valueToLabelMap:Record<string, string[]> = {}; // id is assayID + "||" + keyPropGroup

	constructor(private schemaURI:string, private assayList:AssayDefinition[])
	{
	}

	// uses the given domParent (jQuery object) to build the tree. domParent is the element into which the whole thing will
	// be rendered; the creator{Func/Params} pair is a closure for providing the new object that should be rendered inline,
	// to represent the node entry; it should generally be enclosed within a <span>
	public render(domParent:JQuery):void
	{
		// first part: make sure the schema is loaded
		if (this.schema == null) this.schema = CACHE_SCHEMA[this.schemaURI];
		if (this.schema == null)
		{
			let params:any = {};
			if (this.schemaURI != null) params.schemaURI = this.schemaURI;
			callREST('REST/DescribeSchema', params,
				(data:SchemaSummary) =>
				{
					if (this.schemaURI == null) this.schemaURI = data.schemaURI; // null means default schema; update it
					CACHE_SCHEMA[this.schemaURI] = data;
					this.schema = data;
					this.render(domParent);
				},
				() =>
				{
					OverlayMessage.hide();
					alert('Unable to download schema information.');
				}
			);

			return;
		}

		// second part: find out the properties used in the assays being considered, and do not rest until each of them
		// is downloaded (each of the trees can be large, and take awhile to load)
		let allowPropGroups:Set<string> = null;
		if (this.showAssignments)
		{
			allowPropGroups = new Set<string>();
			for (let assn of this.showAssignments) allowPropGroups.add(keyPropGroup(assn.propURI, assn.groupNest));
		}
		let allPropGroups = new Set<string>();
		for (let assay of this.assayList) for (let annot of assay.annotations) if (annot.valueURI || annot.valueLabel)
		{
			let groupNest = this.stripGroupNest(annot.groupNest);
			let key = this.getLocalizedKeyPropGroup(annot.propURI, groupNest);
			if (!allowPropGroups || allowPropGroups.has(key)) allPropGroups.add(key);
		}

		for (let assn of this.schema.assignments)
		{
			if (!allPropGroups.has(keyPropGroup(assn.propURI, assn.groupNest))) continue;
			let ref = keyPropGroupValue(assn.propURI, assn.groupNest, this.schemaURI);
			if (CACHE_ASSIGN[ref]) continue;

			let params = {'schemaURI': this.schemaURI, 'propURI': assn.propURI, 'groupNest': assn.groupNest};
			callREST('REST/GetPropertyTree', params,
				(data:any) =>
				{
					let treeData = unlaminateTree(data.tree);
					CACHE_ASSIGN[ref] = this.appendSpecialToTree(treeData);
					this.render(domParent);
				},
				() =>
				{
					OverlayMessage.hide();
					alert('Unable to fetch property information');
				}
			);

			return;
		}

		domParent.empty();

		this.mainDiv = $('<div/>').appendTo(domParent).css({'width': '100%'});

		this.assembleTree(allPropGroups);
		this.assembleGrid();

		this.middleDiv = $('<div/>').appendTo(this.mainDiv);
		this.middleDiv.css({'left': 0, 'top': 0, 'margin-bottom': '4em', 'position': 'relative'});

		this.gridSVG = $('<svg/>').css({'left': 0, 'top': 0, 'position': 'absolute', 'pointer-events': 'none'});

		this.renderGrid();

		// unfortunate SVG workaround hack (presumably because JQuery doesn't get the namespaces right)
		let tmp = $('<tmp/>');
		tmp.append(this.gridSVG);
		this.gridSVG = $(tmp.html());
		this.middleDiv.append(this.gridSVG);

		// create a more dynamic SVG container (horribly kludgey, but it works)
		this.overSVG = $('<svg/>');
		this.overSVG.attr('xmlns', 'http://www.w3.org/2000/svg');
		this.overSVG.css({'left': 0, 'top': 0, 'position': 'absolute', 'pointer-events': 'none'});
		let width = this.middleDiv.width(), height = this.middleDiv.height();
		this.overSVG.css({'width': width + 'px', 'height': height + 'px'});
		this.overSVG.attr({'width': width, 'height': height});
		this.overSVG.attr('viewBox', '0 0 ' + width + ' ' + height);
		tmp.empty();
		tmp.append(this.overSVG);
		this.overSVG = $(tmp.html());
		this.middleDiv.append(this.overSVG);
		tmp.remove();

		this.popupDiv = $('<div/>').appendTo(this.middleDiv).css('position', 'absolute');
		this.popupDiv.hide();
		this.popupCol = this.popupRow = null;

		this.middleDiv.mousemove((event:JQueryEventObject) => this.mouseMove(event));
		this.middleDiv.mouseout((event:JQueryEventObject) => this.mouseOut(event));
		this.middleDiv.click((event:JQueryEventObject) => this.mouseClick(event));
		this.middleDiv.dblclick((event:JQueryEventObject) => this.mouseDoubleClick(event));
		OverlayMessage.hide();
	}

	// redraws the content (e.g. when the selection has changed)
	public changeSelection(assayIDList:number[]):void
	{
		if (Vec.equals(assayIDList, this.selectedAssayIDList)) return;
		this.selectedAssayIDList = assayIDList;

		let selected = new Set<number>();
		for (let assayID of assayIDList) selected.add(assayID);

		for (let assay of this.assayList)
		{
			let isSel = selected.has(assay.assayID);

			let svgBG = $('#column_heading_bg_' + assay.assayID), svgFG = $('#column_heading_fg_' + assay.assayID);
			svgBG.attr('fill-opacity', isSel ? '1' : '0');
			svgFG.attr('fill', isSel ? 'white' : Theme.NORMAL_HTML);
		}
	}

	// ------------ private methods ------------

	// generate a prop/group key, but adapt it to the current schema first (with some small flexibility)
	private getLocalizedKeyPropGroup(propURI:string, groupNest:string[]):string
	{
		for (let assn of this.schema.assignments)
		{
			if (compatiblePropGroupNest(propURI, groupNest, assn.propURI, assn.groupNest))
				return keyPropGroup(assn.propURI, assn.groupNest);
		}
	}

	// take all of the schema assignments and build them into one big tree (with several roots), which only includes the terms that
	// are actually used
	private assembleTree(allProps:Set<string>):void
	{
		// fill out all the groups that apply
		this.categoryCols = 0;
		let groupLocators = new Set<string>();
		for (let assn of this.schema.assignments)
			if (allProps.has(keyPropGroup(assn.propURI, assn.groupNest)) && assn.locator.indexOf(':') >= 0)
		{
			let seq = assn.locator.split(':');
			seq.pop(); // last fragment is for the assignment, the rest are for group
			this.categoryCols = Math.max(this.categoryCols, seq.length);
			while (seq.length > 0)
			{
				groupLocators.add(seq.join(':') + ':');
				seq.pop();
			}
		}

		// go through all available assignments
		for (let assn of this.schema.assignments)
		{
			if (!allProps.has(keyPropGroup(assn.propURI, assn.groupNest))) continue;

			let key = keyPropGroupValue(assn.propURI, assn.groupNest, this.schemaURI);
			let branch = CACHE_ASSIGN[key];
			let availValues = new Set<string>();
			for (let node of branch) availValues.add(node.uri);

			let allValues = new Set<string>();

			for (let assay of this.assayList) for (let annot of assay.annotations)
			{
				let groupNest = this.stripGroupNest(annot.groupNest);
				if (compatiblePropGroupNest(annot.propURI, groupNest, assn.propURI, assn.groupNest))
				{
					if (annot.valueURI != null)
					{
						if (availValues.has(annot.valueURI)) allValues.add(annot.valueURI);
					}
					else
					{
						// check for text and add constant in special case; store value valueToLabelMap
						allValues.add(TEXT_ANNOTATION);
						let valueKey = assay.assayID + '||' + keyPropGroup(assn.propURI, assn.groupNest);
						if (this.valueToLabelMap[valueKey]) this.valueToLabelMap[valueKey].push(annot.valueLabel);
						else this.valueToLabelMap[valueKey] = [annot.valueLabel];
					}
				}
			}

			let mask:boolean[] = [];
			for (let n = 0; n < branch.length; n++) if (allValues.has(branch[n].uri))
				for (let p = n; p >= 0; p = branch[p].parent) mask[p] = true;

			let backidx:number[] = [];
			for (let n = 0, p = this.treeProps.length; n < branch.length; n++) if (mask[n]) backidx[n] = p++;

			let blktop = this.treeNodes.length, blksz = 0;
			for (let n = 0; n < branch.length; n++) if (mask[n])
			{
				let node = clone(branch[n]);
				if (node.parent >= 0) node.parent = backidx[node.parent];
				this.treeProps.push(assn.propURI);
				this.treeGroups.push(assn.groupNest);
				this.treeNodes.push(node);
				this.treeLocators.push(assn.locator);
				blksz++;
			}

			// special case where it has a Text Annotation -- stick it the end of the list for now
			if (allValues.has(TEXT_ANNOTATION))
			{
				mask[mask.length + 1] = true;
				backidx[mask.length + 1] = backidx[mask.length] + 1; // just increment it
				let node = {} as SchemaTreeNode;
				node.abbrev = ABBREV_LITERAL;
				node.depth = 0;
				node.uri = expandPrefix(node.abbrev);

				if (assn.suggestions == SuggestionType.Full || assn.suggestions == SuggestionType.Disabled)
					node.name = 'free text';
				else
					node.name = assn.name;

				this.treeProps.push(assn.propURI);
				this.treeGroups.push(assn.groupNest);
				this.treeNodes.push(node);
				this.treeLocators.push(assn.locator);
				blksz++;
			}

			if (blksz == 0) continue; // it can happen

			// add the category block for the assignment
			this.categories.push(
			{
				'name': assn.name,
				'uri': assn.propURI,
				'descr': assn.descr,
				'column': this.categoryCols,
				'row': blktop,
				'height': blksz,
				'assignment': assn
			});
		}
		this.categoryCols++;

		// go back to the groups, if any, and fill in category blocks for them
		for (let grp of this.schema.groups) if (groupLocators.has(grp.locator))
		{
			let depth = 0;
			for (let n = 0; n < grp.locator.length; n++) if (grp.locator.charAt(n) == ':') depth++;

			let found = false, y1 = Number.MAX_VALUE, y2 = Number.MIN_VALUE;
			for (let n = 0; n < this.treeLocators.length; n++) if (this.treeLocators[n].startsWith(grp.locator))
			{
				y1 = Math.min(y1, n);
				y2 = Math.max(y2, n);
				found = true;
			}
			if (!found) continue;

			this.categories.push(
			{
				'name': grp.name,
				'uri': grp.groupURI,
				'descr': grp.descr,
				'column': depth - 1,
				'row': y1,
				'height': y2 - y1 + 1
			});
		}
	}

	// define the grid and its contents
	private assembleGrid():void
	{
		let ncols = this.assayList.length, nrows = this.treeNodes.length;
		this.gridLevel = [];
		this.gridLabel = [];
		for (let i = 0; i < nrows; i++)
		{
			this.gridLevel[i] = [];
			this.gridLabel[i] = [];
			for (let j = 0; j < ncols; j++) this.gridLevel[i][j] = GridItemType.Nothing;
			for (let j = 0; j < ncols; j++) this.gridLabel[i][j] = [''];
		}
		let termIndices:Record<string, number[]> = {}; // propURI -> possible rows (filtered again later)
		for (let n = 0; n < nrows; n++)
		{
			let list = termIndices[this.treeProps[n]];
			if (list == null) termIndices[this.treeProps[n]] = [n]; else list.push(n);
		}

		for (let n = 0; n < ncols; n++)
		{
			let assay = this.assayList[n];
			for (let annot of assay.annotations)
			{
				let groupNest = this.stripGroupNest(annot.groupNest);
				let indices = termIndices[annot.propURI];
				if (indices == null) continue;
				let p = -1;
				for (let idx of indices) if (compatibleGroupNest(groupNest, this.treeGroups[idx]))
				{
					if (this.treeNodes[idx].abbrev == ABBREV_LITERAL)
					{
						p = idx;
						this.gridLevel[p][n] = GridItemType.Literal;
						let valueKey = assay.assayID + '||' + keyPropGroup(annot.propURI, this.treeGroups[idx]);
						if ((this.valueToLabelMap[valueKey] != null) && (this.valueToLabelMap[valueKey].length > 0))
						{
							this.gridLabel[p][n] = this.valueToLabelMap[valueKey];
						}
						break;
					}
					else if (this.treeNodes[idx].uri == annot.valueURI)
					{
						p = idx;
						let level = ALL_ABSENCE_TERMS.includes(this.treeNodes[idx].uri) ? GridItemType.Absence : GridItemType.Explicit;
						this.gridLevel[p][n] = Math.max(this.gridLevel[p][n], level);
						break;
					}
				}
				if (p < 0) continue;

				for (p = this.treeNodes[p].parent; p >= 0; p = this.treeNodes[p].parent)
					this.gridLevel[p][n] = Math.max(this.gridLevel[p][n], 1);
			}
		}
	}

	// draw everything into the canvas
	private renderGrid():void
	{
		// note: the canvas is used only to do text sizing
		let canvas = $('<canvas/>');
		let ctx = (canvas[0] as HTMLCanvasElement).getContext('2d');
		let svg = this.gridSVG;

		// figure out dimensions

		let ncols = this.assayList.length, nrows = this.treeNodes.length;

		let fontSize = 10, fontFamily = 'sans-serif', font = `${fontSize}px ${fontFamily}`;

		let assayLabels:string[] = [];
		this.colHeight = 0;
		for (let assay of this.assayList)
		{
			let [src, id] = UniqueIdentifier.parseKey(assay.uniqueID);
			let label = src ? src.shortName + ' ' + id : assay.assayID.toString();
			assayLabels.push(label);
			this.colHeight = Math.max(this.colHeight, ctx.measureText(label).width + 4);
		}

		const BRACKET_WIDTH = 20, HIER_INDENT = 20;
		this.blockWidth = BRACKET_WIDTH * this.categoryCols;
		this.rowWidth = this.blockWidth;
		ctx.font = font;
		for (let n = 0; n < nrows; n++)
		{
			let txt = this.treeNodes[n].name;
			if (txt.length > 30) txt = txt.substring(0, 30); // for sizing purposes: truncation happens later
			let tw = ctx.measureText(txt).width;
			this.rowWidth = Math.max(this.rowWidth, this.blockWidth + HIER_INDENT * this.treeNodes[n].depth + tw + 4);
		}

		this.rowHeight = 15;
		this.colWidth = 15;

		let width = this.rowWidth + ncols * this.colWidth, height = this.colHeight + nrows * this.rowHeight;

		// size and draw

		this.middleDiv.css('width', Math.max(800, width) + 'px');
		this.middleDiv.css('height', Math.max(500, height) + 'px');

		svg.attr('xmlns', 'http://www.w3.org/2000/svg');
		svg.css({'width': width + 'px', 'height': height + 'px'});
		svg.attr({'width': width, 'height': height});
		svg.attr('viewBox', '0 0 ' + width + ' ' + height);

		let density = pixelDensity();

		// convenience functions for writing to the SVG part of the grid
		let svgRect = (x:number, y:number, w:number, h:number, fill:string):JQuery =>
			$(`<rect x="${x}" y="${y}" width="${w}" height="${h}" fill="${fill}"/>`).appendTo(this.gridSVG);
		let svgDiamond = (x:number, y:number, w:number, h:number, fill:string):JQuery =>
		{
			let xx = x + w, yy = y + h, xm = x + 0.5 * w, ym = y + 0.5 * h;
			return $(`<path d="M ${x},${ym} L ${xm},${y} L ${xx},${ym} L ${xm} ${yy} Z" fill="${fill}"/>`).appendTo(this.gridSVG);
		};
		let svgCircle = (x:number, y:number, r:number, stroke:string, fill:string):JQuery =>
			$(`<circle cx="${x}" cy="${y}" r="${r}" stroke="${stroke}" fill="${fill}"/>`).appendTo(this.gridSVG);
		let svgLine = (x1:number, y1:number, x2:number, y2:number, sz:number, col:string, extra:string = ''):JQuery =>
			$(`<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${col}" stroke-width="${sz}"${extra}/>`).appendTo(this.gridSVG);
		let svgPathStroke = (path:string, sz:number, col:string):JQuery =>
			$(`<path d="${path}" stroke="${col}" stroke-width="${sz}" fill="transparent"/>`).appendTo(this.gridSVG);
		let svgText = (txt:string, x:number, y:number, col:string):JQuery =>
		{
			let obj = $(`<text x="${x}" y="${y}" fill="${col}" font-family="${fontFamily}" font-size="${fontSize}"/>`).appendTo(this.gridSVG);
			obj.text(txt);
			return obj;
		};
		// this is all of the vertical text
		let svgVertical = (txt:string, cx:number, cy:number, col:string):JQuery =>
		{
			let tw = ctx.measureText(txt).width;
			let obj = svgText(txt, cx - 0.5 * tw, cy + 0.35 * fontSize, col);
			obj.attr('transform', `rotate(-90 ${cx},${cy})`);
			return obj;
		};

		// underlay: the background colors for the grid and the group by assignments
		svgRect(this.rowWidth, this.colHeight, ncols * this.colWidth, nrows * this.rowHeight, '#FBFBFF');

		for (let assn of this.queryAssignments)
		{
			for (let cat of this.categories) if (cat.assignment)
			{
				if (!samePropGroupNest(cat.assignment.propURI, cat.assignment.groupNest, assn.propURI, assn.groupNest)) continue;
				let y = this.colHeight + cat.row * this.rowHeight, h = cat.height * this.rowHeight;
				svgRect(this.blockWidth, y, width - this.blockWidth, h, '#F8F8FF');
			}
		}
		let annotHash = new Set<string>();
		for (let annot of this.queryAnnotations) annotHash.add(keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI));
		for (let n = 0; n < this.treeNodes.length; n++)
		{
			let key = keyPropGroupValue(this.treeProps[n], this.treeGroups[n], this.treeNodes[n].uri);
			if (!annotHash.has(key)) continue;
			let y = this.colHeight + n * this.rowHeight;
			svgRect(this.blockWidth, y, width - this.blockWidth, this.rowHeight, '#E0F0FF');
		}

		// cell intensities (intersection of assignment and assay)
		for (let i = 0; i < nrows; i++) for (let j = 0; j < ncols; j++)
		{
			let level = this.gridLevel[i][j];
			if (level == 0) continue;

			let x = this.rowWidth + j * this.colWidth, y = this.colHeight + i * this.rowHeight;
			if (level == GridItemType.Explicit)
				svgRect(x, y, this.colWidth, this.rowHeight, Theme.STRONG_HTML);
			else if (level == GridItemType.Absence)
				svgDiamond(x, y, this.colWidth, this.rowHeight, Theme.STRONG_HTML);
			else if (level == GridItemType.Literal)
				svgCircle(x + this.colWidth / 2, y + this.colWidth / 2, this.colWidth / 3, Theme.STRONG_HTML, '#E0EFFF');
			else if (level == GridItemType.Implied)
			{
				if (this.showHierarchy) svgRect(x + this.colWidth * 0.3, y, this.colWidth * 0.4, this.rowHeight, Theme.STRONG_HTML);
			}
		}

		// baseline grid: vertical / horizontal lines
		for (let n = 0; n <= ncols; n++)
		{
			let x = this.rowWidth + n * this.colWidth - 0.5;
			svgLine(x, this.colHeight, x, height, 1, '#CCD9E8'); // vertical
		}
		for (let n = 0; n <= nrows; n++)
		{
			let y = this.colHeight + n * this.rowHeight - 0.5;
			let isDiv = n > 0 && this.treeProps[n] != this.treeProps[n - 1];
			svgLine(this.rowWidth - (isDiv ? 4 : 0), y, width, y, 1, isDiv ? '#000000' : '#CCD9E8'); // horizontal
		}

		// column labels (identifiers for the assays)
		let selected = new Set<number>();
		for (let assayID of this.selectedAssayIDList) selected.add(assayID);
		for (let n = 0; n < assayLabels.length; n++)
		{
			let assayID = this.assayList[n].assayID, isSel = selected.has(assayID);
			let tw = ctx.measureText(assayLabels[n]).width;
			let x = this.rowWidth + (n + 0.5) * this.colWidth, y = this.colHeight - 4 - 0.5 * tw;
			let svgBG = svgRect(this.rowWidth + n * this.colWidth, 0, this.colWidth, this.colHeight, Theme.STRONG_HTML);
			svgBG.attr('id', 'column_heading_bg_' + assayID);
			svgBG.attr('fill-opacity', isSel ? '1' : '0');
			let svgFG = svgVertical(assayLabels[n], x, y + 1, isSel ? 'white' : Theme.NORMAL_HTML);
			svgFG.attr('id', 'column_heading_fg_' + assayID);
		}

		// hierarchy of tree terms: the text/dotted lines in the clustered nodes of the dendogram like structure
		for (let n = 0; n < nrows; n++)
		{
			let x = this.blockWidth + HIER_INDENT * this.treeNodes[n].depth, y = this.colHeight + n * this.rowHeight + 0.5 * this.rowHeight;
			let txt = this.treeNodes[n].name, tw = ctx.measureText(txt).width;
			let chopped = false;
			while (txt.length > 0 && x + tw > this.rowWidth - 5)
			{
				chopped = true;
				txt = txt.substring(0, txt.length - 1);
				tw = ctx.measureText(txt).width;
			}
			if (chopped) txt += '..';
			svgText(txt, x, y + 0.3 * fontSize, '#313A44'); // text label for the cluster
			svgLine(x + tw + 2, y, this.rowWidth, y, 1, '#CCD9E8', ' stroke-dasharray="1, 3"'); // dotted line extending from each cluster
		}

		// branch structure to indicate hierarchies: the lines in the dendogram like structure
		for (let n = 0; n < nrows; n++)
		{
			let depth = this.treeNodes[n].depth, m = 0;
			for (let i = n + 1; i < nrows && this.treeNodes[i].depth > depth; i++) if (this.treeNodes[i].depth == depth + 1) m = i;

			if (m > n)
			{
				let x = this.blockWidth + (depth + 0.5) * HIER_INDENT;
				let y1 = this.colHeight + (n + 1) * this.rowHeight, y2 = this.colHeight + (m + 0.5) * this.rowHeight;
				svgLine(x, y1, x, y2, 1, '#313A44'); // vertical
			}

			if (depth > 0)
			{
				let x1 = this.blockWidth + (depth - 0.5) * HIER_INDENT, x2 = this.blockWidth + depth * HIER_INDENT - 2;
				let y = this.colHeight + (n + 0.5) * this.rowHeight;
				svgLine(x1, y, x2, y, 1, '#313A44'); // horizontal
			}
		}

		// draw the blocks that denote property boundaries (rendered as brackets around assignment groups)
		this.textBlocks = [];
		for (let n = 0; n < this.categoryCols; n++) this.textBlocks.push([]);

		for (let cat of this.categories)
		{
			let y1 = this.colHeight + cat.row * this.rowHeight + 1.5;
			let y2 = this.colHeight + (cat.row + cat.height) * this.rowHeight - 2.5;
			let x = (cat.column + 1) * BRACKET_WIDTH - 0.5;
			let w = (this.categoryCols - 1 - cat.column) * BRACKET_WIDTH;
			let path = `M ${x + w} ${y1} L ${x} ${y1} Q ${x - 4}  ${y1} ${x - 4} ${y1 + 4} L ${x - 4} ${y2 - 4} ` +
					   `Q ${x - 4} ${y2} ${x} ${y2} L ${x + w} ${y2}`;
			svgPathStroke(path, 1, '#1362B3');

			this.textBlocks[cat.column].push(
			{
				'txt': cat.name,
				'name': cat.name,
				'uri': cat.uri,
				'descr': cat.descr,
				'column': cat.column,
				'row': cat.row,
				'tw': ctx.measureText(cat.name).width,
				'x': (cat.column + 0.5) * BRACKET_WIDTH - 2,
				'x1': (cat.column + 0) * BRACKET_WIDTH,
				'x2': (cat.column + 1) * BRACKET_WIDTH,
				'y': 0.5 * (y1 + y2),
				'y1': y1,
				'y2': y2,
			});
		}

		// rearrange then draw each block
		for (let tcol of this.textBlocks)
		{
			this.finagleTextColumn(tcol, 0.5 * this.colHeight, height);
			for (let tblk of tcol)
			{
				if (tblk.txt != '')
				{
					svgRect(tblk.x - 0.5 * fontSize, tblk.y - 0.5 * tblk.tw, fontSize, tblk.tw, 'white');
					svgVertical(tblk.txt, tblk.x, tblk.y, '#1362B3');
				}
				else svgRect(tblk.x - 2, tblk.y - 2, 4, 4, '#1362B3');
			}
		}
	}

	// takes a column of text blocks (to be rendered vertically) and makes whatever modifications are necessary to
	// make sure they don't overlap: this may involve truncation and/or bumping up or down to fit within the desired
	// boundaries
	private finagleTextColumn(blk:PropertyGridText[], minY:number, maxY:number):void
	{
		blk.sort((b1, b2) => b1.y - b2.y);
		let sz = blk.length, mask = Vec.booleanArray(true, sz);

		while (true)
		{
			// if nothing could be so corrected, look for the biggest overspill, and truncate it
			let worst = -1, worstOver = 0;
			for (let n = 0; n < sz; n++) if (mask[n])
			{
				let nbr1 = n > 0 ? blk[n - 1] : null, nbr2 = n < sz - 1 ? blk[n + 1] : null;
				let y1 = nbr1 ? nbr1.y + 0.5 * nbr1.tw : minY, y2 = nbr2 ? nbr2.y - 0.5 * nbr2.tw : maxY;

				//let over = blk[n].tw - (y2 - y1);
				let lo = (blk[n].y - 0.5 * blk[n].tw), hi = (blk[n].y + 0.5 * blk[n].tw);
				let over = Math.max(y1 - lo, hi - y2);
				if (over > worstOver) {worst = n; worstOver = over;}
			}
			if (worst < 0) break;
			blk[worst].txt = '';
			blk[worst].tw = 4;
			mask[worst] = false;
		}
	}

	// mouse & keyboard events
	private mouseMove(event:JQueryEventObject):void
	{
		let [x, y] = eventCoords(event, this.middleDiv);
		let [title, content, puri, pid, column, row] = this.popupDetails(x, y);

		// direct grid interaction
		this.overSVG.empty();
		if (column >= 0 || row >= 0)
		{
			let path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
			path.setAttribute('d', this.pathHighlight(column, row));
			path.setAttribute('fill', '#C0C0C0');
			path.setAttribute('fill-opacity', '0.2');
			path.setAttribute('stroke', 'black');
			path.setAttribute('stroke-width', '1');
			this.overSVG[0].appendChild(path);
		}

		// popover

		if (title == null)
		{
			this.popupDiv.hide();
			this.popupCol = this.popupRow = null;
			return;
		}
		if (column == this.popupCol && row == this.popupRow) return;
		this.popupCol = column;
		this.popupRow = row;

		let px = column >= 0 ? this.rowWidth + (column + 1) * this.colWidth : this.rowWidth;
		let py = row >= 0 ? this.colHeight + (row + 1) * this.rowHeight : this.colHeight;
		const OFFSET = 5;
		//this.popupDiv.css('left', (px + OFFSET) + 'px');
		//this.popupDiv.css('top', (py + OFFSET) + 'px');
		this.popupDiv.css('left', '0');
		this.popupDiv.css('top', '0');
		this.popupDiv.css('background-color', '#F8F8F8');
		this.popupDiv.css('border-radius', '5px');
		this.popupDiv.css('border', '1px solid black');
		this.popupDiv.css('padding', '0');
		this.popupDiv.css('max-width', '400px');

		this.popupDiv.empty();

		let paraTitle = $('<p width="100%"/>').appendTo(this.popupDiv);
		paraTitle.css('margin', '0');
		paraTitle.css('padding', '0.25em 1em 0.25em 1em');
		paraTitle.css('background-color', '#D0D0D0');
		paraTitle.css('border-bottom', '1px solid black');
		paraTitle.css('text-align', 'center');
		paraTitle.css('border-top-left-radius', '5px');
		paraTitle.css('border-top-right-radius', '5px');

		let paraContent = $('<p></p>').appendTo(this.popupDiv);
		paraContent.css('margin', 0);
		paraContent.css('padding', '0.5em');

		paraTitle.html(title);
		paraContent.html(content);

		if (puri != null && pid != null)
		{
			let propURI:string = null, groupNest:string[] = null;
			if (row >= 0)
			{
				propURI = this.treeProps[row];
				groupNest = this.treeGroups[row];
			}

			let opt:PopoverOptions = {'schemaURI': this.schemaURI, 'propURI': propURI, 'groupNest': groupNest, 'valueURI': puri};
			(async () =>
			{
				$('#' + pid).text(await Popover.fetchCachedVocabDescr(opt));
			})();
		}

		this.popupDiv.css('visibility', 'hidden');
		this.popupDiv.show();

		setTimeout(() =>
		{
			let width = Math.min(this.middleDiv.width(), $(window).width()), popW = this.popupDiv.width();
			let height = Math.min(this.middleDiv.height(), $(window).height()), popH = this.popupDiv.height();
			let curPX = Math.min(px, px - this.middleDiv.scrollLeft());
			let curPY = Math.min(py, py - this.middleDiv.scrollTop());

			if (curPX + OFFSET + popW > width && popW < curPX - this.colWidth - OFFSET)
				curPX = curPX - this.colWidth - OFFSET - 2 - popW; else curPX += OFFSET;
			if (curPY + OFFSET + popH > height && popH < curPX - this.rowHeight - OFFSET)
				curPY = curPY - this.rowHeight - OFFSET - 2 - popH; else curPY += OFFSET;

			this.popupDiv.css('left', curPX + 'px');
			this.popupDiv.css('top', curPY + 'px');
			this.popupDiv.css('visibility', 'visible');

		}, 1);
	}
	private mouseOut(event:JQueryEventObject):void
	{
		this.overSVG.empty();
		this.popupDiv.hide();
		this.popupCol = this.popupRow = null;
	}

	private mouseClick(event:JQueryEventObject):void
	{
		let [x, y] = eventCoords(event, this.middleDiv);
		let [row, col] = this.getRowColForPosition(x, y);
		let ncols = this.assayList.length, nrows = this.treeNodes.length;

		if (col >= 0 && col < ncols)
		{
			if (this.clickedAssay) this.clickedAssay(this.assayList[col].assayID);

			// note: maybe distinguish between clicking on column heading area vs. an actual cell

		}
		else if (row >= 0 && row < nrows)
		{
			this.copyAssignment(row);
		}
	}

	private mouseDoubleClick(event:JQueryEventObject):void
	{
		let [x, y] = eventCoords(event, this.middleDiv);
		let [row, col] = this.getRowColForPosition(x, y);

		if (col < 1)
		{
			let propURI = this.treeProps[row], groupNest = this.treeGroups[row];
			let assn:SchemaAssignment = null;
			for (let look of this.schema.assignments)
				if (samePropGroupNest(look.propURI, look.groupNest, propURI, groupNest)) {assn = look; break;}

			if (this.delegate != null)
			{
				this.delegate.actionDoubleClickedAssignment(assn);
			}
		}
	}

	private getRowColForPosition(x:number, y:number):[number, number]
{
		let row = Math.floor((y - this.colHeight) / this.rowHeight);
		let col = Math.floor((x - this.rowWidth) / this.colWidth);

		return [row, col];
	}

	// render paths to show the mouseover position
	private pathHighlight(column:number, row:number):string
	{
		let ncols = this.assayList.length, nrows = this.treeNodes.length;
		let width = this.rowWidth + ncols * this.colWidth, height = this.colHeight + nrows * this.rowHeight;

		const EDGE = 5; // enough to move the spline off the edge of the page
		let x0 = -EDGE, y0 = -EDGE, x3 = width + EDGE, y3 = height + EDGE;
		let x1 = this.rowWidth + column * this.colWidth - 1, x2 = x1 + this.colWidth + 1;
		let y1 = this.colHeight + row * this.rowHeight - 1, y2 = y1 + this.rowHeight + 1;
		const D = 6; // curvature

		let path = '';
		if (column >= 0 && row >= 0)
		{
			// draw "crosshairs" that converge on a single cell
			path += `M${x0},${y1} `;
			path += `L${x1 - D},${y1} `;
			path += `Q${x1},${y1} ${x1},${y1 - D} `;
			path += `L${x1},${y0} `;

			path += `L${x2},${y0} `;
			if (column < ncols - 1)
			{
				path += `L${x2},${y1 - D} `;
				path += `Q${x2},${y1} ${x2 + D},${y1} `;
			}
			else path += `L${x2},${y1}`;
			path += `L${x3},${y1} `;

			path += `L${x3},${y2} `;
			if (column < ncols - 1 && row < nrows - 1)
			{
				path += `L${x2 + D},${y2} `;
				path += `Q${x2},${y2} ${x2},${y2 + D} `;
			}
			else path += `L${x2},${y2} `;
			path += `L${x2},${y3} `;

			path += `L${x1},${y3} `;
			if (row < nrows - 1)
			{
				path += `L${x1},${y2 + D} `;
				path += `Q${x1},${y2} ${x1 - D}, ${y2} `;
			}
			else path += `L${x1},${y2} `;
			path += `L${x0},${y2} `;

			path += 'Z ';

			path += `M${x1},${y1} L${x1},${y2} L${x2},${y2} L${x2},${y1} Z`;
		}
		else if (column >= 0)
		{
			// draw a vertical bar, with an emphasis on the column header
			path += `M${x1},${y0} L${x2},${y0} L${x2},${y3} L${x1},${y3} Z `;
			let yy = this.colHeight;
			path += `M${x1},${y0} L${x1},${yy} L${x2},${yy} L${x2},${y0} Z`;
		}
		else if (row >= 0)
		{
			// draw a horizontal bar, with an emphasis on the property label
			let xa = this.blockWidth, xb = this.rowWidth;
			path += `M${x0},${y1} L${x3},${y1} L${x3},${y2} L${x0},${y2} Z `;
			path += `M${xa},${y1} L${xa},${y2} L${xb},${y2} L${xb},${y1} Z`;
		}

		return path;
	}

	// returns appropriate title/content information for a position onscreen; return format is:
	//		[title, content, puri, pid, column, row]
	private popupDetails(x:number, y:number):[string, string, string, string, number, number]
	{
		let row = Math.floor((y - this.colHeight) / this.rowHeight);
		let col = Math.floor((x - this.rowWidth) / this.colWidth);
		let ncols = this.assayList.length, nrows = this.treeNodes.length;
		if (col < 0 || col >= ncols) col = -1;
		if (row < 0 || row >= nrows) row = -1;

		if (x < this.blockWidth)
		{
			for (let list of this.textBlocks) for (let tblk of list)
				if (x >= tblk.x1 && x < tblk.x2 && y >= tblk.y1 && y < tblk.y2)
			{
				let title = escapeHTML(tblk.name);
				let content = '<b>URI</b>: ' + collapsePrefix(tblk.uri) + '<p>' + escapeHTML(tblk.descr) + '</p>';
				return [title, content, null, null, -2 - tblk.column, row];
			}
			return [null, null, null, null, -1, -1];
		}

		let title:string = null, content:string = null, puri:string = null, pid:string = null;

		let propAssn:SchemaAssignment = null;
		if (row >= 0)
		{
			let propURI = this.treeProps[row], groupNest = this.treeGroups[row];
			for (let assn of this.schema.assignments)
				if (samePropGroupNest(assn.propURI, assn.groupNest, propURI, groupNest)) {propAssn = assn; break;}
		}

		/* (doesn't happen) if (x < this.blockWidth && y >= this.colHeight && propAssn)
		{
			title = escapeHTML(propAssn.name);
			content = '<b>URI</b>: ' + escapeHTML(collapsePrefix(propAssn.propURI)) + '<p>' + escapeHTML(propAssn.descr) + '</p>';
		}
		else*/ if (x < this.rowWidth && y >= this.colHeight && row >= 0)
		{
			let propURI = this.treeProps[row], groupNest = this.treeGroups[row];
			if (row < this.treeNodes.length)
			{
				let node = this.treeNodes[row];
				title = escapeHTML(node.name);
				content = '<b>URI</b>: ' + escapeHTML(node.abbrev) + '<p>';

				let opt:PopoverOptions = {'schemaURI': this.schemaURI, 'propURI': propURI, 'groupNest': groupNest, 'valueURI': node.uri};
				let descr = Popover.getCachedVocabDescr(opt);
				if (descr) content += '<p>' + escapeHTML(descr) + '</p>';
				else
				{
					puri = node.uri;
					pid = 'descr' + (++this.watermarkDescr);
					content += '<p id="' + pid + '"></p>';
				}
			}
		}
		//else if (x >= this.rowWidth && y < this.colHeight)
		if (col >= 0 && row < 0)
		{
			let assay = this.assayList[col];

			title = 'Assay';
			content = '<b>Assay ID</b>: ' + assay.assayID;
			let [src, id] = UniqueIdentifier.parseKey(assay.uniqueID);
			if (src) content += '<br><b>' + src.name + '</b>: ' + id;
			let txt = orBlank(assay.text);
			if (txt.length > 500) txt = txt.substring(0, 500) + '...';
			content += '<p>' + escapeHTML(txt) + '</p>';
		}
		else if (col >= 0 && row >= 0 && this.gridLevel[row][col] > 0)
		{
			let node = this.treeNodes[row];
			let assay = this.assayList[col];
			title = escapeHTML(node.name);
			content = '<b>Assay ID</b>: ' + assay.assayID;
			let [src, id] = UniqueIdentifier.parseKey(assay.uniqueID);
			if (src) content += '<br><b>' + src.name + '</b>: ' + id;
			content += '<p>';

			if (this.gridLevel[row][col] == GridItemType.Explicit) content += 'Term is used explicitly.';
			else if (this.gridLevel[row][col] == GridItemType.Absence) content += 'Term indicates an absence.';
			else if (this.gridLevel[row][col] == GridItemType.Literal) for (let label of this.gridLabel[row][col]) content += ' - ' + escapeHTML(label) + '<br/>';
			else content += 'This term is implied by a descendent.';
			content += '</p>';
		}

		return [title, content, puri, pid, col, row];
	}

	// copies information about the assignment onto the clipboard
	private copyAssignment(row:number):void
	{
		let lines:string[] = [];

		let propURI = this.treeProps[row], groupNest = this.treeGroups[row];
		let assn:SchemaAssignment = null;
		for (let look of this.schema.assignments)
			if (samePropGroupNest(look.propURI, look.groupNest, propURI, groupNest)) {assn = look; break;}

		lines.push(assn.name + ' - ' + collapsePrefix(propURI));
		for (let n = 0; n < Vec.arrayLength(assn.groupNest); n++)
		{
			let str = '  '.repeat(n + 1);
			str += assn.groupLabel[n] + ' - ' + collapsePrefix(assn.groupNest[n]);
			lines.push(str);
		}

		lines.push('');

		let node = this.treeNodes[row];
		lines.push(node.name + ' - ' + collapsePrefix(node.uri));
		let indent = 1;
		while (node.parent >= 0)
		{
			node = this.treeNodes[node.parent];
			lines.push('  '.repeat(indent) + node.name + ' - ' + collapsePrefix(node.uri));
			indent++;
		}

		lines.push('');

		copyToClipboard(lines.join('\n'));
	}

	// augment the schema tree by adding absence values to the end; they will only be shown when used
	private appendSpecialToTree(treeData:SchemaTreeNode[]):SchemaTreeNode[]
	{
		treeData = treeData.slice(0);

		let pidx = treeData.length;
		let parentURI = expandPrefix('bat:Absence');
		treeData.push({'depth': 0, 'parent': -1, 'uri': parentURI, 'name': 'absence'} as SchemaTreeNode);
		for (let uri of ALL_ABSENCE_TERMS)
		{
			let name = ABSENCE_DETAIL_MAP[uri][0];
			treeData.push({'depth': 1, 'parent': pidx, 'uri': uri, 'name': name} as SchemaTreeNode);
		}

		return treeData;
	}

	// removes all duplication references from a groupNest locator, which effectively collapses duplicates into their raw branches
	private stripGroupNest(groupNest:string[]):string[]
	{
		if (groupNest == null || groupNest.length == 0) return groupNest;
		let dup = false;
		for (let n = 0; n < groupNest.length; n++)
		{
			let [uri, idx] = TemplateManager.decomposeSuffixGroupURI(groupNest[n]);
			if (idx == 0) continue;
			if (!dup) {groupNest = groupNest.slice(0); dup = true;}
			groupNest[n] = uri;
		}
		return groupNest;
	}
}

/* EOF */ }
