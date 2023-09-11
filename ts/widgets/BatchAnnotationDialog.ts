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
	Dialog for previewing a large batch of changes to the assay.
*/

export class BatchAnnotationDialog extends BootstrapDialog
{
	private btnConfirm:JQuery;
	public callbackConfirmed:(annotationList:AssayAnnotation[]) => void = null; // called when confirmed

	constructor(private schema:SchemaSummary, private annotationList:AssayAnnotation[], private showCheckboxes:boolean = false, private annotationSelected:boolean[])
	{
		super('Review Batch Annotations');
		this.withCloseButton = false;
	}

	public isModified():boolean
	{
		if (this.annotationList.length > 0) return true;
		return false;
	}

	public show():void
	{
		if (!this.isModified())
		{
			alert('Nothing to change.');
			return;
		}
		super.show();
	}

	// ------------ private methods ------------

	// based on code from SubmitDialog.ts
	protected populateContent():void
	{
		// post-modify the button area

		this.btnConfirm = $('<button class="btn btn-action"/>').appendTo(this.areaTopRight);
		this.btnConfirm.css({'margin-right': '0.5em'});
		this.btnConfirm.append('<span class="glyphicon glyphicon-upload"></span> Apply Annotations');
		this.btnConfirm.click(() => this.doApply());

		this.areaTopRight.append('<button class="btn btn-action" data-dismiss="modal" aria-hidden="true">Cancel</button>');

		// fill the main content
		let p = $('<p/>').appendTo(this.content);
		let msg = 'The following annotations will be impacted by this batch annotation';

		p.html(msg);

		if (this.annotationList.length > 0)
		{
			this.content.append('<p><u>Annotations Impacted</u>:</p>');
			this.renderAnnotations(this.annotationList, this.content);
		}
	}

	private doApply():void
	{
		let msg = 'Applying Batch Annotations';
		this.content.text(msg);
		this.dlg.delay(100).fadeOut('slow');
		setTimeout(() =>
		{
			this.dlg.modal('hide');
			if (this.callbackConfirmed) this.callbackConfirmed(this.getSelectedAnnotations());
		}, 2000);
	}

	private getSelectedAnnotations():AssayAnnotation[]
	{
		let returnList:AssayAnnotation[] = [];

		for (let n = 0; n < this.annotationList.length; n++)
		{
			if (this.annotationSelected[n] == true) returnList.push(this.annotationList[n]);
		}

		return returnList;
	}

	// render a list of annotations, in the same order as their assignments appear in the schema, with the assignment
	// names rather than labels pulled from the underlying ontology
	private renderAnnotations(annotList:AssayAnnotation[], parent:JQuery):void
	{
		let assnGroup:Record<string, AssayAnnotation[]> = {};
		for (let annot of annotList)
		{
			let key = keyPropGroup(annot.propURI, annot.groupNest), grp = assnGroup[key];
			if (grp) grp.push(annot); else assnGroup[key] = [annot];
		}

		for (let assn of this.schema.assignments)
		{
			let grp = assnGroup[keyPropGroup(assn.propURI, assn.groupNest)];

			if (grp) for (let annot of grp)
			{
				let p = $('<p/>').appendTo(parent);
				p.css({'padding': '0.4em 0 0.4em 2em', 'margin': '0'});

				if (this.showCheckboxes)
				{
					let chkBox = $('<input type="checkbox"/>').appendTo(p);
					chkBox.css('margin-right', '5px');

					// set initial setting to checked
					chkBox.prop('checked', this.annotationSelected[this.annotationList.indexOf(annot)]);
					chkBox.click(() => this.toggleAnnotationSelected(annot));
				}

				if (assn.groupLabel) for (let i = assn.groupLabel.length - 1; i >= 0; i--)
				{
					let blkGroup = $('<font/>').appendTo(p);
					blkGroup.css({'background': 'white', 'border-radius': '5px', 'border': '1px solid black', 'padding': '0.3em'});
					blkGroup.text(assn.groupLabel[i]);

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
		}
	}

	private toggleAnnotationSelected(annot:AssayAnnotation):void
	{
		let annotationIndex = this.annotationList.indexOf(annot);
		this.annotationSelected[annotationIndex] = !this.annotationSelected[annotationIndex];
	}

}

/* EOF */ }
