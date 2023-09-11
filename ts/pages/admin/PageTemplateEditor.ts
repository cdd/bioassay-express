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

///<reference path='../../support/Authentication.ts'/>

namespace BioAssayExpress /* BOF */ {

/*
	Admin page: template editor.
*/

export class PageTemplateEditor
{
	private templates:Template[] = [];
	private editor:TemplateEditor = null;

	constructor()
	{
		this.buildDirectory();
	}

	// ------------ private methods ------------

	private cellCSS = {'text-align': 'center', 'vertical-align': 'middle', 'padding': '0.25em 0.5em'};

	// builds the first-look UI components for the main block, which invite the user to provide information to kickstart a new assay
	private buildDirectory():void
	{
		let main = DOM.find('#main');
		main.empty();

		let ulTemplates = dom('<ul/>').appendTo(main);
		this.templates = [];
		(async () =>
		{
			let result = await asyncREST('REST/GetTemplate', {});
			let prefixes = result.schemaPrefixes as string[];
			for (let pfx of prefixes)
			{
				result = await asyncREST('REST/GetTemplate', {'schemaPrefix': pfx});
				this.templates.push(result.schema);
			}

			this.templates.sort((t1, t2) =>
			{
				let n1 = (t1.branchGroups ? 'Z' : 'A') + t1.root.name;
				let n2 = (t2.branchGroups ? 'Z' : 'A') + t2.root.name;
				return n1.localeCompare(n2);
			});

			for (let template of this.templates)
			{
				let li = dom('<li/>').appendTo(ulTemplates);
				let span = dom('<span/>').appendTo(li).class('pseudoLink');
				span.setText(template.root.name);
				span.onClick(() => this.buildTemplate(template, false));

				let btnDownload = dom('<button class="btn btn-xs btn-action"/>').appendTo(li).css({'margin-left': '0.5em'});
				btnDownload.appendHTML('<span class="glyphicon glyphicon-download" style=\"height: 1.2em;"/>');
				btnDownload.onClick(() => this.downloadTemplate(template));

				if (template.schemaPrefix != 'http://www.bioassayontology.org/bas#')
				{
					let btnDelete = dom('<button class="btn btn-xs btn-normal"/>').appendTo(li).css({'margin-left': '0.5em'});
					btnDelete.appendHTML('<span class="glyphicon glyphicon-remove-circle" style=\"height: 1.2em;"/>');
					btnDelete.onClick(() => this.deleteTemplate(template));
				}
			}
		})();

		let divButtons = dom('<div/>').appendTo(main).css({'display': 'flex', 'gap': '0.5em', 'justify-content': 'center'});
		let btnNew = dom('<button class="btn btn-action">New Template</button>').appendTo(divButtons);
		let btnUpload = dom('<button class="btn btn-normal">Upload</button>').appendTo(divButtons);
		let btnProps = dom('<button class="btn btn-normal">View Properties</button>').appendTo(divButtons);
		let btnValues = dom('<button class="btn btn-normal">View Values</button>').appendTo(divButtons);

		btnNew.onClick(() => 
		{
			let dlg = new TemplateNewDialog();
			dlg.onContinue((template) => this.buildTemplate(template, true));
			dlg.open();
		});
		btnUpload.onClick(() => this.uploadTemplate());
		btnProps.onClick(() => 
		{
			new PickOntologyDialog('property').open();
		});
		btnValues.onClick(() =>
		{
			new PickOntologyDialog('value').open();
		});
	}

	private buildTemplate(template:Template, isNew:boolean):void
	{
		let main = DOM.find('#main');
		main.empty();

		this.editor = new TemplateEditor(template, isNew);
		this.editor.onCancel(() =>
		{
			this.editor = null;
			this.buildDirectory();
		});
		this.editor.onSave(() =>
		{
			let msg = isNew ? 'Create new template?' : 'Save template?';
			if (!confirm(msg)) return;

			(async () =>
			{
				let result = await asyncREST('REST/SubmitTemplate', {'template': this.editor.getTemplate()});
				if (!result.success)
				{
					alert('Submission failed.');
					return;
				}
				this.editor = null;
				this.buildDirectory();
			})();
		});
		this.editor.render(main);
	}

	private downloadTemplate(template:Template):void
	{
		let fn = 'schema_';
		for (let ch of template.root.name)
		{
			ch = ch.toLowerCase();
			if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) fn += ch;
			else if (!fn.endsWith('_')) fn += '_';
		}
		fn += '.json';

		let str = JSON.stringify(template);
		let a = window.document.createElement('a') as HTMLAnchorElement;
		a.href = window.URL.createObjectURL(new Blob([str], {'type': 'application/octet-stream'}));
		a.download = fn;
		document.body.appendChild(a);
		a.click();
		document.body.removeChild(a);
	}

	private deleteTemplate(template:Template):void
	{
		let msg = `Are you sure you wish to delete the template "${template.root.name}" prefix ${template.schemaPrefix}?`;
		if (!confirm(msg)) return;

		msg = 'Warning: deleting a template has disruptive consequences for any assays that rely on it. To continue, type in DELETE in all capitals:';
		let code = prompt(msg);
		if (code != 'DELETE') return;

		(async () =>
		{
			let result = await asyncREST('REST/DeleteTemplate', {'schemaPrefix': template.schemaPrefix});
			if (result.success)
			{
				alert('Template deletion successful.');
				this.buildDirectory();
			}
			else
			{
				alert('Template deletion failed: ' + result.reason);
			}
		})();
	}

	private uploadTemplate():void
	{
		let dlg = new TemplatePasteWholeDialog();
		dlg.onApply((template) =>
		{
			dlg.close();
			this.buildTemplate(template, true);
		});
		dlg.open();
	}
}

/* EOF */ }
