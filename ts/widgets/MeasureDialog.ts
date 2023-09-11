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
	Used by MeasureTable: brings up a modal dialog that shows a single compound, and allows its measurements
	to be viewed and customised.
*/

export class MeasureDialog extends BootstrapDialog
{
	private cpd:MeasureCompound = null;
	private blkCompound:JQuery;
	private blkMeasure:JQuery;
	private blkTagging:JQuery;

	constructor(private data:MeasureData, private cpdidx:number)
	{
		super(cpdidx >= 0 ? 'Compound Measurements' : 'Measurement Tagging');
		this.withCloseButton = false;

		if (cpdidx >= 0) this.cpd = data.compounds[cpdidx];
	}

	// ------------ private methods ------------

	protected populateContent():void
	{
		this.areaTopRight.append('<td align="right"><button class="btn btn-action" data-dismiss="modal" aria-hidden="true">Done</button></td>');

		if (this.cpd != null)
		{
			this.blkCompound = $('<div></div>').appendTo(this.content);
			this.blkMeasure = $('<div></div>').appendTo(this.content);

			this.populateCompound();
			this.populateMeasurements();
		}
		else
		{
			this.blkTagging = $('<div></div>').appendTo(this.content);

			this.populateTagging();
		}
	}

	private populateCompound():void
	{
		this.blkCompound.empty();

		// draw the molecule

		let paraMol = $('<p align="center"></p>').appendTo(this.blkCompound);

		let policy = wmk.RenderPolicy.defaultColourOnWhite();
		let effects = new wmk.RenderEffects();
		let measure = new wmk.OutlineMeasurement(0, 0, policy.data.pointScale);
		let layout = new wmk.ArrangeMolecule(this.cpd.mol, measure, policy, effects);
		layout.arrange();

		let metavec = new wmk.MetaVector();
		new wmk.DrawMolecule(layout, metavec).draw();
		metavec.normalise();
		paraMol.append(metavec.createSVG());

		// external references

		if (this.cpd.pubchemCID > 0 || this.cpd.pubchemSID > 0)
		{
			let paraRef = $('<p>PubChem origins:</p>').appendTo(this.blkCompound);
			if (this.cpd.pubchemCID > 0)
			{
				paraRef.append(' compound ');
				let ahref = $('<a target="_blank"></a>').appendTo(paraRef);
				ahref.attr('href', 'http://pubchem.ncbi.nlm.nih.gov/compound/' + this.cpd.pubchemCID);
				ahref.text('CID ' + this.cpd.pubchemCID);
			}
			if (this.cpd.pubchemSID > 0)
			{
				paraRef.append(' substance ');
				let ahref = $('<a target="_blank"></a>').appendTo(paraRef);
				ahref.attr('href', 'http://pubchem.ncbi.nlm.nih.gov/substance/' + this.cpd.pubchemSID);
				ahref.text('SID ' + this.cpd.pubchemSID);
			}
		}
	}

	private populateMeasurements():void
	{
		this.blkMeasure.empty();

		let table = $('<table></table>').appendTo(this.blkMeasure);

		let doTH = (th:JQuery):void =>
		{
			th.css('text-decoration', 'underline');
			th.css('padding', '0.2em 0.5em 0.2em 0.5em');
		};
		let doTD = (td:JQuery):void =>
		{
			td.css('background-color', '#FBFBFF');
			td.css('padding', '0.2em 0.5em 0.2em 0.5em');
			td.css('border', '1px solid #CCD9E8');
		};

		let titles = ['Name', 'Type', 'Relation', 'Value', 'Units'];
		let tr = $('<tr></tr>').appendTo(table);
		for (let n = 0; n < titles.length; n++)
		{
			let th = $('<th></th>').appendTo(tr);
			th.text(titles[n]);
			doTH(th);
		}

		let assayList:number[] = [];
		for (let column of this.data.columns) if (assayList.indexOf(column.assayID) < 0) assayList.push(column.assayID);

		for (let n = 0; n < assayList.length; n++)
		{
			let measurements:MeasureDatum[] = [];
			for (let measure of this.cpd.measurements) if (this.data.columns[measure.column].assayID == assayList[n]) measurements.push(measure);
			if (measurements.length == 0) continue;

			if (assayList.length > 1)
			{
				let tr = $('<tr></tr>').appendTo(table);
				let tdAssay = $('<td colspan="3"></td>').appendTo(tr);
				let span = $('<span>Assay #' + (n + 1) + '</span>').appendTo(tdAssay);
				this.decorateAssay(span, assayList[n]);
			}

			measurements.sort((m1:MeasureDatum, m2:MeasureDatum):number =>
			{
				let c1 = this.data.columns[m1.column], t1 = c1.type == 'activity' ? 0 : c1.type == 'primary' ? 1 : 2;
				let c2 = this.data.columns[m2.column], t2 = c2.type == 'activity' ? 0 : c2.type == 'primary' ? 1 : 2;
				if (t1 < t2) return -1; else if (t1 > t2) return 1;
				return c1.name.localeCompare(c2.name);
			});

			for (let measure of measurements)
			{
				const column = this.data.columns[measure.column];

				tr = $('<tr></tr>').appendTo(table);

				// (need to show column.assayID; just the ID is useless though...)

				let tdName = $('<td></td>').appendTo(tr);
				tdName.text(column.name);
				doTD(tdName);

				let tdType = $('<td></td>').appendTo(tr);
				tdType.text(column.type);
				doTD(tdType);

				let tdRelation = $('<td></td>').appendTo(tr);
				tdRelation.text(measure.relation);
				doTD(tdRelation);

				let tdValue = $('<td></td>').appendTo(tr);
				tdValue.text(measure.value.toString());
				doTD(tdValue);

				let tdUnits = $('<td></td>').appendTo(tr);
				tdUnits.text(column.units);
				doTD(tdUnits);

				let tdTag = $('<td></td>').appendTo(tr);
				tdTag.css('padding', '0.2em 0.5em 0.2em 0.5em');
				if (column.tagName == null)
				{
					let btn = $('<button class="btn btn-normal"><span class="glyphicon glyphicon-tag"></span></button>').appendTo(tdTag);
					btn.click(() => this.tagColumn(column));
				}
				else
				{
					let btn = $('<button class="btn btn-action"></button>').appendTo(tdTag);
					btn.text(column.tagName);
					btn.click(() => this.untagColumn(column));
				}
			}
		}
	}

	private populateTagging():void
	{
		this.blkTagging.empty();

		let table = $('<table></table>').appendTo(this.blkTagging);
		let tr = $('<tr></tr>').appendTo(table);

		let td1 = $('<td style="padding-right: 1em; vertical-align: top;"></td>').appendTo(tr);
		let td2 = $('<td style="vertical-align: top;"></td>').appendTo(tr);
		td1.append('<h4>Available&nbsp;Measurements</h4>');
		td2.append('<h4>Output&nbsp;Columns</h4>');

		this.populateTaggingLeft(td1);
		this.populateTaggingRight(td2);
	}

	private populateTaggingLeft(parent:JQuery):void
	{
		let table = $('<table></table>').appendTo(parent);

		let assayList:number[] = [];
		for (let column of this.data.columns) if (assayList.indexOf(column.assayID) < 0) assayList.push(column.assayID);

		for (let n = 0; n < assayList.length; n++)
		{
			let tr = $('<tr></tr>').appendTo(table);
			// todo: want a description of the assay...
			let tdAssay = $('<td colspan="3"></td>').appendTo(tr);
			let span = $('<span>Assay #' + (n + 1) + '</span>').appendTo(tdAssay);
			this.decorateAssay(span, assayList[n]);

			let columns:MeasureColumn[] = [], excl = new Set<string>();
			for (let col of this.data.columns) if (col.assayID == assayList[n] && !excl.has(col.name))
			{
				columns.push(col);
				excl.add(col.name);
			}
			columns.sort((c1:MeasureColumn, c2:MeasureColumn) => c1.name < c2.name ? -1 : c1.name > c2.name ? 1 : 0);

			for (let i = 0; i < columns.length; i++)
			{
				let tr = $('<tr></tr>').appendTo(table);
				let td:JQuery[] = [];
				for (let j = 0; j < 4; j++)
				{
					td[j] = $('<td></td>').appendTo(tr);
					td[j].css('vertical-align', 'middle');
					td[j].css('padding', '0.2em 0.5em 0.2em 0.5em');
					if (j < 3)
					{
						td[j].css('background-color', '#FBFBFF');
						td[j].css('border', '1px solid #CCD9E8');
					}
				}
				td[0].text(columns[i].name);
				td[1].text(columns[i].units);
				td[2].text(columns[i].type);

				if (columns[i].tagName == null)
				{
					let btn = $('<button class="btn btn-normal"><span class="glyphicon glyphicon-tag"></span></button>').appendTo(td[3]);
					const column = columns[i];
					btn.click(() => this.tagColumn(column));
				}
				else
				{
					let btn = $('<button class="btn btn-action"></button>').appendTo(td[3]);
					btn.text(columns[i].tagName);
					const column = columns[i];
					btn.click(() => this.untagColumn(column));
				}
			}
		}
	}

	private populateTaggingRight(parent:JQuery):void
	{
		let tagList:string[] = [];
		for (let col of this.data.columns) if (col.tagName != null && tagList.indexOf(col.tagName) < 0) tagList.push(col.tagName);

		if (tagList.length == 0)
		{
			parent.append('<p>No measurements have been tagged yet.</p>');
			return;
		}

		tagList.sort();

		for (let n = 0; n < tagList.length; n++)
		{
			let para = $('<p></p>').appendTo(parent);
			para.css('font-weight', '600');
			para.text(tagList[n]);

			let columns:MeasureColumn[] = [];
			for (let col of this.data.columns) if (col.tagName == tagList[n]) columns.push(col);
			columns.sort((c1:MeasureColumn, c2:MeasureColumn) =>
			{
				if (c1.assayID < c2.assayID) return -1; else if (c1.assayID > c2.assayID) return 1;
				return c1.name < c2.name ? -1 : c1.name > c2.name ? 1 : 0;
			});
			for (let i = 0; i < columns.length; i++)
			{
				para = $('<p></p>').appendTo(parent);
				para.css('padding', '0 0 0 1em');
				para.text(columns[i].name + ' (' + columns[i].type);
				if (columns[i].units) para.append(', ' + columns[i].units);
				if (this.data.assayIDList.length > 1)
				{
					let idx = this.data.assayIDList.indexOf(columns[i].assayID) + 1;
					para.append(', assay #' + idx);
				}
				para.append(')');
			}
		}
	}

	private tagColumn(col:MeasureColumn):void
	{
		let txt = prompt('Enter tag name, which will be used for output columns:', '');
		if (!txt) return;
		if (txt.indexOf(',') >= 0)
		{
			alert('Tag names cannot have commas.');
			return;
		}

		col.tagName = txt;
		if (this.cpd != null) this.populateMeasurements(); else this.populateTagging();
	}

	private untagColumn(col:MeasureColumn):void
	{
		col.tagName = null;
		if (this.cpd != null) this.populateMeasurements(); else this.populateTagging();
	}

	// fetch an assay and provide information about it
	private decorateAssay(parent:JQuery, assayID:number):void
	{
		let params = {'assayID': assayID};
		callREST('REST/GetAssay', params,
			(data:any) =>
			{
				let text:string = data.text;
				let pubchemAID:number = data.pubchemAID;
				let annots:any[] = data.annotations;

				if (pubchemAID > 0)
				{
					parent.append(' (');
					let href = $('<a target="_blank">PubChem AID ' + pubchemAID + '</a>').appendTo(parent);
					href.attr('href', restBaseURL + '/assign.jsp?pubchemAID=' + pubchemAID);
					parent.append(')');
					// (note: should still have a link out if no PubChem AID...)
				}

				let tiptext = '';
				if (text)
				{
					for (let line of text.split('\n'))
					{
						if (!line) continue;
						if (tiptext.length > 0) tiptext += ' ';
						if (line.length < 80)
						{
							tiptext += line;
							if (tiptext.length > 100) break;
						}
						else
						{
							let cutpos = Math.min(100, line.length - 1);
							for (; cutpos > 0; cutpos--) if (line.charAt(cutpos) == ' ') break;
							if (cutpos == 0) cutpos = Math.min(80, line.length);
							tiptext += line.substring(0, cutpos) + '...';
							break;
						}
					}
				}
				tiptext += '<br><i>Annotations:</i> <b>' + annots.length + '</b>';

				Popover.hover(domLegacy(parent), null, tiptext);
			});
	}
}

/* EOF */ }
