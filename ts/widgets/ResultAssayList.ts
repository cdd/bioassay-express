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

const enum ResultAssayColumn
{
	Similarity,
	Compounds,
	Summary,
	AssayID
}

class ResultAssaySort
{
	// column: names the column that is sorted
	// dir: +1 if column sorted in ascending order; column -1 if sorted in descending order
	constructor(public column:ResultAssayColumn, public dir:number) {}
}

class ResultAssayListAssayInfo
{
	// similarity: rank of assay against input annotation terms; if non-negative, we render a column for similarity
	constructor(public assayID:number, public similarity = -1) {}
}

/*
	Displays a list of assays that have been obtained from some kind of query (browse/search). Handles the loading and storing
	of the assay details, basic manipulation, and launch points to functionality that uses a subset list as its predicate.
*/

export class ResultAssayList
{
	public assayList:ResultAssayListAssayInfo[] = null; // assay IDs and similarity for each match; null means waiting for results

	public changedAssaySelection:() => void = null;
	public changedAssayVisibility:() => void = null;

	private assayGroup:SchemaAssignment[] = []; // assignments to partion by

	// define these as necessary to enable extra buttons
	public renderQuery:(div:JQuery) => string[] = null;
	public showCompounds:() => void = null;
	public showAnalysis:(pageFN:string) => void = null;

	// defined to hook into when all of the provided assays have been fetched
	private finishedAssays:() => void = null;

	private mainDiv:JQuery;

	private template:SchemaSummary = null;
	private showQuery = false; // whether to also list out the query strings
	private divQuery:JQuery = null; // the place to show/hide as necessary

	private thResultColumn:JQuery = null;

	// DOM objects for each assay ID
	private domCheck:Record<number, JQuery> = {};
	private domCount:Record<number, JQuery> = {};
	private domText:Record<number, JQuery> = {};
	private domUnique:Record<number, JQuery> = {};

	// cached information about assays (key=assayID): the full assay definition, and clipped text for display
	private assayCache:Record<string, AssayDefinition> = {};
	private assayText:Record<string, string> = {};
	private assayTranslit:Record<string, string> = {};
	private rosterAssays:number[] = [];

	private previewChoice = -1;
	private previewTitles:string[] = [];

	constructor()
	{
	}

	// build the containers for display of results
	public render(domParent:JQuery):void
	{
		domParent.empty();

		this.mainDiv = $('<div/>').appendTo(domParent);
		this.mainDiv.css('width', '100%');
	}

	// record the currently selected template, which is used internally for some functionality
	public setCurrentTemplate(template:SchemaSummary):void
	{
		this.template = template;
	}

	// when the list of assays changes: updates the display, and fetches anything that hasn't been investigated yet
	public replaceAssays(assayIDList:number[], finished:() => void = null):void
	{
		this.finishedAssays = finished;

		this.assayList = assayIDList.map((assayID) => new ResultAssayListAssayInfo(assayID));
		this.redrawContent();

		this.rosterAssays = [];
		this.previewTitles = [];
		for (let assayID of assayIDList)
		{
			let assay = this.assayCache[assayID];
			if (assay)
			{
				if (assay.translitPreviews) for (let preview of assay.translitPreviews)
				if (preview.title)
				{
					if (this.previewTitles.indexOf(preview.title) < 0) this.previewTitles.push(preview.title);
				}
				else this.assayTranslit[assayID] = preview.html;
			}
			else this.rosterAssays.push(assayID);
		}
		this.fetchNextAssay();
	}

	// as above, except uses the search result interface, which prepopulates the necessary assay information, obviating the
	// need for an explicit lookup
	public replaceSearchResults(searchResults:SearchResult[], finished:() => void = null):void
	{
		this.finishedAssays = finished;

		this.assayList = [];
		for (let srch of searchResults)
		{
			this.assayList.push(new ResultAssayListAssayInfo(srch.assayID, srch.similarity));
			let assay:AssayDefinition =
			{
				'assayID': srch.assayID,
				'uniqueID': srch.uniqueID,
				'annotations': srch.annotations,
				'text': srch.text,
				'schemaURI': srch.schemaURI,
				'pubchemXRef': null,
				'countCompounds': srch.countCompounds,
				'curationTime': null,
				'curatorID': null,
				'curatorName': null,
				'curatorEmail': null,
				'history': null,
				'holdingIDList': null,
				'translitPreviews': srch.translitPreviews
			};
			this.assayCache[srch.assayID] = assay;
			this.assayText[srch.assayID] = deriveAssayName(assay, 300);

			if (srch.translitPreviews) for (let preview of srch.translitPreviews)
			{
				if (preview.title)
				{
					if (this.previewTitles.indexOf(preview.title) < 0) this.previewTitles.push(preview.title);
				}
				else this.assayTranslit[srch.assayID] = preview.html;
			}
		}

		this.redrawContent();
		this.rosterAssays = [];
	}
	public finaliseSearchResults():void
	{
		this.updatePreviewTitles();
	}

	// set the list to null, which is distinct from empty (it signifies that there aren't supposed to be any results, rather than nothing matched)
	public clearAssays():void
	{
		this.assayList = null;
		this.rosterAssays = [];
		this.previewTitles = [];
		this.redrawContent();
	}

	// fetch the fully loaded assays; any entry that hasn't been loaded is return as null
	public getAssays():AssayDefinition[]
	{
		let assayDefs:AssayDefinition[] = [];
		if (!this.assayList) return assayDefs;
		for (let assayInfo of this.assayList) assayDefs.push(this.assayCache[assayInfo.assayID]);
		return assayDefs;
	}

	// fetch the fully loaded assays; any entry that hasn't been loaded is return as null
	public getVisibleAssays():AssayDefinition[]
	{
		let assayDefs:AssayDefinition[] = [];
		if (!this.assayList) return assayDefs;
		for (let assayInfo of this.assayList)
		{
			if (this.hiddenAssayIDList().indexOf(assayInfo.assayID) < 0) assayDefs.push(this.assayCache[assayInfo.assayID]);
		}
		return assayDefs;
	}

	// returns true/false for each assay, based on whether the checkbox is on
	public selectedMask():boolean[]
	{
		let mask:boolean[] = [];
		for (let assayInfo of this.assayList)
		{
			let dom = this.domCheck[assayInfo.assayID];
			mask.push(dom ? dom.prop('checked') : false);
		}
		return mask;
	}

	// returns true/false for each assay, based on whether
	public hiddenMask():boolean[]
	{
		let mask:boolean[] = [];
		for (let assayInfo of this.assayList)
		{
			let dom = this.domCheck[assayInfo.assayID];
			mask.push(dom ? dom.prop('readonly') : false);
		}
		return mask;
	}

	// returns the assay indices selected
	public selectedAssayIDList():number[]
	{
		let assayIDList:number[] = this.assayList.map((assayInfo) => assayInfo.assayID);
		return Vec.maskGet(assayIDList, this.selectedMask());
	}

	// returns the assay indices marked hidden
	public hiddenAssayIDList():number[]
	{
		let assayIDList:number[] = this.assayList.map((assayInfo) => assayInfo.assayID);
		return Vec.maskGet(assayIDList, this.hiddenMask());
	}

	// affect how assays are grouped & sorted; setting causes a rearrangement of the data & presentation
	public getAssayGroup():SchemaAssignment[] {return this.assayGroup.slice(0);}
	public setAssayGroup(assayGroup:SchemaAssignment[]):void
	{
		this.assayGroup = assayGroup;
		this.orderByAssayGroup();
		this.redrawContent();
	}

	// switches the checkbox selection on/off
	public toggleAssaySelection(assayID:number):void
	{
		let dom = this.domCheck[assayID];
		dom.prop('checked', !dom.prop('checked'));
	}

	// switches the assay display on/off
	public toggleAssayDisplay(assayID:number):void
	{
		let dom = this.domCheck[assayID];
		// Optional, if you added a checkbox, you could turn it on / off
		dom.prop('disabled', !dom.prop('disabled'));
	}

	// switches the assay display on/off -- does not touch checkbox state
	public toggleAssayHidden(assayID:number):void
	{
		let domCheck = this.domCheck[assayID];
		domCheck.prop('readonly', !domCheck.prop('readonly'));
		this.toggleTextVisibility(assayID);
	}

	public toggleTextVisibility(assayID:number):void
	{
		let domText = this.domText[assayID];

		if (domText.hasClass('lightgrayText'))
		{
			domText.removeClass('lightgrayText');
		}
		else
		{
			domText.addClass('lightgrayText');
		}
	}

	// ------------ private methods ------------

	// redraws everything
	private redrawContent(sortInfo:ResultAssaySort = null):void
	{
		this.mainDiv.empty();

		if (this.assayList == null) return;
		if (this.assayList.length == 0)
		{
			// !! is this needed?
			//if (this.isFetching || !this.anyQuery()) return;
			this.mainDiv.text('No assays matched the criteria.');
			return;
		}

		// top row of buttons

	 	let table = $('<table/>').appendTo($('<p/>').appendTo(this.mainDiv));
		let tr = $('<tr/>').appendTo(table);
		let tdCount = $('<td align="left" valign="middle"/>').appendTo(tr);
		tdCount.text('Matching assays: ' + this.assayList.length);
		let tdButtons = $('<td align="right" valign="middle" style="padding-left: 2em;"/>').appendTo(tr);

		let btnSelAll = $('<button class="btn btn-normal"/>').appendTo(tdButtons);
		btnSelAll.append('<span class="glyphicon glyphicon-ok-sign"></span> All');
		Popover.hover(domLegacy(btnSelAll), null, 'Check all results, to be included in the compound list.');
		btnSelAll.click(() => this.selectAllMatches(true));

		tdButtons.append('&nbsp;');

		let btnSelNone = $('<button class="btn btn-normal"/>').appendTo(tdButtons);
		btnSelNone.append('<span class="glyphicon glyphicon-remove-sign"></span> None');
		Popover.hover(domLegacy(btnSelNone), null, 'Uncheck all results.');
		btnSelNone.click(() => this.selectAllMatches(false));

		tdButtons.append('&nbsp;');

		if (this.showCompounds)
		{
			let btnShowCpd = $('<button class="btn btn-normal"/>').appendTo(tdButtons);
			btnShowCpd.append('<span><img src="images/benzene.svg" style="padding-bottom: 3px;"></img> Show Compounds</span>');
			Popover.hover(domLegacy(btnShowCpd), null, 'Show the compounds associated with the selected assays.');
			btnShowCpd.click(() => this.showCompounds());

			tdButtons.append('&nbsp;');
		}

		if (this.showAnalysis && this.showCompounds)
		{
			let btnGrid = $('<button class="btn btn-normal"/>').appendTo(tdButtons);
			btnGrid.append('<span class="glyphicon glyphicon-th"></span> Assay Grid');
			Popover.hover(domLegacy(btnGrid), null, 'Show grid of assays vs. compounds.');
			btnGrid.click(() => this.showAnalysis('grid.jsp'));

			tdButtons.append('&nbsp;');

			let btnPred = $('<button class="btn btn-normal"/>').appendTo(tdButtons);
			btnPred.append('<span class="glyphicon glyphicon-sort-by-attributes-alt"></span> Predict');
			Popover.hover(domLegacy(btnPred), null, 'Use known compounds to predict activities for new compounds.');

			btnPred.click(() => this.showAnalysis('predict.jsp'));

			tdButtons.append('&nbsp;');
		}

		if (this.showAnalysis)
		{
			let btnBulk = $('<button class="btn btn-normal"/>').appendTo(tdButtons);
			btnBulk.append('<span class="glyphicon glyphicon-blackboard"></span> Bulk Remap');
			Popover.hover(domLegacy(btnBulk), null, 'Apply bulk changes to the selected assays.');
			btnBulk.click(() => this.showAnalysis('bulkmap.jsp'));

			tdButtons.append('&nbsp;');
		}

		let btnCopy = $('<button class="btn btn-normal"/>').appendTo(tdButtons);
		btnCopy.append('<span class="glyphicon glyphicon-copy"></span> Copy');
		Popover.hover(domLegacy(btnCopy), null, 'Copy the list of assays to the clipboard, as text.');
		btnCopy.click(() => this.copyAssays());

		tdButtons.append('&nbsp;');

		let btnDownload = $('<button class="btn btn-normal"/>').appendTo(tdButtons);
		btnDownload.append('<span class="glyphicon glyphicon-download"></span> Download');
		Popover.hover(domLegacy(btnDownload), null, 'Download all assay content, as a zip file.');
		btnDownload.click(() => this.downloadAssays());

		// note: only show the delete button if curator-level privilege is available, because bulk deletion requests could get quite spammy
		if (Authentication.canSubmitDirectly())
		{
			tdButtons.append('&nbsp;');

			let btnDelete = $('<button class="btn btn-normal"/>').appendTo(tdButtons);
			btnDelete.append('<span class="glyphicon glyphicon-trash"></span> Delete');
			Popover.hover(domLegacy(btnDownload), null, 'Delete all selected assays (via the Holding Bay).');
			btnDelete.click(() => this.deleteAssays());
		}

		tdButtons.append('&nbsp;');

		let btnQuery = $('<button class="btn"/>').appendTo(tdButtons);
		if (this.showQuery) btnQuery.addClass('btn-action'); else btnQuery.addClass('btn-normal');
		btnQuery.append('<span class="glyphicon glyphicon-magnet"></span> Query');
		Popover.hover(domLegacy(btnQuery), null, 'Show query strings, which can be used to re-select the assays.');
		btnQuery.click(() => this.toggleQuery(btnQuery));

		this.divQuery = $('<div/>').appendTo(this.mainDiv);
		if (this.renderQuery && this.showQuery) this.renderQuery(this.divQuery);

		// the entries proper

		table = $('<table/>').appendTo(this.mainDiv);
		tr = $('<tr/>').appendTo(table);
		tr.append('<th class="coltitle" style="padding-right: 1em;"><img src="images/benzene.svg"></th>');
		if (this.assayList[0].similarity >= 0)
		{
			let th = $('<th role="button" class="coltitle" style="padding-right: 1em; white-space: nowrap;">Similarity</th>').appendTo(tr);
			th.click(() => this.sortBySimilarity(th));
			if (sortInfo != null && sortInfo.column == ResultAssayColumn.Similarity)
			{
				th.append(' ');
				if (sortInfo.dir == 1) $('<span class="glyphicon glyphicon-chevron-down"/>').appendTo(th);
				else $('<span class="glyphicon glyphicon-chevron-up"/>').appendTo(th);
			}
		}
		if (this.showCompounds)
		{
			let th = $('<th role="button" class="coltitle" style="padding-right: 1em; white-space: nowrap;">Compounds</th>').appendTo(tr);
			th.click(() => this.sortByCountCompounds(th));
			if (sortInfo != null && sortInfo.column == ResultAssayColumn.Compounds)
			{
				th.append(' ');
				if (sortInfo.dir == 1) $('<span class="glyphicon glyphicon-chevron-down"/>').appendTo(th);
				else $('<span class="glyphicon glyphicon-chevron-up"/>').appendTo(th);
			}
		}
		this.thResultColumn = $('<th class="coltitle" colspan="2">Result</th>').appendTo(tr);

		let th = $('<th role="button" class="coltitle" style="white-space: nowrap;">ID</th>').appendTo(tr);
		th.click(() => this.sortByAssayID(th));
		if (sortInfo != null && sortInfo.column == ResultAssayColumn.AssayID)
		{
			th.append(' ');
			if (sortInfo.dir == 1) $('<span class="glyphicon glyphicon-chevron-down"/>').appendTo(th);
			else $('<span class="glyphicon glyphicon-chevron-up"/>').appendTo(th);
		}

		this.domCheck = [];

		for (let n = 0; n < this.assayList.length; n++)
		{
			const idx = n;

			let assayInfo = this.assayList[n], assayID = assayInfo.assayID, assay = this.assayCache[assayID];
			let uniqueID = assay ? assay.uniqueID : null;

			tr = $('<tr/>').appendTo(table);
			let tdCheck = $('<td/>').appendTo(tr);
			let tdCount = null, tdSim = null;
			if (assayInfo.similarity >= 0) tdSim = $('<td class="overline" align="center" valign="middle"/>').appendTo(tr);
			if (this.showCompounds) tdCount = $('<td class="overline" align="center" valign="middle"/>').appendTo(tr);
			let tdText = $('<td class="overline" align="left" valign="top"/>').appendTo(tr);
			let tdView = $('<td class="overline" align="center" valign="bottom"/>').appendTo(tr);
			let tdID = $('<td class="overline" align="center" valign="bottom" style="white-space: nowrap;"/>').appendTo(tr);

			this.domCount[assayID] = tdCount;
			this.domText[assayID] = tdText;
			this.domUnique[assayID] = tdID;

			tdText.css('padding-right', '0.5em');
			tdView.css('padding-right', '0.5em');

			let chk = this.domCheck[assayID];
			if (!chk)
			{
				chk = $('<input type="checkbox"/>');
				this.domCheck[assayID] = chk;
			}
			chk.appendTo(tdCheck);
			chk.off('click');
			chk.click(() =>
			{
				// if the readonly flag is on, then toggle the switch on and redraw the grid
				if (chk.prop('readonly'))
				{
					// disabled for now -- allows checkbox to unhide assay
					//this.toggleAssayHidden(assayID);
					//if (this.changedAssayVisibility) this.changedAssayVisibility();
				}
				else
				{
					this.divQuery.empty();
					if (this.renderQuery && this.showQuery) this.renderQuery(this.divQuery);
					if (this.changedAssaySelection) this.changedAssaySelection();
				}
			});

			let text = this.assayText[assayID];
			let spanText = $('<div/>').appendTo(tdText);
			if (text) spanText.text(text);

			tdText.click(() =>
			{
				// if the readonly flag is on, then toggle the switch on and redraw the grid
				if (chk.prop('readonly'))
				{
					// disabled for now -- allows checkbox to unhide assay
					this.toggleAssayHidden(assayID);
					if (this.changedAssayVisibility) this.changedAssayVisibility();
				}
			});

			let translit = this.assayTranslit[assayID];
			if (translit) Popover.hover(domLegacy(spanText), null, translit);

			if (tdSim) tdSim.text((assayInfo.similarity * 100).toFixed(0) + '%');
			if (tdCount && assay) tdCount.text(assay.countCompounds.toString());

			let linkView = $('<a target="_blank"/>').appendTo(tdView);
			linkView.attr('href', 'assign.jsp?assayID=' + assayID);
			linkView.text('View');
			tdView.append('&nbsp;\u{21F2}');

			let [src, id] = UniqueIdentifier.parseKey(uniqueID);
			if (src)
			{
				let url = UniqueIdentifier.composeRefURL(assay.uniqueID), label = src.shortName + ' ' + id;
				if (url)
				{
					let linkAID = $('<a target="_blank"/>').appendTo(tdID);
					linkAID.attr('href', url);
					linkAID.text(label);
				}
				else tdID.text(label);
			}
		}
	}

	// extract the next assay indicated by the roster, or finish up
	private fetchNextAssay():void
	{
		if (this.rosterAssays.length == 0)
		{
			if (this.finishedAssays) this.finishedAssays();
			this.updatePreviewTitles();
			OverlayMessage.hide();
			return;
		}

		let assayID = this.rosterAssays.shift();

		let params = {'assayID': assayID, 'countCompounds': true, 'translitPreviews': true};

		callREST('REST/GetAssay', params,
			(assay:AssayDefinition) =>
			{
				this.assayCache[assayID] = assay;
				let text = deriveAssayName(assay, 300);
				this.assayText[assayID] = text;

				let domCount = this.domCount[assayID], domText = this.domText[assayID], domID = this.domUnique[assayID];
				if (domCount) domCount.text(assay.countCompounds);
				if (domText) domText.empty();
				let spanText = domText ? $('<span/>').appendTo(domText) : null;
				if (domText) spanText.text(text);
				if (domID)
				{
					let [src, id] = UniqueIdentifier.parseKey(assay.uniqueID);
					if (src)
					{
						domID.empty();
						let url = UniqueIdentifier.composeRefURL(assay.uniqueID), label = src.shortName + ' ' + id;
						if (url)
						{
							let linkAID = $('<a target="_blank"/>').appendTo(domID);
							linkAID.attr('href', url);
							linkAID.text(label);
						}
						else domID.text(label);
					}
				}

				if (assay.translitPreviews) for (let preview of assay.translitPreviews)
				{
					if (preview.title)
					{
						if (this.previewTitles.indexOf(preview.title) < 0) this.previewTitles.push(preview.title);
					}
					else if (spanText)
					{
						this.assayTranslit[assayID] = preview.html;
						Popover.hover(domLegacy(spanText), null, preview.html);
					}
				}

				this.fetchNextAssay();
			});
	}

	// turn all result checkboxes on/off
	private selectAllMatches(sel:boolean):void
	{
		for (let assayInfo of this.assayList) this.domCheck[assayInfo.assayID].prop('checked', sel);
	}

	// transfer the list of assays onto the clipboard in tab-separated-value format, suitable for pasting into a spreadsheet
	private copyAssays():void
	{
		if (!this.template)
		{
			this.performCopy([], true, false, false, true);
			return;
		}

		let divOptions = $('<div/>');
		let makeCheckbox = (text:string):JQuery =>
		{
			let label = $('<label/>').appendTo(divOptions);
			let input = $('<input type="checkbox"/>').appendTo(label);
			$('<span/>').appendTo(label).css({'margin': '0 0.5em 0 0.5em'}).text(text);
			return input;
		};

		let chkLabel = makeCheckbox('Label');
		let chkURI = makeCheckbox('URI');
		let chkAbbrev = makeCheckbox('Abbreviated URI');
		let chkCombine = makeCheckbox('Combine duplicates');

		chkLabel.prop('checked', true);
		chkCombine.prop('checked', true);

		let dlg = new PickAssignmentDialog(this.template, true);
		dlg.title = 'Assignments';
		dlg.labelDone = 'Copy';
		dlg.showAllNone = true;
		dlg.domHeader = divOptions;
		dlg.callbackDone = (assnList:SchemaAssignment[]) =>
		{
			let withLabel = chkLabel.prop('checked');
			let withURI = chkURI.prop('checked');
			let withAbbrev = chkAbbrev.prop('checked');
			let dupCombine = chkCombine.prop('checked');
			this.performCopy(assnList, withLabel, withURI, withAbbrev, dupCombine);
		};
		dlg.show();
	}
	private performCopy(assnList:SchemaAssignment[], withLabel:boolean, withURI:boolean, withAbbrev:boolean, dupCombine:boolean):void
	{
		let multiCol = Vec.numberArray(1, assnList.length);
		if (!dupCombine)
		{
			for (let n = 0; n < assnList.length; n++)
			{
				let assn = assnList[n];
				for (let resultAssay of this.assayList)
				{
					let assay = this.assayCache[resultAssay.assayID];
					let matches = 0;
					for (let annot of assay.annotations)
						if (samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest)) matches++;
					multiCol[n] = Math.max(multiCol[n], matches);
				}
			}
		}
		let perCol = (withLabel ? 1 : 0) + (withURI ? 1 : 0) + (withAbbrev ? 1 : 0);

		let lines:string[] = [];
		let headings = ['AssayID', 'UniqueID', 'IDCode', 'Origin', 'BAE'];
		for (let n = 0; n < assnList.length; n++)
		{
			let assn = assnList[n];
			if (withLabel) headings.push(assn.name);
			if (withURI) headings.push(assn.propURI);
			if (withAbbrev) headings.push(collapsePrefix(assn.propURI));
			for (let i = 1; i < multiCol[n]; i++) for (let j = 0; j < perCol; j++) headings.push('');
		}
		lines.push(headings.join('\t'));

		let base = getBaseURL() + '/assign.jsp?assayID=';

		for (let n = 0; n < this.assayList.length; n++)
		{
			let assayID = this.assayList[n].assayID;
			let assay = this.assayCache[assayID];
			let uniqueID = assay ? assay.uniqueID : null;
			let [src, id] = UniqueIdentifier.parseKey(uniqueID);
			let idCode = src ? /*src.shortName + ' ' +*/ id : '';
			let refURL = UniqueIdentifier.composeRefURL(uniqueID);
			let baeURL = base + assayID;

			let row = [assayID, uniqueID, idCode, refURL, baeURL];
			for (let i = 0; i < assnList.length; i++)
			{
				let assn = assnList[i];

				let dataLabel:string[] = [], dataURI:string[] = [], dataAbbrev:string[] = [];
				for (let annot of assay.annotations) if (samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest))
				{
					dataLabel.push(annot.valueLabel || '');
					dataURI.push(annot.valueURI || '');
					dataAbbrev.push(collapsePrefix(annot.valueURI));
				}

				if (dupCombine)
				{
					if (withLabel) row.push(dataLabel.join(', '));
					if (withURI) row.push(dataURI.join(', '));
					if (withAbbrev) row.push(dataAbbrev.join(', '));
				}
				else
				{
					for (let j = 0; j < multiCol[i]; j++)
					{
						if (withLabel) row.push(j < dataLabel.length ? dataLabel[j] : '');
						if (withURI) row.push(j < dataURI.length ? dataURI[j] : '');
						if (withAbbrev) row.push(j < dataAbbrev.length ? dataAbbrev[j] : '');
					}
				}
			}

			lines.push(row.join('\t'));
		}

		setTimeout(() => copyToClipboard(lines.join('\n')), 1);
	}

	// ask the server for the resulting assays in the form of a zip file
	private downloadAssays():void
	{
		// calling the render function reveals 3 different types of query; the first choice is the list of ID numbers (qassay),
		// as long as it's short, but otherwise it'll use the definition criteria (query) which gets reevaluated on submission
		let [query, qunique, qassay] = this.renderQuery(null);
		let param = encodeURIComponent(qassay);
		if (param.length > 1900 && query) param = encodeURIComponent(query);

		window.location.href = restBaseURL + '/servlet/DownloadQuery/results.zip?assays=true&query=' + param;
	}

	// send all selected assays to the holding bay for deletion
	private deleteAssays():void
	{
		let assayIDList = this.assayList.map((assayInfo) => assayInfo.assayID).filter((assayID) =>
		{
			let dom = this.domCheck[assayID];
			return dom ? dom.prop('checked') : false;
		});
		if (assayIDList.length == 0)
		{
			alert('Select at least one assay in order to delete.');
			return;
		}
		let plural = assayIDList.length == 0 ? '' : 's';
		let msg = `Delete ${assayIDList.length} assay${plural} by submitting Holding Bay requests?`;
		if (!confirm(msg)) return;

		(async () =>
		{
			for (let assayID of assayIDList)
			{
				let params = {'assayID': assayID};
				try
				{
					await asyncREST('REST/DeleteAssay', params);
				}
				catch (ex)
				{
					alert('Unable to delete assay #' + assayID);
					console.log('Assay deletion failed: assayID=' + assayID + '/' + ex);
					return;
				}
				await wmk.yieldDOM();
			}
			alert(`Deletion request${plural} added to the Holding Bay.`);
		})();
	}

	// switches query display on or off
	private toggleQuery(btn:JQuery):void
	{
		this.showQuery = !this.showQuery;
		this.divQuery.empty();
		if (this.showQuery)
		{
			btn.removeClass('btn-normal');
			btn.addClass('btn-action');
			this.renderQuery(this.divQuery);
		}
		else
		{
			btn.removeClass('btn-action');
			btn.addClass('btn-normal');
		}
	}

	// reorders the assay ID list to reflect the selected assay groups, if any
	private orderByAssayGroup():void
	{
		if (this.assayGroup.length == 0) return; // leave it as-is

		let propGrp = new Set<string>();
		for (let assn of this.assayGroup) propGrp.add(keyPropGroup(assn.propURI, assn.groupNest));
		let mapPos:Record<string, number> = {};
		let partitions:number[][] = [];

		for (let assayInfo of this.assayList)
		{
			let assay = this.assayCache[assayInfo.assayID];
			if (!assay) continue; // shouldn't happen
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

			// if none, create a singleton
			if (values.length == 0)
			{
				partitions.push([assayInfo.assayID]);
				continue;
			}

			// otherwise, lookup the tag and associate that as a key index
			values.sort();
			let tag = values.join('\n'), pos = mapPos[tag];
			if (pos == null)
			{
				mapPos[tag] = partitions.length;
				partitions.push([assayInfo.assayID]);
			}
			else partitions[pos].push(assayInfo.assayID);
		}

		// now cluster/sort
		// (note: could use tree pattern to sort on multiple properties...? might be better than nothing...)
		let assn = this.assayGroup[0];
		let assayTree = new TreePattern();
		for (let n = 0; n < partitions.length; n++)
		{
			let branches:string[][] = [];
			for (let assayID of partitions[n]) for (let annot of this.assayCache[assayID].annotations)
			{
				if (!compatiblePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest)) continue;
				let branch = Vec.concat([annot.valueLabel], annot.labelHier).reverse();
				for (let look of branches) if (Vec.equals(branch, look)) {branch = null; break;}
				if (branch) branches.push(branch);
			}
			assayTree.addNode(branches);
		}
		assayTree.cluster();
		this.assayList = [];
		for (let ptn of Vec.idxGet(partitions, assayTree.order))
		{
			for (let assayID of ptn) this.assayList.push(new ResultAssayListAssayInfo(assayID));
		}
	}

	// if previews are available, offers up the option of switching the list view style
	private updatePreviewTitles(previewChoice = -1, sortInfo:ResultAssaySort = null):void
	{
		this.previewChoice = previewChoice; // normal text is always default
		if (this.previewTitles.length == 0) return;

		this.thResultColumn.empty();

		let txtSummary = $('<span role="button" class="coltitle" style="padding-right: 1em; white-space: nowrap;"/>').appendTo(this.thResultColumn);
		txtSummary.text('Summary');
		txtSummary.click(() => this.sortByPreviewTitle(txtSummary, this.previewChoice, true));
		if (sortInfo != null && sortInfo.column == ResultAssayColumn.Summary)
		{
			txtSummary.append(' ');
			if (sortInfo.dir == 1) $('<span class="glyphicon glyphicon-chevron-down"/>').appendTo(txtSummary);
			else $('<span class="glyphicon glyphicon-chevron-up"/>').appendTo(txtSummary);
		}

		let div = $('<div class="btn-group" data-toggle="buttons"/>').appendTo(this.thResultColumn);
		div.css('margin', '0.5em 0 0.5em 0');

		let lblTitle = $('<label class="btn btn-radio"/>').appendTo(div);
		lblTitle.append('<input type="radio" name="options" autocomplete="off">Title</input>');
		lblTitle.click(() => {(sortInfo != null) ? this.sortByPreviewTitle(txtSummary, -1) : this.changePreviewTitle(-1);});
		if (this.previewChoice == -1)
		{
			lblTitle.addClass('active');
			lblTitle.find('input').prop('checked', true);
		}

		for (let n = 0; n < this.previewTitles.length; n++)
		{
			let lbl = $('<label class="btn btn-radio"/>').appendTo(div);
			$('<input type="radio" name="options" autocomplete="off">' + escapeHTML(this.previewTitles[n]) + '</input>').appendTo(lbl);
			lbl.click(() => {(sortInfo != null) ? this.sortByPreviewTitle(txtSummary, n) : this.changePreviewTitle(n);});
			if (this.previewChoice == n)
			{
				lbl.addClass('active');
				lbl.find('input').prop('checked', true);
			}
		}
	}

	private changePreviewTitle(idx:number):void
	{
		this.previewChoice = idx;

		for (let assayInfo of this.assayList)
		{
			let assayID = assayInfo.assayID;
			let assay = this.assayCache[assayID];
			let domText = this.domText[assayID];
			domText.empty();
			let spanText = $('<span/>').appendTo(domText);
			if (this.previewChoice < 0)
			{
				spanText.text(deriveAssayName(assay, 300));
			}
			else
			{
				let html:string = null;
				if (assay.translitPreviews) for (let preview of assay.translitPreviews)
					if (preview.title == this.previewTitles[this.previewChoice]) {html = preview.html; break;}
				if (!html) html = '<font style="color: #808080;">unknown</font>';
				spanText.html(html);
			}

			let translit = this.assayTranslit[assayID];
			if (translit) Popover.hover(domLegacy(spanText), null, translit);
		}
	}

	// ------------ sort methods ------------

	// sorting steps are as follows:
	// 0. check column header for up/down chevron
	// 1. sort this.assayList according to specified criterion and chevron direction (default is ascending)
	// 2. redraw results

	private sortBySimilarity(th:JQuery):void
	{
		// sanity-check availability of similarity #s
		if (this.assayList == null || this.assayList.length <= 0 || this.assayList[0].similarity < 0) return;

		let chevDown:HTMLElement = null, chevUp:HTMLElement = null;
		chevDown = th.find('span.glyphicon-chevron-down')[0];
		if (!chevDown) chevUp = th.find('span.glyphicon-chevron-up')[0];

		let dir = ((!chevDown && !chevUp) || chevUp) ? 1 : -1;
		this.assayList.sort((a, b):number => (a.similarity - b.similarity) * dir);

		this.redrawContent(new ResultAssaySort(ResultAssayColumn.Similarity, dir));
		this.updatePreviewTitles(this.previewChoice);
		this.changePreviewTitle(this.previewChoice);
	}

	private sortByAssayID(th:JQuery):void
	{
		// sanity-check availability of assay IDs
		if (this.assayList == null || this.assayList.length <= 0) return;

		let chevDown:HTMLElement = null, chevUp:HTMLElement = null;
		chevDown = th.find('span.glyphicon-chevron-down')[0];
		if (!chevDown) chevUp = th.find('span.glyphicon-chevron-up')[0];

		let dir = ((!chevDown && !chevUp) || chevUp) ? 1 : -1;
		this.assayList.sort((a, b):number => (a.assayID - b.assayID) * dir);

		this.redrawContent(new ResultAssaySort(ResultAssayColumn.AssayID, dir));
		this.updatePreviewTitles(this.previewChoice);
		this.changePreviewTitle(this.previewChoice);
	}

	private sortByCountCompounds(th:JQuery):void
	{
		// sanity-check availability of assay IDs
		if (this.assayList == null || this.assayList.length <= 0) return;

		let chevDown:HTMLElement = null, chevUp:HTMLElement = null;
		chevDown = th.find('span.glyphicon-chevron-down')[0];
		if (!chevDown) chevUp = th.find('span.glyphicon-chevron-up')[0];

		let dir = ((!chevDown && !chevUp) || chevUp) ? 1 : -1;
		this.assayList.sort((a, b):number =>
		{
			let delta = this.assayCache[a.assayID].countCompounds - this.assayCache[b.assayID].countCompounds;
			return delta * dir;
		});

		this.redrawContent(new ResultAssaySort(ResultAssayColumn.Compounds, dir));
		this.updatePreviewTitles(this.previewChoice);
		this.changePreviewTitle(this.previewChoice);
	}

	private sortByPreviewTitle(span:JQuery, previewChoice:number, flipDirection:boolean = false):void
	{
		// sanity-check availability of assay IDs
		if (this.assayList == null || this.assayList.length <= 0) return;

		let chevDown:HTMLElement = null, chevUp:HTMLElement = null;
		chevDown = span.find('span.glyphicon-chevron-down')[0];
		if (!chevDown) chevUp = span.find('span.glyphicon-chevron-up')[0];

		// do not change chevron if we are not flipping sort-direction
		let dir = ((!chevDown && !chevUp) || chevUp) ? 1 : -1;
		if (!flipDirection && (chevUp || chevDown)) dir *= -1;

		this.assayList.sort((a, b):number => this.comparePreviewTitle(this.assayCache[a.assayID], this.assayCache[b.assayID], dir, previewChoice));

		this.redrawContent(new ResultAssaySort(ResultAssayColumn.Summary, dir));
		this.updatePreviewTitles(previewChoice, new ResultAssaySort(ResultAssayColumn.Summary, dir));
		this.changePreviewTitle(previewChoice);
	}

	private comparePreviewTitle(assayA:AssayDefinition, assayB:AssayDefinition, dir:number, previewChoice:number):number
	{
		let txtA:string = null, txtB:string = null;
		if (previewChoice < 0)
		{
			txtA = deriveAssayName(assayA, 300);
			txtB = deriveAssayName(assayB, 300);
		}
		else
		{
			if (assayA.translitPreviews) for (let preview of assayA.translitPreviews)
				if (preview.title == this.previewTitles[previewChoice]) {txtA = preview.html; break;}

			if (assayB.translitPreviews) for (let preview of assayB.translitPreviews)
				if (preview.title == this.previewTitles[previewChoice]) {txtB = preview.html; break;}
		}

		txtA = txtA == null ? '' : txtA.toLowerCase();
		txtB = txtB == null ? '' : txtB.toLowerCase();
		return txtA == txtB ? 0 : ((txtA > txtB) ? 1 : -1) * dir;
	}
}

/* EOF */ }
