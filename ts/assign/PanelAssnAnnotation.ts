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
	Assignment panel that holds the main annotation editing area - this is where the most important action takes place.
*/

export class PanelAssnAnnotation
{
	private divKey:JQuery;
	private keyIsOpen = false;

	private mainAssignments:JQuery;
	private btnInsertBranch:JQuery;
	private btnRemoveBranch:JQuery;
	private btnCopyAssay:JQuery;
	private btnRemoveAll:JQuery;

	// ------------ public methods ------------

	constructor(private delegate:AssignmentDelegate, private divColumn:JQuery)
	{
	}

	public render():void
	{
		this.divKey = $('<div></div>').appendTo(this.divColumn);
		this.updateKey();

		this.buildTemplateForm();
		this.buildEditingForms();
	}

	// template has changed or other major rebuild event triggered
	public rebuild():void
	{
		this.mainAssignments.empty();
		this.delegate.edit.renderAssignments(this.mainAssignments);
		this.delegate.edit.renderOrphans(this.mainAssignments);
		this.delegate.edit.fillAssignments();

		this.buildEditingForms();
	}

	// set display of various edit-dependent buttons
	public updateEditStatus():void
	{
		this.btnInsertBranch.css('display', this.delegate.actionHasInsertableBranch(null) ? 'inline-block' : 'none');
		this.btnRemoveBranch.css('display', Vec.arrayLength(this.delegate.assay.schemaBranches) > 0 ? 'inline-block' : 'none');

		this.btnRemoveAll.prop('disabled', !this.delegate.editMode);
		this.btnInsertBranch.prop('disabled', !this.delegate.editMode);
		this.btnRemoveBranch.prop('disabled', !this.delegate.editMode);
		this.delegate.edit.updateEditStatus();
	}

	// toggle the "key", which is the inline summary of what the various highlights mean
	public toggleKey(btn:JQuery, open:boolean = null):void
	{
		this.keyIsOpen = open != null ? open : !this.keyIsOpen;
		btn.toggleClass('btn-action', this.keyIsOpen);
		btn.toggleClass('btn-normal', !this.keyIsOpen);
		this.updateKey();
	}

	// ------------ private methods ------------

	// builds the "template form", which is the default editing mode without any data entry forms; this is always available as an option
	private buildTemplateForm():void
	{
		this.delegate.templateForm = $('<div></div>').appendTo(this.divColumn);
		this.mainAssignments = $('<p></p>').appendTo(this.delegate.templateForm);

		let flexButtons = $('<div></div>').appendTo(this.delegate.templateForm);
		flexButtons.css({'display': 'flex', 'width': '100%', 'padding': '0.3em'});

		this.btnInsertBranch = $('<button class="btn btn-normal"><span class="glyphicon glyphicon-plus-sign"></span></button>').appendTo(flexButtons);
		this.btnInsertBranch.css('display', this.delegate.actionHasInsertableBranch(null) ? 'inline-block' : 'none');
		this.btnInsertBranch.css({'margin-right': '0.5em'});
		this.btnInsertBranch.click(() => this.delegate.actionPickNewBranch());
		Popover.hover(domLegacy(this.btnInsertBranch), null, 'Pick a new template branch to graft on.');

		this.btnRemoveBranch = $('<button class="btn btn-normal"><span class="glyphicon glyphicon-minus-sign"></span></button>').appendTo(flexButtons);
		this.btnRemoveBranch.css('display', Vec.arrayLength(this.delegate.assay.schemaBranches) > 0 ? 'inline-block' : 'none');
		this.btnRemoveBranch.css({'margin-right': '0.5em'});
		this.btnRemoveBranch.click(() => this.delegate.actionRemoveBranch());
		Popover.hover(domLegacy(this.btnRemoveBranch), null, 'Remove one of the grafted-on branches.');

		$('<div/>').css('flex-grow', 1).appendTo(flexButtons);

		this.btnCopyAssay = $('<button class="btn btn-normal"></button>').appendTo(flexButtons);
		this.btnCopyAssay.append('<span class="glyphicon glyphicon-copy"></span> Copy Assay');
		this.btnCopyAssay.css({'margin-left': '0.5em'});
		this.btnCopyAssay.click(() => this.delegate.actionCopyAssayContent(null));

		this.btnRemoveAll = $('<button class="btn btn-normal"></button>').appendTo(flexButtons);
		this.btnRemoveAll.append('<span class="glyphicon glyphicon-trash"></span> Remove All Annotations');
		this.btnRemoveAll.css({'margin-left': '0.5em'});
		this.btnRemoveAll.prop('disabled', true);
		this.btnRemoveAll.click(() => this.delegate.actionRemoveAnnotations());

		this.delegate.templateForm.append('<div style="clear: both;"></div>');

		this.delegate.edit.renderAssignments(this.mainAssignments);
		this.delegate.edit.renderOrphans(this.mainAssignments);
		this.delegate.edit.updateClonedTerms();
		this.delegate.edit.fillAssignments();

		this.buildEditingForms();
	}

	// rebuilds the data entry editing forms, if any
	private buildEditingForms():void
	{
		for (let edit of this.delegate.editingForms)
		{
			edit.wasHidden();
			edit.dom.remove();
		}
		this.delegate.editingForms = [];

		for (let form of this.delegate.entryForms) if (form.schemaURIList.indexOf(this.delegate.assay.schemaURI) >= 0)
		{
			let edit = new EditEntryForm(form, this.delegate.schema, this.delegate.assay, this.delegate);
			edit.setEasterEggs(this.delegate.easterEggs);
			edit.updateClonedTerms();
			edit.build(this.divColumn);
			this.delegate.editingForms.push(edit);
		}

		if (!this.delegate.actionIsSelectedFormInitialized()) this.delegate.selectedForm = this.delegate.editingForms.length == 0 ? -1 : 0;
	}

	// shows a visual representation of what the rendering styles mean
	public updateKey():void
	{
		this.divKey.empty();
		if (!this.keyIsOpen) return;

		let divRight = $('<div></div>').appendTo(this.divKey);
		divRight.css('text-align', 'right');

		let span = $('<span></span>').appendTo(divRight);
		span.css({'display': 'inline-block', 'padding': '0', 'white-space': 'nowrap'});
		span.css({'border': '1px solid #96CAFF', 'box-shadow': '0 0 5px rgba(66,77,88,0.1)', 'background-color': '#F8FCFF'});

		$('<p style="margin: 0; color: white; text-align: center;">Annotation key</p>').appendTo(span).css('background-color', Theme.STRONG_HTML);

		$('<p style="margin: 0 0.25em 0 1em; font-weight: bold;">curated term</p>').appendTo(span);
		$('<p style="margin: 0 0.25em 0 1em; color: #808080;">suggested term</p>').appendTo(span);
		$('<p style="margin: 0 0.25em 0 1em;">"text term"</p>').appendTo(span);
		$('<p style="margin: 0 0.25em 0 1em; font-style: italic;">provisional term</p>').appendTo(span);

		if (this.delegate.clonedTerms.length > 0)
		{
			$('<p style="margin: 0 0.25em 0 1em; text-decoration: underline;">cloned term</p>').appendTo(span).css('color', /*Theme.STRONG_HTML*/ '#808080');
		}
		else if (this.delegate.easterEggs.length > 0)
		{
			$('<p style="margin: 0 0.25em 0 1em; text-decoration: underline;">correct easter egg</p>').appendTo(span).css('color', /*Theme.STRONG_HTML*/ '#808080');
			$('<p style="margin: 0 0.25em 0 1em; color: #808080; text-decoration: line-through;">unwanted easter egg</p>').appendTo(span);
		}
	}
}

/* EOF */ }
