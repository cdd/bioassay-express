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
	Offers to select a branch-dialog from the available list.
*/

export class PickBranchDialog extends BootstrapDialog
{
	private branchTemplates:TemplateSummary[] = [];

	private divBranch:JQuery[] = [];

	public callbackPicked:(branch:TemplateSummary) => void = null;

	constructor(private groupNest:string[], branchTemplates:TemplateSummary[])
	{
		super('Select Branch to Insert');

		// formulate the internal list of templates that actually applies
		if (groupNest == null) groupNest = [];
		let topGroup = groupNest.length > 0 ? TemplateManager.decomposeSuffixGroupURI(groupNest[0])[0] : null;
		for (let branch of branchTemplates)
		{
			let match = !topGroup && branch.branchGroups.length == 0;
			if (!match) for (let group of branch.branchGroups) if (group == topGroup) {match = true; break;}
			if (match) this.branchTemplates.push(branch);
		}
	}

	// ------------ private methods ------------

	protected populateContent():void
	{
		for (let branch of this.branchTemplates)
		{
			let div = $('<div></div>').appendTo(this.content);

			let p = $('<p></p>').appendTo(div);
			// (...open/close...)
			let span = $('<span></span>').appendTo(p);
			span.css('font-weight', 'bold');
			span.text(branch.title);
			span.css('cursor', 'pointer');
			span.hover(() => span.css('text-decoration', 'underline'),
					   () => span.css('text-decoration', 'none'));
			span.click(() => this.selectBranch(branch));

			this.divBranch.push(div);
		}
	}

	private selectBranch(branch:TemplateSummary):void
	{
		this.callbackPicked(branch);
		this.hide();
	}
}

/*
	Offers to remove a previously grafted branch.
*/

export class RemoveBranchDialog extends BootstrapDialog
{
	private branchTemplates:TemplateSummary[] = [];

	private divBranch:JQuery[] = [];

	public callbackPicked:(idxList:number[]) => void = null;

	constructor(private branches:SchemaBranch[])
	{
		super('Select Branch to Remove');
	}

	// ------------ private methods ------------

	protected populateContent():void
	{
		let schemaList:string[] = [];
		for (let branch of this.branches) schemaList.push(branch.schemaURI);
		TemplateManager.ensureTemplates(schemaList, () => this.populateTemplates());
	}

	// templates are definitely cached, so go for it
	private populateTemplates():void
	{
		// TODO: branches that have sub-branches need to be non-selectable, or they need to auto-select the sub-branches

		for (let n = 0; n < this.branches.length; n++)
		{
			let schema = TemplateManager.getTemplate(this.branches[n].schemaURI);

			let div = $('<div></div>').appendTo(this.content);

			let p = $('<p></p>').appendTo(div);
			// (...open/close...)
			let span = $('<span></span>').appendTo(p);
			span.css('font-weight', 'bold');
			span.text(schema.name);
			span.css('cursor', 'pointer');
			span.hover(() => span.css('text-decoration', 'underline'),
					   () => span.css('text-decoration', 'none'));
			span.click(() => this.selectBranch([n]));

			this.divBranch.push(div);
		}

		this.dlg.modal({'backdrop': 'static', 'keyboard': true});
	}
	private selectBranch(idxList:number[]):void
	{
		this.callbackPicked(idxList);
		this.dlg.modal('hide');
	}
}

/* EOF */ }
