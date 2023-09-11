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
	Picking assignment(s): given a schema layout, allows individual assignments to be selected.
*/

interface AssignmentNode
{
	tr:JQuery;
	dom:JQuery; // clickable payload
	name:string;
	assn?:SchemaHierarchyAssignment; // defined if it's an assignment
	group?:SchemaHierarchyGroup; // defined if it's a group
}

export class PickAssignmentDialog extends BootstrapDialog
{
	public labelDone = 'Done';
	public picked:SchemaAssignment[] = []; // multiple: these are selected initially; !multiple: these are hidden
	public showAllNone = false; // if true, will create buttons for "check all/none"
	public preferredTypes:SuggestionType[] = null; // optional parameter to encourage only certain types
	public domHeader:JQuery = null; // optional content to insert into the header line
	public domPrepend:JQuery[] = []; // include pre-assignment
	public domAppend:JQuery[] = []; // include post-assignment
	public guessedAssn:SchemaAssignment = null; // optional assignment to bring attention to at the outset

	private checkboxes:JQuery[] = [];
	private checkboxRegistry:{[groupURI:string]:JQuery[]} = {};

	// used for search
	public allNodes:AssignmentNode[] = [];
	public currentSearch:string = '';
	public parentIdx:number[] = [];
	public collapsingList:CollapsingList;
	public btnCollapseKey:JQuery;
	public search:JQuery;

	public callbackDone:(assnlist:SchemaAssignment[]) => void = null;

	constructor(private schema:SchemaSummary, private multiple = false)
	{
		super(multiple ? 'Pick Assignments' : 'Pick Assignment');
		this.withCloseButton = !multiple;
	}

	// ------------ private methods ------------

	protected populateContent():void
	{
		if (!this.withCloseButton)
		{
			let btnDone = $('<button class="btn btn-action" data-dismiss="modal" aria-hidden="true"/>');
			btnDone.text(this.labelDone);
			btnDone.appendTo(this.areaTopRight);
			btnDone.click(() =>
			{
				if (this.multiple) this.updateChecked();
				if (this.callbackDone) this.callbackDone(this.picked);
			});
		}

		if (this.showAllNone)
		{
			let btnNone = $('<button class="btn btn-normal">None</button>').prependTo(this.areaTitle);
			btnNone.css('margin-right', '0.5em');
			btnNone.click(() => this.checkEverything(false));

			let btnAll = $('<button class="btn btn-normal">All</button>').prependTo(this.areaTitle);
			btnAll.click(() => this.checkEverything(true));
			btnAll.css('margin-right', '0.5em');
		}

		let searchContainer = $('<div/>').appendTo(this.areaTitle);
		searchContainer.css('float', 'right');
		this.search = $('<input type="text"/>').appendTo(searchContainer);
		this.search.css('width', '200px');
		this.search.keyup(() => this.collapsingList.applySearch(this.search.val().toString(), true));
		this.populateAssignments();

		if (this.guessedAssn) for (let node of this.allNodes) if (node.assn)
		{
			if (samePropGroupNest(this.guessedAssn.propURI, this.guessedAssn.groupNest, node.assn.propURI, node.assn.groupNest))
			{
				node.dom.css('background-color', '#E0E0FF');
				setTimeout(() =>
				{
					let offset = node.dom.offset().top;
					this.dlg.animate({'scrollTop': offset}, 500);
				}, 1000);
				break;
			}
		}
	}

	private resetDialog():void
	{
		this.search.val('');
		this.collapsingList.collapseTree(true);
		return;
	}

	// fill out the details
	private populateAssignments():void
	{
		let excludeKeys = new Set<string>();
		if (!this.multiple) for (let assn of this.picked) excludeKeys.add(keyPropGroup(assn.propURI, assn.groupNest));

		let headerDiv = $('<div/>').prependTo(this.content);
		headerDiv.css({'display': 'flex', 'justify-content': 'space-between'});
		let divLeft = $('<div/>').appendTo(headerDiv), divRight = $('<div/>').appendTo(headerDiv);

		if (this.domHeader) divLeft.append(this.domHeader);

		this.btnCollapseKey = $('<button class="btn btn-xs btn-normal"/>').appendTo(divRight);
		//this.btnCollapseKey.css({'margin': '3px'});
		this.btnCollapseKey.append('<span>Collapse All</span>&nbsp;');
		this.btnCollapseKey.append('<span class="glyphicon glyphicon-backward" style="width: 0.9em; height: 1.2em;"/>');
		this.btnCollapseKey.click(() => this.resetDialog());

		let table = $('<table/>').appendTo(this.content);

		let schema = this.schema;
		let hier = new SchemaHierarchy(schema);
		let toggleTree:JQuery[] = [];

		let renderAssn = (pidx:number, depth:number, assn:SchemaHierarchyAssignment, group:SchemaHierarchyGroup = null):void =>
		{
			if (excludeKeys.has(keyPropGroup(assn.propURI, assn.groupNest))) return;

			let tr = $('<tr/>').appendTo(table);

			let preferred = true;
			if (this.preferredTypes) preferred = this.preferredTypes.indexOf(assn.suggestions) >= 0;

			let td = $('<td/>').appendTo(tr);
			td.css({'vertical-align': 'middle', 'padding-left': depth + 'em'});

			let spacer = $('<span style="display: inline-block; width: 2.2em;"/>').appendTo(td);

			if (this.multiple)
			{
				let chk = $('<input type="checkbox"/>').appendTo(spacer);
				if (this.findAssignment(assn) >= 0) chk.prop('checked', true);
				this.checkboxes.push(chk);
				this.registerCheckbox(group, chk);
			}
			else
			{
				let img = $('<img/>').appendTo(spacer);
				img.attr('src', restBaseURL + '/images/branch_dot.svg');
				img.css({'width': '10px', 'height': '10px', 'cursor': 'default'});
				spacer.css({'padding-left': '5px'});
			}

			let span = $('<span/>').appendTo(td);
			span.css('cursor', 'pointer');
			if (!preferred) span.css('color', '#C0C0C0');
			span.hover(() => {span.css('text-decoration', 'underline'); span.css('color', '#1362B3');},
					   () => {span.css('text-decoration', 'none'); span.css('color', '#313A44');});
			span.text(assn.name);

			let tip = '<p><i>' + collapsePrefix(assn.propURI) + '</i></p>';
			if (assn.descr) tip += '<p>' + escapeHTML(assn.descr) + '</p>';
			Popover.hover(domLegacy(span), null, tip);

			const idx = assn.assnidx;
			span.click(() => this.pickedAssignment(idx));

			this.allNodes.push({'tr': tr, 'dom': span, 'name': assn.name, 'assn': assn});

			toggleTree.push(null);
			this.parentIdx.push(Math.max(pidx, -1));
		};

		let renderGroup = (pidx:number, depth:number, group:SchemaHierarchyGroup):void =>
		{
			if (group.parent != null)
			{
				let tr = $('<tr/>').appendTo(table);

				let tdContent = $('<td/>');
				tr.append(tdContent);
				tdContent.css('padding-left', depth + 'em');

				let toggle = $('<span/>').appendTo(tdContent).css('margin-right', '15px');
				tdContent.append('&nbsp;');

				let blk = $('<font style="text-decoration: underline;"/>').appendTo(tdContent);
				blk.text(group.name);

				if (group.descr)
				{
					let tip = group.descr;
					if (group.groupURI)
					{
						tip = 'Abbrev: <i>' + collapsePrefix(group.groupURI) + '</i><br>' + tip;
					}
					Popover.hover(domLegacy(blk), null, tip);
				}

				if (this.multiple)
				{
					let btnUnselectChildren = $('<button class="btn btn-xs btn-normal"/>').appendTo(this.areaTopRight);
					btnUnselectChildren.css({'margin-left': '0.25em', 'margin-right': '0em'});
					btnUnselectChildren.append('<span class="glyphicon glyphicon-thumbs-down"/>');
					btnUnselectChildren.click(() => this.toggleChildCheckboxes(group, false));

					let btnSelectChildren = $('<button class="btn btn-xs btn-normal"/>').appendTo(this.areaTopRight);
					btnSelectChildren.css({'margin-left': '0.75em', 'margin-right': '0em'});
					btnSelectChildren.append('<span class="glyphicon glyphicon-thumbs-up"/>');
					btnSelectChildren.click(() => this.toggleChildCheckboxes(group, true));

					tdContent.append(btnSelectChildren);
					tdContent.append(btnUnselectChildren);
				}

				// add the necessary information to create the toggle open/closed tree
				toggleTree.push(toggle);
				this.parentIdx.push(pidx);
				this.allNodes.push({'tr': tr, 'dom': blk, 'name': group.name, 'group': group});
			}

			let subpidx = this.allNodes.length - 1;
			for (let assn of group.assignments) renderAssn(subpidx, depth + 1, assn, group);
			for (let subgrp of group.subGroups) renderGroup(subpidx, depth + 1, subgrp);
		};

		for (let dom of this.domPrepend) $('<tr/>').appendTo(table).append(dom);
		renderGroup(-1, -1, hier.root);
		for (let dom of this.domAppend) $('<tr/>').appendTo(table).append(dom);
		this.collapsingList = new CollapsingList(this.allNodes.map((a) => a.tr), toggleTree, this.parentIdx, [], this.allNodes);
		this.collapsingList.manufacture();
	}

	private registerCheckbox(group:SchemaHierarchyGroup, checkbox:JQuery):void
	{
		let registeredCheckboxes:JQuery[] = [];

		if (group.groupURI in this.checkboxRegistry) registeredCheckboxes = this.checkboxRegistry[group.groupURI];
		else this.checkboxRegistry[group.groupURI] = registeredCheckboxes;

		registeredCheckboxes.push(checkbox);
		this.checkboxRegistry[group.groupURI] = registeredCheckboxes;
	}

	private lookupRegisteredCheckboxes(group:SchemaHierarchyGroup):JQuery[]
	{
		let returnList:JQuery[] = [];

		if (group.groupURI in this.checkboxRegistry) returnList = this.checkboxRegistry[group.groupURI];

		return returnList;
	}

	private toggleChildCheckboxes(group:SchemaHierarchyGroup, checked:boolean):void
	{
		let checkboxList = this.lookupRegisteredCheckboxes(group);

		for (let chk of checkboxList) chk.prop('checked', checked);
		for (let subgroup of group.subGroups) this.toggleChildCheckboxes(subgroup, checked);
	}

	// reassembles the checked list
	private updateChecked():void
	{
		this.picked = [];
		for (let n = 0; n < this.schema.assignments.length; n++) if (this.checkboxes[n].prop('checked'))
			this.picked.push(this.schema.assignments[n]);
	}

	// clicked on a specific assignment, rather than a checkbox
	private pickedAssignment(idx:number):void
	{
		if (this.multiple) this.updateChecked();
		let hit = this.schema.assignments[idx];
		this.hide();
		let results = this.multiple ? this.picked : [];
		if (this.findAssignment(hit) < 0) results.push(hit);
		if (this.callbackDone) this.callbackDone(results);
	}

	// returns the index of the matching assignment, or -1
	private findAssignment(assn:SchemaAssignment):number
	{
		for (let n = 0; n < this.picked.length; n++)
		{
			let p = this.picked[n];
			if (compatiblePropGroupNest(assn.propURI, assn.groupNest, p.propURI, p.groupNest)) return n;
		}
		return -1;
	}

	// change the state of all checkboxes
	private checkEverything(value:boolean):void
	{
		for (let chk of this.checkboxes) chk.prop('checked', value);
	}
}

/* EOF */ }
