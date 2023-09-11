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
	Embedded editor that allows editing of a single template, which may be new or existing.
*/

export interface TemplateEditorWidget
{
	divLine?:DOM;
	divHier?:DOM;
	isOpen:boolean;
	obj:TemplateGroup | TemplateAssignment | TemplateValue;
}

const CSS_NOTE = {'line-height': '1', 'padding': '0 0.5em 0.1em 0.5em', 'color': '#808080', 'background-color': '#F8F8F8', /*'border': '1px solid #D0D0D0',*/ 'border-radius': '0.2em'};
const CSS_NOTE_HOVER = {...CSS_NOTE, 'cursor': 'help'};
const CSS_NOTE_ERROR = {...CSS_NOTE, 'background-color': Theme.ISSUE_HTML};

export class TemplateEditor
{
	private divHeader:DOM;
	private divMain:DOM;
	private widgets:TemplateEditorWidget[] = [];

	private btnUndo:DOM;
	private btnRedo:DOM;
	private btnCancel:DOM;
	private btnSave:DOM;

	private callbackCancel:(editor?:TemplateEditor) => void;
	private callbackSave:(editor?:TemplateEditor) => void;

	private lastSavedKey:string = null;
	private undoStack:Template[] = [];
	private redoStack:Template[] = [];

	constructor(private template:Template, private isNew:boolean)
	{
		/*if (!template)
		{
			const ROOT:TemplateGroup = {'name': 'new template', 'descr': undefined, 'groupURI': undefined, 'canDuplicate': undefined, 'assignments': [], 'subGroups': []};
			this.template = {'schemaPrefix': null, 'root': ROOT};
		}*/
		this.lastSavedKey = isNew ? '!' : JSON.stringify(this.template);
	}

	public onCancel(callback:(editor?:TemplateEditor) => void):void
	{
		this.callbackCancel = callback;
	}
	public onSave(callback:(editor?:TemplateEditor) => void):void
	{
		this.callbackSave = callback;
	}

	public getTemplate():Template
	{
		return this.template;
	}

	public render(domParent:DOM):void
	{
		this.divHeader = dom('<div/>').appendTo(domParent).css({'display': 'flex', 'gap': '0.5em' /*, 'justify-content': 'flex-end'*/});
		let divTitle = dom('<div/>').appendTo(this.divHeader).css({'flex-grow': '1'});
		this.btnUndo = dom('<button class="btn btn-normal">Undo</button>').appendTo(this.divHeader);
		this.btnRedo = dom('<button class="btn btn-normal">Redo</button>').appendTo(this.divHeader);
		this.btnCancel = dom('<button class="btn btn-normal">Cancel</button>').appendTo(this.divHeader);
		this.btnSave = dom('<button class="btn btn-action">Save</button>').appendTo(this.divHeader);

		this.btnUndo.onClick(() => this.performUndo());
		this.btnRedo.onClick(() => this.performRedo());
		this.btnCancel.onClick(() => this.performCancel());
		this.btnSave.onClick(() => this.performSave());

		if (!this.isNew)
		{
			divTitle.appendText('Schema Prefix: ');
			let span = dom('<span/>').appendTo(divTitle).css({'padding': '0.2em', 'border': '1px solid #808080', 'background-color': '#C0C0C0'});
			span.setText(this.template.schemaPrefix);
		}
		else divTitle.setText('New Template');

		this.divMain = dom('<div/>').appendTo(domParent).css({'margin-top': '0.5em', 'padding': '0.5em', 'border': '1px solid black'});

		this.redrawHierarchy();
	}

	// ------------ private methods ------------

	private performUndo():void
	{
		if (this.undoStack.length == 0) return;
		this.redoStack.push(deepClone(this.template));
		this.template = deepClone(this.undoStack.pop());
		this.widgets = [];
		this.redrawHierarchy();
	}
	private performRedo():void
	{
		if (this.redoStack.length == 0) return;
		this.undoStack.push(deepClone(this.template));
		this.template = deepClone(this.redoStack.pop());
		this.widgets = [];
		this.redrawHierarchy();
	}
	private performCancel():void
	{
		let curKey = JSON.stringify(this.template);
		if (curKey != this.lastSavedKey)
		{
			if (!confirm('Template has been modified. Are you sure you wish to cancel?')) return;
		}
		this.callbackCancel(this);
	}
	private performSave():void
	{
		this.callbackSave(this);
		this.lastSavedKey = JSON.stringify(this.template);
	}

	private updateButtons():void
	{
		let canUndo = this.undoStack.length > 0;
		let canRedo = this.redoStack.length > 0;
		let modified = this.lastSavedKey != JSON.stringify(this.template);
		let isBlank = this.template.root.assignments.length == 0 && this.template.root.subGroups.length == 0;

		this.btnUndo.elInput.disabled = !canUndo;
		this.btnRedo.elInput.disabled = !canRedo;
		this.btnSave.elInput.disabled = isBlank || !modified || !this.template.schemaPrefix;
	}

	private stashUndo():void
	{
		this.undoStack.push(deepClone(this.template));
		while (this.undoStack.length > 10) this.undoStack.splice(0, 1);
		this.redoStack = [];
	}

	private redrawHierarchy():void
	{
		// remember which widgets were open prior to the redraw
		let stashOpen:any[] = null;
		if (this.widgets.length > 0)
		{
			stashOpen = [];
			for (let widget of this.widgets) if (widget.isOpen) stashOpen.push(widget.obj);
		}

		this.widgets = [];
		this.divMain.empty();
		this.renderGroup(this.divMain, this.template.root, -1, null, true, stashOpen);

		this.updateButtons();
	}

	private renderGroup(divParent:DOM, group:TemplateGroup, paridx:number, parentGroup:TemplateGroup, isRoot:boolean, stashOpen:any[]):void
	{
		let widget:TemplateEditorWidget = {'isOpen': !stashOpen, 'obj': group};
		if (stashOpen) for (let obj of stashOpen) if (group === obj) widget.isOpen = true;
		this.widgets.push(widget);

		let divLine = widget.divLine = dom('<div/>').appendTo(divParent).css({'display': 'flex', 'gap': '0.5em', 'margin-top': '0.2em'});

		if (!isRoot)
		{
			let divChk = dom('<div/>').appendTo(divLine);
			let img = dom('<img/>').appendTo(divChk);

			let fn = widget.isOpen ? 'branch_close.svg' : 'branch_open.svg';
			img.setAttr('src', restBaseURL + '/images/' + fn);
			img.css({'width': '20px', 'height': '20px', 'cursor': 'pointer'});
			img.onClick(() => this.toggleBranch(img, widget));
		}

		let divRight = dom('<div/>').appendTo(divLine).css({'flex-grow': '1'});
		let divDetails = dom('<div/>').appendTo(divRight).css({'display': 'flex', 'gap': '0.5em', 'align-items': 'center'});

		let divLabel = dom('<div/>').appendTo(divDetails).css({'display': 'inline-block', 'font-weight': 'bold'});
		divLabel.css({'padding': '0 0.5em 0.1em 0.5em', 'color': 'white', 'border-radius': '0.2em'});
		divLabel.css({'background-color': Theme.STRONG_HTML});
		divLabel.setText(group.name);

		let btnEdit = dom('<button class="btn btn-xs btn-action"/>').appendTo(divDetails);
		btnEdit.appendHTML('<span class="glyphicon glyphicon-pencil" style=\"height: 1.2em;"/>');
		btnEdit.onClick(() =>
		{
			let type = isRoot ? TemplateComponentDialogType.Root : TemplateComponentDialogType.Group;
			let dlg = new TemplateComponentDialog(type, group, null, parentGroup, this.pickCustomURI(group.groupURI));
			dlg.onApply(() => this.changeGroup(group, isRoot ? dlg.root : dlg.group));
			if (!isRoot) dlg.onDelete(() => this.deleteGroup(parentGroup, group));
			dlg.onMove(() => this.moveGroup(parentGroup, paridx));
			dlg.open();
		});

		let btnAddAssn = dom('<button class="btn btn-xs btn-normal"/>').appendTo(divDetails);
		btnAddAssn.appendHTML('<span class="glyphicon glyphicon-plus-sign"/>A');
		btnAddAssn.onClick(() =>
		{
			let type = TemplateComponentDialogType.Assignment;
			let dlg = new TemplateComponentDialog(type, null, null, group, this.pickCustomURI(null));
			dlg.onApply(() => this.appendAssignment(group, dlg.assn));
			dlg.open();
		});

		let btnAddGroup = dom('<button class="btn btn-xs btn-normal"/>').appendTo(divDetails);
		btnAddGroup.appendHTML('<span class="glyphicon glyphicon-plus-sign"/>G');
		btnAddGroup.onClick(() =>
		{
			let type = TemplateComponentDialogType.Group;
			let dlg = new TemplateComponentDialog(type, null, null, group, this.pickCustomURI(null));
			dlg.onApply(() => this.appendGroup(group, dlg.group));
			dlg.open();
		});

		if (group.groupURI)
		{
			let divURI = dom('<div/>').appendTo(divDetails).css(CSS_NOTE_HOVER);
			divURI.setText('uri');
			let html = escapeHTML(group.groupURI) + Popover.CACHE_DESCR_CODE;
			Popover.hover(divURI, null, html);
		}

		if (parentGroup)
		{
			if (!group.groupURI)
			{
				dom('<div/>').appendTo(divDetails).css(CSS_NOTE_ERROR).setText('missing group URI');
			}
			else
			{
				let count = 0;
				for (let look of parentGroup.subGroups) if (look.groupURI == group.groupURI) count++;
				if (count >= 2) dom('<div/>').appendTo(divDetails).css(CSS_NOTE_ERROR).setText('duplicate group URI');
			}
		}

		if (group.descr)
		{
			let divDescr = dom('<div/>').appendTo(divDetails).css(CSS_NOTE_HOVER);
			divDescr.setText('descr');
			let html = escapeHTML(group.descr);
			Popover.hover(divDescr, null, html);
		}

		if (group.canDuplicate)
		{
			let divDup = dom('<div/>').appendTo(divDetails).css(CSS_NOTE);
			divDup.setText('duplicable');
		}

		let divHier = widget.divHier = dom('<div/>').appendTo(divParent).css({'margin-left': '1em'});
		divHier.setCSS('display', widget.isOpen ? 'block' : 'none');

		for (let n = 0; n < group.assignments.length; n++) this.renderAssignment(divHier, group.assignments[n], n, group, stashOpen);
		for (let n = 0; n < group.subGroups.length; n++) this.renderGroup(divHier, group.subGroups[n], n, group, false, stashOpen);
	}

	private renderAssignment(divParent:DOM, assn:TemplateAssignment, paridx:number, parentGroup:TemplateGroup, stashOpen:any[]):void
	{
		let widget:TemplateEditorWidget = {'isOpen': false, 'obj': assn};
		if (stashOpen) for (let obj of stashOpen) if (assn === obj) widget.isOpen = true;
		this.widgets.push(widget);

		let divLine = widget.divLine = dom('<div/>').appendTo(divParent).css({'display': 'flex', 'gap': '0.5em', 'margin-top': '0.2em'});

		let divChk = dom('<div/>').appendTo(divLine);
		let img = dom('<img/>').appendTo(divChk);

		let fn = widget.isOpen ? 'branch_close.svg' : 'branch_open.svg';
		img.setAttr('src', restBaseURL + '/images/' + fn);
		img.css({'width': '20px', 'height': '20px', 'cursor': 'pointer'});
		img.onClick(() => this.toggleBranch(img, widget));

		let divRight = dom('<div/>').appendTo(divLine).css({'flex-grow': '1'});
		let divDetails = dom('<div/>').appendTo(divRight).css({'display': 'flex', 'gap': '0.5em', 'align-items': 'center'});

		let divLabel = dom('<div/>').appendTo(divDetails).css({'font-weight': 'bold'});
		divLabel.css({'padding': '0 0.5em 0.1em 0.5em', 'border-radius': '0.2em'});
		divLabel.css({'background-color': Theme.WEAK_HTML});
		divLabel.setText(assn.name);

		let btnEdit = dom('<button class="btn btn-xs btn-action"/>').appendTo(divDetails);
		btnEdit.appendHTML('<span class="glyphicon glyphicon-pencil" style=\"height: 1.2em;"/>');
		btnEdit.onClick(() =>
		{
			let dlg = new TemplateComponentDialog(TemplateComponentDialogType.Assignment, assn, null, parentGroup, this.pickCustomURI(assn.propURI));
			dlg.onApply(() => this.changeAssignment(assn, dlg.assn));
			dlg.onDelete(() => this.deleteAssignment(parentGroup, assn));
			dlg.onMove(() => this.moveAssignment(parentGroup, paridx));
			dlg.open();
		});

		if (assn.suggestions == SuggestionType.Full || assn.suggestions == SuggestionType.Disabled)
		{
			let btnTree = dom('<button class="btn btn-xs btn-action"/>').appendTo(divDetails);
			btnTree.appendHTML('<span class="glyphicon glyphicon-tree-conifer" style=\"height: 1.2em;"/>');
			btnTree.onClick(() => this.openAssignmentTree(assn));
		}

		let btnAddValue = dom('<button class="btn btn-xs btn-normal"/>').appendTo(divDetails);
		btnAddValue.appendHTML('<span class="glyphicon glyphicon-plus-sign"/>V');
		btnAddValue.onClick(() =>
		{
			let type = TemplateComponentDialogType.Value;
			let dlg = new TemplateComponentDialog(type, null, assn, parentGroup, this.pickCustomURI(null));
			dlg.onApply(() => this.appendValue(assn, dlg.value));
			dlg.open();
		});

		if (assn.propURI)
		{
			let divURI = dom('<div/>').appendTo(divDetails).css(CSS_NOTE_HOVER);
			divURI.setText('uri');
			let html = escapeHTML(assn.propURI) + Popover.CACHE_DESCR_CODE;
			Popover.hover(divURI, null, html);
		}

		if (!assn.propURI)
		{
			dom('<div/>').appendTo(divDetails).css(CSS_NOTE_ERROR).setText('missing property URI');
		}
		else
		{
			let count = 0;
			for (let look of parentGroup.assignments) if (look.propURI == assn.propURI) count++;
			if (count >= 2) dom('<div/>').appendTo(divDetails).css(CSS_NOTE_ERROR).setText('duplicate property URI');
		}

		if (assn.suggestions == SuggestionType.Full || assn.suggestions == SuggestionType.Disabled)
		{
			let any = false;
			let want = [TemplateValueSpecification.Item, TemplateValueSpecification.WholeBranch, TemplateValueSpecification.Container];
			for (let value of assn.values) if (want.includes(value.spec)) {any = true; break;}
			if (!any) dom('<div/>').appendTo(divDetails).css(CSS_NOTE_ERROR).setText('empty');
		}

		if (assn.descr)
		{
			let divDescr = dom('<div/>').appendTo(divDetails).css(CSS_NOTE_HOVER);
			divDescr.setText('descr');
			let html = escapeHTML(assn.descr);
			Popover.hover(divDescr, null, html);
		}

		if (assn.suggestions)
		{
			let divSugg = dom('<div/>').appendTo(divDetails).css(CSS_NOTE);
			divSugg.setText(assn.suggestions);
		}

		if (assn.mandatory)
		{
			let divSugg = dom('<div/>').appendTo(divDetails).css(CSS_NOTE);
			divSugg.setText('mandatory');
		}

		let divHier = widget.divHier = dom('<div/>').appendTo(divParent).css({'margin-left': '2.5em'});
		divHier.setCSS('display', widget.isOpen ? 'block' : 'none');

		for (let n = 0; n < assn.values.length; n++) this.renderValue(divHier, assn.values[n], n, assn, parentGroup);
	}

	private renderValue(divParent:DOM, value:TemplateValue, paridx:number, parentAssn:TemplateAssignment, parentGroup:TemplateGroup):void
	{
		let widget:TemplateEditorWidget = {'isOpen': false, 'obj': value};
		this.widgets.push(widget);

		let divLine = widget.divLine = dom('<div/>').appendTo(divParent).css({'display': 'flex', 'gap': '0.5em', 'margin-top': '0.2em'});

		let divRight = dom('<div/>').appendTo(divLine).css({'flex-grow': '1'});
		let divDetails = dom('<div/>').appendTo(divRight).css({'display': 'flex', 'gap': '0.5em', 'align-items': 'center'});

		let divLabel = dom('<div/>').appendTo(divDetails).css({'display': 'inline-block'/*, 'font-weight': 'bold'*/});
		divLabel.css({'padding': '0 0.5em 0.1em 0.5em', 'border-radius': '0.2em'});
		divLabel.css({'background-color': '#F8F8F8', 'border': '1px solid #C0C0C0'});
		divLabel.setText(value.name);

		let btnEdit = dom('<button class="btn btn-xs btn-normal"/>').appendTo(divDetails);
		btnEdit.appendHTML('<span class="glyphicon glyphicon-pencil" style=\"height: 1.2em;"/>');
		btnEdit.onClick(() =>
		{
			let dlg = new TemplateComponentDialog(TemplateComponentDialogType.Value, value, parentAssn, parentGroup, this.pickCustomURI(value.uri));
			dlg.onApply(() => this.changeValue(value, dlg.value));
			dlg.onDelete(() => this.deleteValue(parentAssn, value));
			dlg.onMove(() => this.moveValue(parentAssn, paridx));
			dlg.open();
		});

		if (value.uri)
		{
			let divURI = dom('<div/>').appendTo(divDetails).css(CSS_NOTE_HOVER);
			divURI.setText('uri');
			let html = escapeHTML(value.uri) + Popover.CACHE_DESCR_CODE;
			Popover.hover(divURI, null, html);
		}

		if (value.descr)
		{
			let divDescr = dom('<div/>').appendTo(divDetails).css(CSS_NOTE_HOVER);
			divDescr.setText('descr');
			let html = escapeHTML(value.descr);
			Popover.hover(divDescr, null, html);
		}

		if (value.spec)
		{
			let divSpec = dom('<div/>').appendTo(divDetails).css(CSS_NOTE);
			// TODO: remap to more meaningful notes
			divSpec.setText(value.spec);
		}
	}

	private toggleBranch(img:DOM, widget:TemplateEditorWidget):void
	{
		widget.isOpen = !widget.isOpen;
		let fn = widget.isOpen ? 'branch_close.svg' : 'branch_open.svg';
		img.setAttr('src', restBaseURL + '/images/' + fn);
		widget.divHier.setCSS('display', widget.isOpen ? 'block' : 'none');
	}

	private changeGroup(oldGroup:TemplateGroup, newGroup:TemplateGroup):void
	{
		this.stashUndo();
		oldGroup.name = newGroup.name;
		oldGroup.descr = newGroup.descr;
		oldGroup.groupURI = newGroup.groupURI;
		oldGroup.canDuplicate = newGroup.canDuplicate;
		this.redrawHierarchy();
	}
 	private appendGroup(parentGroup:TemplateGroup, group:TemplateGroup):void
	{
		this.stashUndo();
		for (let widget of this.widgets) if (widget.obj === parentGroup) widget.isOpen = true;
		parentGroup.subGroups.push(group);
		this.redrawHierarchy();
	}
	private deleteGroup(parentGroup:TemplateGroup, oldGroup:TemplateGroup):void
	{
		this.stashUndo();
		for (let n = 0; n < parentGroup.subGroups.length; n++) if (parentGroup.subGroups[n] === oldGroup)
		{
			parentGroup.subGroups.splice(n, 1);
			break;
		}
		this.redrawHierarchy();
	}
	private moveGroup(parentGroup:TemplateGroup, paridx:number):void
	{
		this.stashUndo();
		let sz = parentGroup.subGroups.length;
		let msg = `Group is currently at position #${paridx + 1} of ${sz}. Enter the new position for it:`;
		let newidx = (parseInt(prompt(msg, (paridx + 1).toString())) || 0) - 1;
		if (newidx < 0 || newidx >= sz || newidx == paridx) return;

		parentGroup.subGroups.splice(newidx + (newidx > paridx ? 1 : 0), 0, parentGroup.subGroups[paridx]);
		parentGroup.subGroups.splice(paridx + (newidx < paridx ? 1 : 0), 1);
		this.redrawHierarchy();
	}
	private changeAssignment(oldAssn:TemplateAssignment, newAssn:TemplateAssignment):void
	{
		this.stashUndo();
		oldAssn.name = newAssn.name;
		oldAssn.descr = newAssn.descr;
		oldAssn.propURI = newAssn.propURI;
		oldAssn.suggestions = newAssn.suggestions;
		oldAssn.mandatory = newAssn.mandatory;
		this.redrawHierarchy();
	}
 	private appendAssignment(parentGroup:TemplateGroup, assn:TemplateAssignment):void
	{
		this.stashUndo();
		for (let widget of this.widgets) if (widget.obj === parentGroup) widget.isOpen = true;
		parentGroup.assignments.push(assn);
		this.redrawHierarchy();
	}
	private deleteAssignment(parentGroup:TemplateGroup, oldAssn:TemplateAssignment):void
	{
		this.stashUndo();
		for (let n = 0; n < parentGroup.assignments.length; n++) if (parentGroup.assignments[n] === oldAssn)
		{
			parentGroup.assignments.splice(n, 1);
			break;
		}
		this.redrawHierarchy();
	}
	private moveAssignment(parentGroup:TemplateGroup, paridx:number):void
	{
		this.stashUndo();
		let sz = parentGroup.assignments.length;
		let msg = `Assignment is currently at position #${paridx + 1} of ${sz}. Enter the new position for it:`;
		let newidx = (parseInt(prompt(msg, (paridx + 1).toString())) || 0) - 1;
		if (newidx < 0 || newidx >= sz || newidx == paridx) return;

		parentGroup.assignments.splice(newidx + (newidx > paridx ? 1 : 0), 0, parentGroup.assignments[paridx]);
		parentGroup.assignments.splice(paridx + (newidx < paridx ? 1 : 0), 1);
		this.redrawHierarchy();
	}
	private changeValue(oldValue:TemplateValue, newValue:TemplateValue):void
	{
		this.stashUndo();
		oldValue.uri = newValue.uri;
		oldValue.name = newValue.name;
		oldValue.descr = newValue.descr;
		oldValue.altLabels = newValue.altLabels;
		oldValue.externalURLs = newValue.externalURLs;
		oldValue.spec = newValue.spec;
		oldValue.parentURI = newValue.parentURI;
		this.redrawHierarchy();
	}
 	private appendValue(parentAssn:TemplateAssignment, value:TemplateValue):void
	{
		this.stashUndo();
		for (let widget of this.widgets) if (widget.obj === parentAssn) widget.isOpen = true;
		parentAssn.values.push(value);
		this.redrawHierarchy();
	}
	private deleteValue(parentAssn:TemplateAssignment, oldValue:TemplateValue):void
	{
		this.stashUndo();
		for (let n = 0; n < parentAssn.values.length; n++) if (parentAssn.values[n] === oldValue)
		{
			parentAssn.values.splice(n, 1);
			break;
		}
		this.redrawHierarchy();
	}
	private moveValue(parentAssn:TemplateAssignment, paridx:number):void
	{
		this.stashUndo();
		let sz = parentAssn.values.length;
		let msg = `Value is currently at position #${paridx + 1} of ${sz}. Enter the new position for it:`;
		let newidx = (parseInt(prompt(msg, (paridx + 1).toString())) || 0) - 1;
		if (newidx < 0 || newidx >= sz || newidx == paridx) return;

		parentAssn.values.splice(newidx + (newidx > paridx ? 1 : 0), 0, parentAssn.values[paridx]);
		parentAssn.values.splice(paridx + (newidx < paridx ? 1 : 0), 1);
		this.redrawHierarchy();
	}

	// returns a localised URI that's either manufactured, or current thing if that's what it already is
	private pickCustomURI(uri:string):string
	{
		if (uri && uri.startsWith(this.template.schemaPrefix)) return uri; // suggest the current one
		return new Schema(this.template).customURI();
	}

	// opens a view which shows the whole schema's computed value tree
	private openAssignmentTree(assn:TemplateAssignment):void
	{
		let dlg = new PickOntologyDialog('schema');
		dlg.widget.assn = assn;
		dlg.widget.openDepth = 1;
		dlg.onButtons((div) =>
		{
			//let btnPaste = dom('<button class="btn btn-xs btn-normal"/>').prependTo(div).css({'margin-top': '-0.5em', 'margin-right': '3em'});
			//btnPaste.appendHTML('<span class="glyphicon glyphicon-paste" style=\"height: 1.2em;"/>');
			let btnPaste = dom('<button class="btn btn-normal">Paste</button>').prependTo(div);
			btnPaste.onClick(() =>
			{
				dlg.close();
				this.openPasteDialog(null, assn);
			});
		});
		dlg.widget.onDecorate((branch, div) =>
		{
			let btnAppend = dom('<button class="btn btn-xs btn-action"/>').appendTo(div);
			btnAppend.appendHTML('<span class="glyphicon glyphicon-arrow-down" style=\"height: 1.2em;"/>');
			btnAppend.onClick(() =>
			{
				dlg.close();
				this.appendNewItem(assn, branch);
			});

			let btnModify = dom('<button class="btn btn-xs btn-action"/>').appendTo(div);
			btnModify.appendHTML('<span class="glyphicon glyphicon-pencil" style=\"height: 1.2em;"/>');
			btnModify.onClick(() =>
			{
				dlg.close();
				this.appendModifyItem(assn, branch);
			});

			let btnCopy = dom('<button class="btn btn-xs btn-normal"/>').appendTo(div);
			btnCopy.appendHTML('<span class="glyphicon glyphicon-copy" style=\"height: 1.2em;"/>');
			btnCopy.onClick(() =>
			{
				let str = this.formatBranch(branch);
				copyToClipboard(str, div.elHTML);
			});

			let btnPaste = dom('<button class="btn btn-xs btn-normal"/>').appendTo(div);
			btnPaste.appendHTML('<span class="glyphicon glyphicon-paste" style=\"height: 1.2em;"/>');
			btnPaste.onClick(() =>
			{
				dlg.close();
				this.openPasteDialog(branch, assn);
			});
		});
		dlg.open();
	}

	// appending a child-item from the selected entry in the tree
	private appendNewItem(assn:TemplateAssignment, branch:OntologyBranch):void
	{
		let type = TemplateComponentDialogType.Value;
		let customURI = this.pickCustomURI(null);
		let value:TemplateValue = {'uri': customURI, 'name': '', 'spec': TemplateValueSpecification.Item, 'parentURI': branch.uri};
		let dlg = new TemplateComponentDialog(type, value, assn, null, customURI);
		dlg.onApply(() => this.appendValue(assn, dlg.value));
		dlg.open();
	}

	// appending an item value which is an opportunity to modify
	private appendModifyItem(assn:TemplateAssignment, branch:OntologyBranch):void
	{
		let type = TemplateComponentDialogType.Value;
		let value:TemplateValue = {'uri': branch.uri, 'name': branch.label, 'spec': TemplateValueSpecification.Item};
		let dlg = new TemplateComponentDialog(type, value, assn, null, this.pickCustomURI(null));
		dlg.onApply(() => this.appendValue(assn, dlg.value));
		dlg.open();
	}

	// retain just the relevant part of a branch hierarchy then turn it into a string
	private formatBranch(branch:OntologyBranch):string
	{
		let formulate = (branch:OntologyBranch):OntologyBranch =>
		{
			let form:OntologyBranch = {'uri': branch.uri, 'label': branch.label, 'descr': branch.descr};
			if (Vec.notBlank(branch.children)) form.children = branch.children.map((child) => formulate(child));
			return form;
		};

		let jsonBranch = formulate(branch);
		return wmk.jsonPrettyPrint(jsonBranch);
	}

	// bring up a dialog that prompts for pasting in JSON-text
	private openPasteDialog(parentBranch:OntologyBranch, assn:TemplateAssignment):void
	{
		let dlg = new TemplatePasteValueDialog(parentBranch);
		dlg.onApply((valueList) =>
		{
			dlg.close();

			if (parentBranch) for (let value of valueList) if (!value.parentURI) value.parentURI = parentBranch.uri;

			this.stashUndo();
			assn.values.push(...valueList);
			this.redrawHierarchy();
		});
		dlg.open();
	}
}

/* EOF */ }
