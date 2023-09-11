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
	Popup dialog for requesting a new provisional term. This presumes that a position in the schema hierarchy has already been
	selected, and what remains is to fill out the request content.
*/

export class ProvisionalTermDialog extends BootstrapDialog
{
	private readonly MAX_SIMILAR = 5;
	private inputLabel:JQuery;
	private inputDescr:JQuery;
	private inputExplain:JQuery;
	private divSimilar:JQuery;
	private chkAnnotate:JQuery;
	private role = ProvisionalTermRole.Private;

	private parentIdx = -1; // index into treeData
	private isDuplicated:boolean;
	private btnCreate:JQuery;
	private btnCancel:JQuery;
	private awaitREST = false;

	//private keyword:MatchKeywordBar = null;
	private boxCSS = {'border-radius': '5px', 'border': '1px solid black', 'padding': '0.3em', 'margin-right': '0.2em'};

	constructor(private assn:SchemaAssignment, private treeData:SchemaTreeNode[],
				private parentURI:string, private label:string, private selfunc:(annot:AssayAnnotation) => void)
	{
		super('Create Provisional Term');

		for (let n = 0; n < treeData.length; n++) if (this.treeData[n].uri == parentURI) {this.parentIdx = n; break;}
	}

	// ------------ private methods ------------

	// fill out the details
	protected populateContent():void
	{
		let hdr = $('<p></p>').appendTo(this.content);
		hdr.append('Creating a new provisional term will add <i>placeholder</i> term at the indicated position in the hierarchy. ');
		hdr.append('Make sure the label is correct, and provide a concise summary description. Optionally add an explanation for the new ');
		hdr.append('term, which may affect whether it is incorporated into a formal ontology.');

		let grid = $('<div></div>').appendTo(this.content);
		grid.css({'display': 'grid', 'width': '100%'});
		grid.css({'align-items': 'center', 'justify-items': 'left', 'justify-content': 'start'});
		grid.css({'grid-column-gap': '0.5em', 'grid-row-gap': '0.2em'});
		grid.css('grid-template-columns', '[label] auto [value] minmax(max-content, 1fr) [button] auto [end]');

		let row = 0;
		this.renderHierarchy(grid, ++row);
		this.inputLabel = this.renderInputLine(grid, ++row, 'Label', null);
		this.renderSimilar(grid, ++row);
		this.renderHeading(grid, ++row, '<b>Longer description (1-2 sentences)</b>: this will be seen by all users of the term.', null);
		this.inputDescr = this.renderInputText(grid, ++row);
		this.renderHeading(grid, ++row, '<b>Explanation (optional)</b>: communication to the ontology review team.', null);
		this.inputExplain = this.renderInputText(grid, ++row);
		this.renderRole(grid, ++row);

		this.inputLabel.val(this.label);
		this.inputLabel.focus();

		this.repopulateSimilar();
		this.checkDuplicates();

		let watermark = 0;
		let onChange = ():void =>
		{
			let sync = ++watermark;
			setTimeout(() =>
			{
				if (sync == watermark)
				{
					this.repopulateSimilar();
					this.checkDuplicates();
				}
			}, 100);
		};
		this.inputLabel.change(onChange);
		this.inputLabel.keyup(onChange);

		let divButtons = $('<div></div>').appendTo(this.content);

		let lblAnnotate = $('<label></label>').appendTo(divButtons);
		this.chkAnnotate = $('<input type="checkbox"></input>').appendTo(lblAnnotate);
		this.chkAnnotate.prop('checked', true);
		lblAnnotate.append(' Apply annotation');

		divButtons.css({'text-align': 'right', 'margin-top': '1em'});
		this.btnCancel = $('<button class="btn btn-normal" data-dismiss="modal">Cancel</button>').appendTo(divButtons);
		this.btnCancel.css('margin-left', '0.5em');
		this.btnCreate = $('<button class="btn btn-action">Create</button>').appendTo(divButtons);
		this.btnCreate.click(() => this.actionCreate());
		this.btnCreate.css('margin-left', '0.5em');
	}

	protected onShown():void
	{
		this.inputLabel.focus();
	}

	private renderHeading(grid:JQuery, gridrow:number, lblHTML:string, btn:JQuery):void
	{
		let label = $('<div></div>').appendTo(grid);
		label.css({'grid-row': gridrow.toString(), 'grid-column': 'label / ' + (btn ? 'button' : 'end')});
		let spanTitle = $('<span></span>').appendTo(label);
		spanTitle.html(lblHTML);
	}

	private renderRole(grid:JQuery, gridrow:number):void
	{
		let label = $('<div></div>').appendTo(grid);
		label.css({'grid-row': gridrow.toString(), 'grid-column': 'label', 'font-weight': 'bold'});
		let spanTitle = $('<span></span>').appendTo(label);
		spanTitle.css('font-weight', 'bold');
		spanTitle.text('Role');
		label.append(':');

		let div = $('<div></div>').appendTo(grid);
		div.css({'grid-row': gridrow.toString(), 'grid-column': 'value / end'});
		let divGroup = $('<div class="btn-group" data-toggle="buttons"></div>').appendTo(div);
		for (let role of [ProvisionalTermRole.Private, ProvisionalTermRole.Public, ProvisionalTermRole.Deprecated])
		{
			let lblTitle = $('<label class="btn btn-radio"></label>').appendTo(divGroup);
			let txt = '?';
			if (role == ProvisionalTermRole.Private) txt = 'Private';
			else if (role == ProvisionalTermRole.Public) txt = 'Public';
			else if (role == ProvisionalTermRole.Deprecated) txt = 'Deprecated';
			let inputSeg = $('<input type="radio" name="options" autocomplete="off">' + txt + '</input>').appendTo(lblTitle);

			if (role == this.role)
			{
				lblTitle.addClass('active');
				inputSeg.prop('checked', true);
			}
			lblTitle.click(() => this.role = role);
		}
	}

	private renderInputLine(grid:JQuery, gridrow:number, lblText:string, btn:JQuery):JQuery
	{
		let label = $('<div></div>').appendTo(grid);
		label.css({'grid-row': gridrow.toString(), 'grid-column': 'label'});
		let spanTitle = $('<span></span>').appendTo(label);
		spanTitle.css('font-weight', 'bold');
		spanTitle.text(lblText);
		label.append(':');

		let input = $('<input type="text" size="40" class="text-box" spellcheck="false"></input>').appendTo(grid);
		let colpos = 'value / ' + (btn ? 'button' : 'end');
		input.css({'grid-row': gridrow.toString(), 'grid-column': colpos, 'width': '100%', 'font': 'inherit'});

		return input;
	}

	private renderInputText(grid:JQuery, gridrow:number):JQuery
	{
		let textarea = $('<textarea rows="3" class="text-box" spellcheck="false"></textarea>').appendTo(grid);
		textarea.css({'grid-row': gridrow.toString(), 'grid-column': 'label / end', 'width': '100%'});

		return textarea;
	}

	private renderHierarchy(grid:JQuery, gridrow:number):void
	{
		let label = $('<div></div>').appendTo(grid);
		label.css({'grid-row': gridrow.toString(), 'grid-column': 'label'});
		let spanTitle = $('<span></span>').appendTo(label);
		spanTitle.css('font-weight', 'bold');
		spanTitle.text('Parent');
		label.append(':');

		let div = $('<div></div>').appendTo(grid);
		div.css({'grid-row': gridrow.toString(), 'grid-column': 'value / end', 'padding': '0.2em'});

		let divFlex = $('<div></div>').appendTo(div);
		divFlex.css({'display': 'flex', 'flex-wrap': 'wrap', 'justify-content': 'start'});

		if (this.assn.groupLabel) for (let n = this.assn.groupLabel.length - 1; n >= 0; n--)
		{
			let blkGroup = $('<font></font>').appendTo(divFlex);
			blkGroup.css({...this.boxCSS, 'background': 'white'});
			blkGroup.text(this.assn.groupLabel[n]);
		}

		let blkProp = $('<font></font>').appendTo(divFlex);
		blkProp.addClass('weakblue');
		blkProp.css(this.boxCSS);
		blkProp.append($(Popover.displayOntologyTerm({'propURI': this.assn.propURI}, this.assn.name, this.assn.descr).elHTML));

		let idxList:number[] = [];
		for (let idx = this.parentIdx; idx >= 0; idx = this.treeData[idx].parent) idxList.unshift(idx);
		for (let idx of idxList)
		{
			let node = this.treeData[idx];
			let blkValue = $('<font></font>').appendTo(divFlex);
			blkValue.addClass('lightgray');
			blkValue.css(this.boxCSS);
			blkValue.append($(Popover.displayOntologyTerm({'valueURI': node.uri}, node.name, null, node.altLabels, node.externalURLs).elHTML));
		}
	}

	private renderSimilar(grid:JQuery, gridrow:number):void
	{
		let label = $('<div></div>').appendTo(grid);
		label.css({'grid-row': gridrow.toString(), 'grid-column': 'label'});
		let spanTitle = $('<span></span>').appendTo(label);
		spanTitle.css('font-weight', 'bold');
		spanTitle.text('Similar');
		label.append(':');

		this.divSimilar = $('<div></div>').appendTo(grid);
		this.divSimilar.css({'grid-row': gridrow.toString(), 'grid-column': 'value / end', 'padding': '0.2em'});
		this.divSimilar.css({'width': '100%', 'border': '1px solid #CCD9E8'});
	}

	// fill out the list of "similar" labels from the current hierarchy
	private repopulateSimilar():void
	{
		this.divSimilar.empty();

		let label = (this.inputLabel.val() as string).trim().toLowerCase();
		if (!label) {this.divSimilar.text('n/a'); return;}

		let similarNodes = this.searchSimilar(label);
		if (similarNodes.length == 0)
		{
			this.divSimilar.text('Nothing.');
			return;
		}

		for (let {'node': similarNode, matched} of similarNodes)
		{
			let divFlex = $('<div></div>').appendTo(this.divSimilar);
			divFlex.css({'display': 'flex', 'flex-wrap': 'wrap', 'justify-content': 'start', 'align-items': 'center', 'margin': '0.3em'});

			let nodes = [similarNode];
			for (let idx = nodes[0].parent; idx >= 0; idx = this.treeData[idx].parent) nodes.push(this.treeData[idx]);
			for (let i = nodes.length - 1; i >= 0; i--)
			{
				let node = nodes[i];
				let blkValue = $('<font></font>').appendTo(divFlex);
				blkValue.addClass(node.childCount == 0 ? 'lightgray' : 'weakblue');
				blkValue.css(this.boxCSS);
				if (i == 0) blkValue.css('text-decoration', 'underline');
				blkValue.append($(Popover.displayOntologyTerm({'valueURI': node.uri}, node.name, null, node.altLabels, node.externalURLs).elHTML));
			}
			if (matched) divFlex.append(`Matched synonym: ${matched}`);
		}
	}

	private searchSimilar(label:string):{node:SchemaTreeNode; matched:string}[]
	{
		let similar = this.treeData.map((node) =>
		{
			let similarity = fuzzyStringSimilarity(label, node.name.toLowerCase());
			let matched:string = null;
			if (node.altLabels) for (let altLabel of node.altLabels)
			{
				let altSimilarity = fuzzyStringSimilarity(label, altLabel.toLowerCase());
				if (altSimilarity <= similarity) continue;
				similarity = altSimilarity;
				matched = altLabel;
			}
			return {'distance': -similarity, 'matched': matched, node};
		});
		similar = similar.filter((o) => o.distance < 0);
		let order = Vec.idxSort(similar.map((s) => s.distance));
		order = order.splice(0, this.MAX_SIMILAR);
		return order.map((idx) =>
		{
			const {node, matched} = similar[idx];
			return {node, matched};
		});
	}

	// see if the proposed label is a duplicated, and colour the background accordingly
	private checkDuplicates():void
	{
		this.isDuplicated = false;
		let label = this.inputLabel.val().toString().trim().toLowerCase();
		for (let node of this.treeData)
		{
			if (node.parent < 0 || this.parentURI != this.treeData[node.parent].uri) continue;
			if (label == node.name.trim().toLowerCase())
			{
				this.isDuplicated = true;
				break;
			}
		}
		this.inputLabel.css('background-color', this.isDuplicated ? '#FF935E' : '');
	}

	// the user has asked to create the term...
	private actionCreate():void
	{
		if (this.awaitREST) return;

		if (this.isDuplicated)
		{
			alert('This exact label is already present at the indicated branch position.');
			return;
		}

		let params =
		{
			'parentURI': this.parentURI,
			'label': this.inputLabel.val().toString().trim(),
			'description': this.inputDescr.val().toString().trim(),
			'explanation': this.inputExplain.val().toString().trim(),
			'role': this.role,
		};
		if (!params.label) return;

		this.buttonState(true);
		callREST('REST/RequestProvisional', params,
			(data:any) =>
			{
				if (!data.success)
				{
					let errmsg:string = data.errmsg;
					if (!errmsg) errmsg = 'Provisional term request not allowed.'; // fallback
					alert(errmsg);
					return;
				}

				let {uri} = data;

				if (this.chkAnnotate.prop('checked'))
				{
					let annot:AssayAnnotation =
					{
						'propURI': this.assn.propURI,
						'propLabel': this.assn.name,
						'propDescr': this.assn.descr,
						'valueURI': uri,
						'valueLabel': params.label,
						'groupNest': this.assn.groupNest,
						'groupLabel': this.assn.groupLabel,
						'altLabels': [],
						'externalURLs': []
					};
					this.selfunc(annot);
				}
				this.dlg.modal('hide');
			},
			() => alert('Failed request for provisional term'),
			() => this.buttonState(false));
	}

	private buttonState(disabled:boolean):void
	{
		this.awaitREST = disabled;
		this.btnCreate.prop('disabled', disabled);
		this.btnCancel.prop('disabled', disabled);
	}
}

/* EOF */ }
