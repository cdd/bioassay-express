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
	Supporting functionality for the templates report.
*/

export class PageTemplate
{
	private divSchema:JQuery[] = [];
	private roster:number[] = [];
	private schemaDetails:SchemaSummary[] = []; // more detailed than template summary

	constructor(private templates:TemplateSummary[])
	{
	}

	// assembles the page from scratch
	public buildContent():void
	{
		let main = $('#main');
		main.empty();

		$('<h1>Available Templates</h1>').appendTo(main);

		let ol = $('<ol></ol>').appendTo($('<p></p>').appendTo(main));

		for (let n = 0; n < this.templates.length; n++)
		{
			let li = $('<li></li>').appendTo(ol);
			let ahref = $('<a></a>').appendTo(li);
			ahref.attr('href', '#' + (n + 1));
			ahref.text(this.templates[n].title);
		}

		for (let n = 0; n < this.templates.length; n++)
		{
			main.append('<a name="' + (n + 1) + '"></a>');
			let h1 = $('<h1></h1>').appendTo(main);
			h1.text(this.templates[n].title);

			let p = $('<p>Schema URI: </p>').appendTo(main);
			let b = $('<b></b>').appendTo(p);
			b.text(this.templates[n].schemaURI);

			this.divSchema.push($('<p></p>').appendTo(main));
			this.roster.push(n);
		}

		this.grabNext();
	}

	// ------------ private methods ------------

	private grabNext():void
	{
		if (this.roster.length == 0) return;

		const idx = this.roster.shift();
		let params = {'schemaURI': this.templates[idx].schemaURI};
		callREST('REST/DescribeSchema', params,
			(data:SchemaSummary) =>
			{
				this.fillSchema(idx, data);
				this.grabNext();
			});
	}

	private fillSchema(idx:number, schema:SchemaSummary):void
	{
		this.schemaDetails[idx] = schema;

		let div = this.divSchema[idx];

		if (schema.descr)
		{
			let p = $('<p style="font-style: italic;"></p>').appendTo(div);
			p.text(schema.descr);
		}

		// NOTE: to-do... add in the group nesting

		for (let n = 0; n < schema.assignments.length; n++)
		{
			let indent = 1; // ...

			let p = $('<p></p>').appendTo(div);
			p.css('margin-left', (indent * 2) + 'em');

			let b = $('<b></b>').appendTo(p);
			b.text(schema.assignments[n].name);
			// (popover stuff...)
		}
	}
}

/* EOF */ }
