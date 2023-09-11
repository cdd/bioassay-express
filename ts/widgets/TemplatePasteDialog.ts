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
	Dialog for inputting JSON-formatted text for pasting into a tree.
*/

export class TemplatePasteValueDialog extends BaseDialog
{
	private callbackApply:(branch:TemplateValue[]) => void;

	private areaJSON:DOM;
	private btnApply:DOM;
	private btnParse:DOM;
	private divHierarchy:DOM;

	private parsedBranch:OntologyBranch = null;

	constructor(private branchParent:OntologyBranch)
	{
		super('Paste Terms');
	}

	public onApply(callback:(branch:TemplateValue[]) => void):void
	{
		this.callbackApply = callback;
	}

	// ------------ private methods ------------

	protected populate():void
	{
		super.populate();

		this.btnApply = dom('<button class="btn btn-action">Apply</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
		this.btnApply.onClick(() => this.applyChanges());

		let btnCancel = dom('<button class="btn btn-normal">Cancel</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
		btnCancel.onClick(() => this.close());

		let mainVbox = dom('<div/>').appendTo(this.domBody).css({'display': 'flex'});
		mainVbox.css({'flex-direction': 'column', 'align-items': 'stretch', 'gap': '0.5em'});

		let divLineage = dom('<div/>').appendTo(mainVbox).css({'display': 'flex', 'gap': '0.5em', 'align-items': 'baseline'});
		dom('<div/>').appendTo(divLineage).setText('Parent Term');
		let divParentURI = dom('<div/>').appendTo(divLineage).css({'flex-grow': '1', 'padding': '0.2em', 'border-radius': '5px', 'background-color': '#F0F0F0'});
		divParentURI.setText(this.branchParent == null ? 'none' : `${this.branchParent.label} <${this.branchParent.uri}>`);

		dom('<div/>').appendTo(mainVbox).setText('Paste in a JSON-formatted branch definition below:');

		this.areaJSON = dom('<textarea rows="5" class="text-box" spellcheck="false"/>').appendTo(mainVbox).css({'width': '100%'});
		this.areaJSON.onInput(() => this.updateStatus());

		let divButtons = dom('<div/>').appendTo(mainVbox).css({'text-align': 'center'});
		this.btnParse = dom('<button class="btn btn-action">Parse</button>').appendTo(divButtons);
		this.btnParse.onClick(() => this.parseText());

		this.divHierarchy = dom('<div/>').appendTo(mainVbox);

		this.updateStatus();

		this.installEscapeKey();
	}

	// ------------ private methods ------------

	private updateStatus():void
	{
		this.btnApply.elInput.disabled = this.parsedBranch == null;
		this.btnParse.elInput.disabled = !this.areaJSON.getValue();
	}

	private parseText():void
	{
		let txt = this.areaJSON.getValue();
		if (!txt) return;

		let json:any;
		try {json = JSON.parse(txt);}
		catch (ex)
		{
			alert('The text is not valid JSON.');
			return;
		}
		if (json == null || typeof json != 'object' || Array.isArray(json))
		{
			alert('The JSON formatted text must be an object (i.e. surrounded by curly braces).');
			return;
		}

		let processBranch = (json:any):OntologyBranch =>
		{
			if (typeof json.uri != 'string' || typeof json.label != 'string') return null;
			let branch:OntologyBranch = {'uri': json.uri, 'label': json.label};
			if (typeof json.descr == 'string') branch.descr = json.descr;
			if (Array.isArray(json.children)) for (let childJSON of json.children)
			{
				let child = processBranch(childJSON);
				if (child)
				{
					if (!branch.children) branch.children = [];
					branch.children.push(child);
				}
			}
			return branch;
		};

		let branch = processBranch(json);
		if (!branch)
		{
			alert('The JSON object is not a branch: it must at least have label and uri defined.');
			return;
		}

		this.parsedBranch = branch;
		this.renderBranch();
		this.updateStatus();
	}

	private applyChanges():void
	{
		if (!this.parsedBranch) return;

		let valueList:TemplateValue[] = [];

		let process = (parent:OntologyBranch, branch:OntologyBranch):void =>
		{
			let value:TemplateValue =
			{
				'uri': branch.uri,
				'name': branch.label,
				'descr': branch.descr,
				'spec': TemplateValueSpecification.Item
			};
			if (parent) value.parentURI = parent.uri;
			valueList.push(value);

			if (branch.children) for (let child of branch.children) process(branch, child);
		};
		process(null, this.parsedBranch);

		this.callbackApply(valueList);
	}

	private renderBranch():void
	{
		let render = (branch:OntologyBranch, divParent:DOM):void =>
		{
			dom('<div/>').appendTo(divParent).setText(`\u{2022} ${branch.label}`);

			if (Vec.notBlank(branch.children))
			{
				let divIndent = dom('<div/>').appendTo(divParent).css({'margin-left': '2em'});
				for (let child of branch.children) render(child, divIndent);
			}
		};

		this.divHierarchy.empty();
		render(this.parsedBranch, this.divHierarchy);
	}
}

export class TemplatePasteWholeDialog extends BaseDialog
{
	private callbackApply:(template:Template) => void;

	private areaJSON:DOM;
	private btnApply:DOM;
	private btnParse:DOM;
	private divNotes:DOM;

	private currentPrefixes:string[] = null;
	private parsedTemplate:Template = null;

	constructor()
	{
		super('Upload New Template');
		this.minPortionWidth = this.maxPortionWidth = 90;
	}

	public onApply(callback:(template:Template) => void):void
	{
		this.callbackApply = callback;
	}

	// ------------ private methods ------------

	protected populate():void
	{
		this.populate();

		this.btnApply = dom('<button class="btn btn-action">Apply</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
		this.btnApply.onClick(() => this.applyChanges());

		let btnCancel = dom('<button class="btn btn-normal">Cancel</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
		btnCancel.onClick(() => this.close());

		let mainVbox = dom('<div/>').appendTo(this.domBody).css({'display': 'flex'});
		mainVbox.css({'flex-direction': 'column', 'align-items': 'stretch', 'gap': '0.5em'});

		dom('<div/>').appendTo(mainVbox).setText('Paste or drag in a JSON-formatted bioassay template:');

		this.areaJSON = dom('<textarea rows="10" class="text-box" spellcheck="false"/>').appendTo(mainVbox).css({'width': '100%'});
		this.areaJSON.onInput(() => this.updateStatus());

		let divButtons = dom('<div/>').appendTo(mainVbox).css({'text-align': 'center'});
		this.btnParse = dom('<button class="btn btn-action">Parse</button>').appendTo(divButtons);
		this.btnParse.onClick(() => this.parseText());

		this.divNotes = dom('<div/>').appendTo(mainVbox);

		this.updateStatus();

		(async () =>
		{
			let result = await asyncREST('REST/GetTemplate', {});
			this.currentPrefixes = result.schemaPrefixes as string[];
		})();

		this.installEscapeKey();
	}

	// ------------ private methods ------------

	private updateStatus():void
	{
		this.btnApply.elInput.disabled = this.parsedTemplate == null;
		this.btnParse.elInput.disabled = !this.areaJSON.getValue();
	}

	private parseText():void
	{
		let txt = this.areaJSON.getValue();
		if (!txt) return;

		let json:any;
		try {json = JSON.parse(txt);}
		catch (ex)
		{
			this.divNotes.setText('The text is not valid JSON.');
			return;
		}
		if (json == null || typeof json != 'object' || Array.isArray(json))
		{
			this.divNotes.setText('The JSON formatted text must be an object (i.e. surrounded by curly braces).');
			return;
		}

		let template = json as Template, pfx = template.schemaPrefix, name = template.root ? template.root.name : null;
		if (!pfx || (!pfx.startsWith('http://') && !pfx.startsWith('https://')))
		{
			this.divNotes.setText('The template must have the schemaPrefix defined, and be a valid URI.');
			return;
		}
		if (!name)
		{
			this.divNotes.setText('The template must have a named group.');
			return;
		}
		if (this.currentPrefixes.includes(pfx))
		{
			this.divNotes.setText(`The prefix "${pfx}" is already in use. Duplicates are not allowed.`);
			return;
		}

		this.parsedTemplate = template;

		let numAssn = 0, numGroup = 0;
		let scanGroup = (group:TemplateGroup):void =>
		{
			numAssn += Vec.len(group.assignments);
			numGroup++;
			if (group.subGroups) for (let sub of group.subGroups) scanGroup(sub);
		};
		scanGroup(template.root);

		this.divNotes.empty();
		dom('<div/>').appendTo(this.divNotes).setHTML(`Template name: <b>${escapeHTML(name)}</b>.`);
		dom('<div/>').appendTo(this.divNotes).setHTML(`Prefix <tt>${escapeHTML(pfx)}</tt>.`);
		dom('<div/>').appendTo(this.divNotes).setHTML(`Assignments: ${numAssn}, groups: ${numGroup}.`);

		this.updateStatus();
	}

	private applyChanges():void
	{
		/* ... actually it goes to the editor, so no need for this
		if (!this.parsedTemplate) return;
		let pfx = this.parsedTemplate.schemaPrefix, name = this.parsedTemplate.root.name;
		let msg = `This will create a new template called "${name}" with the prefix "${pfx}". The name can be changed later, but ` +
				  `the prefix cannot. Continue?`;
		if (!confirm(msg)) return;*/

		this.callbackApply(this.parsedTemplate);
	}
}

/* EOF */ }
