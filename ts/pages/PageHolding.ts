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

///<reference path='../widgets/BootstrapDialog.ts'/>

namespace BioAssayExpress /* BOF */ {

/*
	Displays all entries in the holding bay.
*/

type ApplyTypes = 'applied' | 'deleted';
type HoldingCompare = (h1:HoldingBayAssay, h2:HoldingBayAssay) => number;

export class PageHolding
{
	private busy = false;

	private tableNew:JQuery;
	private tableOld:JQuery;
	private order:number[] = [];
	private assayIDList:number[] = [];
	private cmpFunctions:Map<string, (i1:number, i2:number) => number> = null;
	private lastSortField:string;
	private sortDirection:number = 1;
	private filter:JQuery;

	private selection:Set<number> = new Set();
	private selectionCB:Record<number, JQuery> = {};
	private availableHoldingIDs:Set<number> = new Set(); // holdingIDs that can be removed or applied
	private selectedButtons:JQuery[] = [];

	private domEntries:Record<number, JQuery> = {};
	public holdingIDList:number[] = [];
	public holdingBayAssayStubMap:Record<number, HoldingBayAssay> = {};

	constructor(private parent:JQuery, private holdingStubsList:HoldingBayAssay[])
	{
		for (let n = 0; n < this.holdingStubsList.length; n++)
		{
			this.order.push(n);
			let holdingID = holdingStubsList[n].holdingID;
			this.holdingIDList.push(holdingID);
			this.assayIDList.push(holdingStubsList[n].assayID);
			this.holdingBayAssayStubMap[holdingID] = this.holdingStubsList[n];
			this.availableHoldingIDs.add(holdingID);
		}
		this.prepareCmpFunctions();
	}

	// assembles the page from scratch
	public build():void
	{
		if (this.holdingIDList == null)
		{
			callREST('REST/GetHoldingBay', {},
				(data:any) =>
				{
					this.holdingIDList = data.holdingID;
					this.assayIDList = data.assayID;
					this.populateContent();
				});
		}
		else this.populateContent();
	}

	// ------------ private methods ------------

	private cellCSS = {'text-align': 'center', 'vertical-align': 'middle', 'padding': '0.25em 0.5em'};

	private populateContent():void
	{
		this.renderFilter();

		let orderNew = this.order.filter((idx) => this.assayIDList[idx] == 0);
		let orderOld = this.order.filter((idx) => this.assayIDList[idx] != 0);

		if (orderNew.length > 0)
		{
			$('<h1/>').appendTo(this.parent).text('New Assays');
			this.tableNew = $('<div/>').appendTo(this.parent);
			this.renderTable(this.tableNew, true);
		}
		if (orderOld.length > 0)
		{
			$('<h1/>').appendTo(this.parent).text('Updated Assays');
			this.tableOld = $('<div/>').appendTo(this.parent);
			this.renderTable(this.tableOld, false);
		}

		let div = $('<div/>').appendTo(this.parent).css({'padding-top': '1em'});

		let btnSelAll = $('<button class="btn btn-action">Select All</button>').appendTo(div);
		btnSelAll.click(() => this.selectAll(true));
		div.append(' ');
		let btnSelNone = $('<button class="btn btn-action">Unselect All</button>').appendTo(div);
		btnSelNone.click(() => this.selectAll(false));
		div.append(' ');

		this.renderSelectionButtons(div);
		this.selectionChanged();
	}

	private renderFilter():void
	{
		let div = $('<div/>').appendTo(this.parent);
		div.addClass('search-box');
		div.css({'margin': '1em 0em', 'float': 'none'});
		this.filter = $('<input type="search" placeholder="filter holding bay entries"/>').appendTo(div);
		this.filter.css('margin-right', '1em');
		this.filter.keyup(() =>
		{
			if (this.tableNew) this.renderTable(this.tableNew, true);
			if (this.tableOld) this.renderTable(this.tableOld, false);
		});
	}

	private matchesFilter(holding:HoldingBayAssay):boolean
	{
		let txt = this.filter.val().toString().toLocaleLowerCase().trim();
		if (txt == '') return true;
		if (holding.curatorID.toLocaleLowerCase().includes(txt)) return true;
		if (holding.curatorName.toLocaleLowerCase().includes(txt)) return true;
		if (holding.curatorEmail.toLocaleLowerCase().includes(txt)) return true;

		let s = UniqueIdentifier.composeUniqueID(holding.uniqueID || holding.currentUniqueID);
		if (s && s.toLocaleLowerCase().includes(txt)) return true;
		return false;
	}

	private renderTable(container:JQuery, newAssays:boolean):void
	{
		let order = newAssays
				? this.order.filter((idx) => this.assayIDList[idx] == 0)
				: this.order.filter((idx) => this.assayIDList[idx] != 0);

		order = order.filter((idx) => this.matchesFilter(this.holdingBayAssayStubMap[this.holdingIDList[idx]]));

		let grouped:Record<string, number[]> = {};
		if (newAssays)
		{
			// group by uniqueID
			let nextGroup = 1;
			let mapping = new Map();
			for (let idx of order)
			{
				let uniqueID = this.holdingBayAssayStubMap[this.holdingIDList[idx]].uniqueID;
				if (uniqueID && !mapping.has(uniqueID)) mapping.set(uniqueID, nextGroup++);
				let key = `k-${uniqueID ? mapping.get(uniqueID) : nextGroup++}`;
				(grouped[key] = grouped[key] || []).push(idx);
			}
		}
		else
		{
			// group by assayID
			for (let idx of order)
			{
				let key = `k-${this.assayIDList[idx]}`; // conversion to non-numeric string preserves order
				(grouped[key] = grouped[key] || []).push(idx);
			}
		}
		const hasGroups = Object.keys(grouped).length < order.length;

		container.empty();
		let table = $('<table/>').appendTo(container);
		this.renderHeader(table, newAssays, hasGroups);
		let tbody = $('<tbody/>').appendTo(table);
		for (let key in grouped) this.renderGroup(tbody, grouped[key], newAssays, hasGroups);
	}

	private renderHeader(container:JQuery, newAssays:boolean, hasGroups:boolean):void
	{
		let tr = $('<tr/>').appendTo($('<thead/>').appendTo(container))
				.css({'background-color': '#C0C0C0'});
		let headers = ['Select', 'Holding ID', 'Assay ID', 'Unique ID', 'Date', 'Curator', 'Content', '', ''];
		if (newAssays) headers = headers.filter((h) => h != 'Assay ID');
		for (let header of headers)
		{
			let th = $('<th/>').appendTo(tr).css({...this.cellCSS, 'background-color': 'grey', 'color': 'white'});
			th.text(header);
			if (hasGroups && header == 'Select') th.attr('colspan', 2);
			if (this.cmpFunctions.has(header))
				th.addClass('pseudoLink').click(() => this.sortBy(header));
		}
	}

	private renderGroup(tbody:JQuery, groupIdx:number[], newAssays:boolean, hasGroups:boolean):void
	{
		let groupHoldingIDs = groupIdx.map((idx) => this.holdingIDList[idx]);

		groupHoldingIDs.sort((a, b) => a - b);
		groupHoldingIDs.forEach((holdingID, n) =>
		{
			let holding = this.holdingBayAssayStubMap[holdingID];

			let cells:(string | JQuery)[] = [];
			let attrs:{}[] = Array(newAssays || n > 0 ? 7 : 8).fill(null);
			let css:{}[] = Array(newAssays || n > 0 ? 7 : 8).fill(null);

			if (hasGroups && n == 0)
			{
				attrs[cells.length] = {'rowspan': groupIdx.length};
				css[cells.length] = {'border-bottom': '1px solid lightgrey'};
				const cb = $('<span/>');
				cells.push(cb);
				if (groupHoldingIDs.length > 1)
				{
					cb.addClass('pseudoLink');
					cb.text('Group');
					cb.click(() => this.selectionGroupChanged(groupHoldingIDs));
				}
			}

			const cb = $('<input type="checkbox"/>');
			cb.change(() => this.selectionChanged(holdingID));
			this.selectionCB[holdingID] = cb;
			cells.push(cb);

			cells.push(holdingID.toString());

			if (!newAssays && n == 0)
			{
				attrs[cells.length] = {'rowspan': groupIdx.length};
				css[cells.length] = {'border-bottom': '1px solid lightgrey'};
				cells.push($('<a target="_blank">' + holding.assayID + '</a>').attr('href', 'assign.jsp?assayID=' + holding.assayID));
			}

			if (!newAssays || (newAssays && n == 0))
			{
				let s = holding.uniqueID || holding.currentUniqueID;
				css[cells.length] = {'text-align': s ? 'left' : 'center'};
				if (newAssays)
				{
					css[cells.length] = {'text-align': s ? 'left' : 'center', 'border-bottom': '1px solid lightgrey'};
					attrs[cells.length] = {'rowspan': groupIdx.length};
				}
				s = UniqueIdentifier.composeUniqueID(s);
				cells.push(s || $('<span>&mdash;</span>'));
			}

			let time = new Date(holding.submissionTime);
			cells.push(time.toISOString().substring(0, 10));
			cells.push(holding.curatorName || holding.curatorEmail);

			if (n == 0)
			{
				let assayID = 0;
				for (let hbay of this.holdingStubsList) if (hbay.assayID && groupHoldingIDs.includes(hbay.holdingID))
				{
					assayID = hbay.assayID;
					break;
				}

				attrs[cells.length] = {'rowspan': groupIdx.length};
				css[cells.length] = {'border-bottom': '1px solid lightgrey'};
				let div = $('<a href="#"/>');
				div.click(() => (async () => this.showDetails(groupHoldingIDs, assayID))());
				div.text('Details');
				cells.push(div);
			}

			if (holding.deleteFlag)
				cells.push($('<span class="glyphicon glyphicon-exclamation-sign"/>').css({'color': 'red'}));
			else
				cells.push($('<div/>'));

			let div = $('<div/>');
			this.renderSelectionButtons(div, [holdingID]);
			cells.push(div);

			let tr = $('<tr/>').appendTo(tbody);
			if (n == groupIdx.length - 1) tr.css({'border-bottom': '1px solid lightgrey'});
			cells.forEach((cell, ncell) =>
			{
				let td = $('<td/>').appendTo(tr).css(this.cellCSS);
				td.append(cell);
				if (attrs[ncell]) td.attr(attrs[ncell]);
				if (css[ncell]) td.css(css[ncell]);
			});
			this.domEntries[holdingID] = tr;
		});
	}

	// shows specifics for all of the holding bay entries; if they are deltas based on an existing assay, then assayID > 0
	private async showDetails(holdingIDList:number[], assayID:number):Promise<void>
	{
		holdingIDList = holdingIDList.filter((id) => this.availableHoldingIDs.has(id));
		if (holdingIDList.length == 0) return;

		let assay = {} as AssayDefinition;
		if (assayID)
		{
			try {assay = await asyncREST('REST/GetAssay', {'assayID': assayID});}
			catch (e) {} // silent failure
		}
		assay.holdingIDList = holdingIDList;

		let selnAssayChangesDlg = new SelectAssayChangesDialog(
			assay, [],
			(changes:HoldingBayAssay[]):void =>
			{
				for (let change of changes)
				{
					this.selectionCB[change.holdingID].prop('checked', true);
					this.selectionChanged(change.holdingID);
				}
			});
		selnAssayChangesDlg.show();
	}

	private renderSelectionButtons(container:JQuery, selection?:number[]):void
	{
		let suffix = selection ? '' : ' Selected';
		let btnApply = $('<button class="btn btn-action"/>').appendTo(container);
		btnApply.text(`Apply${suffix}`);
		btnApply.click(() => this.applySelected(selection));
		container.append(' ');
		let btnRemove = $('<button class="btn btn-normal"/>').appendTo(container);
		btnRemove.text(`Remove${suffix}`);
		btnRemove.click(() => this.removeSelected(selection));
		this.selectedButtons = [btnApply, btnRemove];
	}

	private selectionChanged(idx:number = null):void
	{
		if (idx != null)
		{
			if (this.selectionCB[idx].prop('checked'))
				this.selection.add(idx);
			else
				this.selection.delete(idx);
		}
		// synchronize checkboxes with selection
		for (let idx in this.selectionCB)
			this.selectionCB[idx].prop('checked', this.selection.has(Number(idx)));
		let nselected = this.selection.size;
		for (let button of this.selectedButtons)
			button.prop('disabled', nselected == 0);
	}

	private selectionGroupChanged(groupIdx:number[]):void
	{
		for (const idx of groupIdx) this.selection.add(idx);
		this.selectionChanged();
	}

	// set all the checkboxes on/off
	private selectAll(value:boolean):void
	{
		if (value)
		{
			for (let holdingID of this.availableHoldingIDs) this.selection.add(holdingID);
		}
		else this.selection.clear();
		this.selectionChanged();
	}

	private applySelected(selections:(Set<number> | number[]) = this.selection):void
	{
		if (this.busy) return;
		if (!this.checkPermission()) return;
		let selection = Array.from(selections).sort();

		callREST('REST/GetHoldingBay', {'holdingIDList': selection}, (data:any) =>
			{
				let changes = data.list as HoldingBayAssay[];
				if (changes.length == 1 && changes[0].deleteFlag)
				{
					let dlg = new PageHolding.ConfirmDeleteAssayDialog(changes[0],
							() => this.restApplyHoldingBay(selection, 'deleted'));
					dlg.show();
					return;
				}
				let dlg = new PageHolding.ApplyChangesDialog(changes,
						() => this.restApplyHoldingBay(selection, 'applied'));
				dlg.show();
			},
			() => alert('Could not retrieve assay changes from holding bay.'));
	}

	private static ConfirmDeleteAssayDialog = class extends BootstrapDialog
	{
		constructor(private holding:HoldingBayAssay, private confirmChanges:() => void)
		{
			super('Delete assay');
		}

		protected populateContent():void
		{
			const warning = $('<span class="glyphicon glyphicon-exclamation-sign"/>').css({'padding-right': '0.5em', 'color': 'red'});

			const assay = UniqueIdentifier.composeUniqueID(this.holding.currentUniqueID) || this.holding.assayID;
			const title = $('<div/>').appendTo(this.content);
			title.append(warning);
			title.append(`Assay <b>${assay}</b> will be permanently deleted from the database.`);

			let divFooter = $('<div/>').appendTo(this.content).css({'text-align': 'right', 'margin-top': '1em'});
			let btnCancel = $('<button class="btn btn-normal" data-dismiss="modal"/>').appendTo(divFooter);
			btnCancel.css({'margin-right': '0.5em'});
			btnCancel.text('Cancel');
			let btnDelete = $('<button class="btn btn-action"/>').appendTo(divFooter);
			btnDelete.text('Delete assay');
			btnDelete.click(() =>
			{
				this.confirmChanges();
				this.hide();
			});
		}
	};

	private static ApplyChangesDialog = class extends BootstrapDialog
	{
		private cellCSS = {'text-align': 'center', 'vertical-align': 'middle', 'padding': '0.25em 0.5em'};

		constructor(private holdings:HoldingBayAssay[], private confirmChanges:() => void)
		{
			super('Apply changes');
		}

		protected populateContent():void
		{
			const holdings = this.holdings.sort((a, b) => a.holdingID - b.holdingID);
			const title = $('<div/>').appendTo(this.content);
			title.append('<b>The following changes will be applied to the assay and saved into the database.</b>');

			const table = $('<table/>').appendTo(this.content).css('width', '100%');
			this.renderHeader(table);
			this.renderTableBody($('<tbody/>').appendTo(table), holdings);

			let divFooter = $('<div/>').appendTo(this.content).css({'text-align': 'right', 'margin-top': '1em'});
			let btnCancel = $('<button class="btn btn-normal" data-dismiss="modal"/>').appendTo(divFooter);
			btnCancel.css({'margin-right': '0.5em'});
			btnCancel.text('Cancel');
			let btnApply = $('<button class="btn btn-action"/>').appendTo(divFooter);
			btnApply.text('Apply');
			btnApply.click(() =>
			{
				this.confirmChanges();
				this.hide();
			});
		}

		private renderHeader(container:JQuery):void
		{
			const thead = $('<thead/>').appendTo(container);
			const tr = $('<tr/>').appendTo(thead);
			tr.css({'background-color': '#C0C0C0'});
			let headers = ['Holding ID', 'Assay ID', 'Unique ID', 'Changes'];
			for (let header of headers)
			{
				const th = $('<th/>').appendTo(tr);
				th.css({...this.cellCSS, 'background-color': 'grey', 'color': 'white'});
				th.text(header);
			}
		}

		private renderTableBody(tbody:JQuery, holdings:HoldingBayAssay[]):void
		{
			let parity = false;
			for (let holding of holdings)
			{
				parity = !parity;
				const tr = $('<tr/>').appendTo(tbody);
				tr.css('background-color', parity ? '#F0F0F0' : '#F8F8F8');
				$('<td/>').appendTo(tr).text(holding.holdingID).css(this.cellCSS);
				$('<td/>').appendTo(tr).text(holding.assayID).css(this.cellCSS);
				$('<td/>').appendTo(tr).text(holding.uniqueID || holding.currentUniqueID).css(this.cellCSS);
				const td = $('<td/>').appendTo(tr);
				const changes = this.renderHoldingBayConcise(holding) as string[];
				if (holding.deleteFlag)
				{
					td.append(changes.toString());
					continue;
				}
				td.append(changes.pop());
				while (changes.length > 0) td.append($('<br>')).append(changes.pop());
			}
		}

		private renderHoldingBayConcise(entry:HoldingBayAssay):(JQuery | string[])
		{
			if (entry.deleteFlag)
			{
				const result = $('<div/>').css({'background-color': '#f2dede', 'display': 'grid'});
				let div = $('<div/>').appendTo(result).css({'position': 'relative', 'color': 'red', 'padding': '0.2em'});
				div.append($('<span class="glyphicon glyphicon-exclamation-sign"/>').css({'padding-right': '0.5em', 'color': 'red'}));
				div.append(' Assay will be deleted');
				return result;
			}
			const txt = [];
			if (entry.text) txt.push('Text added');
			if (entry.uniqueID && (entry.currentUniqueID != entry.uniqueID))
			{
				let s = `Unique ID changed (${entry.currentUniqueID || '-'} to ${entry.uniqueID})`;
				txt.push(s);
			}
			const pluralize = (length:number, text:string, plural:string = 's'):string => length == 1 ? `1 ${text}` : `${length} ${text}${plural}`;
			if (Vec.arrayLength(entry.schemaBranches) > 0) txt.push(`${pluralize(entry.schemaBranches.length, 'schema branch', 'es')} added`);
			if (Vec.arrayLength(entry.added) > 0) txt.push(`${pluralize(entry.added.length, 'annotation')} added`);
			if (Vec.arrayLength(entry.removed) > 0) txt.push(`${pluralize(entry.removed.length, 'annotation')} removed`);
			return txt;
		}
	};

	private sortBy(field:string):void
	{
		this.sortDirection = field == this.lastSortField ? -this.sortDirection : 1;
		this.lastSortField = field;
		this.order.sort(this.cmpFunctions.get(field));
		if (this.tableNew) this.renderTable(this.tableNew, true);
		if (this.tableOld) this.renderTable(this.tableOld, false);
	}

	private prepareCmpFunctions():void
	{
		this.cmpFunctions = new Map();
		this.cmpFunctions.set('Holding ID', this.cmpWrapper(this.sortHoldingID));
		this.cmpFunctions.set('Assay ID', this.cmpWrapper(this.sortAssayID));
		this.cmpFunctions.set('Unique ID', this.cmpWrapper(this.sortUniqueID));
		this.cmpFunctions.set('Date', this.cmpWrapper(this.sortSubmissionTime));
		this.cmpFunctions.set('Curator', this.cmpWrapper(this.sortCurator));
	}

	private cmpWrapper(cmpFunction:HoldingCompare):(i1:number, i2:number) => number
	{
		return (i1:number, i2:number) =>
		{
			let h1 = this.holdingBayAssayStubMap[this.holdingIDList[i1]];
			let h2 = this.holdingBayAssayStubMap[this.holdingIDList[i2]];
			return this.sortDirection * cmpFunction(h1, h2);
		};
	}

	private sortHoldingID(h1:HoldingBayAssay, h2:HoldingBayAssay):number
	{
		return h1.holdingID - h2.holdingID;
	}
	private sortAssayID(h1:HoldingBayAssay, h2:HoldingBayAssay):number
	{
		return h1.assayID - h2.assayID;
	}
	private sortSubmissionTime(h1:HoldingBayAssay, h2:HoldingBayAssay):number
	{
		return h1.submissionTime - h2.submissionTime;
	}
	private sortCurator(h1:HoldingBayAssay, h2:HoldingBayAssay):number
	{
		let s1 = h1.curatorName || h1.curatorEmail;
		let s2 = h2.curatorName || h2.curatorEmail;
		if (!s1) return s2 ? 1 : 0;
		if (!s2) return -1;
		return s1.toLowerCase().localeCompare(s2.toLowerCase());
	}
	private sortUniqueID(h1:HoldingBayAssay, h2:HoldingBayAssay):number
	{
		let s1 = h1.uniqueID;
		let s2 = h2.uniqueID;
		if (!s1) return s2 ? 1 : 0;
		if (!s2) return -1;
		s1 = UniqueIdentifier.composeUniqueID(s1).toLowerCase();
		s2 = UniqueIdentifier.composeUniqueID(s2).toLowerCase();
		return s1.localeCompare(s2);
	}

	// request the deletion of holding bay entries
	private removeSelected(selections:(Set<number> | number[]) = this.selection):void
	{
		if (this.busy) return;
		if (!this.checkPermission()) return;
		let selection = Array.from(selections).sort();

		let msg = selection.length == 1
				? `Remove entry ${selection[0]} from the holding bay without applying it?`
				: `Remove the selected entries [${selection.join(', ')}] from the holding bay without applying them?`;
		if (!confirm(msg)) return;
		this.restApplyHoldingBay(selection, 'deleted');
	}

	private restApplyHoldingBay(selection:number[], type:ApplyTypes):void
	{
		this.busy = true;
		let params = {'applyList': selection};
		callREST('REST/ApplyHoldingBay', params,
			() =>
			{
				this.busy = false;
				this.feedbackSuccess(type, selection);
			},
			() =>
			{
				this.busy = false;
				if (type == 'applied') alert('Apply failed.');
				if (type == 'deleted') alert('Delete failed.');
			}
		);
	}

	private feedbackSuccess(label:string, selection:number[]):void
	{
		for (let idx of selection)
		{
			this.availableHoldingIDs.delete(idx);
			let tr = this.domEntries[idx];
			tr.css({'color': 'lightgrey'});
			tr.find('td:first').empty();
			tr.find('td:last').empty().append(label);
		}
		this.selection.clear();
		this.selectionChanged();
	}

	// returns false if the user is not allowed to do stuff
	private checkPermission():boolean
	{
		if (!Authentication.isLoggedIn())
		{
			alert('You need to login before proposing changes.');
			return false;
		}
		if (!Authentication.canApplyHolding())
		{
			alert('Insufficient user privileges to affect holding bay entries.');
			return false;
		}
		return true;
	}
}

/* EOF */ }
