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
	Editing of assay assignments, using a specific form to layout the contents. The form can be thought of as a window onto
	the template overall.
*/

interface EditEntryFormText
{
	block:string;
	domTranslit:JQuery;
}

export class EditEntryForm extends DataEntry
{
	public dom:JQuery = null;

	private tdMenu:JQuery; // the outer container for the menu item
	private divMenu:JQuery; // the floating container for the menu item
	private tdForm:JQuery; // container for the editing content
	private headings:JQuery[] = [];
	private textBlocks:EditEntryFormText[] = [];
	private firstScroll = true;
	private scrollHandler:() => void = null;
	private btnSubmit:JQuery = null;
	public editingButtons:JQuery[] = []; // buttons that should be visible only when editing
	private groupClosed:Record<string, boolean> = {}; // similar to edit assignments -- may want to move to data entry

	constructor(public form:EntryForm, public schema:SchemaSummary, public assay:AssayDefinition, public delegate:AssignmentDelegate)
	{
		super(schema, assay, delegate);
	}

	public replaceTemplate(schema:SchemaSummary):void
	{
		this.schema = schema;
	}

	// manufactures all of the content; after being called, the 'dom' property is defined
	public build(parent:JQuery):void
	{
		this.dom = $('<table/>').appendTo(parent);
		this.dom.css('margin', '0.3em');
		this.dom.attr('class', 'editingFormContainer');
		this.dom.attr('formType', this.form.name);

		let tr = $('<tr/>').appendTo(this.dom);

		this.tdMenu = $('<td valign="top"/>').appendTo(tr);
		this.tdMenu.click((event:JQueryMouseEventObject) => {Popover.removeAllPopovers(); event.stopPropagation();});

		tr.append('<td class="vborder"> </td>');
		this.tdForm = $('<td valign="top"/>').appendTo(tr);
		this.tdForm.css('padding-left', '0.3em');

		this.rebuildForm();

		this.divMenu = $('<div/>').appendTo(this.tdMenu);
		this.divMenu.css('padding-right', '0.3em');
		this.buildMenu(this.divMenu);

		this.fillAssignments();

		// install a click-closer on the parent widget to make it easier to dismiss the current open box
		parent.click(() => {if (this.currentOpen) this.actionClose(this.currentOpen);});
	}

	public updateSectionView(isClosed:boolean, chevron:JQuery, btnSection:JQuery, divContent:JQuery):void
	{
		if (isClosed)
		{
			chevron.addClass('glyphicon-chevron-right');
			chevron.removeClass('glyphicon-chevron-down');
			btnSection.addClass('btn-normal');
			btnSection.removeClass('btn-action');
		}
		else
		{
			chevron.addClass('glyphicon-chevron-down');
			chevron.removeClass('glyphicon-chevron-right');
			btnSection.addClass('btn-action');
			btnSection.removeClass('btn-normal');
		}
		divContent.css('display', isClosed ? 'none' : 'block');
	}

	// called when shown/hidden, as an opportunity to do extra setup or cleanup
	public wasShown():void
	{
		this.scrollHandler = () => this.scrollWindow();
		window.addEventListener('scroll', this.scrollHandler, false);
	}
	public wasHidden():void
	{
		if (this.scrollHandler) window.removeEventListener('scroll', this.scrollHandler);
	}

	public fillAssignments():void
	{
		super.fillAssignments();
		this.syncTransliterations();
	}

	public updateEditStatus():void
	{
		this.btnSubmit.prop('disabled', !this.delegate.editMode);
		let display = this.delegate.editMode ? 'inline-block' : 'none';
		for (let button of this.editingButtons) button.css('display', display);
	}

	// ------------ private methods ------------

	private buildMenu(parent:JQuery):void
	{
		let table = $('<table width="100%"/>').appendTo(parent);
		table.css('border', '1px solid #96CAFF');
		table.css('box-shadow', '0 0 5px rgba(66,77,88,0.1)');
		table.css('background-color', '#F8FCFF');
		table.css('padding', '0.5em');

		for (let n = 0; n < this.form.sections.length; n++)
		{
			let section = this.form.sections[n];
			let tr = $('<tr/>').appendTo(table), td = $('<td/>').appendTo(tr);
			//tr.css('background-color', ...); ... want to make the selection update while scrolling (?)
			td.css('padding', '0.5em');
			let href = $('<a href="#"/>').appendTo(td);
			href.css('white-space', 'nowrap');
			href.text(section.name);

			if (section.description) Popover.hover(domLegacy(href), null, escapeHTML(section.description));

			href.click(() =>
			{
				this.headings[n][0].scrollIntoView({'behavior': 'smooth', 'block': 'start'});
				return false;
			});
		}

		let divbtn = $('<div/>').appendTo(parent);
		divbtn.css('text-align', 'center');
		divbtn.css('padding-top', '0.5em');
		this.btnSubmit = $('<button class="btn btn-action"/>').appendTo(divbtn);
		this.btnSubmit.append('<span class="glyphicon glyphicon-upload"></span> Submit');
		this.btnSubmit.click(() => this.delegate.actionSubmitAssayChanges());
	}

	// recreates all the form content, whether during the initial setup, or a hard relayout
	private rebuildForm():void
	{
		this.tdForm.empty();
		this.headings = [];
		this.boxes = [];
		this.currentOpen = null;

		for (let n = 0; n < this.form.sections.length; n++)
		{
			let section = this.form.sections[n];
			section.locator = n + ':';

			let divFlex = $('<div/>').appendTo(this.tdForm).css({'display': 'flex', 'width': '100%'});
			divFlex.css({'align-items': 'center', 'margin-top': '0.5em', 'border-top': '2px solid #E6EDF2'});

			let divChevron = $('<div/>').appendTo(divFlex).css({'width': '2em', 'flex': '0 0', 'margin-right': '0.5em'});
			let btnSection = $('<button class="btn btn-xs btn-action"/>').appendTo(divChevron);
			let chevron = $('<span class="glyphicon glyphicon-chevron-down" style=\"height: 1.2em;"/>').appendTo(btnSection);

			let divHeading = $('<div/>').appendTo(divFlex).css({'flex': '1 1'});
			divHeading.css({'font-size': '1.2em', 'font-weight': '600', 'border-bottom': '1px solid #E6EDF2'});
			divHeading.html(section.name);
			this.headings.push(divHeading);

			let divButtons = $('<div/>').appendTo(divFlex).css({'text-align': 'right'});

			let btnNotApplicableGroup = $('<button class="btn btn-xs btn-normal"/>').appendTo(divButtons);
			btnNotApplicableGroup.css({'margin-left': '0.3em'});
			btnNotApplicableGroup.append($('<span class="glyphicon glyphicon-ban-circle" style=\"height: 1.2em;"></span>'));
			this.editingButtons.push(btnNotApplicableGroup);
			btnNotApplicableGroup.click(() =>
			{
				let entryFormHierarchy = new EntryFormHierarchy(this.form, this.schema);
				let assignmentList = entryFormHierarchy.findAssignmentList(section.locator, true);
				this.delegate.actionAssignNotApplicable(assignmentList);
			});

			let btnCopyGroup = $('<button class="btn btn-xs btn-normal"/>').appendTo(divButtons);
			btnCopyGroup.css({'margin-left': '0.3em'});
			btnCopyGroup.append($('<span class="glyphicon glyphicon-copy" style=\"height: 1.2em;"/>'));
			btnCopyGroup.click(() =>
			{
				let entryFormHierarchy = new EntryFormHierarchy(this.form, this.schema);
				let assignmentList = entryFormHierarchy.findAssignmentList(section.locator, true);
				this.delegate.actionCopyAssayContent(assignmentList);
			});

			let multiplicity = 1;

			if (section.duplicationGroup)
			{
				let groupNest = expandPrefixes(section.duplicationGroup);

				let btnDuplicateGroup = $('<button class="btn btn-xs btn-normal"/>').appendTo(divButtons);
				btnDuplicateGroup.css({'margin-left': '0.3em'});
				btnDuplicateGroup.append($('<span class="glyphicon glyphicon-asterisk" style=\"height: 1.2em;"/>'));
				this.editingButtons.push(btnDuplicateGroup);
				btnDuplicateGroup.click(() => this.delegate.actionDuplicateGroup(groupNest, false));
				Popover.hover(domLegacy(btnDuplicateGroup), null, 'Duplicate the group');

				for (let dupl of Vec.safeArray(this.assay.schemaDuplication)) if (sameGroupNest(groupNest, dupl.groupNest))
				{
					multiplicity = dupl.multiplicity;
					break;
				}
			}

			let divContent = $('<div/>').appendTo(this.tdForm);
			for (let n = 1; n <= multiplicity; n++)
			{
				let divItem = divContent;
				let dupidx = 0; // default: no duplication

				if (multiplicity > 1)
				{
					dupidx = n;
					let groupNest = TemplateManager.groupSuffix(expandPrefixes(section.duplicationGroup), dupidx);

					let flex = $('<div/>').appendTo(divContent).css({'display': 'flex', 'margin-top': '0.2em'});

					let divLabel = $('<div/>').appendTo(flex);
					divLabel.css({'flex-grow': '0', 'padding-right': '0.2em'});
					let spanLabel = $('<span/>').appendTo(divLabel).css({'background-color': Theme.WEAK_HTML, 'padding': '0.2em'});
					spanLabel.text(dupidx.toString());

					divItem = $('<div/>').appendTo(flex).css({'flex-grow': '1'});
					divItem.css({'border': `1px solid ${Theme.WEAK_HTML}`, 'border-radius': '3px', 'padding': '0.2em'});

					let divButtons = $('<div/>').appendTo(flex);
					divButtons.css({'flex-grow': '0', 'padding-left': '0.2em', 'white-space': 'nowrap'});

					let btnDelete = $('<button class="btn btn-xs btn-normal"></button>').appendTo(divButtons);
					btnDelete.append($('<span class="glyphicon glyphicon-remove-sign" style=\"height: 1.2em;"></span>'));
					this.editingButtons.push(btnDelete);
					btnDelete.click(() => this.delegate.actionDeleteGroup(groupNest));

					if (n > 1)
					{
						divButtons.append('<br/>');
						let btnMove = $('<button class="btn btn-xs btn-normal"></button>').appendTo(divButtons);
						btnMove.append($('<span class="glyphicon glyphicon-arrow-up" style=\"height: 1.2em;"></span>'));
						this.editingButtons.push(btnMove);
						btnMove.click(() => this.delegate.actionMoveGroup(groupNest, -1));
					}
					if (n < multiplicity)
					{
						divButtons.append('<br/>');
						let btnMove = $('<button class="btn btn-xs btn-normal"></button>').appendTo(divButtons);
						btnMove.append($('<span class="glyphicon glyphicon-arrow-down" style=\"height: 1.2em;"></span>'));
						this.editingButtons.push(btnMove);
						btnMove.click(() => this.delegate.actionMoveGroup(groupNest, 1));
					}
				}

				if (section.layout) for (let layout of section.layout) this.buildLayout(divItem, layout, dupidx);
				if (section.transliteration) this.buildTextContent(divItem, section, dupidx);
			}

			// decide if the group should be closed
			let isGroupNA = this.isGroupAllNotApplicable(section.locator);
			this.groupClosed[section.locator] = isGroupNA;

			// update the view if it is shown or not
			this.updateSectionView(this.groupClosed[section.locator], chevron, btnSection, divContent);

			btnSection.click(() =>
			{
				this.groupClosed[section.locator] = !this.groupClosed[section.locator];
				this.updateSectionView(this.groupClosed[section.locator], chevron, btnSection, divContent);
			});
		}
	}

	// returns true if every assignment in the group has an N/A annotation
	private isGroupAllNotApplicable(locator:string):boolean
	{
		let entryFormHierarchy = new EntryFormHierarchy(this.form, this.schema);
		let assignmentList = entryFormHierarchy.findAssignmentList(locator, true);
		let assignmentKeys = new Set<string>();
		for (let assn of assignmentList) assignmentKeys.add(keyPropGroup(assn.propURI, assn.groupNest));

		for (let annot of this.assay.annotations) if (annot.valueURI == ABSENCE_NOTAPPLICABLE)
			assignmentKeys.delete(keyPropGroup(annot.propURI, annot.groupNest));
		return assignmentKeys.size == 0;
	}

	private buildLayout(parent:JQuery, layout:EntryFormLayout, dupidx:number):void
	{
		if (!layout.type) console.log('Blank layout entry: ' + JSON.stringify(layout));
		else if (layout.type == EntryFormType.Table) this.buildTable(parent, layout, dupidx);
		else if (layout.type == EntryFormType.Row) this.buildRow(parent, layout, dupidx);
		else if (layout.type == EntryFormType.Cell) this.buildCell(parent, layout, dupidx);
		else console.log('Unknown layout type: "' + layout.type + '"');
	}

	private buildTable(parent:JQuery, layoutTable:EntryFormLayout, dupidx:number):void
	{
		let table = $('<table/>').appendTo(parent);
		if (layoutTable.layout) for (let row of layoutTable.layout)
		{
			if (row.type == EntryFormType.Row) this.buildRow(table, row, dupidx);
			else console.log('Form entry: tables can only contain rows.');
		}
	}

	private buildRow(parent:JQuery, layoutRow:EntryFormLayout, dupidx:number):void
	{
		let tr = $('<tr/>').appendTo(parent);
		if (layoutRow.layout) for (let cell of layoutRow.layout)
		{
			if (cell.type == EntryFormType.Cell) this.buildCell(tr, cell, dupidx);
			else console.log('Form entry: rows can only contain cells.');
		}
	}

	private buildCell(parent:JQuery, layoutCell:EntryFormLayout, dupidx:number):void
	{
		// special case: empty placeholder
		if (!layoutCell.label && !layoutCell.field)
		{
			parent.append('<td/>');
			return;
		}

		// see if the field refers to an assignment (if specified, it definitely should)
		let idx = -1;
		if (layoutCell.field)
		{
			let propURI = expandPrefix(layoutCell.field[0]);
			let groupNest = expandPrefixes(layoutCell.field.slice(1, layoutCell.field.length));
			if (dupidx > 0) groupNest = TemplateManager.groupSuffix(groupNest, dupidx);

			for (let n = 0; n < this.schema.assignments.length; n++)
			{
				let assn = this.schema.assignments[n];
				if (compatiblePropGroupNest(propURI, groupNest, assn.propURI, assn.groupNest)) {idx = n; break;}
			}

			if (idx < 0)
			{
				console.log('FORM PARSING failure, assignment not found (form: ' + this.form.name + ')');
				console.log('propURI=[' + propURI + '], groupNest=' + JSON.stringify(groupNest));
			}
		}

		// render label and/or field
		let style = {'text-align': 'left', 'vertical-align': 'top', 'padding-right': '0.5em'};
		if (layoutCell.label)
		{
			let td = $('<td/>').appendTo(parent).css(style);
			td.css('padding-top', '0.65em');
			td.css('white-space', 'nowrap');
			let span = $('<span/>').appendTo(td);
			span.text(layoutCell.label);

			if (idx >= 0)
			{
				let assn = this.schema.assignments[idx];
				let assnNameReformatted = assn.name.replace(/ /g, '_');
				let [title, content] = Popover.popoverOntologyTerm(assn.propURI, assn.name, assn.descr);
				Popover.click(domLegacy(span), title, content,
					{'schemaURI': this.schema.schemaURI, 'propURI': assn.propURI, 'groupNest': assn.groupNest});

				let attrLabelID = 'section_' + assnNameReformatted + '_label';
				span.attr('id', attrLabelID);
			}
		}
		if (layoutCell.field)
		{
			let assn = this.schema.assignments[idx];
			let assnNameReformatted = assn.name.replace(/ /g, '_');

			let td = $('<td/>').appendTo(parent).css(style);
			let attrFieldId = 'section_' + assnNameReformatted + '_field';
			td.attr('id', attrFieldId);

			if (layoutCell.span > 1) td.attr('colspan', layoutCell.span);

			if (idx < 0)
			{
				console.log('Field not in template: ' + layoutCell.field);
				td.append('[?]');
			}
			else this.renderAssignment(td, idx);
		}
	}

	// creates a text block that can be used to display a transliteration that's tailored specifically to this section
	private buildTextContent(parent:JQuery, section:EntryFormSection, dupidx:number):void
	{
		// autogenerated (aka transliteration) first

		parent.append('<p style="padding: 0.5em 0 0 0; margin: 0;"><b>Autogenerated Text</b></p>');

		let divTranslit = $('<div/>').appendTo(parent);
		divTranslit.css('border', '1px solid #96CAFF');
		divTranslit.css('box-shadow', '0 0 5px rgba(66,77,88,0.1)');
		divTranslit.css('background-color', '#F8FCFF');
		divTranslit.css('padding', '0.5em');
		divTranslit.text('...');
		divTranslit.addClass('autogeneratedText');

		// TODO: use suffix...
		this.textBlocks.push({'block': section.transliteration, 'domTranslit': divTranslit});
	}

	// create all of the widgets for a box that corresponds to an assignment
	private renderAssignment(parent:JQuery, idx:number):void
	{
		let assn = this.schema.assignments[idx];

		let table = $('<table border="0" style="margin-top: 2px;"/>').appendTo(parent);
		let tr = $('<tr/>').appendTo(table);

		let td1 = $('<td/>').appendTo(tr);
		td1.css({'vertical-align': 'top', 'padding-top': '0.25em'});

		let td2 = $('<td/>').appendTo(tr);
		td2.css({'vertical-align': 'center', 'min-width': '10em', 'padding': '0.3em', 'white-space': 'nowrap'});

		let span1 = $('<span/>').appendTo(td2), span2 = $('<span/>').appendTo(td2);
		for (let s of [span1, span2]) s.css({'display': 'inline-block', 'white-space': 'normal'});
		span1.css('min-width', '14em');

		let box = new DataEntryBox(assn, idx, this.delegate, this);
		box.domCheck = td1;
		box.domAssn = span1;
		box.domButton = span2;
		this.boxes.push(box);

		td1.click((event:JQueryMouseEventObject) => {Popover.removeAllPopovers(); event.stopPropagation();});
		td2.click(() =>
		{
			Popover.removeAllPopovers();
			if (this.delegate.editMode) this.actionOpen(box);
			event.stopPropagation();
		});
	}

	// the window scrolled, possibly for the first time: make sure the menu floats
	private scrollWindow():void
	{
		if (this.firstScroll)
		{
			this.firstScroll = false;

			this.tdMenu.css('width', this.tdMenu.width() + 'px');
			this.divMenu.css('position', 'relative');
		}

		let delta = window.pageYOffset - this.tdMenu.offset().top;
		let deltaMax = this.tdMenu.height() - this.divMenu.height();
		delta = Math.max(0, Math.min(deltaMax, delta));

		this.divMenu.css('top', delta + 'px');
	}

	// any transliteration blocks that need an update will be refreshed
	private syncTransliterations():void
	{
		// note: might want to implement a watermark system so we can discard earlier results; without one we're dependent on them coming
		// back in the same order, which is not guaranteed; however there will need to be one watermark for each block...

		for (let tr of this.textBlocks)
		{
			// !! if nothing changed, continue

			let params =
			{
				'assayID': this.assay.assayID,
				'uniqueID': this.assay.uniqueID,
				'schemaURI': this.assay.schemaURI,
				'schemaBranches': this.assay.schemaBranches,
				'schemaDuplication': this.assay.schemaDuplication,
				'block': tr.block,
				'annotations': this.assay.annotations
			};
			callREST('REST/TransliterateAssay', params,
				(result:any) =>
				{
					let html:string = result.html;
					tr.domTranslit.html(html);

					// check watermark; remove from roster of changed...
				});
		}
	}
}

/* EOF */ }
