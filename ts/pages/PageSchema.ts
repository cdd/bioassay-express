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
	Supporting functionality for the schema report.
*/

export class PageSchema
{
	private schema:SchemaSummary;
	private propCounts:Record<string, number>; // propURI to count
	private annotCounts:Record<string, Record<string, number>>; // first idx is propURI, second is valueURI-to-count
	private divParts:JQuery[];
	private roster:number[];

	constructor()
	{
	}

	// assembles the page from scratch
	public buildContent():void
	{
		callREST('REST/DescribeSchema', {},
			(data:SchemaSummary) =>
			{
				this.schema = data;

				callREST('REST/SummariseAnnotations', {},
					(data:any) =>
					{
						this.propCounts = data.propCounts;
						this.annotCounts = data.annotCounts;
						this.createParts();
					});
			},
			(data) => $('#main').text('Fetching schema failed.'));
	}

	// ------------ private methods ------------

	private createParts():void
	{
		let main = $('#main');
		main.empty();

		let h1 = $('<h1></h1>').appendTo(main);
		h1.text(this.schema.name);
		let para = $('<p><b>Schema URI</b>: </p>').appendTo(main);
		$('<font></font>').appendTo(para).text(this.schema.schemaURI);
		para.append('. <b>Description</b>: ');
		$('<font></font>').appendTo(para).text(this.schema.descr);

		para = $('<p align="right"></p>').appendTo(main);
		let btn = $('<button class="btn btn-action"></button>').appendTo(para);
		$('<span class="glyphicon glyphicon-download-alt" style=\"height: 1.2em;"></span>').appendTo(btn);
		btn.append(' Download All');
		btn.click(() => window.location.href = restBaseURL + '/servlet/DownloadAnnotations/all_annotations.zip');

		this.divParts = [];
		for (let n = 0; n < this.schema.assignments.length; n++)
		{
			let assn = this.schema.assignments[n];
			let h2 = $('<h2></h2>').appendTo(main);
			h2.text(assn.name);

			para = $('<p><b>Property URI</b>: </p>').appendTo(main);
			$('<font></font>').appendTo(para).text(assn.propURI);
			para.append('. <b>Description</b>: ');
			$('<font></font>').appendTo(para).text(assn.descr ? assn.descr : '(none)');

			let valcounts = this.annotCounts[assn.propURI], total = 0;
			if (valcounts != null) for (let valueURI in valcounts) total += valcounts[valueURI];
			if (!assn.descr || !assn.descr.endsWith('.')) para.append('.');
			para.append(' <b>Total annotations</b>: <u>' + total + '</u>.');

			let pcounts = this.propCounts[assn.propURI];
			if (pcounts != null) para.append(' Property used by <u>' + pcounts + '</u> assays.');

			this.divParts[n] = $('<div></div>').appendTo(main);
		}

		this.roster = Vec.identity0(this.schema.assignments.length);
		this.fetchNextPart();
	}

	private fetchNextPart():void
	{
		if (this.roster.length == 0) return;

		let idx = this.roster.shift();

		let params = {'locator': this.schema.assignments[idx].locator};
		callREST('REST/DescribeSchema', params,
			(data) =>
			{
				this.fillPart(idx, data.values);
				this.fetchNextPart();
			});
	}

	private fillPart(idx:number, allValues:SchemaValue[]):void
	{
		let propURI = this.schema.assignments[idx].propURI;
		let counts = this.annotCounts[propURI];
		if (counts == null) counts = {};

		let values:SchemaValue[] = [];
		for (let v of allValues)
		{
			let c = counts[v.uri];
			if (c && c > 0) values.push(v);
		}

		values.sort((v1:SchemaValue, v2:SchemaValue):number =>
		{
			let c1 = counts[v1.uri] || 0, c2 = counts[v2.uri] || 0;
			return c1 < c2 ? 1 : c1 > c2 ? -1 : 0;
		});

		let divParent = this.divParts[idx];

		let ul = $('<ul></ul>').appendTo(divParent);
		for (let n = 0; n < values.length; n++)
		{
			let val = values[n], num = counts[val.uri] | 0;
			let li = $('<li></li>').appendTo(ul);

			li.append('[' + num + '] ');
			let span = $('<span></span>').appendTo(li);
			span.text(val.name);

			let tip = '<b>Abbrev</b>: ' + escapeHTML(val.abbrev);
			tip += '<p>' + (val.descr ? escapeHTML(val.descr) : '(no description)') + '</p>';

			tip += '<p>';
			tip += 'Used in ' + num + ' annotation' + (num == 1 ? '' : 's') + '. ';
			if (!val.inSchema) tip += 'The term is <b>not</b> part of the schema. ';
			else if (val.isExplicit) tip += 'Term is mentioned explicitly in the schema. ';
			else tip += 'Term is included in the schema as part of a branch. ';
			if (val.hasModel) tip += 'Has a model for predicting suggestions.';

			tip += '</p>';

			Popover.hover(domLegacy(span), val.name, tip);
		}

		let para = $('<p align="right"></p>').appendTo(divParent);
		let btn = $('<button class="btn btn-action"></button>').appendTo(para);
		$('<span class="glyphicon glyphicon-download-alt" style=\"height: 1.2em;"></span>').appendTo(btn);
		btn.append(' Download');
		let capname = this.mixedCapsName(this.schema.assignments[idx].name);
		let url = restBaseURL + '/servlet/DownloadAnnotations/annotation_' + capname + '.tsv?propURI=' + encodeURIComponent(propURI);
		btn.click(() => window.location.href = url);
	}

	// filename-friendly versions of names, sans whitespace (e.g. "foo bar" -> "FooBar")
	private mixedCapsName(name:string):string
	{
		let ret = '';
		for (let word in name.split('\\s+')) if (word.length > 0)
		{
			for (let n = 0; n < word.length; n++)
			{
				ret += word.charAt(0).toUpperCase() + word.substring(1);
			}
		}
		return ret;
	}
}

/* EOF */ }
