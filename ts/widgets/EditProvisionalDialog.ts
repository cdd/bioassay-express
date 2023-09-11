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
	Popup dialog for viewing & editing a provisional term and controlling its escalation to OntoloBridge.
*/

declare var ONTOLOBRIDGES:OntoloBridge[];

export class EditProvisionalDialog extends BootstrapDialog
{
	private inputLabel:JQuery;
	private areaDescr:JQuery;
	private areaExplain:JQuery;
	private divBridge:JQuery;
	private role:ProvisionalTermRole;
	private btnUpdate:JQuery;
	private btnDelete:JQuery;
	private divOntoGroup:JQuery = null;

	constructor(private provTerm:ProvisionalTerm, private onChanged:() => void)
	{
		super('Edit Provisional Term');
	}

	protected onShown():void
	{
		this.inputLabel.focus();
	}

	// ------------ private methods ------------

	protected populateContent():void
	{
		let grid = $('<div/>').appendTo(this.content);
		grid.css('display', 'grid');
		grid.css({'width': '100%', 'align-items': 'center', 'justify-items': 'left', 'justify-content': 'start'});
		grid.css({'grid-column-gap': '0.5em', 'grid-row-gap': '0.2em'});
		grid.css('grid-template-columns', '[label] auto [value] minmax(max-content, 1fr) [button] auto [end]');
		grid.css({'border': '1px solid #6090A0', 'background-color': '#F0F8FF', 'padding': '0.5em', 'margin-bottom': '0.5em'});

		let row = 0;

		//let makeLine = (title:string, content?:string, url?:string):JQuery =>
		let makeLine = (title:string, domOrText?:JQuery | string):JQuery =>
		{
			row++;
			let label = $('<div/>').appendTo(grid);
			label.css({'grid-row': row.toString(), 'grid-column': 'label'});
			let spanTitle = $('<span/>').appendTo(label);
			spanTitle.css('font-weight', 'bold');
			spanTitle.text(title);
			label.append(':');

			let div = $('<div/>').appendTo(grid);
			div.css({'grid-row': row.toString(), 'grid-column': 'value / end', 'width': '100%'});

			if (domOrText == null) {}
			else if (typeof domOrText == 'string') div.text(domOrText);
			else div.append(domOrText);

			return div;
		};

		let div = makeLine('Proposer');
		if (this.provTerm.proposerName) div.text(this.provTerm.proposerName + ' ');
		div.append('(<i>' + this.provTerm.proposerID + '</i>)');

		div = makeLine('Parent');
		if (this.provTerm.parentLabel) div.text(this.provTerm.parentLabel + ' ');
		div.append('<i>&lt;' + escapeHTML(collapsePrefix(this.provTerm.parentURI)) + '&gt;</i>');

		this.inputLabel = $('<input type="text" size="40" class="text-box" spellcheck="false"/>');
		this.inputLabel.css({'width': '100%'});
		this.inputLabel.val(this.provTerm.label);
		makeLine('Label', this.inputLabel);

		if (!this.provTerm.remappedTo)
		{
			makeLine('URI', collapsePrefix(this.provTerm.uri));
		}
		else
		{
			makeLine('Approved URI', collapsePrefix(this.provTerm.remappedTo));
			makeLine('Temporary URI', collapsePrefix(this.provTerm.uri));
		}

		this.areaDescr = $('<textarea rows="3" class="text-box" spellcheck="false"/>');
		this.areaDescr.css({'width': '100%'});
		this.areaDescr.val(this.provTerm.description);
		makeLine('Description', this.areaDescr);

		this.areaExplain = $('<textarea rows="3" class="text-box" spellcheck="false"/>');
		this.areaExplain.css({'width': '100%'});
		this.areaExplain.val(this.provTerm.explanation);
		makeLine('Explanation', this.areaExplain);

		makeLine('Role', this.createRole());

		let dateFormat:Intl.DateTimeFormatOptions = {'year': 'numeric', 'month': 'short', 'weekday': 'short', 'day': 'numeric'};
		makeLine('Created', new Date(this.provTerm.createdTime).toLocaleDateString('default', dateFormat));
		makeLine('Last modified', new Date(this.provTerm.modifiedTime).toLocaleDateString('default', dateFormat));

		// TODO: consider styling status line with red, yellow, and green background colors
		// to indicate "unsubmitted", "requested", and "approved".
		if (this.provTerm.bridge)
		{
			let div = makeLine('Status');
			div.append(this.provTerm.bridgeStatus + ' [' + this.provTerm.bridge.name + '] ');
			div.append('Token: ');
			div.append(this.provTerm.bridge.token); // TODO: turn this into a link that shows something meaningful on the
													// appropriate OntoloBridge page (assuming that comes to exist...)
		}
		else makeLine('Status', 'unsubmitted');

		this.divBridge = $('<div/>').appendTo(grid);
		this.divBridge.css({'grid-row': (++row).toString(), 'grid-column': 'label / end'});

		let divButtons = $('<div/>').appendTo(this.content);
		divButtons.css({'text-align': 'right', 'margin-top': '1em'});

		this.btnDelete = $('<button class="btn btn-normal"/>').appendTo(divButtons);
		this.btnDelete.append('<span class="glyphicon glyphicon-remove" style="height: 1.2em;"></span> Delete');
		this.btnDelete.click(() => this.deleteTerm());
		this.btnDelete.css('margin-left', '0.5em');

		this.btnUpdate = $('<button class="btn btn-action"/>').appendTo(divButtons);
		this.btnUpdate.append('<span class="glyphicon glyphicon-circle-arrow-up" style="height: 1.2em;"></span> Update');
		this.btnUpdate.click(() => this.updateChanges());
		this.btnUpdate.css('margin-left', '0.5em');

		this.inputLabel.change(() => this.statusUpdate());
		this.inputLabel.keyup(() => this.statusUpdate());
		this.areaDescr.change(() => this.statusUpdate());
		this.areaDescr.keyup(() => this.statusUpdate());
		this.areaExplain.change(() => this.statusUpdate());
		this.areaExplain.keyup(() => this.statusUpdate());

		if (!this.provTerm.bridge && ONTOLOBRIDGES.length > 0 && Authentication.canAccessOntoloBridge())
		{
			this.divOntoGroup = $('<div class="btn-group"/>').appendTo(divButtons);
			this.divOntoGroup.css('margin-left', '0.5em');
			let btn = $('<button type="button" class="form-control btn btn-action dropdown-toggle" data-toggle="dropdown"/>').appendTo(this.divOntoGroup);
			let spanTitle = $('<span>OntoloBridge</span>').appendTo(btn);
			btn.append(' <span class="caret"></span>');
			let ulOptions = $('<ul class="dropdown-menu" role="menu"/>').appendTo(this.divOntoGroup);

			let divActive = $('<div/>').appendTo(grid);
			divActive.css({'grid-row': (++row).toString(), 'grid-column': 'label / end'});
			divActive.css('display', 'none');

			for (let n = 0; n < ONTOLOBRIDGES.length; n++)
			{
				let bridge = ONTOLOBRIDGES[n];
				let li = $('<li/>').appendTo(ulOptions);
				let href = $('<a href="#"/>').appendTo(li);
				href.text(bridge.name);
				href.click((event:JQueryEventObject) =>
				{
					this.activateBridge(bridge);
					event.preventDefault();
				});
			}
		}

		this.statusUpdate();
	}

	// create the group selection widget
	private createRole():JQuery
	{
		this.role = this.provTerm.role;

		let div = $('<div/>');
		let divGroup = $('<div class="btn-group" data-toggle="buttons"/>').appendTo(div);
		for (let role of [ProvisionalTermRole.Private, ProvisionalTermRole.Public, ProvisionalTermRole.Deprecated])
		{
			let lblTitle = $('<label class="btn btn-radio"/>').appendTo(divGroup);
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
			lblTitle.click(() =>
			{
				this.role = role;
				this.statusUpdate();
			});
		}
		return div;
	}

	// disable/enable the update button
	private statusUpdate():void
	{
		let modified = this.inputLabel.val() != this.provTerm.label ||
						this.areaDescr.val() != this.provTerm.description ||
						this.areaExplain.val() != this.provTerm.explanation ||
						this.role != this.provTerm.role;
		this.btnUpdate.prop('disabled', !modified);
		if (this.divOntoGroup)
		{
			let available = this.role != ProvisionalTermRole.Private && this.role != ProvisionalTermRole.Deprecated;
			this.divOntoGroup.find('button').prop('disabled', !available);
		}
	}

	private buttonState(disabled:boolean):void
	{
		this.btnUpdate.prop('disabled', disabled);
		this.btnDelete.prop('disabled', disabled);
		if (!disabled) this.statusUpdate();
	}

	// upload modifications to the server
	private updateChanges():void
	{
		let params =
		{
			'provisionalID': this.provTerm.provisionalID,
			'label': this.inputLabel.val(),
			'description': this.areaDescr.val(),
			'explanation': this.areaExplain.val(),
			'role': this.role,
		};
		this.buttonState(true);
		callREST('REST/RequestProvisional', params,
			(data:any) =>
			{
				if (data.success)
				{
					this.onChanged();
					this.hide();
				}
				else alert('Update failed.');
			},
			() => alert('Update failed.'),
			() => this.buttonState(false));
}

	// delete the term from the server
	private deleteTerm():void
	{
		if (!confirm('Are you sure you would like to delete this provisional term?')) return;

		let params = {'provisionalIDList': [this.provTerm.provisionalID]};
		this.buttonState(true);
		callREST('REST/DeleteProvisionalTerm', params,
			(data:any) =>
			{
				const notDeleted:{['ID']:number; ['reason']:string}[] = data.notDeleted;
				let deleted:number[] = data.deleted;
				if (Vec.arrayLength(deleted) >= 1)
				{
					this.onChanged();
					this.hide();
					return;
				}
				const REASON:Record<string, string> =
				{
					'insufficient permission': 'You do not have sufficient permission to delete the term.',
					'not leaf node': 'The term is not a leaf node, you need to delete terms linked to it first.',
					'term is used': 'The term is used as an annotation.',
					'term is used in holding bay': 'The term is used as an annotation in a holding bay entry.',
				};
				const reason = notDeleted[0].reason;
				alert(`Deletion failed. ${REASON[reason]}`);
			},
			() => alert('Deletion failed.'),
			() => this.buttonState(false));
	}

	// actives the page for invoking the corresponding ontolobridge connection, which levels up the term request into
	// something that's waiting to become a real global ontology term
	private activateBridge(bridge:OntoloBridge):void
	{
		if (this.role == ProvisionalTermRole.Private || this.role == ProvisionalTermRole.Deprecated) return;

		this.divBridge.empty();

		let divBlock = $('<div/>').appendTo(this.divBridge);
		divBlock.css({'border': '1px solid #6090A0', 'background-color': 'white', 'padding': '0.5em'});

		let heading = $('<h1/>').appendTo(divBlock);
		heading.css('margin-top', '0');
		heading.text(bridge.name);

		let paraDescr = $('<p/>').appendTo(divBlock);
		paraDescr.css({'margin-left': '2em', 'font-style': 'italic'});
		paraDescr.text(bridge.description);

		divBlock.append('<hr>');

		let paraInfo = $('<p/>').appendTo(divBlock);
		paraInfo.html('Submitting this request to the OntoloBridge connection will initiate the process of adding ' +
					  'the term to a <i>public</i> ontology. It will be checked by an ontology expert prior to approval, ' +
					  'and once it is approved, the temporary provisional term will be <i>replaced</i> by the public URI.');

		let divButtons = $('<div/>').appendTo(divBlock);
		divButtons.css('text-align', 'right');

		let btnCancel = $('<button class="btn btn-normal">Cancel</button>').appendTo(divButtons);
		btnCancel.css('margin-left', '0.5em');
		btnCancel.click(() => this.divBridge.empty());

		let btnSubmit = $('<button class="btn btn-action"/>').appendTo(divButtons);
		btnSubmit.append('<span class="glyphicon glyphicon-upload" style="height: 1.2em;"></span> Submit');
		btnSubmit.css('margin-left', '0.5em');
		btnSubmit.click(() => this.submitBridge(bridge));
	}

	// sends the request: if this succeeds, the escalation process will have been initiated
	private submitBridge(bridge:OntoloBridge):void
	{
		let params =
		{
			'provisionalID': this.provTerm.provisionalID,
			'bridgeName': bridge.name,
		};
		callREST('REST/OntoloBridgeRequest', params,
			(data:any) =>
			{
				if (!data.success)
				{
					alert('Submission failed: ' + data.status);
					return;
				}
				this.onChanged();
				this.hide();
			},
			() => alert('Failed submission'));
	}
}

/* EOF */ }
