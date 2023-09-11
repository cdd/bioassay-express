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

export class SelectAssayChangesDialog extends BootstrapDialog
{
	private originalSchema:SchemaSummary = null; // schema corresponding to incoming assay / null if not applicable
	private holdingBayDeltas:HoldingBayAssay[];
	private holdingBaySchema:SchemaSummary[] = [];

	private cboxList:JQuery[] = []; // checkboxes, one for each row, indicating whether to use corresponding holding bay item

	constructor(private assay:AssayDefinition,
				private usedHbayDeltas:HoldingBayAssay[],
				private callbackAssayChanges:(hbayChanges:HoldingBayAssay[]) => void,
				private modalWasDismissed:(selChangesDlg:SelectAssayChangesDialog) => void = null)
	{
		super('Select Holding Bay');
	}

	// ------------ private methods ------------

	protected populateContent():void
	{
		(async () =>
		{
			if (this.assay.schemaURI)
			{
				let oa = this.assay;
				this.originalSchema = await TemplateManager.asyncGraftTemplates(oa.schemaURI, oa.schemaBranches, oa.schemaDuplication);
			}

			let params = {'holdingIDList': this.assay.holdingIDList};
			this.holdingBayDeltas = (await asyncREST('REST/GetHoldingBay', params)).list;

			let lastSchema = this.originalSchema;
			for (let hb of this.holdingBayDeltas)
			{
				let schemaURI = hb.schemaURI;
				if (!schemaURI && lastSchema) schemaURI = lastSchema.schemaURI;
				if (schemaURI) lastSchema = await TemplateManager.asyncGraftTemplates(schemaURI, hb.schemaBranches, hb.schemaDuplication);
				this.holdingBaySchema.push(lastSchema);
			}

			this.render();
		})();
	}

	private render():void
	{
		let boxCSS = {'background-color': 'rgb(217, 237, 247, 0.33)', 'border': '1px solid rgb(150, 202, 255)',
				'box-shadow': 'rgba(66, 77, 88, 0.1) 0px 0px 5px', 'margin': '0 0 0.5em 0', 'padding': '0.5em'};
		let gridCSS = {'display': 'grid', 'place-items': 'center left', 'align-items': 'stretch',
				'grid-column-gap': '0.5em', 'grid-row-gap': '0.2em',
				'grid-template-columns': '[start check] auto [summary] auto [content] minmax(0, 1fr) [end]'};

		this.content.append('<p>Merging holding bay entries may overwrite any unsaved changes.</p>');

		let divGrid = $('<div/>').appendTo(this.content).css({...boxCSS, ...gridCSS});

		for (let n = 0; n < this.holdingBayDeltas.length; n++)
		{
			let fromSchema = n == 0 ? this.originalSchema : this.holdingBaySchema[n - 1];
			let toSchema = this.holdingBaySchema[n];
			this.createLine(n + 1, divGrid, this.holdingBayDeltas[n], fromSchema, toSchema);
		}

		// list buttons in footer
		let divFooter = $('<div/>').appendTo(this.content).css({'text-align': 'right', 'margin-top': '1em'});

		let btnCancel = $('<button class="btn btn-normal" data-dismiss="modal"/>').appendTo(divFooter).css({'margin-right': '0.5em'});
		btnCancel.text('Cancel');

		let btnApply = $('<button class="btn btn-action"/>').appendTo(divFooter);
		btnApply.text('Apply');
		btnApply.click(() =>
		{
			this.applySelectedAssayChanges();
			this.hide();
		});
	}

	protected onShown():void
	{
		if (this.cboxList.length > 0) this.cboxList[0].focus();
	}
	protected onHidden():void
	{
		if (this.modalWasDismissed) this.modalWasDismissed(this);
	}

	private createLine(gridRow:number, divGrid:JQuery, hbayDelta:HoldingBayAssay, fromSchema:SchemaSummary, toSchema:SchemaSummary):void
	{
		let alreadyUsed = this.usedHbayDeltas.findIndex((e) => e.holdingID == hbayDelta.holdingID) > -1;
		let commonCSS = {'grid-row': gridRow.toString(), 'color': alreadyUsed ? '#888888' : 'black'};

		let divCheck = $('<div/>').appendTo(divGrid).css({...commonCSS, 'grid-column': 'check'});

		let divSummary = $('<div/>').appendTo(divGrid).css({...commonCSS, 'grid-column': 'summary'});

		let divContent = $('<div/>').appendTo(divGrid).css({...commonCSS, 'grid-column': 'content', 'width': '100%', 'overflow': 'auto'});
		divContent.css({'white-space': 'nowrap', 'padding': '0.2em'});
		divContent.css({'border': '1px solid rgb(150, 202, 255, 0.25)', 'background-color': 'rgb(217, 237, 247, 0.5)'});

		// preselect those holding bay items already used and disable corresponding checkbox
		let cbox = $('<input type="checkbox" name="selectedAssayChanges"/>').appendTo(divCheck).css({'margin': '1em'});
		cbox.prop({'checked': alreadyUsed, 'disabled': alreadyUsed});
		this.cboxList.push(cbox);

		this.renderHoldingBaySummary(divSummary, hbayDelta);
		this.renderHoldingBayOverview(divContent, hbayDelta, fromSchema, toSchema);
	}

	public renderHoldingBaySummary(divSummary:JQuery, hbayDelta:HoldingBayAssay):void
	{
		let assayID = $('<p/>').appendTo(divSummary).css({'margin': '5px 0px 3px 0px'});
		assayID.append(`<b>Assay ID</b>:  ${hbayDelta.assayID}`);

		let curator = hbayDelta.curatorName == '' ? hbayDelta.curatorEmail : hbayDelta.curatorName;
		let curatorName = $('<p/>').appendTo(divSummary).css({'margin': '3px 0px 3px 0px'});
		curatorName.append(`<b>Curator</b>:  ${curator}`);

		let schemaURI = $('<p/>').appendTo(divSummary).css({'margin': '3px 0px 3px 0px'});
		schemaURI.append(`<b>Schema URI</b>:  ${hbayDelta.schemaURI}`);

		let submissionTime = $('<p/>').appendTo(divSummary).css({'margin': '3px 0px 3px 0px'});
		submissionTime.append(`<b>Submission time</b>:  ${new Date(hbayDelta.submissionTime).toLocaleString()}`);
	}

	public renderHoldingBayOverview(divContent:JQuery, hbayDelta:HoldingBayAssay, fromSchema:SchemaSummary, toSchema:SchemaSummary):void
	{
		let scrollItemCount = 0;
		if (hbayDelta.deleteFlag)
		{
			divContent.css({'background-color': '#f2dede', 'display': 'grid'});
			let div = $('<div/>').appendTo(divContent).css({'position': 'relative', 'align-self': 'center'});
			let spanExcl = $('<span class="glyphicon glyphicon-exclamation-sign"/>').appendTo(div);
			spanExcl.css({'padding-right': '0.5em', 'color': 'red'});
			div.append(' Assay will be deleted');
			scrollItemCount++;
		}
		if (hbayDelta.uniqueID)
		{
			let textCSS = {'white-space': 'nowrap', 'overflow': 'hidden', 'text-overflow': 'ellipsis', 'width': '50em'};
			let p = $('<p/>').appendTo(divContent).css(textCSS);
			p.append(`<u>Unique ID</u>: ${hbayDelta.uniqueID}`);
			scrollItemCount++;
		}
		if (hbayDelta.text)
		{
			let textCSS = {'white-space': 'nowrap', 'overflow': 'hidden', 'text-overflow': 'ellipsis', 'width': '50em'};
			let p = $('<p/>').appendTo(divContent).css(textCSS);
			p.append(`<u>Text</u>: ${hbayDelta.text}`);
			scrollItemCount++;
		}
		if (hbayDelta.schemaBranches)
		{
			let div = $('<div/>').appendTo(divContent);
			let branchSchemaURIs = hbayDelta.schemaBranches.map((branch) => branch.schemaURI);
			TemplateManager.ensureTemplates(branchSchemaURIs, () =>
			{
				div.append('<u>Schema branches</u>:');
				for (let branch of hbayDelta.schemaBranches)
				{
					let schema = TemplateManager.getTemplate(branch.schemaURI);
					if (!schema) continue;
					let p = $('<p style="margin: 0 0 0 1em; font-weight: bold;"/>').appendTo(div);
					p.text(schema.name);
				}
			});
		}
		if (Vec.arrayLength(hbayDelta.added) > 0)
		{
			divContent.append('<p><u>Added</u>:</p>');
			for (let annot of this.sortedAnnotations(hbayDelta.added, toSchema)) this.appendAnnotation(divContent, annot, toSchema);
			scrollItemCount += 1 + Vec.arrayLength(hbayDelta.added);
		}
		if (Vec.arrayLength(hbayDelta.removed) > 0)
		{
			divContent.append('<p><u>Removed</u>:</p>');
			for (let annot of this.sortedAnnotations(hbayDelta.removed, fromSchema)) this.appendAnnotation(divContent, annot, fromSchema);
			scrollItemCount += 1 + Vec.arrayLength(hbayDelta.removed);
		}

		// expand max-height whenever we have more than 4 scroll items in the delta
		if (scrollItemCount > 4)
		{
			let numPx = Math.min(360, 45 * scrollItemCount);
			divContent.css('max-height', numPx + 'px');
		}
	}

	private appendAnnotation(parent:JQuery, annot:AssayAnnotation, schema:SchemaSummary):void
	{
		let p = $('<p/>').appendTo(parent).css({'padding': '0.4em 0 0.4em 2em', 'margin': '0'});

		let idx = schema.assignments.findIndex((look) => samePropGroupNest(annot.propURI, annot.groupNest, look.propURI, look.groupNest));
		if (idx < 0)
		{
			p.text('(not found)');
			return;
		}
		let assn = schema.assignments[idx];

		if (assn.groupLabel) for (let i = assn.groupLabel.length - 1; i >= 0; i--)
		{
			let blkGroup = $('<font/>').appendTo(p);
			blkGroup.css({'background': 'white', 'border-radius': '5px', 'border': '1px solid black', 'padding': '0.3em'});
			blkGroup.text(assn.groupLabel[i]);

			let [_, dupidx] = TemplateManager.decomposeSuffixGroupURI(assn.groupNest[i]);
			if (dupidx > 0)
			{
				p.append('&nbsp;');
				let span = $('<span/>').appendTo(p);
				span.css({'background': 'black', 'color': 'white', 'border-radius': '5px', 'padding': '0.3em'});
				span.text(dupidx.toString());
			}

			p.append('&nbsp;');
		}

		let blkProp = $('<font/>').appendTo(p);
		blkProp.addClass('weakblue');
		blkProp.css({'border-radius': '5px', 'border': '1px solid black', 'padding': '0.3em'});
		blkProp.append($(Popover.displayOntologyAssn(assn).elHTML));

		p.append('&nbsp;');

		let vlabel = annot.valueLabel;
		if (!vlabel) vlabel = '?';
		if (vlabel.length > 100) vlabel = vlabel.substring(0, 100) + '...';

		if (annot.valueURI)
		{
			let blkValue = $('<font/>').appendTo(p);
			blkValue.addClass('lightgray');
			blkValue.css({'border-radius': '5px', 'border': '1px solid black', 'padding': '0.3em'});
			blkValue.append($(Popover.displayOntologyValue(annot).elHTML));
		}
		else
		{
			let blkText = $('<i/>').appendTo(p);
			blkText.text(annot.valueLabel);
		}
	}

	private applySelectedAssayChanges():void
	{
		let selectedDeltas:HoldingBayAssay[] = [];
		for (let n = 0; n < this.holdingBayDeltas.length; n++)
		{
			if (this.cboxList[n].prop('checked')) selectedDeltas.push(this.holdingBayDeltas[n]);
		}
		if (this.callbackAssayChanges != null) this.callbackAssayChanges(selectedDeltas);
	}

	// sort the annotations according to the position in the schema
	private sortedAnnotations(annotations:AssayAnnotation[], schema:SchemaSummary):AssayAnnotation[]
	{
		let assnIndex:Record<string, number> = {};
		schema.assignments.forEach((assn, idx) => assnIndex[keyPropGroup(assn.propURI, assn.groupNest)] = idx);
		let prio = (annot:AssayAnnotation):number =>
		{
			let idx = assnIndex[keyPropGroup(annot.propURI, annot.groupNest)];
			return idx >= 0 ? idx : Number.MAX_SAFE_INTEGER;
		};
		let sorted = annotations.slice(0);
		sorted.sort((annot1, annot2) => prio(annot1) - prio(annot2));
		return sorted;
	}
}

/* EOF */ }
