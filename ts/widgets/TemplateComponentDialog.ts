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

///<reference path='BaseDialog.ts'/>

namespace BioAssayExpress /* BOF */ {

/*
	Dialog for editing individual components of a template (groups, assignments, values).
*/

export enum TemplateComponentDialogType
{
	Root,
	Group,
	Assignment,
	Value,
}

const EMPTY_GROUP:TemplateGroup = {'name': undefined, 'groupURI': undefined, 'canDuplicate': false, 'assignments': [], 'subGroups': []};
const EMPTY_ASSIGNMENT:TemplateAssignment = {'name': undefined, 'propURI': undefined, 'suggestions': SuggestionType.Full, 'mandatory': false, 'values': []};
const EMPTY_VALUE:TemplateValue = {'uri': undefined, 'name': undefined, 'spec': TemplateValueSpecification.Item};

export class TemplateComponentDialog extends BaseDialog
{
	// one of these is defined, depending on type
	public root:TemplateGroup = null;
	public group:TemplateGroup = null;
	public assn:TemplateAssignment = null;
	public value:TemplateValue = null;

	private isFresh:boolean;

	private callbackApply:(dlg?:TemplateComponentDialog) => void;
	private callbackDelete:(dlg?:TemplateComponentDialog) => void;
	private callbackMove:(dlg?:TemplateComponentDialog) => void;

	private btnApply:DOM;
	private mainVbox:DOM;

	private inputURI:DOM = null;
	private btnCustom:DOM = null;
	private inputName:DOM = null;
	private areaDescr:DOM = null;

	private inputSearch:DOM = null;
	private ontoWidget:PickOntologyWidget = null;

	private chkDuplicate:DOM = null;
	private chkMandatory:DOM = null;
	private areaAltLabels:DOM = null;
	private areaExternalURLs:DOM = null;
	private inputParentURI:DOM = null;

	private lastSearch = '';
	private lastResult = '';
	private searchResults:OntologySearch[] = [];
	private searchIndex = 0;

	constructor(private type:TemplateComponentDialogType, private initObj:TemplateGroup | TemplateAssignment | TemplateValue,
				private stackAssn:TemplateAssignment, private stackGroup:TemplateGroup, private customURI:string)
	{
		super();
		this.maxPortionWidth = 95;
		this.isFresh = !initObj;

		if (type == TemplateComponentDialogType.Root)
		{
			this.title = 'Edit Root';
			this.root = clone(initObj as TemplateGroup);
		}
		else if (type == TemplateComponentDialogType.Group)
		{
			this.title = this.isFresh ? 'Add Group' : 'Edit Group';
			this.group = clone(initObj as TemplateGroup || EMPTY_GROUP);
		}
		else if (type == TemplateComponentDialogType.Assignment)
		{
			this.title = this.isFresh ? 'Add Assignment' : 'Edit Assignment';
			this.assn = clone(initObj as TemplateAssignment || EMPTY_ASSIGNMENT);
		}
		else if (type == TemplateComponentDialogType.Value)
		{
			this.title = this.isFresh ? 'Add Value' : 'Edit Value';
			this.value = clone(initObj as TemplateValue || EMPTY_VALUE);
		}
	}

	public onApply(callback:(dlg?:TemplateComponentDialog) => void):void
	{
		this.callbackApply = callback;
	}

	public onDelete(callback:(dlg?:TemplateComponentDialog) => void):void
	{
		this.callbackDelete = callback;
	}

	public onMove(callback:(dlg?:TemplateComponentDialog) => void):void
	{
		this.callbackMove = callback;
	}

	// ------------ private methods ------------

	protected populate():void
	{
		super.populate();

		this.btnApply = dom('<button class="btn btn-action">Apply</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
		this.btnApply.onClick(() => this.applyChanges());

		if (this.type != TemplateComponentDialogType.Root && !this.isFresh)
		{
			let btnDelete = dom('<button class="btn btn-normal">Delete</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
			btnDelete.onClick(() => this.deleteComponent());
		}

		if (!this.isFresh)
		{
			if ((this.type == TemplateComponentDialogType.Group && this.stackGroup.subGroups.length > 1) ||
				(this.type == TemplateComponentDialogType.Assignment && this.stackGroup.assignments.length > 1) ||
				(this.type == TemplateComponentDialogType.Value && this.stackAssn.values.length > 1))
			{
				let btnMove = dom('<button class="btn btn-normal">Move</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
				btnMove.onClick(() => this.moveComponent());
			}
		}

		// !! todo: add "Copy" button as well?

		let btnCancel = dom('<button class="btn btn-normal">Cancel</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
		btnCancel.onClick(() => this.close());

		this.mainVbox = dom('<div/>').appendTo(this.domBody).css({'display': 'flex'});
		this.mainVbox.css({'flex-direction': 'column', 'align-items': 'stretch', 'gap': '0.5em', 'width': '90vw', 'max-height': 'calc(100vh - 10em)'});

		if (this.type == TemplateComponentDialogType.Root) this.populateRoot();
		else if (this.type == TemplateComponentDialogType.Group) this.populateGroup();
		else if (this.type == TemplateComponentDialogType.Assignment) this.populateAssignment();
		else if (this.type == TemplateComponentDialogType.Value) this.populateValue();

		this.updateStatus();

		this.installEscapeKey(false);
		
		if (this.inputURI)
		{
			if (this.inputURI.getValue() && !this.inputName.getValue())
				this.inputName.grabFocus();
			else
				this.inputURI.grabFocus();
		}
		else this.inputName.grabFocus();

		if (this.ontoWidget) this.ontoWidget.showSelected();
	}

	private applyChanges():void
	{
		this.callbackApply(this);
		this.close();
	}

	private deleteComponent():void
	{
		let thing = this.type == TemplateComponentDialogType.Group ? 'group' :
					this.type == TemplateComponentDialogType.Assignment ? 'assignment' :
					this.type == TemplateComponentDialogType.Value ? 'value' : '?';
		if (!confirm(`Delete this ${thing}?`)) return;
		this.callbackDelete(this);
		this.close();
	}

	private moveComponent():void
	{
		this.callbackMove(this);
		this.close();
	}

	private updateStatus():void
	{
		let same = false;
		if (!this.initObj) {}
		else if (this.type == TemplateComponentDialogType.Root)
		{
			let prev = this.initObj as TemplateGroup, cur = this.root;
			same = prev.name == cur.name && prev.descr == cur.descr;
		}
		else if (this.type == TemplateComponentDialogType.Group)
		{
			let prev = this.initObj as TemplateGroup, cur = this.group;
			same = prev.name == cur.name && prev.descr == cur.descr && prev.groupURI == cur.groupURI && prev.canDuplicate == cur.canDuplicate;
		}
		else if (this.type == TemplateComponentDialogType.Assignment)
		{
			let prev = this.initObj as TemplateAssignment, cur = this.assn;
			same = prev.name == cur.name && prev.descr == cur.descr && prev.propURI == cur.propURI &&
				   prev.suggestions == cur.suggestions && prev.mandatory == cur.mandatory;
		}
		else if (this.type == TemplateComponentDialogType.Value)
		{
			let prev = this.initObj as TemplateValue, cur = this.value;
			same = prev.uri == cur.uri && prev.name == cur.name && prev.descr == cur.descr &&
				   Vec.equals(prev.altLabels, cur.altLabels) && Vec.equals(prev.externalURLs, cur.externalURLs) &&
				   prev.spec == cur.spec && prev.parentURI == cur.parentURI;
		}
		this.btnApply.elInput.disabled = same;
	}

	private populateRoot():void
	{
		this.makeNameDescr();

		this.inputName.setValue(this.root.name);
		this.areaDescr.setValue(this.root.descr);
	}

	private populateGroup():void
	{
		let preSelected:PickOntologyWidgetPreSel[] = [];
		for (let group of this.stackGroup.subGroups) if (group.groupURI && group.groupURI != this.group.groupURI)
			preSelected.push({'uri': group.groupURI, 'include': true, 'andChildren': false});

		this.makeTermSelector('Group URI', 'property', this.group.groupURI, preSelected);
		this.makeNameDescr();

		this.ontoWidget.onPicked((branch) =>
		{
			(async () =>
			{
				let label = branch.label;
				if (label.startsWith('has ')) label = label.substring(4);
				else if (label.startsWith('is ')) label = label.substring(3);
				else if (label.startsWith('uses ')) label = label.substring(5);

				await this.ontoWidget.changeSelection(branch.uri);
				this.inputURI.setValue(this.group.groupURI = expandPrefix(branch.uri));
				this.inputName.setValue(this.group.name = label);
				this.areaDescr.setValue(this.group.descr = branch.descr);
				this.updateStatus();
			})();
		});
		this.btnCustom.onClick(() =>
		{
			this.inputURI.setValue(this.group.groupURI = this.customURI);
			this.updateStatus();
		});

		this.inputName.setValue(this.group.name);
		this.areaDescr.setValue(this.group.descr);

		let divRow = dom('<div/>').appendTo(this.mainVbox);

		this.chkDuplicate = dom('<input type="checkbox"/>');
		let label = dom('<label/>').appendTo(divRow);
		label.append(this.chkDuplicate);
		label.appendHTML(' <font style="font-weight: normal;">Can duplicate group</font>');
		this.chkDuplicate.elInput.checked = !!this.group.canDuplicate;
		this.chkDuplicate.onChange(() =>
		{
			this.group.canDuplicate = this.chkDuplicate.elInput.checked;
			this.updateStatus();
		});
	}

	private populateAssignment():void
	{
		let preSelected:PickOntologyWidgetPreSel[] = [];
		for (let assn of this.stackGroup.assignments) if (assn.propURI && assn.propURI != this.assn.propURI)
			preSelected.push({'uri': assn.propURI, 'include': true, 'andChildren': false});

		this.makeTermSelector('Property URI', 'property', this.assn.propURI, preSelected);
		this.makeNameDescr();

		this.ontoWidget.onPicked((branch) =>
		{
			(async () =>
			{
				let label = branch.label;
				if (label.startsWith('has ')) label = label.substring(4);
				else if (label.startsWith('is ')) label = label.substring(3);
				else if (label.startsWith('uses ')) label = label.substring(5);

				await this.ontoWidget.changeSelection(branch.uri);
				this.inputURI.setValue(this.assn.propURI = expandPrefix(branch.uri));
				this.inputName.setValue(this.assn.name = label);
				this.areaDescr.setValue(this.assn.descr = branch.descr);
				this.updateStatus();
			})();
		});
		this.btnCustom.onClick(() =>
		{
			this.inputURI.setValue(this.assn.propURI = this.customURI);
			this.updateStatus();
		});

		this.inputName.setValue(this.assn.name);
		this.areaDescr.setValue(this.assn.descr);

		let divRow = dom('<div/>').appendTo(this.mainVbox).css({'display': 'flex', 'gap': '0.5em', 'align-items': 'center'});

		const BUTTONS:[string, SuggestionType][] =
		[
			['Full', SuggestionType.Full],
			['Disabled', SuggestionType.Disabled],
			['Field', SuggestionType.Field],
			['URL', SuggestionType.URL],
			['ID', SuggestionType.ID],
			['String', SuggestionType.String],
			['Number', SuggestionType.Number],
			['Integer', SuggestionType.Integer],
			['Date', SuggestionType.Date],
		];
		dom('<div/>').appendTo(divRow).css({'font-weight': 'bold'});
		let divSuggest = dom('<div class="btn-group" data-toggle="buttons"/>').appendTo(divRow);
		for (let [label, value] of BUTTONS)
		{
			let lbl = dom('<label class="btn btn-radio"/>').appendTo(divSuggest);
			lbl.setText(label);
			if (value == this.assn.suggestions) lbl.addClass('active');
			lbl.onClick(() =>
			{
				for (let child of divSuggest.children()) child.removeClass('active');
				lbl.addClass('active');
				this.assn.suggestions = value;
				this.updateStatus();
			});
		}

		this.chkMandatory = dom('<input type="checkbox"/>');
		let label = dom('<label/>').appendTo(divRow);
		label.append(this.chkMandatory);
		label.appendHTML(' <font style="font-weight: normal;">Mandatory</font>');
		this.chkMandatory.elInput.checked = !!this.assn.mandatory;
		this.chkMandatory.onChange(() =>
		{
			this.assn.mandatory = this.chkMandatory.elInput.checked;
			this.updateStatus();
		});
	}

	private populateValue():void
	{
		let preSelected:PickOntologyWidgetPreSel[] = [];
		for (let value of this.stackAssn.values) if (value.uri && value.uri != this.value.uri)
		{
			if (value.spec == TemplateValueSpecification.Item || value.spec == TemplateValueSpecification.WholeBranch)
				preSelected.push({'uri': value.uri, 'include': true, 'andChildren': true});
			else if (value.spec == TemplateValueSpecification.Exclude || value.spec == TemplateValueSpecification.ExcludeBranch)
				preSelected.push({'uri': value.uri, 'include': false, 'andChildren': true});
			else if (value.spec == TemplateValueSpecification.Container)
				preSelected.push({'uri': value.uri, 'include': true, 'andChildren': true});
		}

		this.makeTermSelector('Value URI', 'value', this.value.uri, preSelected);
		this.makeNameDescr();

		this.ontoWidget.onPicked((branch) =>
		{
			(async () =>
			{
				await this.ontoWidget.changeSelection(branch.uri);
				this.inputURI.setValue(this.value.uri = expandPrefix(branch.uri));
				/*if (!this.value.name)*/ this.inputName.setValue(this.value.name = branch.label);
				/*if (branch.descr && !this.value.descr)*/ this.areaDescr.setValue(this.value.descr = branch.descr);
				if (branch.altLabels && Vec.isBlank(this.value.altLabels))
				{
					this.value.altLabels = branch.altLabels;
					this.areaAltLabels.setValue(branch.altLabels.join('\n'));
				}
				if (branch.externalURLs && Vec.isBlank(this.value.externalURLs))
				{
					this.value.externalURLs = branch.externalURLs;
					this.areaExternalURLs.setValue(branch.externalURLs.join('\n'));
				}
				this.updateStatus();
			})();
		});
		this.btnCustom.onClick(() =>
		{
			this.inputURI.setValue(this.value.uri = this.customURI);
			this.updateStatus();
		});

		this.inputName.setValue(this.value.name);
		this.areaDescr.setValue(this.value.descr);

		let divRow1 = dom('<div/>').appendTo(this.mainVbox).css({'display': 'flex', 'gap': '0.5em', 'align-items': 'baseline'});
		dom('<div/>').appendTo(divRow1).css({'font-weight': 'bold'}).setText('Alt. Labels');
		this.areaAltLabels = dom('<textarea rows="2" class="text-box" spellcheck="false"/>').appendTo(divRow1).css({'flex-grow': '1'});
		dom('<div/>').appendTo(divRow1).css({'font-weight': 'bold'}).setText('Ext. URLs');
		this.areaExternalURLs = dom('<textarea rows="2" class="text-box" spellcheck="false"/>').appendTo(divRow1).css({'flex-grow': '1'});

		this.areaAltLabels.setValue(Vec.safeArray(this.value.altLabels).join('\n'));
		this.areaExternalURLs.setValue(Vec.safeArray(this.value.externalURLs).join('\n'));

		this.areaAltLabels.onInput(() =>
		{
			this.value.altLabels = this.areaAltLabels.getValue().split('\n').filter((v) => !!v);
			if (this.value.altLabels.length == 0) this.value.altLabels = undefined;
			this.updateStatus();
		});
		this.areaExternalURLs.onInput(() =>
		{
			this.value.externalURLs = this.areaExternalURLs.getValue().split('\n').filter((v) => !!v);
			if (this.value.externalURLs.length == 0) this.value.externalURLs = undefined;
			this.updateStatus();
		});

		let divRow2 = dom('<div/>').appendTo(this.mainVbox).css({'display': 'flex', 'gap': '0.5em', 'align-items': 'center'});
		const BUTTONS:[string, TemplateValueSpecification][] =
		[
			['Item', TemplateValueSpecification.Item],
			['Exclude', TemplateValueSpecification.Exclude],
			['Whole Branch', TemplateValueSpecification.WholeBranch],
			['Exclude Branch', TemplateValueSpecification.ExcludeBranch],
			['Container', TemplateValueSpecification.Container],
		];
		dom('<div/>').appendTo(divRow2).css({'font-weight': 'bold'});
		let divSuggest = dom('<div class="btn-group" data-toggle="buttons"/>').appendTo(divRow2);
		for (let [label, value] of BUTTONS)
		{
			let lbl = dom('<label class="btn btn-radio"/>').appendTo(divSuggest);
			lbl.setText(label);
			if (value == this.value.spec) lbl.addClass('active');
			lbl.onClick(() =>
			{
				for (let child of divSuggest.children()) child.removeClass('active');
				lbl.addClass('active');
				this.value.spec = value;
				this.updateStatus();
			});
		}

		dom('<div/>').appendTo(divRow2).css({'font-weight': 'bold'}).setText('Parent URI');
		this.inputParentURI = dom('<input type="text" class="line-edit"/>').appendTo(divRow2).css({'flex-grow': '1'});
		this.inputParentURI.setValue(this.value.parentURI);
		this.inputParentURI.onInput(() =>
		{
			this.value.parentURI = this.inputParentURI.getValue() || undefined;
			this.updateStatus();
		});
		// !! button to pick?
	}

	private makeTermSelector(title:string, type:string, uri:string, preSelected:PickOntologyWidgetPreSel[]):void
	{
		let divHeader = dom('<div/>').appendTo(this.mainVbox).css({'display': 'flex', 'width': '100%', 'gap': '0.5em', 'align-items': 'baseline'});

		dom('<div/>').appendTo(divHeader).css({'font-weight': 'bold'}).setText(title);
		this.inputURI = dom('<input type="text" class="line-edit"/>').appendTo(divHeader).css({'flex-grow': '1'});
		this.inputURI.setAttr('placeholder', 'enter the URI or a partial search term');
		this.inputURI.setValue(uri);
		this.inputURI.onInput(() => this.modifiedURI());

		this.btnCustom = dom('<button class="btn btn-normal">Custom</button>').appendTo(divHeader);

		let divSearch = dom('<div/>').appendTo(this.mainVbox).css({'margin-bottom': '-0.4em', 'text-align': 'right'});
		dom('<span>Search</span>').appendTo(divSearch).css({'font-weight': 'bold'});
		this.inputSearch = dom('<input type="text" class="line-edit"/>').appendTo(divSearch).css({'width': '10em', 'margin': '0 0.5em 0 0.5em'});
		this.inputSearch.onKeyDown((key) =>
		{
			if (key.key == 'Enter') this.activateSearch();
		});
		let btnSearch = dom('<button class="btn btn-xs btn-normal"/>').appendTo(divSearch);
		btnSearch.appendHTML('<span class="glyphicon glyphicon-search" style=\"height: 1.2em;"/>');
		btnSearch.onClick(() => this.activateSearch());

		let divHier = dom('<div/>').appendTo(this.mainVbox).css({'flex-grow': '1'});
		divHier.css({'border': '1px solid #808080', 'border-radius': '4px', 'padding': '0.5em'});
		divHier.css({'overflow': 'auto'});

		let selected = uri ? [uri] : [];
		this.ontoWidget = new PickOntologyWidget(this.type == TemplateComponentDialogType.Value ? 'value' : 'property', selected, preSelected);
		this.ontoWidget.render(divHier);
	}

	private makeNameDescr():void
	{
		let grid = dom('<div/>').appendTo(this.mainVbox).css({'display': 'grid'});
		grid.css({'gap': '0.5em', 'align-items': 'baseline'});
		grid.css({'grid-template-columns': '[start title] auto [content] 1fr [button] auto [end]'});

		dom('<div/>').appendTo(grid).css({'font-weight': 'bold', 'grid-area': '1 / title'}).setText('Name');
		this.inputName = dom('<input type="text" class="line-edit"/>').appendTo(grid).css({'grid-area': '1 / content'});
		this.inputName.onInput(() => this.modifiedName());

		dom('<div/>').appendTo(grid).css({'font-weight': 'bold', 'grid-area': '2 / title'}).setText('Descr');
		this.areaDescr = dom('<textarea rows="3" class="text-box" spellcheck="false"/>').appendTo(grid).css({'grid-area': '2 / content'});
		this.areaDescr.onInput(() => this.modifiedDescr());

		if (this.inputURI)
		{
			let btnRevertName = dom('<button class="btn btn-normal">Revert</button>').appendTo(grid).css({'grid-area': '1 / button'});
			btnRevertName.onClick(() =>
			{
				(async () =>
				{
					let uri = this.inputURI.getValue();
					let branch = await this.ontoWidget.getBranch(uri);
					if (branch)
					{
						this.inputName.setValue(branch.label);
						this.modifiedName();
					}
					else alert('URI is not in the baseline dictionary.');
				})();
			});

			let btnRevertDescr = dom('<button class="btn btn-normal">Revert</button>').appendTo(grid).css({'grid-area': '2 / button'});
			btnRevertDescr.onClick(() =>
			{
				(async () =>
				{
					let uri = this.inputURI.getValue();
					let branch = await this.ontoWidget.getBranch(uri);
					if (branch)
					{
						this.areaDescr.setValue(branch.descr);
						this.modifiedDescr();
					}
					else alert('URI is not in the baseline dictionary.');
				})();
			});
		}
	}

	private modifiedURI():void
	{
		if (this.type == TemplateComponentDialogType.Group) this.group.groupURI = this.inputURI.getValue();
		else if (this.type == TemplateComponentDialogType.Assignment) this.assn.propURI = this.inputURI.getValue();
		else if (this.type == TemplateComponentDialogType.Value) this.value.uri = this.inputURI.getValue();

		this.updateStatus();
	}
	private modifiedName():void
	{
		if (this.type == TemplateComponentDialogType.Root) this.root.name = this.inputName.getValue();
		else if (this.type == TemplateComponentDialogType.Group) this.group.name = this.inputName.getValue();
		else if (this.type == TemplateComponentDialogType.Assignment) this.assn.name = this.inputName.getValue();
		else if (this.type == TemplateComponentDialogType.Value) this.value.name = this.inputName.getValue();

		this.updateStatus();
	}
	private modifiedDescr():void
	{
		if (this.type == TemplateComponentDialogType.Root) this.root.descr = this.areaDescr.getValue();
		else if (this.type == TemplateComponentDialogType.Group) this.group.descr = this.areaDescr.getValue();
		else if (this.type == TemplateComponentDialogType.Assignment) this.assn.descr = this.areaDescr.getValue();
		else if (this.type == TemplateComponentDialogType.Value) this.value.descr = this.areaDescr.getValue();

		this.updateStatus();
	}

	private activateSearch():void
	{
		let query = this.inputSearch.getValue();

		// if nothing changed, jump to the next one
		if (query == this.lastResult && this.searchResults.length > 0)
		{
			this.searchIndex = (this.searchIndex + 1) % this.searchResults.length;
			this.ontoWidget.selectSearchResult(this.searchResults, this.searchIndex).then();
			return;
		}

		if (query == this.lastSearch) return;
		this.lastSearch = query;

		// execute the query
		(async () =>
		{
			let matches:OntologySearch[] = [];

			if (query)
			{
				let type = this.type == TemplateComponentDialogType.Value ? 'value' : 'property';
				await wmk.yieldDOM();
				let result = await asyncREST('REST/OntologySearch', {type, query, 'caseSensitive': false, 'maxResults': 100});
				if (query != this.lastSearch) return; // debounce
				matches = result.matches;
			}

			this.lastResult = query;
			this.searchResults = matches;
			if (this.searchResults.length > 0)
			{
				this.searchIndex = 0;
				await this.ontoWidget.selectSearchResult(this.searchResults, 0);
			}
			else this.ontoWidget.deselectSearchResults();
		})();
	}
}

/* EOF */ }
