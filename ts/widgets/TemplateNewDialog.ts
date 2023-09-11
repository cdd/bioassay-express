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
	Dialog for getting started creating a new template, to make sure it gets off on the right footing.
*/

const TEXT_PREFIX =
	'Provide a URI base that is globally unique for this template, ' +
	'and is suitable for using as a prefix for custom terms.';

const TEXT_NAME =
	'Create a short concise name, ideally 15-25 characters, using ' +
	'lower case except for proper nouns.';

export class TemplateNewDialog extends BaseDialog
{
	private callbackContinue:(template:Template) => void;

	private btnContinue:DOM;
	private inputPrefix:DOM;
	private inputName:DOM;
	private divCurrent:DOM;
	private usedPrefixes = new Set<string>();
	private usedNames = new Set<string>();

	constructor()
	{
		super('New Template');
	}

	// ------------ private methods ------------

	protected populate():void
	{
		super.populate();

		this.btnContinue = dom('<button class="btn btn-action">Continue</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
		this.btnContinue.onClick(() => this.continueToEdit());

		let btnCancel = dom('<button class="btn btn-normal">Cancel</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
		btnCancel.onClick(() => this.close());

		let vboxMain = dom('<div/>').appendTo(this.domBody).css({'display': 'flex'});
		vboxMain.css({'flex-direction': 'column', 'align-items': 'stretch', 'gap': '0.5em'/*, 'width': '90vw', 'max-height': 'calc(100vh - 10em)'*/});

		let hboxPrefix = dom('<div/>').appendTo(vboxMain).css({'display': 'flex', 'gap': '0.5em', 'align-items': 'baseline'});
		dom('<div>Schema Prefix</div>').appendTo(hboxPrefix);
		this.inputPrefix = dom('<input type="text" class="line-edit"/>').appendTo(hboxPrefix).css({'flex-grow': '1'});
		this.inputPrefix.setValue('http://');
		this.inputPrefix.onInput(() => this.updateStatus());

		dom('<div/>').appendTo(vboxMain).css({'padding-left': '2em'}).setText(TEXT_PREFIX);
		
		let hboxName = dom('<div/>').appendTo(vboxMain).css({'display': 'flex', 'gap': '0.5em', 'align-items': 'baseline'});
		dom('<div>Name</div>').appendTo(hboxName);
		this.inputName = dom('<input type="text" class="line-edit"/>').appendTo(hboxName).css({'flex-grow': '1'});
		this.inputName.onInput(() => this.updateStatus());

		dom('<div/>').appendTo(vboxMain).css({'padding-left': '2em'}).setText(TEXT_NAME);

		this.divCurrent = dom('<div/>').appendTo(vboxMain).css({'width': '100%'});
		this.divCurrent.css({'padding': '0.5em', 'border': '1px solid black', 'background-color': '#F8F8F8', 'border-radius': '5px'});
		this.divCurrent.setText('Loading...');

		this.loadCurrent().then();

		this.updateStatus();

		this.installEscapeKey();
	}

	public onContinue(callback:(template:Template) => void)
	{
		this.callbackContinue = callback;
	}

	// ------------ private methods ------------

	private continueToEdit():void
	{
		this.close();

		let template:Template =
		{
			'schemaPrefix': this.inputPrefix.getValue(),
			'root':
			{
				'name': this.inputName.getValue(),
				'groupURI': null,
				'canDuplicate': false,
				'assignments': [],
				'subGroups': []
			}
		};
		this.callbackContinue(template);
	}

	private updateStatus():void
	{
		let prefix = this.inputPrefix.getValue(), name = this.inputName.getValue();

		let good = /^https?:\/\/(.*)\.(\w+)\/.*$/.test(prefix) && /^\w+/.test(name);
		if (this.usedPrefixes.has(prefix) || this.usedNames.has(name)) good = false;

		this.btnContinue.elInput.disabled = !good;
	}

	private async loadCurrent():Promise<void>
	{
		let result = await asyncREST('REST/GetTemplate', {});
		let prefixes = result.schemaPrefixes as string[];

		if (Vec.isBlank(prefixes))
		{
			this.divCurrent.setText('Template loading failed.');
			return;
		}

		let first = true;

		for (let pfx of prefixes)
		{
			result = await asyncREST('REST/GetTemplate', {'schemaPrefix': pfx});
			let template = result.schema as Template;

			if (first) this.divCurrent.empty();
			first = false;

			let div = dom('<div/>').appendTo(this.divCurrent);
			
			let spanPfx = dom('<span/>').appendTo(div).css({'padding': '0.2em', 'background-color': Theme.WEAK_HTML, 'border-radius': '5px'});
			spanPfx.setText(template.schemaPrefix);

			let spanName = dom('<span/>').appendTo(div).css({'padding': '0.2em', 'margin-left': '0.5em', 'font-weight': 'bold'});
			spanName.setText(template.root.name);

			this.usedPrefixes.add(template.schemaPrefix);
			this.usedNames.add(template.root.name);
		}

		this.updateStatus();
	}
}

/* EOF */ }
