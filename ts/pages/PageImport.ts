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

///<reference path='../../../WebMolKit/src/decl/jquery/index.d.ts'/>

namespace BioAssayExpress /* BOF */ {

/*
	Supporting functionality for the import feature.
*/

interface PageImportColumn extends ImportOperationColumn
{
	tdDest?:JQuery;
}

interface PageImportCell extends ImportOperationCell
{
	tdDest?:JQuery;
}

interface PageImportUploadEntry
{
	uniqueID:string;
	text:string;
	appendText:string[];
	added:AssayAnnotation[];
	removed:AssayAnnotation[];
	schemaURI:string;
	holdingBay:boolean;
}

// TODO -- move this into a common area
interface PageAssignmentOptions
{
	entryForms?:EntryForm[];
	availableTemplates?:TemplateSummary[];
	branchTemplates?:TemplateSummary[];
	editMode?:boolean;
	canRequestProvisionals?:boolean;
	holdingBaySubmit?:number[];
	holdingBayDelete?:number[];
	absenceTerms?:string[];
	uriPatternMaps?:URIPatternMaps;
}

const FAILMSG = 'The content doesn\'t look like a text-based table; should be tab- or comma-separated values ' +
				'such as .csv, .tsv, .txt, or JSON-formatted assay data.';

export class PageImport
{
	private divMain:JQuery = null;
	private op:ImportOperation = null;

	private colLabel:JQuery[] = [];
	private colList:JQuery[] = [];

	private roleNames:Record<number, string> = {};
	//private mapTypeNames:Record<number, string> = {};

	private chosenTemplate = 0;
	private divTemplate:JQuery;

	private watermarkCache = 0;
	private cacheAssnList:Record<string, SchemaValue[]> = {}; // key: schema+prop+group

	private availableTemplates:TemplateSummary[] = [];
	private branchTemplates:TemplateSummary[] = [];
	public uriPatternMaps:URIPatternMaps = new URIPatternMaps();

	constructor(opt:PageAssignmentOptions = {})
	{
		this.roleNames[ImportOperationRole.Ignore] = 'Ignore';
		this.roleNames[ImportOperationRole.ID] = 'ID';
		this.roleNames[ImportOperationRole.Text] = 'Text';
		this.roleNames[ImportOperationRole.AppendText] = 'Append Text';
		this.roleNames[ImportOperationRole.Assignment] = 'Assignment';
		this.roleNames[ImportOperationRole.Literal] = 'Literal';
		this.roleNames[ImportOperationRole.DeleteAssignment] = 'Delete Assignment';
		this.roleNames[ImportOperationRole.DeleteLiteral] = 'Delete Literal';
		this.roleNames[ImportOperationRole.SplitColumn] = 'Split Column';

		if (opt.availableTemplates != null) this.availableTemplates = opt.availableTemplates;
		if (opt.branchTemplates != null) this.branchTemplates = opt.branchTemplates;
		if (opt.uriPatternMaps != null) this.uriPatternMaps = opt.uriPatternMaps;
		Popover.uriPatternMaps = this.uriPatternMaps;
	}

	// assembles the page from scratch
	public buildContent():void
	{
		this.divMain = $('#main');
		this.divMain.empty();

		let hiddenFileInput = $('<input/>').appendTo(document.body).attr('type', 'file').css({'display': 'none'});
		let divTarget = $('<div/>').appendTo(this.divMain);
		divTarget.css({'padding': '50px'});
		divTarget.css({'background-color': '#F8FCFF', 'border': '1px solid #96CAFF', 'box-shadow': '0 0 5px rgba(66,77,88,0.1)'});
		divTarget.append('Drag or paste tabular content into this page or ');
		divTarget.append($('<a/>').text('select file').click(() => hiddenFileInput.click()));
		divTarget.append('. Typical formats include comma-separated text (CSV), or even ');
		divTarget.append('better, tab-separated text (TSV or TXT). Clipboard pasting from spreadsheets like Excel generally works. ');
		divTarget.append('JSON-formatted assay data can also be imported (including ZIP files).');

		// pasting: captures the menu/hotkey form
		document.addEventListener('paste', (event:any):boolean =>
		{
			if (this.op != null) return;
			let wnd = window as any;
			let handled = false;
			if (wnd.clipboardData && wnd.clipboardData.getData) handled = this.pasteText(wnd.clipboardData.getData('Text'));
			else if (event.clipboardData && event.clipboardData.getData) handled = this.pasteText(event.clipboardData.getData('text/plain'));
			if (!handled) alert('The text you pasted doesn\'t look like a text-based table; should be tab- or comma-separated values.');
			event.preventDefault();
			return false;
		});

		document.body.addEventListener('dragover', (event) =>
		{
			event.stopPropagation();
			event.preventDefault();
			event.dataTransfer.dropEffect = this.op == null ? 'copy' : 'none';
		});
		document.body.addEventListener('drop', (event) =>
		{
			event.stopPropagation();
			event.preventDefault();
			if (this.op != null) return;
			this.dropInto(event.dataTransfer);
		});

		// file input selector`
		hiddenFileInput.change((event) => this.loadFile(event));
	}

	// ------------ private methods ------------

	// takes the incoming text (from whatever source) and tries to turn it into a meaningful regular table; fails if that's not possible
	// for any reason; will try to guess the basic parameters, like separator character & escaping method
	private pasteText(txt:string):boolean
	{
		if (!txt) return false;

		this.divMain.empty();
		this.divMain.append('Processing text...');

		let op = new ImportOperation(this.cacheAssnList,
									 () => this.renderMatrix(),
									 (colidx:number) => this.changedColumn(colidx),
									 () => this.availableTemplates[this.chosenTemplate],
									 (schemaURI:string) => this.changeTemplate(schemaURI));
		if (!op.interpretText(txt))
		{
			this.divMain.text(FAILMSG);
			return false;
		}
		this.op = op;

		this.op.rebuildMatrix();
		this.op.autoMapColumns();
		this.renderMatrix();
		return true;
	}

	// something was dragged into the molecule query area
	private dropInto(transfer:DataTransfer):void
	{
		/* TODO: If the input content is binary rather than text, make a service request to REST/InterpretAssay.
				 The service will need to be extended to allow arrays (e.g. 'allowMultiple': true), and return
				 'assayList' rather than 'assay'. In this mode, it should respnd to ZIP files by pulling out all
				 entries that are .json assay descriptions (probably only those initially). On success, proceed
				 to call parseJSONArray(..). */
		let items = transfer.items, files = transfer.files;

		for (let n = 0; n < items.length; n++)
		{
			if (items[n].kind == 'string')
			{
				items[n].getAsString((str:string) =>
				{
					if (!this.pasteText(str)) alert(FAILMSG);
				});
				return;
			}
		}
		for (let n = 0; n < files.length; n++)
		{
			//let fn = files[n].name;
			// (any limitations based on file extension? should generally be OK to just chop it up and see what's in there)

			let reader = new FileReader();
			reader.onload = (event) =>
			{
				let str = reader.result.toString();
				if (!this.pasteText(str)) alert(FAILMSG);
			};
			reader.readAsText(files[n]);
			return;
		}
	}

	private loadFile(event:JQueryEventObject):void
	{
		let reader = new FileReader();
		reader.onload = () =>
		{
			let str = reader.result.toString();
			if (!this.pasteText(str)) alert(FAILMSG);
		};
		let file = (event.target as unknown as {files:Blob[]}).files[0];
		reader.readAsText(file);
	}

	// recreate all of the DOM objects for the current definition of the columns & matrix
	private renderMatrix():void
	{
		let nrows = this.op.matrix.length, ncols = this.op.columns.length;

		this.divMain.empty();

		let flexBar = $('<div class="flexbar"/>').appendTo(this.divMain);
		this.divTemplate = $('<div/>').appendTo(flexBar);
		this.renderTemplates();

		if (Vec.arrayLength(this.op.lines) > 0)
		{
			let divFirst = $('<div/>').appendTo(flexBar);
			let lblFirst = $('<label style="font-weight: normal;"/>').appendTo(divFirst);
			let chkFirstRow = $('<input type="checkbox" id="checkFirstRow"/>').appendTo(lblFirst);
			lblFirst.append(' First Row Titles');
			chkFirstRow.prop('checked', this.op.firstRowTitles);
			chkFirstRow.change(() =>
			{
				this.op.firstRowTitles = chkFirstRow.prop('checked');
				this.op.rebuildMatrix();
				this.op.autoMapColumns();
				this.renderMatrix();
			});
		}

		let table = $('<table/>').appendTo(this.divMain);

		let tr = $('<tr/>').appendTo(table);
		for (let n = 0; n < ncols; n++)
		{
			let td = $('<td/>').appendTo(tr);
			td.css({'background-color': Theme.WEAK_HTML, 'border': '1px solid ' + Theme.STRONG_HTML});
			td.css({'text-align': 'left', 'vertical-align': 'middle', 'padding': '0 0.5em 0 0.5em', 'font-weight': 'bold'});
			td.css({'white-space': 'nowrap'});
			td.text(this.op.columns[n].name);

			let btnRemove = $('<button class="btn btn-xs btn-action"/>').appendTo(td);
			btnRemove.css({'visibility': 'hidden', 'margin-left': '0.5em', 'margin-bottom': '0.2em'});
			btnRemove.append($('<span class="glyphicon glyphicon-remove" style=\"height: 1.0em;"/>'));
			btnRemove.click(() =>
			{
				this.op.columns.splice(n, 1);
				for (let row of this.op.matrix) row.splice(n, 1);
				this.renderMatrix();
			});

			td.mouseenter(() => btnRemove.css('visibility', 'visible'));
			td.mouseleave(() => btnRemove.css('visibility', 'hidden'));
		}

		tr = $('<tr/>').appendTo(table);
		for (let c = 0; c < ncols; c++)
		{
			let td = $('<td/>').appendTo(tr);
			td.css('padding', '0.25em 0.5em 0.25em 0.5em');
			let col = this.op.columns[c];

			let div = $('<div class="btn-group"/>').appendTo(td);
			let btn = $('<button class="btn btn-action dropdown-toggle" data-toggle="dropdown"/>').appendTo(div);
			this.colLabel[c] = $('<span/>').appendTo(btn);
			btn.append(' <span class="caret"></span>');

			this.colList[c] = $('<ul class="dropdown-menu dropdown-menu-left"/>').appendTo(div);

			this.repopulateColumnRole(c);
		}

		tr = $('<tr/>').appendTo(table);
		for (let n = 0; n < ncols; n++)
		{
			let td = $('<td/>').appendTo(tr);
			td.css('padding', '0 0.5em 0 0.5em');
			let col = this.op.columns[n] as PageImportColumn;
			col.tdDest = td;
		}

		for (let r = 0; r < nrows; r++)
		{
			tr = $('<tr/>').appendTo(table);
			for (let c = 0; c < ncols; c++)
			{
				let td = $('<td/>').appendTo(tr);
				td.css('border', '1px solid ' + Theme.WEAK_HTML);
				td.css('text-align', 'left');
				td.css('vertical-align', 'left');
				td.css('padding', '0 0.5em 0 0.5em');
				let cell = this.op.matrix[r][c] as PageImportCell;
				cell.tdDest = td;

				this.redrawCell(r, c);
			}
		}

		let divAction = $('<div align="center"/>').appendTo(this.divMain);
		divAction.css('padding', '0.5em 0 0.5em 0');
		let btnPropose = $('<button class="btn btn-action"/>').appendTo(divAction);
		btnPropose.append('<span class="glyphicon glyphicon-upload"></span> Import Content');
		btnPropose.click(() =>
		{
			btnPropose.prop('disabled', true);
			this.actionApplyImport();
			btnPropose.prop('disabled', false);
		});
	}

	// changes the current template, if the schemaURI is known
	private changeTemplate(schemaURI:string):boolean
	{
		for (let n = 0; n < this.availableTemplates.length; n++) if (this.availableTemplates[n].schemaURI == schemaURI)
		{
			if (this.chosenTemplate != n)
			{
				this.chosenTemplate = n;
				this.renderTemplates();
				this.op.autoMapColumns();
			}
			return true;
		}
		return false;
	}

	// render template selection, or fixed if just one
	private renderTemplates():void
	{
		let schema = this.availableTemplates[this.chosenTemplate];

		this.divTemplate.empty();
		let container = $('<div/>').appendTo(this.divTemplate);
		container.css('margin', '0.5em 0 0.5em 0');

		if (this.availableTemplates.length <= 1)
		{
			let div = $('<div/>').appendTo(container);
			div.css('margin', '0.5em 0 0.5em 0');
			let span = $('<span/>').appendTo(div);
			span.addClass('strongblue');
			span.css('border-radius', '4px');
			span.css('color', 'white');
			span.css('padding', '0.5em');
			span.css('white-space', 'nowrap');
			span.text(schema.title);
			return;
		}

		let div = $('<div class="btn-group"/>').appendTo(container);

		let btn = $('<button type="button" class="form-control btn btn-action dropdown-toggle" data-toggle="dropdown"/>').appendTo(div);
		btn.text(schema.title);
		btn.append(' <span class="caret"></span>');

		let ul = $('<ul class="dropdown-menu" role="menu"/>').appendTo(div);
		for (let n = 0; n < this.availableTemplates.length; n++)
		{
			let li = $('<li/>').appendTo(ul);
			let t = this.availableTemplates[n];

			let href = $('<a href="#"/>').appendTo(li);
			if (n == this.chosenTemplate)
			{
				let b = $('<b/>').appendTo(href);
				b.text(t.title);
				href.click(() => false);
			}
			else
			{
				href.text(t.title);
				href.click(() =>
				{
					this.chosenTemplate = n;
					this.renderTemplates();
				});
			}
		}
	}

	// change role type: this can initiate a further selection option; the subtype is specific to role
	private changeRole(colidx:number, role:ImportOperationRole, subtype:any):void
	{
		let col = this.op.columns[colidx] as PageImportColumn;

		if (role == ImportOperationRole.ID)
		{
			col.role = role;
			col.destination = subtype; // this is a UniqueIDSource
			this.changedColumn(colidx);
			this.updateIDColumn(colidx);
		}
		else if (role == ImportOperationRole.Assignment || role == ImportOperationRole.Literal ||
				 role == ImportOperationRole.DeleteAssignment || role == ImportOperationRole.DeleteLiteral)
		{
			let schemaURI = this.availableTemplates[this.chosenTemplate].schemaURI;
			let schema = TemplateManager.getTemplate(schemaURI);
			if (!schema)
			{
				TemplateManager.ensureTemplates([schemaURI], () =>
				{
					schema = TemplateManager.getTemplate(schemaURI);
					if (schema) this.pickDestination(colidx, role, schema);
				});
			}
			else this.pickDestination(colidx, role, schema);
		}
		else if (role == ImportOperationRole.SplitColumn) this.requestSplitColumn(colidx);
		else
		{
			col.role = role;
			col.tdDest.empty();
			this.changedColumn(colidx);
			if (role == ImportOperationRole.Ignore)
			{
				for (let n = 0; n < this.op.matrix.length; n++)
				{
					this.op.matrix[n][colidx].destination = null;
					this.redrawCell(n, colidx);
				}

				// do-associate the keyword, if any
				let map =
				{
					'schemaURI': this.availableTemplates[this.chosenTemplate].schemaURI,
					'keyword': col.name,
				};
				let params = {'deletions': [map]};
				callREST('REST/AutomapDefine', params, (result:any) => {}); // no need to monitor whether it worked or not
			}
		}
	}

	// provides the option of splitting a column into multiple cases based on a separator string
	private requestSplitColumn(colidx:number):void
	{
		const self = this;
		class RequestSplit extends BootstrapDialog
		{
			private inputText:JQuery;
			constructor()
			{
				super('Split Column');
				this.maxWidth = '50%';
			}
			protected populateContent():void
			{
				let para = $('<p/>').appendTo(this.content);
				para.text(
					'Enter one or more separator characters (e.g. space, comma, or escape codes such as \\t for tabs). This will be ' +
					'used to split into multiple columns, if applicable.');

				let divInput = $('<div/>').appendTo(this.content).css('text-align', 'center');
				this.inputText = $('<input type="text" size="10"/>').appendTo(divInput);
				this.inputText.keydown((event:JQueryKeyEventObject) =>
				{
					let keyCode = event.keyCode || event.which;
					if (keyCode == KeyCode.Enter) this.apply();
				});

				let divButtons = $('<div/>').appendTo(this.content);
				divButtons.css({'text-align': 'right', 'margin-top': '0.5em'});
				let btnApply = $('<button class="btn btn-action"/>').appendTo(divButtons);
				btnApply.append('<span class="glyphicon glyphicon-ok"></span> Apply');
				btnApply.click(() => this.apply());
			}
			protected onShown():void
			{
				this.inputText.focus();
			}
			private apply():void
			{
				let value = this.inputText.val().toString();
				this.hide();
				self.applySplitColumn(colidx, value);
			}
		}
		let dlg = new RequestSplit();
		dlg.show();
	}

	// action the column split, given the requested separator
	private applySplitColumn(colidx:number, sep:string):void
	{
		if (sep.length == 0) return;
		sep = sep.replace(/\\t/g, '\t'); // any others? (\r and \n are disallowed)

		let ensplitted:string[][] = [];
		let numExtra = 0;
		let numRows = this.op.matrix.length;
		for (let r = 0; r < numRows; r++)
		{
			let cell = this.op.matrix[r][colidx] as PageImportCell;
			let bits = cell.value ? cell.value.split(sep) : [];
			if (bits.length > 1)
			{
				ensplitted[r] = bits;
				numExtra = Math.max(numExtra, bits.length - 1);
			}
			else ensplitted[r] = [];
		}

		if (numExtra == 0)
		{
			alert('Found nothing to split.');
			return;
		}

		// insert the column
		let precol = this.op.columns[colidx];
		for (let n = 0; n < numExtra; n++)
		{
			let col:ImportOperationColumn =
			{
				'name': precol.name + "'".repeat(n + 1),
				'role': precol.role,
				'destination': precol.destination,
			};
			this.op.columns.splice(colidx + 1 + n, 0, col);
			for (let r = 0; r < numRows; r++) this.op.matrix[r].splice(colidx + 1 + n, 0, {'value': ''});
		}

		// fill in the details
		for (let r = 0; r < numRows; r++)
		{
			for (let n = 0; n < ensplitted[r].length; n++)
			{
				let cell = this.op.matrix[r][colidx + n], col = this.op.columns[colidx];
				cell.value = ensplitted[r][n];
				cell.destination = null;
				if (precol.role == ImportOperationRole.Literal)
				{
					let annot:AssayAnnotation =
					{
						'valueLabel': cell.value,
						'propURI': col.destination.propURI,
						'groupNest': col.destination.groupNest
					};
					if (!this.op.hasAnnotationAlready(n, annot)) cell.destination = {'name': cell.value};
				}
			}
		}

		this.renderMatrix();
	}

	// update the role information for one column
	private repopulateColumnRole(colidx:number):void
	{
		let col = this.op.columns[colidx], name = this.roleNames[col.role];
		if (col.role == ImportOperationRole.ID)
		{
			let src:UniqueIDSource = col.destination;
			name += ': ' + (src ? src.name : 'full');
		}
		this.colLabel[colidx].text(name);

		let showRoles = [ImportOperationRole.Ignore, ImportOperationRole.ID, ImportOperationRole.Assignment, ImportOperationRole.Literal,
						 ImportOperationRole.AppendText, ImportOperationRole.DeleteAssignment, ImportOperationRole.DeleteLiteral];

		if (col.role == ImportOperationRole.Ignore || col.role == ImportOperationRole.Literal)
		{
			showRoles.push(null);
			showRoles.push(ImportOperationRole.SplitColumn);
		}

		let ul = this.colList[colidx];
		ul.empty();
		for (let role of showRoles)
		{
			if (role == null)
			{
				$('<div/>').appendTo(ul).css('height', '0.5em');
				continue;
			}

			let subtypes:any[] = [null]; // for most roles, just one thing
			if (role == ImportOperationRole.ID)
			{
				subtypes = UniqueIdentifier.sources().slice(0);
				subtypes.push(null); // placeholder for fully-formed uniqueID
			}
			for (let subtype of subtypes)
			{
				let li = $('<li/>').appendTo(ul);
				let href = $('<a href="#"/>').appendTo(li);

				name = this.roleNames[role];
				let isSelected = role == col.role;
				if (role == ImportOperationRole.ID)
				{
					if (subtype)
					{
						let src:UniqueIDSource = subtype;
						name += ': ' + src.name;
					}
					else name = 'ID: full';
					isSelected = isSelected && subtype === col.destination;
				}

				if (isSelected) href.css('font-weight', 'bold');
				else if (role == ImportOperationRole.SplitColumn) href.css('font-style', 'italic');

				href.text(name);
				href.click(() =>
				{
					this.changeRole(colidx, role, subtype);
					ul.dropdown('toggle');
					return false;
				});
			}
		}
	}

	// column and its rows have been modified in some capacity
	private changedColumn(colidx:number):void
	{
		this.repopulateColumnRole(colidx);
		for (let r = 0; r < this.op.matrix.length; r++) this.redrawCell(r, colidx);
	}

	// with schema information in hand, select which assignment to map the column to
	private pickDestination(colidx:number, role:ImportOperationRole, schema:SchemaSummary):void
	{
		let col = this.op.columns[colidx] as PageImportColumn;

		let preferredTypes:SuggestionType[] = [];
		if (role == ImportOperationRole.Assignment || role == ImportOperationRole.DeleteAssignment)
		{
			preferredTypes = [SuggestionType.Full, SuggestionType.Disabled];
		}
		else if (role == ImportOperationRole.Literal || role == ImportOperationRole.DeleteLiteral)
		{
			preferredTypes = [SuggestionType.Field, SuggestionType.URL, SuggestionType.ID, SuggestionType.String,
							  SuggestionType.Number, SuggestionType.Integer, SuggestionType.Date];
		}
		let allowedAssn = schema.assignments.filter((a) => preferredTypes.indexOf(a.suggestions) >= 0);
		let guessedAssn = this.op.guessAssignmentBestMatch(col, allowedAssn);

		let dlg = new PickAssignmentDialog(schema, false);
		dlg.preferredTypes = preferredTypes;
		dlg.guessedAssn = guessedAssn;

		let prevXScroll = $(window).scrollLeft(), prevYScroll = $(window).scrollTop();

		dlg.callbackDone = (assnlist:SchemaAssignment[]) =>
		{
			col.role = role;
			col.destination = assnlist[0];
			col.tdDest.empty();
			for (let n = Vec.arrayLength(assnlist[0].groupLabel) - 1; n >= 0; n--)
			{
				let font = $('<font/>').appendTo(col.tdDest);
				font.css('white-space', 'nowrap');
				font.text(assnlist[0].groupLabel[n] + '\u{2192}');
				col.tdDest.append(' ');
			}
			let font = $('<font/>').appendTo(col.tdDest);
			font.css('white-space', 'nowrap');
			font.css('color', Theme.STRONG_HTML);
			font.text(assnlist[0].name);

			if (role == ImportOperationRole.Assignment || role == ImportOperationRole.DeleteAssignment)
			{
				this.op.autoAssignWhenPossible(colidx, assnlist[0]);
			}
			else if (role == ImportOperationRole.Literal || role == ImportOperationRole.DeleteLiteral)
			{
				for (let n = 0; n < this.op.matrix.length; n++)
				{
					let cell = this.op.matrix[n][colidx];
					let annot:AssayAnnotation =
					{
						'valueLabel': cell.value,
						'propURI': col.destination.propURI,
						'groupNest': col.destination.groupNest
					};
					if (!this.op.hasAnnotationAlready(n, annot)) cell.destination = {'name': cell.value};
				}
			}

			// associate the keyword, for future reference
			let map =
			{
				'schemaURI': this.availableTemplates[this.chosenTemplate].schemaURI,
				'keyword': col.name,
				'propURI': col.destination.propURI,
				'groupNest': col.destination.groupNest,
			};
			let params = {'mappings': [map]};
			callREST('REST/AutomapDefine', params, (result:any) => {}); // no need to monitor whether it worked or not

			this.changedColumn(colidx);
			window.scrollTo(prevXScroll, prevYScroll);
		};
		dlg.show();
	}

	// recreates the contents of a cell, and the interactive actions that go along with it
	private redrawCell(rowidx:number, colidx:number):void
	{
		let cell = this.op.matrix[rowidx][colidx] as PageImportCell, col = this.op.columns[colidx];
		let assn:SchemaAssignment = col.destination;
		cell.tdDest.empty();

		// render the content, differently depending on whether it has been "mapped" to a destination
		if (!cell.destination)
		{
			let text = orBlank(cell.value);
			let needsEllipsis = text.length > 30;
			if (needsEllipsis) text = text.substring(0, 30);

			let span = $('<span/>').appendTo(cell.tdDest);
			span.text(text);
			if (needsEllipsis)
			{
				let spanEllipsis = $('<span>\u{2026}</span>').appendTo(span);
				spanEllipsis.css({'text-decoration': 'underline', 'cursor': 'pointer'});
				Popover.hover(domLegacy(spanEllipsis), null, escapeHTML(cell.value));
				spanEllipsis.click(() =>
				{
					span.text(cell.value);
					spanEllipsis.remove();
				});
			}

			if (col.role == ImportOperationRole.Assignment || col.role == ImportOperationRole.DeleteAssignment)
			{
				span.css('cursor', 'pointer');
				span.hover(() => span.css({'text-decoration': 'underline', 'color': Theme.STRONG_HTML}),
						   () => span.css({'text-decoration': 'none', 'color': Theme.NORMAL_HTML}));
				span.click(() => this.pickValueDestination(rowidx, colidx));
			}
			// (different display style for role==ID?)

			if (col.role == ImportOperationRole.Assignment && cell.value)
			{
				cell.tdDest.append('&nbsp;');
				let btnLiteral = $('<button class="btn btn-xs btn-normal"/>').appendTo(cell.tdDest);
				btnLiteral.append($('<span class="glyphicon glyphicon-font" style=\"height: 1.0em;"/>'));
				btnLiteral.click(() =>
				{
					cell.destination = {'name': cell.value};
					this.redrawCell(rowidx, colidx);
				});
			}
		}
		else
		{
			let uri:string = cell.destination.uri, name:string = cell.destination.name;
			if (uri || name)
			{
				let blkValue = $('<font/>').appendTo(cell.tdDest);
				blkValue.addClass('lightgray');
				blkValue.css('border-radius', 5);
				blkValue.css('border', '1px solid black');
				blkValue.css('padding', '0 0.3em 0 0.3em');

				if (uri)
				{
					let schemaURI = this.availableTemplates[this.chosenTemplate].schemaURI;
					blkValue.append($(Popover.displayOntologyTerm({'schemaURI': schemaURI, 'propURI': assn.propURI,
																'groupNest': assn.groupNest, 'valueURI': uri}, name, null).elHTML));
				}
				else
				{
					if (name.length > 32) name = name.substring(0, 30) + '\u{2026}';
					blkValue.text('"' + name + '"');
				}

				if (col.role == ImportOperationRole.Assignment || col.role == ImportOperationRole.DeleteAssignment)
				{
					cell.tdDest.append('&nbsp;');

					let btnClear = $('<button class="btn btn-xs btn-action"/>').appendTo(cell.tdDest);
					btnClear.append($('<span class="glyphicon glyphicon-remove" style=\"height: 1.2em;"/>'));
					btnClear.click(() => this.clearValueDestination(rowidx, colidx));
				}
			}
		}

		// possibly show content that's there in the existing assay
		const AFFECTED = [ImportOperationRole.Assignment, ImportOperationRole.Literal,
				 		  ImportOperationRole.DeleteAssignment, ImportOperationRole.DeleteLiteral];
		let assay:AssayDefinition;
		if (AFFECTED.indexOf(col.role) >= 0)
		{
			let assay = this.op.assayForRow(rowidx);
			if (assay && assay.assayID)
			{
				let propURI = col.destination.propURI as string, groupNest = col.destination.groupNest as string[];
				this.renderExistingAnnotations(cell.tdDest, assay, propURI, groupNest);
			}
		}
	}

	// scrub any destination assignment for the given cell
	private clearValueDestination(rowidx:number, colidx:number):void
	{
		let cell = this.op.matrix[rowidx][colidx];
		//cell.map = PageImportMapType.None;
		cell.destination = null;
		this.redrawCell(rowidx, colidx);

		// tell the service to forget the mapping
		let assn:SchemaAssignment = this.op.columns[colidx].destination;
		let map:any =
		{
			'schemaURI': this.availableTemplates[this.chosenTemplate].schemaURI,
			'propURI': assn.propURI,
			'groupNest': assn.groupNest,
			'keyword': cell.value,
		};
		let params = {'deletions': [map]};
		callREST('REST/AutomapDefine', params, (result:any) => {}); // no need to monitor whether it worked or not
	}

	// pick a specific value to map a cell to, by bringing up the tree view dialog
	private pickValueDestination(rowidx:number, colidx:number):void
	{
		let cell = this.op.matrix[rowidx][colidx], col = this.op.columns[colidx];
		let assn:SchemaAssignment = col.destination;
		const settings:PickTermSettings =
		{
			'schema': TemplateManager.getTemplate(this.availableTemplates[this.chosenTemplate].schemaURI), // can assume it's cached already
			'multi': false,
		};

		this.op.guessValueBestMatch(cell.value, assn, (value:SchemaValue):void =>
		{
			let dlg = new PickTermDialog(assn, settings, (annot:AssayAnnotation):void =>
			{
				this.selectAnnotation(rowidx, colidx, annot);
			});
			if (value)
			{
				dlg.revealInTree.add(value.uri);
				dlg.predictionScores[value.uri] = 1;
			}
			const FONT = `<font style="color: ${Theme.STRONG_HTML}; text-decoration: underline;">`;
			dlg.showTree('Map ' + FONT + escapeHTML(cell.value) + '</font> To');
			if (value) dlg.scrollToItem(value.uri);
		});
	}

	// creating a new mapping
	private selectAnnotation(rowidx:number, colidx:number, annot:AssayAnnotation):void
	{
		let cell = this.op.matrix[rowidx][colidx];
		cell.destination = {'uri': annot.valueURI, 'name': annot.valueLabel};
		this.redrawCell(rowidx, colidx);

		// and as a bonus: do the same for any other rows with the same content
		for (let r = 0; r < this.op.matrix.length; r++)
		{
			let look = this.op.matrix[r][colidx];
			if (!look.destination && look.value == cell.value)
			{
				//look.map = PageImportMapType.Term;
				look.destination = cell.destination;
				this.redrawCell(r, colidx);
			}
		}

		// let the service know that there are new auto-mappings which may be used later on
		let map =
		{
			'schemaURI': this.availableTemplates[this.chosenTemplate].schemaURI,
			'propURI': annot.propURI,
			'groupNest': annot.groupNest,
			'keyword': cell.value,
			'valueURI': annot.valueURI
		};
		let params = {'mappings': [map]};
		callREST('REST/AutomapDefine', params, (result:any) => {}); // no need to monitor whether it worked or not
	}

	// a column has been changed to ID type, so go through and lookup all of the values to see if they match assays
	private updateIDColumn(colidx:number):void
	{
		let col = this.op.columns[colidx];
		let src:UniqueIDSource = col.destination; // null = it's already a fully-formed uniqueID

		let roster:string[] = []; // raw strings

		// see if there's a common prefix, and if so, strip it out from all of them
		let zapCount:Record<string, number> = {};
		for (let n = 0; n < this.op.matrix.length; n++)
		{
			let idCode = this.op.matrix[n][colidx].value;
			let uniqueID = src ? src.prefix + idCode : idCode as string;

			if (this.op.getCachedAssay(uniqueID)) continue;
			let pfx = '';
			while (idCode.length > 0)
			{
				let ch = idCode.charAt(0);
				if (ch >= '0' && ch <= '9') break;
				pfx += ch;
				idCode = idCode.substring(1);
				if (this.op.getCachedAssay(uniqueID)) break;
			}
			if (!this.op.getCachedAssay(uniqueID)) continue;
			let count = zapCount[pfx];
			zapCount[pfx] = count ? count + 1 : 1;
		}
		let bigpfx:string = null;
		for (let pfx in zapCount) if (bigpfx == null || zapCount[pfx] > zapCount[bigpfx]) bigpfx = pfx;
		if (bigpfx != null && zapCount[bigpfx] > 0.8 * this.op.matrix.length)
		{
			for (let n = 0; n < this.op.matrix.length; n++)
			{
				let cell = this.op.matrix[n][colidx];
				if (cell.value.startsWith(bigpfx)) cell.value = cell.value.substring(bigpfx.length);
			}
		}

		// consider each row: if the ID is already in the cache; update it; if not, add it to the list of things to check out
		for (let n = 0; n < this.op.matrix.length; n++)
		{
			let cell = this.op.matrix[n][colidx] as PageImportCell;
			let uniqueID = src ? src.prefix + cell.value : cell.value as string;
			let assay = this.op.getCachedAssay(uniqueID);
			if (assay && assay.assayID) this.updateIDRow(n, colidx, assay.assayID);
			else
			{
				cell.tdDest.text(cell.value);
				if (roster.indexOf(cell.value) < 0) roster.push(cell.value);
			}
		}

		this.fillNextAssayID(++this.watermarkCache, colidx, src, roster);
	}

	// given that an assay has become available, update the row and the ID column to reflect this
	private updateIDRow(rowidx:number, colidx:number, assayID:number):void
	{
		let cell = this.op.matrix[rowidx][colidx] as PageImportCell;
		cell.tdDest.empty();
		if (assayID > 0)
		{
			let href = $('<a target="_blank"/>').appendTo(cell.tdDest);
			href.attr('href', restBaseURL + '/assign.jsp?assayID=' + assayID);
			href.text(cell.value);
		}
		else
		{
			let ital = $('<i/>').appendTo(cell.tdDest);
			ital.text(cell.value);
			cell.tdDest.append(' (new)');
		}

		// also update any columns that have a destination encoded
		const AFFECTED = [ImportOperationRole.Assignment, ImportOperationRole.Literal,
				 		  ImportOperationRole.DeleteAssignment, ImportOperationRole.DeleteLiteral];
		for (let n = 0; n < this.op.columns.length; n++)
		{
			if (AFFECTED.indexOf(this.op.columns[n].role) >= 0) this.redrawCell(rowidx, n);
		}
	}

	// fetches the next assay, by uniqueID code
	private fillNextAssayID(watermark:number, colidx:number, src:UniqueIDSource, roster:string[]):void
	{
		if (roster.length == 0 || watermark != this.watermarkCache) return;

		let value = roster.shift();
		let uniqueID = src ? src.prefix + value : value;

		this.op.obtainCachedAssay(uniqueID, (assay) =>
		{
			let assayID = assay ? assay.assayID : 0;
			for (let n = 0; n < this.op.matrix.length; n++)
				if (this.op.matrix[n][colidx].value == value) this.updateIDRow(n, colidx, assayID);
			this.fillNextAssayID(watermark, colidx, src, roster);
		});
	}

	// make it happen, if applicable
	private actionApplyImport():boolean
	{
		let nrows = this.op.matrix.length, ncols = this.op.matrix[0].length;

		let idcol = -1;
		for (let n = 0; n < ncols; n++) if (this.op.columns[n].role == ImportOperationRole.ID) {idcol = n; break;}
		if (idcol < 0)
		{
			alert('Designate one column as the "ID" before importing.');
			return false;
		}
		let numText = 0, numTerms = 0, numLabels = 0, numDeleteTerms = 0, numDeleteLabels = 0;
		const APPLICABLE = [ImportOperationRole.Text, ImportOperationRole.AppendText,
							ImportOperationRole.Assignment, ImportOperationRole.Literal,
							ImportOperationRole.DeleteAssignment, ImportOperationRole.DeleteLiteral];
		for (let c = 0; c < ncols; c++)
		{
			let col = this.op.columns[c];
			if (APPLICABLE.indexOf(col.role) < 0) continue;

			for (let r = 0; r < nrows; r++)
			{
				if (col.role == ImportOperationRole.Text || col.role == ImportOperationRole.AppendText)
				{
					if (this.op.matrix[r][c].value) numText++;
				}
				else
				{
					let dest = this.op.matrix[r][c].destination;
					if (!dest) {}
					else if (dest.uri)
					{
						if (col.role == ImportOperationRole.Assignment) numTerms++;
						else if (col.role == ImportOperationRole.DeleteAssignment) numDeleteTerms++;
					}
					else if (dest.name)
					{
						if (col.role == ImportOperationRole.Literal) numLabels++;
						else if (col.role == ImportOperationRole.DeleteLiteral) numDeleteLabels++;
					}
				}
			}
		}
		if (numText == 0 && numTerms == 0 && numLabels == 0 && numDeleteTerms == 0 && numDeleteLabels == 0)
		{
			alert('There must be at least one annotation mapping in order to perform an import.');
			return false;
		}

		// gather the action items
		let uploadList:PageImportUploadEntry[] = [];
		let uploadMap:Record<string, PageImportUploadEntry> = {};
		let schemaURI = this.availableTemplates[this.chosenTemplate].schemaURI;
		let src:UniqueIDSource = this.op.columns[idcol].destination;
		let numUpdates = 0, numNew = 0;

		for (let r = 0; r < nrows; r++)
		{
			let value = this.op.matrix[r][idcol].value;
			let uniqueID = src ? src.prefix + value : value;

			let text:string = null, appendText:string[] = [];
			let annotations:any[] = [], deletions:any[] = [];

			for (let c = 0; c < ncols; c++)
			{
				let col = this.op.columns[c];

				if (col.role == ImportOperationRole.Text)
				{
					text = this.op.matrix[r][c].value;
					continue;
				}
				if (col.role == ImportOperationRole.AppendText)
				{
					if (this.op.matrix[r][c].value) appendText.push(this.op.matrix[r][c].value);
					continue;
				}

				if (col.role != ImportOperationRole.Assignment && col.role != ImportOperationRole.Literal &&
					col.role != ImportOperationRole.DeleteAssignment && col.role != ImportOperationRole.DeleteLiteral) continue;

				let dest = this.op.matrix[r][c].destination;
				if (!dest || (!dest.uri && !dest.name)) continue;

				let assn:SchemaAssignment = this.op.columns[c].destination;
				let annot =
				{
					'propURI': assn.propURI,
					'groupNest': assn.groupNest,
					'valueURI': dest.uri,
					'valueLabel': dest.name
				};
				if (col.role == ImportOperationRole.Assignment || col.role == ImportOperationRole.Literal)
					annotations.push(annot);
				else // col.role == PageImportRole.DeleteAssignment || col.role == PageImportRole.DeleteLiteral
					deletions.push(annot);
			}
			if (!text && appendText.length == 0 && annotations.length == 0 && deletions.length == 0) continue;

			let entry = uploadMap[uniqueID];
			if (entry)
			{
				entry.appendText = Vec.concat(entry.appendText, appendText);
				entry.added = Vec.concat(entry.added, annotations);
				entry.removed = Vec.concat(entry.removed, deletions);
			}
			else
			{
				entry =
				{
					'uniqueID': uniqueID,
					'text': text,
					'appendText': appendText,
					'added': annotations,
					'removed': deletions,
					'schemaURI': schemaURI,
					'holdingBay': true
				};
				uploadList.push(entry);
				uploadMap[uniqueID] = entry;

				if (this.op.getCachedAssay(uniqueID)) numUpdates++; else numNew++;
			}
		}

		let msg = 'Import ' + numNew + ' new assay' + (numNew == 1 ? '' : 's') + ', ' + numUpdates +
				  ' update' + (numUpdates == 1 ? '' : 's') + '. ' +
				  'Modifications will be added to the Holding Bay, rather than directly modifying the database. Continue?';
		if (!confirm(msg)) return;

		let main = $('#main');
		main.text('Uploading');
		let totalUpload = uploadList.length;

		let uploadNextAssay = ():void =>
		{
			if (uploadList.length == 0)
			{
				main.text('Import complete: ' + totalUpload + ' ');
				let ahref = $('<a>holding bay</a>').appendTo(main);
				ahref.attr('href', '../holding.jsp');
				main.append(' entr' + (totalUpload == 1 ? 'y' : 'ies') + ' created. ');
				return;
			}

			let params = uploadList.shift();

			if (params.appendText.length > 0)
			{
				let text = params.text;
				if (!text && params.uniqueID)
				{
					let assay = this.op.getCachedAssay(params.uniqueID);
					if (assay) text = assay.text;
				}
				for (let apptxt of params.appendText)
				{
					apptxt = apptxt.trim();
					if (!text) text = apptxt;
					else if (text.indexOf(apptxt) >= 0) {}
					else text = text.trim() + '\n\n' + apptxt;
				}
				params.text = text;

				delete params.appendText;
			}

			main.text('Uploading ' + (totalUpload - uploadList.length) + ' of ' + totalUpload);

			callREST('REST/SubmitAssay', params, (result:any) =>
				{
					// (should probably check the result to see if something happened...)
					uploadNextAssay();
				});
		};
		uploadNextAssay();
	}

	// show annotations that were already present
	private renderExistingAnnotations(dom:JQuery, assay:AssayDefinition, propURI:string, groupNest:string[]):void
	{
		let annotList = assay.annotations.filter((annot) => samePropGroupNest(annot.propURI, annot.groupNest, propURI, groupNest));
		for (let annot of annotList)
		{
			let div = $('<div/>').appendTo(dom);
			div.append('\u{2022}&nbsp;');
			let span = $('<span/>').appendTo(div);
			span.css({'background-color': 'white', 'border': '1px solid #606060', 'padding': '0.1em 0.2em', 'margin': '0.1em 0'});
			if (annot.valueURI)
			{
				let schemaURI = this.availableTemplates[this.chosenTemplate].schemaURI;
				let opt:PopoverOptions = {'schemaURI': schemaURI, 'propURI': annot.propURI,
										  'groupNest': annot.groupNest, 'valueURI': annot.valueURI};
				span.append($(Popover.displayOntologyTerm(opt, annot.valueLabel, null).elHTML));
			}
			else span.text('"' + annot.valueLabel + '"');
		}
	}
}

/* EOF */ }
