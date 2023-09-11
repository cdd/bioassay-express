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
	Copying assay content to the clipboard: brings up a bunch of choices about which parts of the assay to copy, and
	what format. There are enough degrees of freedom to justify an entire dialog box.
*/

const enum Format
{
	JSON,
	TSV,
	Text
}

export class CopyAssayDialog extends BootstrapDialog
{
	private btnCopy:JQuery;
	private format = Format.JSON;
	private chkText:JQuery;
	private chkSchema:JQuery;
	private chkUniqueID:JQuery;
	private listChecks:JQuery[];
	private listAnnots:AssayAnnotation[];

	constructor(private assay:AssayDefinition, private schema:SchemaSummary)
	{
		super('Copy Assay Content');
		this.withCloseButton = false;
	}

	public performHeadlessCopy(format:Format = Format.JSON):void
	{
		this.format = format;
		this.doHeadlessCopy();
	}

	// ------------ private methods ------------

	protected populateContent():void
	{
		// top-right buttons
		this.btnCopy = $('<button class="btn btn-action"></button>').appendTo(this.areaTopRight);
		this.btnCopy.css('margin-right', '0.5em');
		this.btnCopy.append('<span class="glyphicon glyphicon-copy"></span> Copy<span id="dlgCopyAssayButton"></span>');
		this.btnCopy.click(() => this.doCopy());
		this.areaTopRight.append('<button id="dlgCopyAssayClose" class="btn btn-normal" data-dismiss="modal" aria-hidden="true">Cancel</button>');

		// main content

		let divLine = $('<div class="flexbar"></div>').appendTo(this.content);
		divLine.append('<div>Format</div>');
		let divFormat = $('<div class="btn-group" data-toggle="buttons"></div>').appendTo(divLine);
		let lblJSON = $('<label class="btn btn-radio"></label>').appendTo(divFormat);
		let lblTSV = $('<label class="btn btn-radio"></label>').appendTo(divFormat);
		let lblText = $('<label class="btn btn-radio"></label>').appendTo(divFormat);
		lblJSON.append('<input type="radio" name="options" autocomplete="off">JSON</input>');
		lblTSV.append('<input type="radio" name="options" autocomplete="off">Tab-separated</input>');
		lblText.append('<input type="radio" name="options" autocomplete="off">Readable Text</input>');
		lblJSON.click(() => this.format = Format.JSON);
		lblTSV.click(() => this.format = Format.TSV);
		lblText.click(() => this.format = Format.Text);

		lblJSON.click();

		if (this.assay.text)
		{
			divLine = $('<div></div>').appendTo(this.content);
			this.chkText = $('<input type="checkbox" id="dlgCopyAssayText"></input>').appendTo(divLine);
			divLine.append(' <label for="dlgCopyAssayText" style="font-weight: normal;">Include text</label>');
		}
		if (this.assay.schemaURI) // (always true?)
		{
			divLine = $('<div></div>').appendTo(this.content);
			this.chkSchema = $('<input type="checkbox" id="dlgCopyAssaySchema"></input>').appendTo(divLine);
			divLine.append(' <label for="dlgCopyAssaySchema" style="font-weight: normal;">Include schema</label>');
		}
		if (this.assay.uniqueID)
		{
			divLine = $('<div></div>').appendTo(this.content);
			this.chkUniqueID = $('<input type="checkbox" id="dlgCopyAssayUniqueID"></input>').appendTo(divLine);
			divLine.append(' <label for="dlgCopyAssayUniqueID" style="font-weight: normal;">Include identifier</label>');
		}

		if (this.assay.annotations.length > 0)
		{
			divLine = $('<div class="flexbar"></div>').appendTo(this.content);

			divLine.append('<div><b>Annotations</b></div>');

			let btnSelAll = $('<button class="btn btn-normal"></button>').appendTo($('<div></div>').appendTo(divLine));
			btnSelAll.append('<span class="glyphicon glyphicon-ok-sign"></span> All');
			Popover.hover(domLegacy(btnSelAll), null, 'Check all results, to be included in the compound list.');
			btnSelAll.click(() => this.selectAllAnnotations(true));

			let btnSelNone = $('<button class="btn btn-normal"></button>').appendTo($('<div></div>').appendTo(divLine));
			btnSelNone.append('<span class="glyphicon glyphicon-remove-sign"></span> None');
			Popover.hover(domLegacy(btnSelNone), null, 'Uncheck all results.');
			btnSelNone.click(() => this.selectAllAnnotations(false));
		}

		this.renderAnnotations(this.content);
	}

	// action time: copy the requested content to the clipboard
	private doCopy():void
	{
		let text = this.chkText && this.chkText.prop('checked') ? this.assay.text : null;
		let schemaURI = this.chkSchema && this.chkSchema.prop('checked') ? this.assay.schemaURI : null;
		let schemaBranches = this.chkSchema && this.chkSchema.prop('checked') ? this.assay.schemaBranches : null;
		let schemaDuplication = this.chkSchema && this.chkSchema.prop('checked') ? this.assay.schemaDuplication : null;
		let uniqueID = this.chkUniqueID && this.chkUniqueID.prop('checked') ? this.assay.uniqueID : null;
		let annotList:AssayAnnotation[] = [];
		for (let n = 0; n < this.listChecks.length; n++) if (this.listChecks[n].prop('checked')) annotList.push(this.listAnnots[n]);

		if (this.format == Format.JSON) this.copyAsJSON(text, schemaURI, schemaBranches, schemaDuplication, uniqueID, annotList);
		else if (this.format == Format.TSV) this.copyAsTSV(text, schemaURI, uniqueID, annotList);
		else if (this.format == Format.Text) this.copyAsText(text, schemaURI, uniqueID, annotList);

		this.dlg.modal('hide');
	}

	private doHeadlessCopy():void
	{
		let text = this.assay.text;
		let schemaURI = this.assay.schemaURI;
		let schemaBranches = this.assay.schemaBranches;
		let schemaDuplication = this.assay.schemaDuplication;
		let uniqueID = this.assay.uniqueID;
		let annotList:AssayAnnotation[] = [];

		for (let annot of this.assay.annotations) annotList.push(annot);

		if (this.format == Format.JSON) this.copyAsJSON(text, schemaURI, schemaBranches, schemaDuplication, uniqueID, annotList);
		else if (this.format == Format.TSV) this.copyAsTSV(text, schemaURI, uniqueID, annotList);
		else if (this.format == Format.Text) this.copyAsText(text, schemaURI, uniqueID, annotList);
	}

	private selectAllAnnotations(sel:boolean):void
	{
		for (let chk of this.listChecks) chk.prop('checked', sel);
	}

	// render a list of annotations, in the same order as their assignments appear in the schema, with the assignment
	// names rather than labels pulled from the underlying ontology
	private renderAnnotations(parent:JQuery):void
	{
		let assnGroup:Record<string, AssayAnnotation[]> = {};
		for (let annot of this.assay.annotations)
		{
			let key = keyPropGroup(annot.propURI, annot.groupNest), grp = assnGroup[key];
			if (grp) grp.push(annot); else assnGroup[key] = [annot];
		}

		this.listChecks = [];
		this.listAnnots = [];

		for (let assn of this.schema.assignments)
		{
			let grp = assnGroup[keyPropGroup(assn.propURI, assn.groupNest)];
			if (grp) for (let annot of grp)
			{
				let p = $('<p></p>').appendTo(parent);
				p.css('padding', '0.4em 0 0.4em 1em');
				p.css('margin', '0');

				let chk = $('<input type="checkbox"></input>').appendTo(p);
				chk.prop('checked', true);

				let toggle = ():JQuery => chk.prop('checked', !chk.prop('checked'));

				p.append('&nbsp;');

				if (assn.groupLabel) for (let i = assn.groupLabel.length - 1; i >= 0; i--)
				{
					let blkGroup = $('<font></font>').appendTo(p);
					blkGroup.css('background', 'white');
					blkGroup.css('border-radius', 5);
					blkGroup.css('border', '1px solid black');
					blkGroup.css('padding', '0.3em');
					blkGroup.css('user-select', 'none');
					blkGroup.click(toggle);
					blkGroup.text(assn.groupLabel[i]);

					p.append(' ');
				}

				let blkProp = $('<font></font>').appendTo(p);
				blkProp.addClass('weakblue');
				blkProp.css('border-radius', 5);
				blkProp.css('border', '1px solid black');
				blkProp.css('padding', '0.3em');
				blkProp.css('user-select', 'none');
				blkProp.click(toggle);
				blkProp.append($(Popover.displayOntologyAssn(assn).elHTML));

				p.append(' ');

				let vlabel = annot.valueLabel;
				if (!vlabel) vlabel = '?';
				if (vlabel.length > 100) vlabel = vlabel.substring(0, 100) + '...';

				if (annot.valueURI)
				{
					let blkValue = $('<font></font>').appendTo(p);
					blkValue.addClass('lightgray');
					blkValue.css('border-radius', 5);
					blkValue.css('border', '1px solid black');
					blkValue.css('padding', '0.3em');
					blkValue.css('user-select', 'none');
					blkValue.click(toggle);
					blkValue.append($(Popover.displayOntologyValue(annot).elHTML));
				}
				else
				{
					let blkText = $('<i></i>').appendTo(p);
					blkText.css('user-select', 'none');
					blkText.click(toggle);
					blkText.text(annot.valueLabel);
				}

				this.listChecks.push(chk);
				this.listAnnots.push(annot);
			}
		}
	}

	// copies all the current annotations onto the clipboard, using JSON format: this is the most useful machine readable format
	// for use by various custom scripts
	public copyAsJSON(text:string, schemaURI:string, schemaBranches:SchemaBranch[], schemaDuplication:SchemaDuplication[],
					  uniqueID:string, annotList:AssayAnnotation[]):void
	{
		let content:any = {};
		if (text) content.text = text;
		if (schemaURI) content.schemaURI = schemaURI;
		if (schemaBranches) content.schemaBranches = schemaBranches;
		if (schemaDuplication) content.schemaDuplication = schemaDuplication;
		if (uniqueID) content.uniqueID = uniqueID;
		if (annotList.length > 0) content.annotations = annotList;

		if (this.content) copyToClipboard(JSON.stringify(content, null, 2), this.content[0]);
		else copyToClipboard(JSON.stringify(content, null, 2));
	}

	// copies the current annotations using tab-separated-values: this is not quite as ideal as JSON for machine readability, but
	// it is convenient for pasting into spreadsheets
	public copyAsTSV(text:string, schemaURI:string, uniqueID:string, annotList:AssayAnnotation[]):void
	{
		let lines:string[] = [];

		if (text) lines.push('text\t' + text);
		if (schemaURI) lines.push('schemaURI\t' + schemaURI);
		if (uniqueID) lines.push('uniqueID\t' + uniqueID);

		let header =
		[
			'property abbrevation',
			'property URI',
			'property label',
			'value abbreviation',
			'value URI',
			'value label'
		];
		lines.push(header.join('\t'));

		for (let annot of annotList)
		{
			let entry =
			[
				collapsePrefix(annot.propURI),
				annot.propURI,
				annot.propLabel,
				collapsePrefix(annot.valueURI),
				annot.valueURI,
				annot.valueLabel
			];
			lines.push(entry.join('\t'));
		}

		if (this.content) copyToClipboard(lines.join('\n'), this.content[0]);
		else copyToClipboard(lines.join('\n'));
	}

	// copies the current annotations as human readable text
	public copyAsText(text:string, schemaURI:string, uniqueID:string, annotList:AssayAnnotation[]):void
	{
		let lines:string[] = [];

		if (schemaURI) lines.push('Schema: [' + this.schema.name + '] <' + this.schema.schemaURI + '>');
		if (uniqueID)
		{
			let [src, id] = UniqueIdentifier.parseKey(uniqueID);
			lines.push('UniqueID: ' + src.name + ' ' + id);
		}

		lines.push('---- Annotations ----');
		for (let assn of this.schema.assignments)
		{
			lines.push(assn.name + ' <' + collapsePrefix(assn.propURI) + '> ' + collapsePrefixes(assn.groupNest));

			let values:string[][] = [];
			for (let annot of annotList)
				if (samePropGroupNest(annot.propURI, annot.groupNest, assn.propURI, assn.groupNest)) values.push([annot.valueLabel, annot.valueURI]);
			values.sort((v1:string[], v2:string[]) => v1[0].localeCompare(v2[0]));

			if (values.length == 0) lines.push('    (none)');
			for (let v of values) lines.push('    ' + v[0] + ' <' + v[1] + '>');
		}

		if (this.content) copyToClipboard(lines.join('\n'), this.content[0]);
		else copyToClipboard(lines.join('\n'));
	}
}

/* EOF */ }
