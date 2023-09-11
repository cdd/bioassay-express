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
	Assignment header panel: manages all of the widgets at the top of the assignment panel including title, command buttons,
	identifier, template selection, search, etc.
*/

export class PanelAssnHeader
{
	public btnEdit:JQuery;
	public spanEdit:JQuery;
	public btnNew:JQuery;
	public btnClone:JQuery;

	public divSearchBox:JQuery;
	public inputSearchValue:JQuery;

	public divKeywordBox:JQuery;
	public inputKeyword:JQuery;

	public btnIDType:JQuery;
	public spanIDTitle:JQuery;
	public ulIDList:JQuery;
	public inputUniqueID:JQuery;
	public btnOrigin:JQuery;
	public divTemplateChoice:JQuery;
	public divFormSelector:JQuery;

	public findIdent:FindIdentifier;

	constructor(private delegate:AssignmentDelegate,
		public divButtons:JQuery, public divSearch:JQuery, public divKeyword:JQuery, public divIdentity:JQuery)
	{
	}

	public render():void
	{
		// the very top row: fill in the missing details

		let divFlex = $('<div class="flexbuttons"></div>').appendTo(this.divButtons);

		this.btnEdit = $('<button class="btn btn-action"></button>').appendTo($('<div></div>').appendTo(divFlex));
		this.btnEdit.append('<span class="glyphicon glyphicon-edit"></span>&nbsp;');
		this.spanEdit = $('<span>Edit</span>&nbsp;').appendTo(this.btnEdit);
		this.btnEdit.click(() =>
		{
			if (!this.delegate.editMode)
				this.delegate.actionEnterEditMode();
			else
				this.delegate.actionSubmitAssayChanges();
		});

		this.btnNew = $('<button class="btn btn-action"></button>').appendTo($('<div></div>').appendTo(divFlex));
		this.btnNew.append('<span class="glyphicon glyphicon-flash"></span> New');
		this.btnNew.click(this.delegate.actionClearNew);

		this.btnClone = $('<button class="btn btn-action"></button>').appendTo($('<div></div>').appendTo(divFlex));
		this.btnClone.append('<span class="glyphicon glyphicon-plus-sign"></span> Clone');
		this.btnClone.click(this.delegate.actionCloneNew);

		this.divSearchBox = $('<div class="search-box"></div>').appendTo(this.divSearch);
		this.inputSearchValue = $('<input type="text" value="" placeholder="find by ID"></input>').appendTo(this.divSearchBox);

		// keyword searching bar

		this.divKeywordBox = $('<div class="keyword-box"></div>').appendTo(this.divKeyword);
		this.inputKeyword = $('<input type="text" value="" placeholder="find term to annotate with"></input>').appendTo(this.divKeywordBox);

		// identity, template and other related controls

		let divType = $('<div></div>').appendTo(this.divIdentity);
		let btnGroup = $('<div class="btn-group"></div>').appendTo(divType);
		this.btnIDType = $('<button class="form-control btn btn-action dropdown-toggle" data-toggle="dropdown"></button>').appendTo(btnGroup);
		this.spanIDTitle = $('<span>Unique ID</span>').appendTo(this.btnIDType);
		this.btnIDType.append('<span class="caret"></span>');
		this.ulIDList = $('<ul class="dropdown-menu" role="menu"></ul>').appendTo(btnGroup);

		let divID = $('<div></div>').appendTo(this.divIdentity);
		let divIDBox = $('<div class="identity-box"></div>').appendTo(divID);
		this.inputUniqueID = $('<input placeholder="assay ID"></input)').appendTo(divIDBox);
		this.inputUniqueID.attr({'autocomplete': 'off', 'autocorrect': 'off', 'autocapitalize': 'off', 'spellcheck': 'false'});

		let divOrigin = $('<div></div>').appendTo(this.divIdentity);
		this.btnOrigin = $('<button class="btn btn-normal"><span class="glyphicon glyphicon-hand-right"></span> Origin</button>').appendTo(divOrigin);
		this.btnOrigin.attr('title', 'Opens the original assay in a new tab');
		this.btnOrigin.click(this.delegate.actionShowOrigin);

		this.divTemplateChoice = $('<div></div>').appendTo(this.divIdentity);
		this.divFormSelector = $('<div></div>').appendTo(this.divIdentity);
		// (no longer used?) this.divModifiedStatus = $('<div></div>').appendTo(this.divIdentity);

		this.setupFindAssay();
		this.setupKeywordSearch();
		this.rebuildIdentifiers();
	}

	// configures the dropdown menu and the identifier box
	public rebuildIdentifiers():void
	{
		let spanTitle = this.spanIDTitle, ulOptions = this.ulIDList;
		let inputUniqueID = this.inputUniqueID;

		inputUniqueID.off('input'); // temporary
		let [cursrc, id] = UniqueIdentifier.parseKey(this.delegate.assay.uniqueID);
		if (id == null) id = purifyTextPlainInput(inputUniqueID.val());
		spanTitle.text(cursrc ? cursrc.name : 'Unique ID');

		let sources = UniqueIdentifier.sources();
		ulOptions.empty();
		for (let n = -1; n < sources.length; n++)
		{
			let src = n < 0 ? null : sources[n];
			let title = n < 0 ? 'None' : sources[n].name;
			let li = $('<li></li>').appendTo(ulOptions);
			let href = $('<a href="#"></a>').appendTo(li);
			href.text(title);
			if (cursrc && !src)
			{
				href.click(() =>
				{
					this.delegate.assay.uniqueID = null;
					this.rebuildIdentifiers();
				});
			}
			else if (src && (!cursrc || cursrc.name != src.name))
			{
				href.click(() =>
				{
					this.delegate.assay.uniqueID = UniqueIdentifier.makeKey(src, id);
					this.rebuildIdentifiers();
					if (!this.delegate.assay.schemaURI)
					{
						this.delegate.assay.schemaURI = src.defaultSchema;
						this.delegate.actionBuildTemplate();
					}
				});
			}
		}

		// include a delete button, unless it's a new assay
		if (this.delegate.assay.assayID > 0)
		{
			let btnDelete = $('<button class="btn btn-action" style="margin-left: 20px; margin-top: 10px;"></button>').appendTo($('<li></li>').appendTo(ulOptions));
			btnDelete.append('<span class="glyphicon glyphicon-trash" style=\"height: 1.2em;"></span> Delete Assay');
			btnDelete.click(() => this.delegate.actionDeleteAssay());
		}

		if (id != null) inputUniqueID.val(id);
		inputUniqueID.prop('disabled', cursrc == null || !this.delegate.editMode);

		inputUniqueID.on('input' /*'change'...?*/, () => this.delegate.actionUpdateUniqueIDValue());
	}

	// the "edit" button has a lifecycle which involves disabled/enabled, and transmogrifying into a Submit button
	public updateEditButton():void
	{
		if (!this.delegate.editMode)
		{
			this.spanEdit.text('Edit');
			this.btnEdit.prop('disabled', false);
		}
		else
		{
			this.spanEdit.text('Submit');
			this.btnEdit.prop('disabled', !Authentication.canSubmitHoldingBay() || !this.delegate.actionIsModified());
		}
	}

	// ------------ private methods ------------

	private setupFindAssay():void
	{
		// install the "find assay" hooks
		this.findIdent = new FindIdentifier((match:FindIdentifierMatch) =>
		{
			let url = 'assign.jsp?';
			if (match.uniqueID) url += 'uniqueID=' + encodeURIComponent(match.uniqueID); else url += 'assayID=' + match.assayID;
			document.location.href = url;
		});
		this.findIdent.install(this.inputSearchValue);
	}

	// configures the "keyword search" input: every time something changes, update the list of matching keywords
	public setupKeywordSearch():void
	{
		this.delegate.keyword = new GlobalKeywordBar(this.delegate.assay.schemaURI, (annot:AssayAnnotation) => this.delegate.actionAppendAnnotation(annot));
		this.delegate.keyword.changeAnnotations(this.delegate.assay.annotations);
		this.delegate.keyword.install(this.inputKeyword);
		this.delegate.keyword.setEnabled(this.delegate.editMode);
	}

}

/* EOF */ }
