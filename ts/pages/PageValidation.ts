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
	Supporting functionality for the validation report page.
*/

interface ValidationSchemaTerm
{
	idx:number;
	propURI:string;
	groupNest:string[];
	valueURI:string;
}

// interface ValidationResultTerm
// {
// 	uniqueID:string;
// 	assnName:string;
// 	assnURI:string;
// 	absenceName:string;
// 	absenceURI:string;
// 	text:string;
// 	date:string;
// }

interface SchemaCheckResult
{
	assayID:number;
	uniqueID:string;
	schemaURI:string;
	propURI:string;
	propLabel:string;
	groupNest:string[];
	valueURI:string;
	valueLabel:string;
}

interface Result
{
	assayID:number;
	uniqueID:string;
	propURI?:string;
	groupNest?:string[];
	valueURI?:string;
	valueLabel?:string;
}

interface ValidationResult
{
	results?:Result[][][];
	assaySet?:Set<number>;
	orphans?:SchemaCheckResult[];
}

// overview table
class PageValidationBlock
{
	public div:JQuery;
	public summaryBlocks:JQuery[][]; // individual table cells
	public rowTotalBlocks:JQuery[]; // row total cells
	public columnTotalBlocks:JQuery[]; // column total cells
	public orphanBlock:JQuery; // orphan count cell
}

const SPECIAL_OUTOFSCHEMA = '_outOfSchema';
const SPECIAL_BLANK = '_blank';
const SPECIAL_AXIOM = '_axiom';
const SPECIAL_NUMBER = '_number';
let ABSENCE_TERMS =
[
	SPECIAL_OUTOFSCHEMA,
	SPECIAL_BLANK,
	SPECIAL_AXIOM,
	SPECIAL_NUMBER,
	ABSENCE_UNKNOWN,
	ABSENCE_REQUIRESTERM,
	ABSENCE_NEEDSCHECKING,
	ABSENCE_AMBIGUOUS,
	ABSENCE_MISSING,
	ABSENCE_DUBIOUS,
];
const ABSENCE_DESCR =
[
	'Assays containing terms that are not in the schema tree, possibly because the schema has been subsequently modified.',
	'Assays for which at least one of the mandatory assignments has been left blank.',
	'Assays containing terms that are marked as contradictory due to axiom rules.',
	'Assays with misformatted numeric data.',
	'Assays with the <i>unknown</i> term indicated.',
	'Assays marked with <i>requires term</i>.',
	'Assays with the <i>needs checking</i> term indicated.',
	'Assays with the <i>ambiguous</i> term indicated.',
	'Assays with the <i>missing</i> term indicated.',
	'Assays with the <i>dubious</i> term indicated.',
];
/* ... replace with: don't complain about anything in the absence tree, even the "parent" branch
let ACCEPTABLE_ABSENCE_TERMS =
[
	ABSENCE_NOTAPPLICABLE,
	ABSENCE_NOTDETERMINED,
];*/
const ACCEPTABLE_ABSENCE_TERMS =
[
	...ALL_ABSENCE_TERMS,
	'http://www.bioassayontology.org/bat#Absence',
];

export class PageValidation
{
	// control retrieval of information from server
	private searchRoster:number[] = []; // identifiers waiting to be submitted
	private progress:ProgressBar;

	// currently displayed validation results
	private currentSchemaURI:string = null;
	private block:PageValidationBlock;
	private templateOverview:JQuery;
	private templateHeader:JQuery;
	private drillDownDiv:JQuery;

	// information collected for the various schemas
	private validationResults:Record<string, ValidationResult> = {};
	private currentValidationResults:ValidationResult = {};

	constructor(private availableTemplates:TemplateSummary[])
	{
		ScrollToTop.enable();
		this.currentSchemaURI = this.availableTemplates[0].schemaURI;
		for (let template of this.availableTemplates)
		{
			this.validationResults[template.schemaURI] = {};
		}
	}

	// assembles the page from scratch
	public build():void
	{
		let main = $('#main');
		main.empty();

		this.progress = new ProgressBar(500, 20, () => this.stopSearch());
		this.progress.render(main);

		this.renderTemplateSelector(main);
		this.templateHeader = $('<div id="header"/>').appendTo(main);
		this.templateHeader.css({'position': 'sticky', 'top': '0px'});

		this.templateOverview = $('<div id="overview"/>').appendTo(main);

		this.block = new PageValidationBlock();
		this.block.div = $('<div/>').appendTo(main);

		this.drillDownDiv = $('<div/>').appendTo(main).text('drill-down');

		this.retrieveTemplateSchemas();
	}

	// ------------ private methods ------------

	private renderTemplateSelector(container:JQuery):void
	{
		if (this.availableTemplates.length == 0) return;

		let div = $('<div/>').appendTo(container);
		let selector = $('<div class="btn-group" data-toggle="buttons"/>').appendTo(div);
		selector.css('margin', '0.5em 0em');

		for (let template of this.availableTemplates)
		{
			let lbl = $('<label class="btn btn-radio"/>').appendTo(selector);
			lbl.append($('<input type="radio"/>'));
			lbl.append(escapeHTML(template.title));
			if (this.currentSchemaURI == template.schemaURI) lbl.addClass('active');
			lbl.click(() =>
			{
				if (this.currentSchemaURI == template.schemaURI) return;

				this.currentSchemaURI = template.schemaURI;
				this.drillDownDiv.empty();

				this.currentValidationResults = {};
				this.renderTemplateOverview();
				this.populateOutlineSummary();
				this.updateSearchResults();
			});
		}
	}

	private renderTemplateOverview():void
	{
		let schema = TemplateManager.getTemplate(this.currentSchemaURI);

		this.templateHeader.empty();
		let div = $('<div/>').appendTo(this.templateHeader);
		div.css({'display': 'flex', 'justify-content': 'space-between', 'align-items': 'center'});
		div.css({'border-top': 'solid 1px black', 'border-bottom': 'solid 1px black', 'padding': '0.5em'});
		div.css({'background-color': 'white'});

		let title = $('<div/>').appendTo(div);
		title.css({'text-align': 'center', 'font-weight': 'bold', 'font-size': 'large'});
		title.text(schema.name);

		let btnDiv = $('<div/>').appendTo(div);
		let btn = $('<button class="btn btn-normal"/>').appendTo(btnDiv);
		btn.append('<span class="glyphicon glyphicon-copy"></span> Copy all to Clipboard');
		btn.click(() => this.copyValidationResults(this.validationResults[this.currentSchemaURI]));

		btn = $('<button class="btn btn-normal"/>').appendTo(btnDiv);
		btn.append('<span class="glyphicon glyphicon-copy"></span> Copy selected to Clipboard');
		btn.css({'margin-left': '0.5em'});
		btn.click(() => this.copyValidationResults(this.currentValidationResults));

		this.templateOverview.empty();
		if (schema.descr) $('<p/>').appendTo(this.templateOverview).text(schema.descr);
	}

	// creates the table skeleton for summary counts
	private populateOutlineSummary():void
	{
		let block = this.block;
		let schema = TemplateManager.getTemplate(this.currentSchemaURI);

		let commonCSS = {'padding': '0.2em', 'white-space': 'nowrap', 'text-align': 'center', 'vertical-align': 'middle'};
		block.div.empty();

		let table = $('<table/>').appendTo(block.div);
		table.css('margin-top', '1em');
		let thead = $('<thead/>').appendTo(table);
		let tr = $('<tr/>').appendTo(thead);

		let th = $('<th/>').appendTo(tr);
		th.css({...commonCSS, 'text-align': 'left', 'text-decoration': 'underline'});
		th.text('Assignment');
		for (let n = 0; n < ABSENCE_TERMS.length; n++)
		{
			let th = $('<th/>').appendTo(tr);
			th.css({...commonCSS, 'border': '1px solid black'});
			let span = $('<span/>').appendTo(th);
			span.text(this.absenceTermName(ABSENCE_TERMS[n]));
			Popover.hover(domLegacy(span), null, ABSENCE_DESCR[n]);
		}

		block.summaryBlocks = [];
		block.columnTotalBlocks = [];
		block.rowTotalBlocks = [];
		let renderAssn = (depth:number, assn:SchemaHierarchyAssignment):void =>
		{
			tr = $('<tr/>').appendTo(table);

			let td = $('<td/>').appendTo(tr);
			td.css({...commonCSS, 'text-align': 'left'});
			td.css('padding-left', (0.2 + 0.5 * depth) + 'em');
			td.append($(Popover.displayOntologyAssn(assn).elHTML));

			let colBlk:JQuery[] = [];
			for (let i = 0; i < ABSENCE_TERMS.length; i++)
			{
				td = $('<td>0</td>').appendTo(tr);
				td.css({...commonCSS, 'border': '1px solid #A0A0A0', 'background-color': '#FFFFFF'});
				if (ABSENCE_TERMS[i] == SPECIAL_BLANK && !assn.mandatory)
				{
					td.css('color', '#C0C0C0');
					td.text('n/a');
				}
				colBlk.push(td);
			}
			block.summaryBlocks.push(colBlk);

			// add the final column that has row totals
			td = $('<td>0</td>').appendTo(tr);
			td.css({...commonCSS, 'padding': '0.2em 1em 0.2em 1em', 'text-align': 'left', 'font-weight': 'bold'});
			block.rowTotalBlocks.push(td);
		};
		let renderGroup = (depth:number, group:SchemaHierarchyGroup):void =>
		{
			if (group.parent != null)
			{
				let tr = $('<tr/>').appendTo(table);
				let td = $('<td/>').appendTo(tr);
				let blk = $('<div>').appendTo(td);
				blk.css({'text-decoration': 'underline', 'margin-left': `${(0.5 * depth)}em`});
				blk.text(group.name);

				if (group.descr)
				{
					let tip = group.descr;
					if (group.groupURI) tip = `Abbrev: <i>${collapsePrefix(group.groupURI)}</i><br>` + tip;
					Popover.hover(domLegacy(blk), null, tip);
				}
			}

			for (let assn of group.assignments) renderAssn(depth + 1, assn);
			for (let subgrp of group.subGroups) renderGroup(depth + 1, subgrp);
		};

		renderGroup(-1, new SchemaHierarchy(schema).root);

		// add a row for totals
		block.columnTotalBlocks = [];
		tr = $('<tr/>').appendTo(table);
		tr.append('<td/>');
		for (let n = 0; n < ABSENCE_TERMS.length; n++)
		{
			let td = $('<td>0</td>').appendTo(tr);
			td.css({...commonCSS, 'font-weight': 'bold', 'border-top': '2px solid black'});
			block.columnTotalBlocks.push(td);
		}

		// add a row for orphans
		tr = $('<tr/>').appendTo(table);
		let td = $('<td/>').appendTo(tr);
		td.append('<b>orphans</b>');
		block.orphanBlock = $('<td>0</td>').appendTo(tr);
		block.orphanBlock.css({...commonCSS, 'border': '1px solid #808080'});
	}

	private getAbsenceTerms():ValidationSchemaTerm[]
	{
		let absenceTerms = [];
		let absenceAlready = new Set<string>();

		for (let {schemaURI} of Object.values(this.availableTemplates))
		{
			let schema = TemplateManager.getTemplate(schemaURI);
			// populate the table with schema-specific parts, and also gather the terms
			for (let n = 0; n < schema.assignments.length + 1; n++)
			{
				let assn = n < schema.assignments.length ? schema.assignments[n] : null;
				if (assn != null && !absenceAlready.has(assn.propURI))
				{
					let term:ValidationSchemaTerm =
					{
						'idx': n,
						'propURI': schema.assignments[n].propURI,
						'groupNest': schema.assignments[n].groupNest,
						'valueURI': 'http://www.bioassayontology.org/bat#Absence'
					};
					absenceTerms.push(term);
				}
			}
		}
		return absenceTerms;
	}

	private retrieveTemplateSchemas():void
	{
		let schemaList = this.availableTemplates.map((t) => t.schemaURI);
		TemplateManager.ensureTemplates(schemaList, () =>
		{
			this.renderTemplateOverview();
			this.populateOutlineSummary();
			this.initializeValidationResults();
			(async () => this.retrieveValidationData())();
		});
	}

	private async retrieveValidationData():Promise<void>
	{
		try
		{
			let params =
			{
				'withAssayID': true,
				'withUniqueID': false,
				'withCurationTime': false,
			};
			let data = await asyncREST('REST/ListCuratedAssays', params);
			this.searchRoster = data.assayIDList;
		}
		catch (e)
		{
			alert('Fetching search identifiers failed');
			return;
		}

		let absenceTerms = this.getAbsenceTerms();
		this.progress.setProgress(0);
		let searchPos = 0;
		let searchSize = Math.max(this.searchRoster.length, 1);
		while (this.searchRoster.length > 0)
		{
			let batch = this.searchRoster.splice(0, 20);
			searchPos += batch.length;

			let data = await asyncREST('REST/SchemaCheck', {'assayIDList': batch});
			this.contemplateSchemaCheck(data.outOfSchema, data.missingMandatory);
			this.contemplateSpecificList(data.axiomViolation, SPECIAL_AXIOM);
			this.contemplateSpecificList(data.numberMisformat, SPECIAL_NUMBER);

			let params = {'assayIDList': batch, 'search': absenceTerms, 'threshold': 0};
			data = await asyncREST('REST/Search', params);
			this.contemplateSearchResults(data.results);

			this.updateSearchResults();
			this.progress.setProgress(searchPos / searchSize);
		}
		this.progress.remove();
	}

	// called when the search is stopped
	private stopSearch():void
	{
		this.searchRoster = [];
		this.progress.remove();
	}

	private initializeValidationResults():void
	{
		for (let template of this.availableTemplates)
		{
			let schemaURI = template.schemaURI;
			let schema = TemplateManager.getTemplate(schemaURI);

			let validationResult = this.validationResults[schemaURI];
			validationResult.assaySet = new Set<number>();
			validationResult.orphans = [];

			let results:Result[][][] = [];
			for (let n = 0; n < schema.assignments.length; n++)
			{
				results.push([]);
				for (let m = 0; m < ABSENCE_TERMS.length; m++)
					results[n][m] = [];
			}
			validationResult.results = results;
		}
	}

	private updateSearchResults():void
	{
		let block = this.block;

		let {results, assaySet, orphans} = this.validationResults[this.currentSchemaURI];

		let columnTotals = new Array(block.columnTotalBlocks.length).fill(0);
		for (let [nrow, validRow] of enumerate(results))
		{
			let summaryCounts = validRow.map((col) => col.length);
			for (let [div, count, column] of zipArrays(block.summaryBlocks[nrow], summaryCounts, ABSENCE_TERMS))
			{
				if (div.text() == 'n/a') continue;
				let ncol = ABSENCE_TERMS.indexOf(column);
				this.changeCount(div, count, count, () => this.drillDown(nrow, ncol));
				columnTotals[ncol] += count;
			}

			let rowTotal = Vec.sum(summaryCounts);
			let rowPercent = assaySet.size > 0 ? rowTotal * 100 / assaySet.size : 0;

			let div = block.rowTotalBlocks[nrow];
			let text = `${rowTotal} (${rowPercent.toFixed(1)}%)`;
			this.changeCount(div, rowTotal, text, () => this.drillDown(nrow, null));
		}

		for (let [div, count, column] of zipArrays(block.columnTotalBlocks, columnTotals, ABSENCE_TERMS))
		{
			this.changeCount(div, count, count, () => this.drillDown(null, ABSENCE_TERMS.indexOf(column)));
		}

		let orphanCount = orphans.length;
		this.changeCount(block.orphanBlock, orphanCount, orphanCount, () => this.drillDownOrphans());
	}

	private changeCount(div:JQuery, count:number, content:(string | number), clickCallback:() => void):void
	{
		div.text(content.toString());
		if (count > 0)
		{
			div.css('background-color', '#E6EDF2');
			div.addClass('pseudoLink');
			div.off('click').on('click', clickCallback);
		}
	}

	private drillDownOrphans():void
	{
		let {orphans} = this.validationResults[this.currentSchemaURI];

		this.currentValidationResults = {'orphans': orphans};

		this.drillDownDiv.empty();
		let commonCSS = this.commonDrillDownCSS;
		let grouped = groupBy(orphans, (orphan) => orphan.assayID, -1);

		let h2 = $('<h2/>').appendTo(this.drillDownDiv);
		h2.append('Orphans');

		let table = this.drillDownTable(['Assay', 'Orphans']);
		for (let key in grouped)
		{
			let group = grouped[key];
			let tr = $('<tr/>').appendTo(table);
			let td = $('<th/>').appendTo(tr).css({...commonCSS, 'text-align': 'left', 'white-space': 'nowrap'});
			td.append(this.assayLink(group[0]));

			td = $('<td>').appendTo(tr).css({...commonCSS, 'text-align': 'left', 'white-space': 'nowrap'});
			for (let checkResult of group)
			{
				let div = $('<div/>').appendTo(td);
				div.append(checkResult.propLabel).append(': ');
				div.append(checkResult.valueLabel || 'blank').append(' (');
				let assn = {'groupLabel': collapsePrefixes(checkResult.groupNest), 'name': collapsePrefix(checkResult.propURI)};
				div.append(this.assignmentLabel(assn as SchemaAssignment)).append(')');
			}
		}
	}

	private drillDown(assnidx:number, absenceidx:number):void
	{
		// collect validation results matching query
		let {results} = this.validationResults[this.currentSchemaURI];
		let data:[number, number, Result][] = [];
		this.currentValidationResults = {'results': []};
		for (let [nrow, row] of enumerate(results))
		{
			if (assnidx != null && assnidx != nrow) continue;
			for (let [ncol, cell] of enumerate(row))
			{
				if (absenceidx != null && absenceidx != ncol) continue;
				let cellContent:[number, number, Result][] = cell.map((result) => [nrow, ncol, result]);
				data.push(...cellContent);
				if (cell.length > 0)
				{
					if (!this.currentValidationResults.results[nrow]) this.currentValidationResults.results[nrow] = [];
					this.currentValidationResults.results[nrow][ncol] = cell;
				}
			}
		}

		this.drillDownDiv.empty();
		for (let [n, absenceTerm] of enumerate(ABSENCE_TERMS))
		{
			let filtered = data.filter((result) => result[1] == n);
			if (filtered.length == 0) continue;

			let h2 = $('<h2/>').appendTo(this.drillDownDiv);
			h2.append(this.absenceTermName(absenceTerm));
			$('<div/>').appendTo(this.drillDownDiv).append(ABSENCE_DESCR[n]);

			if (absenceTerm == SPECIAL_BLANK)
				this.drillDownBlank(data);
			else if (absenceTerm == SPECIAL_AXIOM)
				this.drillDownAxiom(data);

			if (absenceTerm == SPECIAL_OUTOFSCHEMA)
				this.drillDownOutOfSchema(filtered.map((r) => r[2]));
			else
				this.drillDownTableByAssignment(filtered);
		}

		$('html, body').animate({'scrollTop': this.drillDownDiv.offset().top}, 500);
	}

	private commonDrillDownCSS = {'padding': '0.2em', 'text-align': 'center', 'vertical-align': 'top'};

	private drillDownTable(labels:string[]):JQuery
	{
		let table = $('<table/>').appendTo(this.drillDownDiv);
		let tr = $('<tr/>').appendTo($('<thead/>').appendTo(table));
		for (let label of labels)
		{
			let th = $('<th/>').appendTo(tr);
			th.css(this.commonDrillDownCSS);
			th.append(label);
		}
		return table;
	}

	private drillDownBlank(results:[number, number, Result][]):void
	{
		let schema = TemplateManager.getTemplate(this.currentSchemaURI);

		let grouped = groupBy(results, (element:[number, number, Result]) => element[2].uniqueID || element[2].assayID, -1);
		let assays = Object.keys(grouped);

		let text = [];
		if (assays.length == 1)
			text.push('One assay has missing required assignments.');
		else
			text.push(`${assays.length} assays have missing required assignments.`);
		if (assays.length > 5) text.push('The top-5 assays are:');
		$('<div/>').appendTo(this.drillDownDiv).text(text.join(' '));

		let commonCSS = this.commonDrillDownCSS;

		let table = this.drillDownTable(['Assay', 'Number', 'Assignments']);
		for (let key of Object.keys(grouped).slice(0, 5))
		{
			let group = grouped[key];
			let tr = $('<tr/>').appendTo(table);
			let td = $('<th/>').appendTo(tr).css({...commonCSS, 'text-align': 'left'});
			td.append(this.assayLink(group[0][2]));
			td = $('<td/>').appendTo(tr).css(commonCSS);
			td.text(group.length);
			td = $('<td>').appendTo(tr).css({...commonCSS, 'text-align': 'left'});

			let elements = group.map((result) => this.assignmentLabel(schema.assignments[result[0]]));
			joinElements(td, elements, ', ');
		}
	}

	private drillDownAxiom(results:[number, number, Result][]):void
	{
		let schema = TemplateManager.getTemplate(this.currentSchemaURI);

		let grouped = groupBy(results, (element) => element[2].valueLabel || element[2].valueURI, -1);
		let values = Object.keys(grouped);

		let text = [];
		if (values.length == 1)
			text.push('One annotation is flagged as an axiom violation.');
		else
			text.push(`${values.length} annotations are flagged as axiom violations.`);
		if (values.length > 5) text.push('The top-5 annotations causing axiom violations are:');
		$('<div/>').appendTo(this.drillDownDiv).text(text.join(' '));

		let commonCSS = this.commonDrillDownCSS;
		let table = this.drillDownTable(['Annotation', 'Number', 'Assays']);
		for (let key of Object.keys(grouped).slice(0, 5))
		{
			let group = grouped[key];
			let tr = $('<tr/>').appendTo(table);
			let td = $('<td/>').appendTo(tr).css({...commonCSS, 'text-align': 'left', 'white-space': 'nowrap'});
			td.append($('<b/>').append(key)).append($('<br>'));
			td.append(this.assignmentLabel(schema.assignments[group[0][0]]));
			td = $('<td/>').appendTo(tr).css(commonCSS);
			td.text(group.length);
			td = $('<td/>').appendTo(tr).css({...commonCSS, 'text-align': 'left'});
			let elements = group.map((result) => this.assayLink(result[2]));
			joinElements(td, elements, ', ');
		}
	}

	private drillDownOutOfSchema(results:Result[]):void
	{
		let grouped = groupBy(results, (element) => element.valueURI, -1);

		let commonCSS = {'padding': '0.2em', 'text-align': 'center', 'vertical-align': 'top'};
		let table = this.drillDownTable(['', 'Number', 'Origin', 'Assays']);

		const getOrigin = (result:Result):string =>
		{
			let {uniqueID} = result;
			let src = UniqueIdentifier.parseKey(uniqueID)[0];
			return (src && src.name) || 'no source';
		};
		for (let key in grouped)
		{
			let group = grouped[key];
			let byOrigin = groupBy(group, getOrigin, -1);

			for (let [norigin, origin] of enumerate(Object.keys(byOrigin)))
			{
				let tr = $('<tr/>').appendTo(table);
				if (norigin == 0)
				{
					let td = $('<th/>').appendTo(tr).css({...commonCSS, 'text-align': 'left'});
					td.text(collapsePrefix(key));
					td.attr('rowspan', byOrigin.length.toString());
					
					td = $('<td/>').appendTo(tr).css(commonCSS);
					td.text(group.length);
					td.attr('rowspan', byOrigin.length.toString());
				}
				let td = $('<td>').appendTo(tr).css({...commonCSS, 'white-space': 'nowrap'});
				td.text(origin);

				td = $('<td>').appendTo(tr).css({...commonCSS, 'text-align': 'left'});
				joinElements(td, byOrigin[origin].map((result) => this.assayLink(result)), ', ');
				this.offerBulkRemap(td, byOrigin[origin]);
			}
		}
	}

	private drillDownTableByAssignment(results:[number, number, Result][]):void
	{
		let schema = TemplateManager.getTemplate(this.currentSchemaURI);

		let commonCSS = this.commonDrillDownCSS;
		let grouped = groupBy(results, (element) => `key-${element[0]}`, -1);

		let table = this.drillDownTable(['', 'Number', 'Assays']);
		for (let key in grouped)
		{
			let group = grouped[key];
			let assn = schema.assignments[parseInt(key.split('-')[1])];
			let tr = $('<tr/>').appendTo(table);
			let td = $('<th/>').appendTo(tr).css({...commonCSS, 'text-align': 'left', 'white-space': 'nowrap'});
			td.append(this.assignmentLabel(assn));

			td = $('<td/>').appendTo(tr).css(commonCSS);
			td.text(group.length);

			td = $('<td/>').appendTo(tr).css({...commonCSS, 'text-align': 'left'});
			joinElements(td, group.map((result) => this.assayLink(result[2])), ', ');
			this.offerBulkRemap(td, group.map((bits) => bits[2]));
		}
	}

	private assignmentLabel(assn:SchemaAssignment):JQuery
	{
		let s:(string | JQuery)[] = assn.groupLabel.map((label) => $('<u/>').append(label));
		s.push(assn.name);
		return joinElements($('<span/>'), s, ', ');
	}

	private assayLink(result:Result):JQuery
	{
		let link:JQuery = null;
		if (result.uniqueID)
		{
			let [src, id] = UniqueIdentifier.parseKey(result.uniqueID);
			if (src) link = $(`<a target="_blank">${src.shortName} ${id}</a>`);
		}
		if (!link) link = $(`<a target="_blank">${result.assayID}</a>`);
		link.attr('href', `../assign.jsp?assayID=${result.assayID}&edit=true`);
		link.css({'white-space': 'nowrap'});
		return link;
	}

	// construct a button that packs the whole lot off to the bulk remap page
	private offerBulkRemap(dom:JQuery, results:Result[]):void
	{
		if (!Authentication.canBulkChangeAnnotations())	return;
		
		if (results.length == 0) return;
		let div = $('<div/>').appendTo(dom).css({'text-align': 'right'});

		let bulkURL = getBaseURL(1) + '/bulkmap.jsp?assays=';
		bulkURL += results.map((result) => result.assayID.toString()).join('%2C');
		bulkURL += '&schema=' + encodeURIComponent(this.currentSchemaURI);

		let btnBulk = $('<button class="btn btn-normal"/>').appendTo(div);
		btnBulk.append('<span class="glyphicon glyphicon-blackboard"></span> Bulk Remap');
		Popover.hover(domLegacy(btnBulk), null, 'Apply bulk changes to these assays.');
		btnBulk.click(() => window.open(bulkURL, '_blank'));
	}

	// given a set of results that represent terms missing from the schema: handle them
	private contemplateSchemaCheck(outOfSchema:SchemaCheckResult[], missingMandatory:SchemaCheckResult[]):void
	{
		if (outOfSchema.length == 0 && missingMandatory.length == 0) return;

		for (let result of Vec.concat(outOfSchema, missingMandatory))
		{
			let schema = TemplateManager.getTemplate(result.schemaURI);
			if (!schema) continue;

			let {results, assaySet, orphans} = this.validationResults[result.schemaURI];

			assaySet.add(result.assayID);

			let assnidx = this.findAssignment(schema, result);
			if (assnidx == -1) // orphan?
			{
				orphans.push(result);
			}
			else if (result.valueURI == null)
			{
				const assn = schema.assignments[assnidx];
				if (!assn.mandatory) continue; // only count missing mandatory

				let idx = ABSENCE_TERMS.indexOf(SPECIAL_BLANK);
				results[assnidx][idx].push({'assayID': result.assayID, 'uniqueID': result.uniqueID});
			}
			else if (ABSENCE_TERMS.includes(result.valueURI))
			{
				let idx = ABSENCE_TERMS.indexOf(result.valueURI);
				results[assnidx][idx].push({'assayID': result.assayID, 'uniqueID': result.uniqueID, 'valueURI': result.valueURI, 'valueLabel': result.valueLabel});
			}
			else if (ACCEPTABLE_ABSENCE_TERMS.includes(result.valueURI))
			{
				continue; // acceptable abscence term
			}
			else
			{
				let idx = ABSENCE_TERMS.indexOf(SPECIAL_OUTOFSCHEMA);
				results[assnidx][idx].push({'assayID': result.assayID, 'uniqueID': result.uniqueID, 'valueURI': result.valueURI, 'propURI': result.propURI, 'groupNest': result.groupNest});
			}
		}
	}

	// handle lists that are specific to one abscence term
	private contemplateSpecificList(schemaCheckResults:SchemaCheckResult[], absenceTerm:string):void
	{
		if (schemaCheckResults.length == 0) return;

		let idx = ABSENCE_TERMS.indexOf(absenceTerm);

		for (let result of schemaCheckResults)
		{
			let schema = TemplateManager.getTemplate(result.schemaURI);
			if (!schema) continue;

			let {results, assaySet} = this.validationResults[result.schemaURI];

			assaySet.add(result.assayID);

			let assnidx = this.findAssignment(schema, result);
			if (assnidx == -1) continue;

			results[assnidx][idx].push({'assayID': result.assayID, 'uniqueID': result.uniqueID, 'valueURI': result.valueURI, 'valueLabel': result.valueLabel});
		}
	}

	// given a batch of search results, adapt the dynamic list of things-thus-far
	private contemplateSearchResults(searchResults:SearchResult[]):void
	{
		for (let result of searchResults)
		{
			let schema = TemplateManager.getTemplate(result.schemaURI);
			if (!schema) continue;

			let {results} = this.validationResults[result.schemaURI];

			if (result.similarity == 0) continue;

			for (let annot of result.annotations)
			{
				let absidx = ABSENCE_TERMS.indexOf(collapsePrefix(annot.valueURI));
				if (absidx < 0) continue;

				let assnidx = this.findAssignment(schema, annot);
				if (assnidx == -1) continue;

				results[assnidx][absidx].push(result);
			}
		}
	}

	private findAssignment(schema:SchemaSummary, result:(SchemaCheckResult | AssayAnnotation)):number
	{
		for (let n = 0; n < schema.assignments.length; n++)
		{
			let {propURI, groupNest} = schema.assignments[n];
			if (compatiblePropGroupNest(propURI, groupNest, result.propURI, result.groupNest))
			{
				return n;
			}
		}
		return -1;
	}

	// puts together all the content as tab-separated values and adds to clipboard
	private copyValidationResults(validationResult:ValidationResult):void
	{
		let schema = TemplateManager.getTemplate(this.currentSchemaURI);

		let header = ['validation', 'assignment', 'propURI', 'uniqueID', 'annotation', 'annotURI'];
		let content:string[] = [];
		for (let nrow in validationResult.results)
		{
			let assn = schema.assignments[nrow];
			for (let ncol in validationResult.results[nrow])
			{
				let absenceTerm = ABSENCE_TERMS[ncol];
				let commonParts = [this.absenceTermName(absenceTerm), assn.name, formatAssignment(assn)];
				for (let result of validationResult.results[nrow][ncol])
				{
					let row =
					[
						...commonParts,
						result.uniqueID,
						result.valueLabel,
						collapsePrefix(result.valueURI),
					];
					content.push(row.join('\t'));
				}
			}
		}

		if (validationResult.orphans) for (let orphan of validationResult.orphans)
		{
			let row =
			[
				'orphan',
				orphan.propLabel,
				formatAssignment(orphan),
				orphan.assayID,
				orphan.valueLabel,
				collapsePrefix(orphan.valueURI),
			];
			content.push(row.join('\t'));
		}
		if (content.length > 0)
		{
			content.unshift(header.join('\t'));
			copyToClipboard(content.join('\n'));
		}
		else
		{
			if (validationResult == this.currentValidationResults)
				alert('Select violations first.');
			else
				alert('No violations available.');
		}
	}

	private absenceTermName(txt:string):string
	{
		txt = collapsePrefix(txt);
		if (txt == SPECIAL_OUTOFSCHEMA) txt = 'Out-of-Schema';
		else if (txt == SPECIAL_BLANK) txt = 'Blank';
		else if (txt == SPECIAL_AXIOM) txt = 'Axiom';
		else if (txt == SPECIAL_NUMBER) txt = 'Number';
		else if (txt.startsWith('bat:')) txt = txt.substring(4);
		return txt;
	}
}

/* EOF */ }
