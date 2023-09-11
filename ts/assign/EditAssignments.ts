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
	Editing of assay assignments, without any form entry template: this displays all of the content top-to-bottom based on the
	layout of the template, and is the always available method.
*/

export class EditAssignments extends DataEntry
{
	public groupClosed:Record<string, boolean> = {}; // locator to boolean
	public groupBlock:Record<string, JQuery> = {}; // locator to <div>
	public assnBlock:Record<string, JQuery> = {}; // locator to <table>
	public availGroups:Record<string, SchemaGroup> = {}; // list of available groups
	public groupNotApplicableButtons:JQuery[] = []; // buttons For Not Applicable assignments
	public groupToggleButtons:JQuery[] = []; // buttons For Not Applicable assignments

	private domTitleOrphan:JQuery = $();
	private domPropOrphan:JQuery = $();
	private domAssnOrphan:JQuery = $();

	private btnCollapseKey:JQuery = null;
	private schemaHierarchy:SchemaHierarchy = null;

	constructor(public schema:SchemaSummary, public assay:AssayDefinition, public delegate:AssignmentDelegate)
	{
		super(schema, assay, delegate);
		this.schemaHierarchy = new SchemaHierarchy(this.schema);
	}

	public replaceTemplate(schema:SchemaSummary):void
	{
		this.schema = schema;
		this.groupClosed = {};
		this.groupBlock = {};
		this.assnBlock = {};
		this.schemaHierarchy = new SchemaHierarchy(this.schema);
	}

	// create all the assignment boxes prior to filling them with content
	public renderAssignments(parent:JQuery):void
	{
		if (this.schema.groups.length > 2)
		{
			let headerDiv = $('<div/>').prependTo(parent);
			headerDiv.css('text-align', 'right');

			this.btnCollapseKey = $('<button class="btn btn-xs btn-normal"/>').prependTo(headerDiv);
			this.btnCollapseKey.css({'margin': '3px'});
			this.btnCollapseKey.append('<span>Collapse All</span>&nbsp;');
			this.btnCollapseKey.append('<span class="glyphicon glyphicon-backward" style="width: 0.9em; height: 1.2em;"/>');
			this.btnCollapseKey.click(() => this.collapseAllGroups());
		}

		for (let group of this.schema.groups) if (group.locator) this.availGroups[group.locator] = group;

		this.boxes = [];

		// render "group" definition above a given assignment, if applicable
		let renderGroupLines = (locator:string):void =>
		{
			let groups:SchemaGroup[] = [];
			if (locator.indexOf(':') < 0) return;

			let sequence = locator.split(':');
			sequence.pop();

			while (sequence.length > 0)
			{
				locator = sequence.join(':') + ':';
				let group = this.availGroups[locator];
				if (!group) break;
				groups.unshift(group);
				this.availGroups[locator] = null;
				sequence.pop();
			}

			for (let group of groups)
			{
				let div = $('<div/>').appendTo(parent);
				let locator = group.locator;
				this.groupBlock[locator] = div;
				let isGroupNA = this.shouldCollapseGroup(locator);
				this.groupClosed[locator] = isGroupNA;

				let glyph = this.groupClosed[locator] ? 'right' : 'down';

				let btnToggle = $('<button class="btn btn-xs btn-normal"/>').appendTo(div);
				btnToggle.append($('<span class="glyphicon glyphicon-chevron-' + glyph + '" style=\"height: 1.2em;"/>'));
				btnToggle.click(() =>
				{
					this.groupClosed[locator] = !this.groupClosed[locator];
					let glyph = this.groupClosed[locator] ? 'right' : 'down';
					btnToggle.empty();
					btnToggle.append($('<span class="glyphicon glyphicon-chevron-' + glyph + '" style=\"height: 1.2em;"/>'));
					this.updateBlockVisibility();
				});
				this.groupToggleButtons.push(btnToggle);

				div.append('&nbsp;');

				let blk = $('<font/>').appendTo(div).css({'font-weight': 'bold'});
				blk.text(group.name);

				let btnNotApplicableGroup = $('<button class="btn btn-xs btn-normal"/>').appendTo(div);
				btnNotApplicableGroup.css('float', 'right');
				btnNotApplicableGroup.css('margin-right', '5px');
				btnNotApplicableGroup.append($('<span class="glyphicon glyphicon-ban-circle" style=\"height: 1.2em;"/>'));
				this.groupNotApplicableButtons.push(btnNotApplicableGroup);
				btnNotApplicableGroup.click(() =>
				{
					let assignmentList = this.schemaHierarchy.findAssignmentList(group.locator, true);
					this.delegate.actionAssignNotApplicable(assignmentList);
				});

				let btnCopyGroup = $('<button class="btn btn-xs btn-normal"/>').appendTo(div);
				btnCopyGroup.css('float', 'right');
				btnCopyGroup.css('margin-right', '5px');
				btnCopyGroup.append($('<span class="glyphicon glyphicon-copy" style=\"height: 1.2em;"/>'));
				btnCopyGroup.click(() =>
				{
					let assignmentList = this.schemaHierarchy.findAssignmentList(group.locator, true);
					this.delegate.actionCopyAssayContent(assignmentList);
				});

				let groupNest = Vec.concat([group.groupURI], group.groupNest);
				let [rawGroup, dupidx] = TemplateManager.decomposeSuffixGroupURI(group.groupURI), multiplicity = 1;
				let rawNest = groupNest.slice(0);
				rawNest[0] = rawGroup;

				if (this.assay.schemaDuplication) for (let dupl of this.assay.schemaDuplication) if (sameGroupNest(rawNest, dupl.groupNest))
				{
					multiplicity = dupl.multiplicity;
					break;
				}

				if (multiplicity > 1) blk.append(' (' + dupidx + ')');

				if (this.delegate.editMode)
				{
					if (group.canDuplicate)
					{
						div.append('&nbsp;');
						let btnDuplicate = $('<button class="btn btn-xs btn-normal"/>').appendTo(div);
						btnDuplicate.append($('<span class="glyphicon glyphicon-asterisk" style=\"height: 1.2em;"/>'));
						btnDuplicate.click(() => this.delegate.actionDuplicateGroup(groupNest, true));
						Popover.hover(domLegacy(btnDuplicate), null, 'Duplicate the group');
					}
					if (multiplicity == 1 /* && at least one assignment...*/)
					{
						div.append('&nbsp;');
						let btnErase = $('<button class="btn btn-xs btn-normal"/>').appendTo(div);
						btnErase.append($('<span class="glyphicon glyphicon-erase" style=\"height: 1.2em;"/>'));
						btnErase.click(() => this.delegate.actionEraseGroup(groupNest));
						Popover.hover(domLegacy(btnErase), null, 'Erase group contents');
					}
					if (multiplicity > 1)
					{
						div.append('&nbsp;');
						let btnDelete = $('<button class="btn btn-xs btn-normal"/>').appendTo(div);
						btnDelete.append($('<span class="glyphicon glyphicon-remove-sign" style=\"height: 1.2em;"/>'));
						btnDelete.click(() => this.delegate.actionDeleteGroup(groupNest));
						Popover.hover(domLegacy(btnDelete), null, 'Remove duplicated group');
					}
					if (dupidx > 1)
					{
						div.append('&nbsp;');
						let btnMove = $('<button class="btn btn-xs btn-normal"/>').appendTo(div);
						btnMove.append($('<span class="glyphicon glyphicon-arrow-up" style=\"height: 1.2em;"/>'));
						btnMove.click(() => this.delegate.actionMoveGroup(groupNest, -1));
					}
					if (dupidx > 0 && dupidx < multiplicity)
					{
						div.append('&nbsp;');
						let btnMove = $('<button class="btn btn-xs btn-normal"/>').appendTo(div);
						btnMove.append($('<span class="glyphicon glyphicon-arrow-down" style=\"height: 1.2em;"/>'));
						btnMove.click(() => this.delegate.actionMoveGroup(groupNest, 1));
					}
					if (this.delegate.actionHasInsertableBranch(groupNest))
					{
						div.append('&nbsp;');
						let btnBranch = $('<button class="btn btn-xs btn-normal"/>').appendTo(div);
						btnBranch.append($('<span class="glyphicon glyphicon-plus-sign" style=\"height: 1.2em;"/>'));
						btnBranch.click(() => this.delegate.actionPickNewBranch(groupNest));
						Popover.hover(domLegacy(btnBranch), null, 'Insert new branch');
					}
				}

				// TODO: if this is a branch, offer a delete button, and possibly move up/down (like for multiplicity)

				let depth = 0;
				for (let n = 0; n < locator.length; n++) if (locator.charAt(n) == ':') depth++;
				div.css('margin-left', (0.5 * depth) + 'em');

				if (group.groupURI || group.descr)
				{
					let tip = group.descr;
					if (group.groupURI)
					{
						tip = 'Abbrev: <i>' + collapsePrefix(group.groupURI) + '</i><br>' + tip;
					}
					Popover.click(domLegacy(blk), group.name, tip);
				}
			}
		};

		this.domTitleOrphan = $();
		this.domPropOrphan = $();
		this.domAssnOrphan = $();

		// render a series of boxes, one for each assignment category
		let assignments = this.schema.assignments;
		for (let n = 0; n < assignments.length; n++)
		{
			let assn = assignments[n];
			const idx = n;

			renderGroupLines(assn.locator);

			let divAssn = $('<div class="flexbuttons"/>').appendTo(parent);
			divAssn.css('align-items', 'flex-start');
			divAssn.css('flex-wrap', 'nowrap');
			divAssn.css('justify-content', 'flex-start');
			this.assnBlock[assn.locator] = divAssn;

			let div1 = $('<div/>').appendTo(divAssn);
			div1.css('flex', '0 1 16em');
			div1.css('padding-top', '0.5em');
			div1.css('background-image', 'url(images/dot3.png)');
			div1.css('background-repeat', 'repeat-x');
			div1.css('background-position-y', '1.3em');

			let outspan = $('<span/>').appendTo(div1);
			outspan.css('background-color', 'white');
			let depth = 0;
			for (let i = 0; i < assn.locator.length; i++) if (assn.locator.charAt(i) == ':') depth++;
			outspan.css('padding-left', (0.5 * depth) + 'em');

			let span = $('<span/>').appendTo(outspan);
			span.css('background-color', 'white');
			span.css('padding', '0.3em');
			span.text(assn.name);

			let tip = '<div><b>Property</b>: ' + collapsePrefix(assn.propURI) + '</div>';
			if (assn.descr) tip += '<div align="left">' + escapeHTML(assn.descr) + '</div>';
			if (assn.mandatory) tip += '<div><i>Required term.</i></div>';
			Popover.click(domLegacy(outspan), assn.name, tip);

			let div2 = $('<div/>').appendTo(divAssn);
			div2.css({'flex': '0 0 auto', 'padding-top': '0.3em'});
			let divCheck = $('<div/>').appendTo(div2);

			let div3 = $('<div/>').appendTo(divAssn);
			div3.css({'flex': '1 1 14em', 'vertical-align': 'center', 'padding': '0.3em'});
			let divValues = $('<div/>').appendTo(div3);
			divValues.css('min-width', '14em');

			let divButton = $('<div/>').appendTo(div3);
			divButton.css({'align-self': 'center'});

			let box = new DataEntryBox(assn, idx, this.delegate, this);
			box.domProp = span;
			box.domCheck = divCheck;
			box.domAssn = divValues;
			box.domButton = divButton;
			this.boxes.push(box);

			div1.click((event:JQueryMouseEventObject) => {Popover.removeAllPopovers(); event.stopPropagation();});
			div2.click((event:JQueryMouseEventObject) => {Popover.removeAllPopovers(); event.stopPropagation();});
			div3.click(() =>
			{
				Popover.removeAllPopovers();
				if (this.delegate.editMode) this.actionOpen(box);
				event.stopPropagation();
			});
		}

		this.updateBlockVisibility();

		// install a click-closer on the parent widget to make it easier to dismiss the current open box
		parent.click(() => this.closeCurrentBox());
	}

	// if there needs to be an orphan box, create it
	public renderOrphans(parent:JQuery):void
	{
		if (this.enumerateOrphans().length == 0) return;

		let table = $('<table border="0" style="margin-top: 2px;"/>').appendTo(parent);
		let tr = $('<tr/>').appendTo(table);

		let td1 = $('<td/>').appendTo(tr);
		td1.css('vertical-align', 'center');
		td1.css('min-width', '13em');
		td1.css('background-image', 'url(images/dot3.png)');
		td1.css('background-repeat', 'repeat-x');
		td1.css('background-position', '0 50%');

		let span = $('<span/>').appendTo(td1);
		span.css('background-color', 'white');
		span.css('padding', '0.3em');
		span.html('<i>orphans</i>');

		Popover.click(domLegacy(span), 'Orphan Assignments',
					'Terms that were assigned within a different template, but do not have a place in this one.');

		let td2 = $('<td/>').appendTo(tr);
		td2.css('vertical-align', 'center');
		td2.css('min-width', '10em');
		td2.css('padding', '0.3em');

		this.domTitleOrphan = td1;
		this.domPropOrphan = span;
		this.domAssnOrphan = td2;
	}

	// using the template layout from the servlet, and the assignments for the current assay (if any), fills in the
	public fillAssignments():void
	{
		$('#btnRemoveAll').prop('disabled', !this.assay.annotations || this.assay.annotations.length == 0);
		super.fillAssignments();
		this.fillOrphanAnnotations();
	}

	public collapseAllGroups():void
	{
		for (let group of this.schema.groups)
		{
			this.groupClosed[group.locator] = true;
		}

		// Noticed chevrons were not being set correctly
		for (let button of this.groupToggleButtons)
		{
			button.empty();
			button.append($('<span class="glyphicon glyphicon-chevron-right" style=\"height: 1.2em;"/>'));
		}

		this.updateBlockVisibility();
	}

	// called when edit status changes: enable/disable buttons as necessary
	public updateEditStatus():void
	{
		for (let button of this.groupNotApplicableButtons)
		{
			if (this.delegate.editMode) button.css('display', 'block'); else button.css('display', 'none');
		}
	}

	// ------------ private methods ------------

	// lists the subset of annotations that are "orphans", i.e. their propURI/groupURI does not match anything in the template
	private enumerateOrphans():AssayAnnotation[]
	{
		let propGroupKeys = new Set<string>();
		for (let assn of this.schema.assignments) propGroupKeys.add(keyPropGroup(assn.propURI, assn.groupNest));
		let orphans:AssayAnnotation[] = [];
		for (let annot of this.assay.annotations)
			if (!propGroupKeys.has(keyPropGroup(annot.propURI, annot.groupNest))) orphans.push(annot);
		orphans.sort((o1, o2) => orBlank(o1.propLabel).localeCompare(o2.propLabel));
		return orphans;
	}

	// display any assignments that fell off the template, with deletion being the
	private fillOrphanAnnotations():void
	{
		let td = this.domAssnOrphan;
		td.empty();
		let divblk = $('<div/>').appendTo(td);
		divblk.addClass('annot-block-sel');

		let orphans = this.enumerateOrphans();
		if (orphans.length == 0)
		{
			divblk.html('<i>none</i>');
			return;
		}
		orphans.sort((a1:AssayAnnotation, a2:AssayAnnotation):number =>
		{
			let c = orBlank(a1.propLabel).localeCompare(orBlank(a2.propLabel));
			if (c != 0) return c;
			return orBlank(a1.valueLabel).localeCompare(orBlank(a2.valueLabel));
		});

		for (let annot of orphans)
		{
			let div = $('<div class="annot-conf"/>').appendTo(divblk);

			let blkProp = $('<font/>').appendTo(div);
			blkProp.css('color', '#1362B3');
			blkProp.css('padding', '0.3em');
			blkProp.append($(Popover.displayOntologyProp(annot).elHTML));

			let blkValue = $('<font/>').appendTo(div);
			blkValue.css('padding', '0.3em');
			blkValue.append($(Popover.displayOntologyValue(annot).elHTML));

			if (this.delegate.editMode)
			{
				div.append('&nbsp;');

				let btn = $('<button class="btn btn-xs btn-action"/>').appendTo(div);
				$('<span class="glyphicon glyphicon-remove" style=\"height: 1.2em;"/>').appendTo(btn);
				const propURI = annot.propURI, uri = annot.valueURI, label = annot.valueLabel;
				btn.click(() =>
				{
					if (uri)
						this.delegate.actionDeleteTerm(propURI, null, uri);
					else
						this.delegate.actionDeleteText(propURI, null, label);
				});
			}
		}

		divblk.children().each((idx:number, elem:Element):void =>
		{
			let div = $(elem);
			let first = idx == 0, last = idx == divblk.children().length - 1;

			if (first && last) div.css('border-radius', '3px');
			else if (first) div.css('border-radius', '3px 3px 0 0');
			else if (last) div.css('border-radius', '0 0 3px 3px');

			if (!first) div.css('border-top', 'none');
		});
	}

	// goes through groups/assignments and decides whether they should be visible or hidden, based on when the chevron is
	// clicked open or closed
	private updateBlockVisibility():void
	{
		let isVisible = (locator:string):boolean =>
		{
			locator = locator.substring(0, locator.lastIndexOf(':') + 1);
			while (locator.length > 0)
			{
				if (this.groupClosed[locator]) return false;
				locator = locator.substring(0, locator.lastIndexOf(':'));
				locator = locator.substring(0, locator.lastIndexOf(':') + 1);
			}
			return true;
		};

		for (let group of this.schema.groups)
		{
			let div = this.groupBlock[group.locator];
			if (!div) continue;
			let locator = group.locator.substring(0, group.locator.lastIndexOf(':'));
			div.css('display', isVisible(locator) ? 'block' : 'none');
		}
		for (let assn of this.schema.assignments)
		{
			let table = this.assnBlock[assn.locator];
			table.css('display', isVisible(assn.locator) ? 'flex' : 'none');
		}
	}

	// returns true if a group is comprised entirely of N/A, which makes it boring
	private shouldCollapseGroup(locator:string):boolean
	{
		for (let assn of this.schemaHierarchy.findAssignmentList(locator, true))
		{
			let hasNA = false;
			for (let annot of this.assay.annotations)
				if (samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest))
			{
				if (annot.valueURI != ABSENCE_NOTAPPLICABLE) return false;
				hasNA = true;
			}
			if (!hasNA) return false;
		}
		return true;
	}
}

/* EOF */ }
