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
	PubChemXRef: widget for showing the additional information that's available from PubChem, in the form of cross references.
	These can be things like Gene/Protein IDs that can be turned into links; literature references; and miscellaneous stuff.
*/

export class VariousAssayXRef
{
	private domParent:JQuery;

	constructor(private assay:AssayDefinition)
	{
	}

	// creates the pieces for convenient viewing
	public render(domParent:JQuery):void
	{
		this.domParent = domParent;
		domParent.empty();

		if (this.assay.assayID > 0 || this.assay.uniqueID /*|| this.assay.pubchemSource*/) this.renderIdentifiers();
		if (this.assay.pubchemXRef && this.assay.pubchemXRef.length > 0) this.renderPubChem();
	}

	// ------------ private methods ------------

	// display identifiers
	private renderIdentifiers():void
	{
		let parts:JQuery[] = [];

		if (this.assay.assayID > 0)
		{
			let span = $('<span>Assay ID: </span>');
			let value = $('<i></i>').appendTo(span);
			value.text(this.assay.assayID.toString());
			parts.push(span);
		}
		if (this.assay.uniqueID)
		{
			let [src, id] = UniqueIdentifier.parseKey(this.assay.uniqueID);
			if (src && id)
			{
				let span = $('<span></span>');
				span.text(src.name + ': ');

				let url = UniqueIdentifier.composeRefURL(this.assay.uniqueID);
				if (url)
				{
					let ahref = $('<a target="_blank"></a>').appendTo(span);
					ahref.attr('href', UniqueIdentifier.composeRefURL(this.assay.uniqueID));
					ahref.text(id);
				}
				else span.append(id);
				parts.push(span);
			}
		}

		let para = $('<p></p>').appendTo(this.domParent);
		for (let n = 0; n < parts.length; n++)
		{
			if (n > 0) para.append(', ');
			para.append(parts[n]);
		}
	}

	// display the various PubChem metadata
	private renderPubChem():void
	{
		let gene:any[] = [], protein:any[] = [], omim:any[] = [], taxonomy:any[] = [], pubmed:any[] = [], other:any[] = [];
		
		for (let xref of this.assay.pubchemXRef)
		{
			if (xref.type == 'gene')
			{
				gene.push({'text': xref.comment, 'url': 'http://www.ncbi.nlm.nih.gov/gene/' + xref.id});
			}
			else if (xref.type == 'protein_gi')
			{
				protein.push({'text': xref.comment, 'url': 'http://www.ncbi.nlm.nih.gov/protein/' + xref.id});
			}
			else if (xref.type == 'mim')
			{
				omim.push({'text': xref.comment, 'url': 'http://omim.org/entry/' + xref.id});
			}
			else if (xref.type == 'taxonomy')
			{
				taxonomy.push({'text': xref.comment, 'url': 'https://www.ncbi.nlm.nih.gov/Taxonomy/Browser/wwwtax.cgi?id=' + xref.id});
			}
			else if (xref.type == 'pmid')
			{
				pubmed.push({'text': xref.comment, 'url': 'http://www.ncbi.nlm.nih.gov/pubmed/' + xref.id});
			}
			else if (xref.type == 'asurl' || xref.type == 'aid') {}
			else
			{
				let obj:any = {'text': xref.comment, 'type': xref.type};
				if (xref.id.startsWith('http://') || xref.id.startsWith('https://')) obj.url = xref.id; else obj.value = xref.id;
				other.push(obj);
			}
		}
		
		$('<h2>PubChem Cross References</h2>').appendTo(this.domParent);
		
		let table = $('<table class="data"></table>').appendTo(this.domParent);
		
		this.createSection(table, 'Gene', gene);
		this.createSection(table, 'Protein', protein);
		this.createSection(table, 'OMIM', omim);
		this.createSection(table, 'Taxonomy', taxonomy);
		this.createSection(table, 'PubMed', pubmed);
		this.createSection(table, 'Other', other);
	}

	// creates one subsection
	private createSection(table:JQuery, heading:string, content:any[]):void
	{
		if (content.length == 0) return;
		let tr = $('<tr></tr>').appendTo(table);
		let tdHeading = $('<th class="data" valign></th>').appendTo(tr);
		let tdContent = $('<td class="data"></td>').appendTo(tr);
		
		tdHeading.text(heading);
		
		for (let n = 0; n < content.length; n++)
		{
			if (n > 0) tdContent.append($('<br></br>'));

			let type = content[n].type, text = content[n].text, url = content[n].url, value = content[n].value;
			if (url)
			{
				if (type) tdContent.append(type + ': ');
				let ahref = $('<a target="_blank"></a>').appendTo(tdContent);
				ahref.attr('href', url);
				ahref.text(text ? text : url);
			}
			else
			{
				let line = (type ? type + ' = ' : '') + value;
				if (text) line += ' ' + text;
				tdContent.text(line);
			}
		}
	}
}

/* EOF */ }