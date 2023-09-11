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
	Assay submission dialog: contacts the server with a request to make changes to the given assay. The service takes an
	optional "original assay" parameter, for cases where it is more appropriate to calculate the difference (i.e. annotations
	got added or deleted).
*/

export class SubmitDialog extends BootstrapDialog
{
	private btnUpdate:JQuery;
	private btnSubmit:JQuery;
	private divMissing:JQuery;

	// what gets sent to the submission service
	private assayUniqueID:string = null;
	private assayText:string = null;
	private assaySchemaURI:string = null;
	private assayBranches:SchemaBranch[] = null;
	private assayDuplication:SchemaDuplication[] = null;
	private annotsAdded:AssayAnnotation[] = [];
	private annotsRemoved:AssayAnnotation[] = [];
	private annotsExtracted:AssayAnnotation[] = [];

	// defined only if there are text extracts
	private chkTextExtracts:JQuery = null;
	private includeExtracts = false; // by default: don't do anything with any text-extracted terms
	private onlyExtracts = true; // true if text extracts are the only content
	private divExtracts:JQuery = null;
	private deleteSelectedHoldingBay:JQuery = null;

	public callbackUpdated:(assayID:number) => void = null; // called when updated directly
	public callbackHolding:(holdingID:number) => void = null; // called when placed in holding bay

	constructor(private assay:AssayDefinition, originalAssay:AssayDefinition,
				private schema:SchemaSummary, private originalSchema:SchemaSummary,
				private extractions:AssayTextExtraction[],
				private violations:AssayAnnotation[] = [],
				private assayHasHoldingBay:boolean = false,
				private selectedHbayChanges:HoldingBayAssay[] = [])
	{
		super('Submit Assay');
		this.withCloseButton = false;

		this.assayUniqueID = assay.uniqueID && assay.uniqueID != originalAssay.uniqueID ? assay.uniqueID : null;
		this.assayText = assay.text != originalAssay.text ? assay.text : null;
		this.assaySchemaURI = assay.schemaURI && assay.schemaURI != originalAssay.schemaURI ? assay.schemaURI : null;
		this.assayBranches = this.composeBranches(assay.schemaBranches, originalAssay.schemaBranches);
		this.assayDuplication = this.composeDuplication(assay.schemaDuplication, originalAssay.schemaDuplication);
		this.composeDifference(this.annotsAdded, assay.annotations, originalAssay.annotations);
		this.composeDifference(this.annotsRemoved, originalAssay.annotations, assay.annotations);
		this.composeExtractions();
	}

	// returns true if there are assay differences that result from user intervention
	public isModified():boolean
	{
		if (this.assayUniqueID != null || this.assayText != null || this.assaySchemaURI != null ||
			this.assayBranches != null || this.assayDuplication != null) return true;
		if (this.annotsAdded.length > 0 || this.annotsRemoved.length > 0 || this.annotsExtracted.length > 0) return true;
		return false;
	}

	public show():void
	{
		if (!this.isModified())
		{
			alert('Assay has not changed: no need to submit anything.');
			return;
		}
		super.show();
	}

	// ------------ private methods ------------

	protected populateContent():void
	{
		// post-modify the button area

		this.btnSubmit = $('<button class="btn btn-action"/>').appendTo(this.areaTopRight);
		this.btnSubmit.css({'margin-right': '0.5em'});
		this.btnSubmit.append('<span class="glyphicon glyphicon-upload"></span> Submit to Holding Bay');
		this.btnSubmit.click(() => this.doSubmit(true));

		if (Authentication.canSubmitDirectly())
		{
			this.btnUpdate = $('<button class="btn btn-normal"/>').appendTo(this.areaTopRight);
			this.btnUpdate.css({'margin-right': '0.5em'});
			this.btnUpdate.append('<span class="glyphicon glyphicon-upload"></span> Update Directly');
			this.btnUpdate.click(() => this.doSubmit(false));
		}

		this.areaTopRight.append('<button class="btn btn-action" data-dismiss="modal" aria-hidden="true">Cancel</button>');

		// inform user about existing holding bay entries
		if (this.assayHasHoldingBay)
		{
			let p = $('<div/>').addClass('alert alert-info').appendTo(this.content);
			p.html('The assay has holding bay entries. It will be safer to <i>Submit</i> to the holding bay and review all submissions.');
		}

		// offer user to delete merged holding bay entries when submitting assay
		if (this.selectedHbayChanges && this.selectedHbayChanges.length > 0)
		{
			const entryIDs = this.selectedHbayChanges.map((entry) => entry.holdingID);
			const sentry1 = entryIDs.length == 1 ? 'entry' : 'entries';
			const sentry2 = entryIDs.length == 1 ? 'this entry' : 'these entries';
			let p = $('<div/>').addClass('alert alert-info').appendTo(this.content);
			p.append(`You merged holding bay ${sentry1} (${entryIDs.join(', ')}). Do you want to remove ${sentry2} from the holding bay when submitting the assay?`);
			p.append(' ');
			this.deleteSelectedHoldingBay = $('<input type="checkbox"/>').appendTo(p);
		}

		// fill the main content
		let p = $('<p/>').appendTo(this.content);
		let msg = 'Submitting this assay will place your modifications into the holding bay, pending approval. ' +
				  'The proposed changes will be viewable separately in the meanwhile.';
		if (Authentication.canSubmitDirectly())
		{
			msg = 'You have permission to <i>Update</i> modifications directly to the database. Updated submissions will be ' +
				  'applied immediately, and the modifications recorded as part of the annotation history. Or you can ' +
				  '<i>Submit</i> to the holding bay.';
		}
		p.html(msg);

		if (this.annotsExtracted.length > 0)
		{
			this.divExtracts = $('<div/>').appendTo(this.content);
			this.divExtracts.css({'border': '1px solid black', 'background-color': Theme.WEAK_HTML});
			this.divExtracts.css({'padding': '0.5em', 'margin-bottom': '0.5em'});

			let p = $('<p/>').appendTo(this.divExtracts);
			this.chkTextExtracts = $('<input type="checkbox" id="chkTextExtracts"/>').appendTo(p);
			this.chkTextExtracts.prop('checked', this.includeExtracts);
			this.chkTextExtracts.change(() => this.updateTextExtracts());
			let msg = 'Include text-extracted annotation' + (this.annotsExtracted.length == 1 ? '' : 's');
			p.append(` <label for="chkTextExtracts" style="font-weight: normal;">${msg}</label>`);

			this.renderAnnotations(this.annotsExtracted, this.divExtracts, this.schema);
		}

		if (this.assayUniqueID != null)
		{
			let p = $('<p/>').appendTo(this.content);
			let [uid, src] = UniqueIdentifier.parseKey(this.assayUniqueID);
			let txt = uid ? uid.name + ' ' + src : this.assayUniqueID;
			p.html('Unique ID set to: <b>' + escapeHTML(txt) + '</b>');
			this.onlyExtracts = false;
		}
		if (this.assayText != null)
		{
			let p = $('<p/>').appendTo(this.content);
			p.html('Replacing text (length <b>' + this.assayText.length + '</b> characters).');
			this.onlyExtracts = false;
		}

		if (this.assaySchemaURI)
		{
			this.content.append('<p>Schema changed to <b>' + escapeHTML(this.schema.name) + '</b>.</p>');
			this.onlyExtracts = false;
		}

		if (this.assayBranches)
		{
			let div = $('<div/>').appendTo(this.content);
			TemplateManager.ensureTemplates(this.assayBranches.map((branch) => branch.schemaURI), () =>
			{
				div.append('<p>Schema branches:</p>');
				for (let branch of this.assayBranches)
				{
					let schema = TemplateManager.getTemplate(branch.schemaURI);
					if (!schema) continue;
					let p = $('<p style="margin: 0 0 0 1em; font-weight: bold;"/>').appendTo(div);
					p.text(schema.name);
				}
			});
			this.onlyExtracts = false;
		}

		if (this.assayDuplication)
		{
			// (show something?)
			this.onlyExtracts = false;
		}

		if (this.annotsAdded.length > 0)
		{
			this.content.append('<p><u>Added</u>:</p>');
			this.renderAnnotations(this.annotsAdded, this.content, this.schema);
			this.onlyExtracts = false;
		}
		if (this.annotsRemoved.length > 0)
		{
			this.content.append('<p><u>Removed</u>:</p>');
			this.renderAnnotations(this.annotsRemoved, this.content, this.originalSchema);
			this.onlyExtracts = false;
		}

		this.divMissing = $('<div/>').appendTo(this.content);
		this.renderMissing();

		this.updateTextExtracts();
	}

	// action time: push the result out
	private doSubmit(toHoldingBay:boolean):void
	{
		if (!this.includeExtracts && this.onlyExtracts) return;

		if (this.btnUpdate) this.btnUpdate.prop('disabled', true);
		this.btnSubmit.prop('disabled', true);

		let added = this.annotsAdded;
		if (this.includeExtracts) added = Vec.concat(added, this.annotsExtracted);

		let params =
		{
			'assayID': this.assay.assayID,
			'uniqueID': this.assayUniqueID,
			'schemaURI': this.assaySchemaURI,
			'schemaBranches': this.assayBranches,
			'schemaDuplication': this.assayDuplication,
			'text': this.assayText,
			'added': added,
			'removed': this.annotsRemoved,
			'holdingBay': toHoldingBay
		};

		callREST('REST/SubmitAssay', params,
			(data:any) =>
			{
				if (this.deleteSelectedHoldingBay && this.deleteSelectedHoldingBay.prop('checked')) this.deleteHoldingBayEntries();
				this.submissionResult(data);
			},
			() => alert('Submission failed'));
	}

	private deleteHoldingBayEntries():void
	{
		const entryIDs = this.selectedHbayChanges.map((entry) => entry.holdingID);
		const sentry1 = entryIDs.length == 1 ? 'entry' : 'entries';
		callREST('REST/ApplyHoldingBay', {'deleteList': entryIDs},
			() => { }, // alert(`Holding bay ${sentry1} deleted.`); },
			() => { alert(`Deleting holding bay ${sentry1} failed.`); }
		);
	}

	// branch list: non-null only if different
	private composeBranches(newBranches:SchemaBranch[], oldBranches:SchemaBranch[]):SchemaBranch[]
	{
		if (newBranches == null) newBranches = [];
		if (oldBranches == null) oldBranches = [];
		if (newBranches.length != oldBranches.length) return newBranches;
		for (let n = 0; n < newBranches.length; n++)
		{
			if (newBranches[n].schemaURI != oldBranches[n].schemaURI || !sameGroupNest(newBranches[n].groupNest, oldBranches[n].groupNest))
				return newBranches;
		}
		return null;
	}

	// duplication ilst: non-null only if different
	private composeDuplication(newDupl:SchemaDuplication[], oldDupl:SchemaDuplication[]):SchemaDuplication[]
	{
		if (newDupl == null) newDupl = [];
		if (oldDupl == null) oldDupl = [];
		if (newDupl.length != oldDupl.length) return newDupl;
		for (let n = 0; n < newDupl.length; n++)
		{
			if (newDupl[n].multiplicity != oldDupl[n].multiplicity || !sameGroupNest(newDupl[n].groupNest, oldDupl[n].groupNest))
				return newDupl;
		}
		return null;
	}

	// make up a diff-list between the two
	private composeDifference(output:AssayAnnotation[], include:AssayAnnotation[], exclude:AssayAnnotation[]):void
	{
		let exclKeys = new Set<string>();
		const SEP = ':#*#:';
		for (let annot of exclude) exclKeys.add(keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI + SEP + annot.valueLabel));
		for (let annot of include)
		{
			if (exclKeys.has(keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI + SEP + annot.valueLabel))) continue;
			output.push(annot);
		}
	}

	// contemplates what to make of the extracted values
	private composeExtractions():void
	{
		const SEP = '::';
		let got = new Set<string>();
		for (let annot of this.assay.annotations) if (annot.valueURI)
			got.add(keyPropGroup(annot.propURI, annot.groupNest) + SEP + annot.valueURI);
		for (let annot of this.extractions) if (!got.has(keyPropGroup(annot.propURI, annot.groupNest) + SEP + annot.valueURI))
			this.annotsExtracted.push(annot);
	}

	// received result of submission
	private submissionResult(data:any):void
	{
		this.btnSubmit.prop('disabled', true);

		let success:boolean = data.success;
		let status:string = data.status;
		let holdingID:number = data.holdingID; // only if added to holding bay
		let assayID:number = data.assayID; // only if applied directly

		if (success)
		{
			let msg = 'Assay placed in holding bay.';
			if (status == 'applied') msg = 'Assay applied directly to the database.';
			this.content.text(msg);
			this.dlg.delay(1500).fadeOut('slow');
			setTimeout(() =>
			{
				this.dlg.modal('hide');
				if (assayID)
				{
					if (this.callbackUpdated) this.callbackUpdated(assayID);
				}
				else if (holdingID)
				{
					if (this.callbackHolding) this.callbackHolding(holdingID);
				}
			}, 2000);
		}
		else
		{
			let msg = 'Submission failed.';
			if (status == 'nologin') msg = 'Login credentials invalid, possibly expired.';
			else if (status == 'denied') msg = 'Permission denied.';
			this.content.text(msg);
		}
	}

	// render a list of annotations, in the same order as their assignments appear in the schema, with the assignment
	// names rather than labels pulled from the underlying ontology
	private renderAnnotations(annotList:AssayAnnotation[], parent:JQuery, schema:SchemaSummary):void
	{
		let assnGroup:Record<string, AssayAnnotation[]> = {};
		for (let annot of annotList)
		{
			let key = keyPropGroup(annot.propURI, annot.groupNest), grp = assnGroup[key];
			if (grp) grp.push(annot); else assnGroup[key] = [annot];
		}

		let mapViol:Record<string, AssayAnnotation> = {};
		for (let viol of this.violations) mapViol[keyPropGroupValue(viol.propURI, viol.groupNest, viol.valueURI)] = viol;

		for (let assn of schema.assignments)
		{
			let grp = assnGroup[keyPropGroup(assn.propURI, assn.groupNest)];
			if (grp) for (let annot of grp)
			{
				let p = $('<p/>').appendTo(parent).css({'padding': '0.4em 0 0.4em 2em', 'margin': '0'});

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

					if (mapViol[keyPropGroupValue(annot.propURI, annot.groupNest, annot.valueURI)])
					{
						p.append('&nbsp;');
						let spanGlyph = $('<span class="glyphicon glyphicon glyphicon-fire"/>').appendTo(p);
						spanGlyph.css({'height': '1.2em', 'color': '#FF0000'});
						Popover.hover(domLegacy(spanGlyph), null, 'Annotation is in violation of axiomatic rules.');
					}
				}
				else
				{
					let blkText = $('<i/>').appendTo(p);
					blkText.text(annot.valueLabel);
				}
			}
		}
	}

	// displays information about mandatory assignments without content
	private renderMissing():void
	{
		this.divMissing.empty();

		let gotAlready = new Set<string>();
		for (let annot of this.assay.annotations) gotAlready.add(keyPropGroup(annot.propURI, annot.groupNest));
		if (this.includeExtracts) for (let extract of this.extractions) gotAlready.add(keyPropGroup(extract.propURI, extract.groupNest));

		let assnBlank:SchemaAssignment[] = [];
		for (let assn of this.schema.assignments) if (assn.mandatory)
		{
			let key = keyPropGroup(assn.propURI, assn.groupNest);
			if (!gotAlready.has(key)) assnBlank.push(assn);
		}
		if (assnBlank.length == 0) return;

		let pfx = assnBlank.length == 1 ? 'This assignment' : 'These assignments';
		this.divMissing.append('<div style="margin-top: 0.5em;">' + pfx + '  should not be blank:</div>');

		for (let assn of assnBlank)
		{
			let p = $('<p/>').appendTo(this.divMissing);
			p.css({'padding': '0.4em 0 0.4em 2em', 'margin': '0'});
			if (assn.groupLabel) for (let i = assn.groupLabel.length - 1; i >= 0; i--)
			{
				let blkGroup = $('<font/>').appendTo(p);
				blkGroup.css({'background': 'white', 'border-radius': 5, 'border': '1px solid black', 'padding': '0.3em'});
				blkGroup.text(assn.groupLabel[i]);
				p.append('&nbsp;');
			}

			let blkProp = $('<font/>').appendTo(p);
			blkProp.addClass('weakblue');
			blkProp.css({'border-radius': 5, 'border': '1px solid black', 'padding': '0.3em'});
			blkProp.append($(Popover.displayOntologyAssn(assn).elHTML));
		}
	}

	// toggle for upgrading text extracts has been modified
	private updateTextExtracts():void
	{
		if (!this.chkTextExtracts) return;

		this.includeExtracts = this.chkTextExtracts.prop('checked');
		this.divExtracts.find('font').css('opacity', this.includeExtracts ? 1 : 0.5);
		this.btnSubmit.prop('disabled', !this.includeExtracts && this.onlyExtracts);
		this.renderMissing();
	}
}

/* EOF */ }
