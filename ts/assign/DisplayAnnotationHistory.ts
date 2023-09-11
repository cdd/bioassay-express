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
	Presents a renderable display of the history of an assay's annotation, if relevant information is available.
*/

export class DisplayAnnotationHistory
{
	private domParent:JQuery;
	private isOpen = false;
	private btnRestoreAnnotationList:JQuery[] = [];
	private btnRestoreTextList:JQuery[] = [];

	constructor(private delegate:AssignmentDelegate)
	{
	}

	// creates the pieces for convenient viewing
	public render(domParent:JQuery):void
	{
		this.domParent = domParent;

		let assay = this.delegate.assay;
		if (!assay.curationTime && !assay.curatorID && Vec.arrayLength(assay.history) == 0) return;

		let table = $('<table></table>').appendTo(this.domParent);

		let tr = $('<tr></tr>').appendTo(table);
		let td = $('<td></td>').appendTo(tr);
		td.css({'padding-right': '0.5em'});
		let btn = $('<button type="button" class="btn btn-xs btn-normal"></button>').appendTo(td);
		btn.css({'padding-left': 0, 'padding-right': '0', 'margin-top': '0.1em'});
		let span = $('<span class="glyphicon glyphicon-plus" style="width: 1.6em; height: 1.2em;"></span>').appendTo(btn);

		td = $('<td></td>').appendTo(tr);
		td.css({'text-align': 'left', 'font-weight': 'bold'});
		td.text('Annotation History');

		let trLines:JQuery[] = [];
		btn.click(() => this.toggleOpen(span, trLines));

		// add the summary
		if (assay.curationTime > 0 || assay.curatorID)
		{
			tr = $('<tr style="display: none;"></tr>').appendTo(table);
			trLines.push(tr);
			tr.append($('<td></td>'));
			td = $('<td style="text-align: left;"></td>').appendTo(tr);

			if (assay.curationTime > 0)
			{
				let time = new Date(assay.curationTime);
				let para = $('<p style="margin-bottom: 0;"></p>').appendTo(td);
				para.text('Most recently modified: ' + time.toLocaleString());
			}
			if (assay.curatorID)
			{
				let para = $('<p style="margin-bottom: 0;"></p>').appendTo(td);
				let cname = assay.curatorName ? assay.curatorName : assay.curatorEmail;
				para.text('Last curator: ' + escapeHTML(orBlank(cname)) + ' (' + escapeHTML(assay.curatorID) + ')');
			}
		}

		// fields that get patched: these start with current and iterate backwards
		let currentText = this.delegate.initialText;
		let currentUniqueID = assay.uniqueID;

		// create each history block
		if (assay.history) for (let n = assay.history.length - 1; n >= 0; n--)
		{
			let h = assay.history[n];

			tr = $('<tr style="display: none;"></tr>').appendTo(table);
			trLines.push(tr);
			tr.append($('<td></td>'));
			td = $('<td style="text-align: left;"></td>').appendTo(tr);
			td.css('border-top', '1px solid #E6EDF2');

			if (h.curationTime > 0)
			{
				let time = new Date(h.curationTime);
				let para = $('<p style="margin-bottom: 0;"></p>').appendTo(td);
				para.text('Time: ' + time.toLocaleString());
			}
			if (h.curatorID)
			{
				let para = $('<p style="margin-bottom: 0;"></p>').appendTo(td);
				let cname = h.curatorName ? h.curatorName : h.curatorEmail;
				para.text('Curator: ' + escapeHTML(cname) + ' (' + escapeHTML(h.curatorID) + ')');
			}
			if (h.textPatch)
			{
				let para = $('<p></p>').appendTo(td);

				// do a bit of GNU-diff sifting to give some idea of the extent of the change
				let major = 0, minor = 0;
				for (let line of h.textPatch.split('\n'))
				{
					if (line.length == 0) {}
					else if (line.charAt(0) == '@') major++;
					else minor++;
				}
				para.append('<b>Text</b>: changed ');
				para.append(major + ' section' + (major == 1 ? '' : 's') + ', ');
				para.append(minor + ' piece' + (minor == 1 ? '' : 's') + '. ');

				currentText = applyDiffPatch(h.textPatch, currentText);

				let snap = $('<span></span>').appendTo(para);
				let href = $('<a href="#">Show...</a>').appendTo(snap);
				let showText = currentText;
				snap.click(() => {snap.css('font-style', 'italic'); snap.text(showText); return false;});
			}
			if (h.uniqueIDPatch)
			{
				let para = $('<p style="margin-bottom: 0;"></p>').appendTo(td);
				para.append('Changed unique ID from ');
				let b1 = $('<b></b>').appendTo(para);
				para.append(' to ');
				let b2 = $('<b></b>').appendTo(para);
				para.append('.');

				let toUniqueID = currentUniqueID, fromUniqueID = applyDiffPatch(h.uniqueIDPatch, currentUniqueID);

				let [uid, src] = UniqueIdentifier.parseKey(fromUniqueID);
				if (uid) b1.text(uid.name + ' ' + src); else b1.text(fromUniqueID);

				[uid, src] = UniqueIdentifier.parseKey(toUniqueID);
				if (uid) b2.text(uid.name + ' ' + src); else b2.text(fromUniqueID);

				currentUniqueID = fromUniqueID;
			}

			let subTable = $('<table></table>').appendTo(td);
			this.createAnnotations(subTable, 'Added:', Vec.concat(h.annotsAdded, h.labelsAdded));
			this.createAnnotations(subTable, 'Removed:', Vec.concat(h.annotsRemoved, h.labelsRemoved));

			let divbtn = $('<div></div>').appendTo(td);
			divbtn.css('text-align', 'center');
			divbtn.css('padding', '0.5em');

			if (h.annotsAdded.length > 0 || h.annotsRemoved.length > 0 || h.labelsAdded.length > 0 || h.labelsRemoved.length > 0)
			{
				let btnRestoreAnnotations = $('<button class="btn btn-action"></button>').appendTo(divbtn);
				btnRestoreAnnotations.append('<span class="glyphicon glyphicon-repeat"></span> Restore Annotations');
				btnRestoreAnnotations.click(() => this.delegate.actionRestoreFromHistory(h));
				this.btnRestoreAnnotationList.push(btnRestoreAnnotations);
			}
			if (h.textPatch)
			{
				divbtn.append(' ');
				let restoreText = currentText;
				let btnRestoreText = $('<button class="btn btn-action"></button>').appendTo(divbtn);
				btnRestoreText.append('<span class="glyphicon glyphicon-repeat"></span> Restore Text');
				btnRestoreText.click(() => this.delegate.actionRestoreHistoryText(restoreText));
				this.btnRestoreTextList.push(btnRestoreText);
			}
		}
	}

	public updateEditStatus():void
	{
		for (let btn of this.btnRestoreAnnotationList) btn.prop('disabled', !this.delegate.editMode);
		for (let btn of this.btnRestoreTextList) btn.prop('disabled', !this.delegate.editMode);
	}

	// ------------ private methods ------------

	// toggles visibility on or off
	private toggleOpen(span:JQuery, trLines:JQuery[]):void
	{
		this.isOpen = !this.isOpen;
		span.addClass(this.isOpen ? 'glyphicon-minus' : 'glyphicon-plus');
		span.removeClass(this.isOpen ? 'glyphicon-plus' : 'glyphicon-minus');
		for (let tr of trLines) tr.css('display', this.isOpen ? 'table-row' : 'none');
	}

	// renders an array of annotations into an array
	private createAnnotations(table:JQuery, title:string, annotations:AssayAnnotation[]):void
	{
		if (annotations == null) return;

		// sort the annotations based on their order of appearance in the template
		if (annotations.length > 1)
		{
			let orderAssn:Record<string, number> = {}, assnList = this.delegate.schema.assignments;
			for (let n = 0; n < assnList.length; n++) orderAssn[keyPropGroup(assnList[n].propURI, assnList[n].groupNest)] = n;
			let order = Vec.idxSort(annotations.map((a) => orderAssn[keyPropGroup(a.propURI, a.groupNest)] || Number.MAX_VALUE));
			annotations = Vec.idxGet(annotations, order);
		}

		// render each one
		for (let n = 0; n < annotations.length; n++)
		{
			let annot = annotations[n];
			let tr = $('<tr></tr>').appendTo(table);
			let td = $('<td></td>').appendTo(tr);
			if (n == 0)
			{
				td.css('font-weight', 'bold');
				td.css('padding-right', '0.5em');
				td.text(title);
			}

			td = $('<td></td>').appendTo(tr);
			let para = $('<p style="padding: 0.5em 0 0 0;"></p>').appendTo(td);

			if (annot.groupLabel) for (let i = annot.groupLabel.length - 1; i >= 0; i--)
			{
				let blkGroup = $('<font></font>').appendTo(para);
				blkGroup.css('background', 'white');
				blkGroup.css('border-radius', 5);
				blkGroup.css('border', '1px solid black');
				blkGroup.css('padding', '0.3em');
				blkGroup.text(annot.groupLabel[i]);

				para.append('&nbsp;');
			}

			let blkProp = $('<font></font>').appendTo(para);
			blkProp.addClass('weakblue');
			blkProp.css('border-radius', 5);
			blkProp.css('border', '1px solid black');
			blkProp.css('padding', '0.3em');
			blkProp.css('cursor', 'pointer');
			blkProp.append($(Popover.displayOntologyProp(annot).elHTML));

			para.append('&nbsp;');

			if (annot.valueURI)
			{
				let blkValue = $('<font></font>').appendTo(para);
				blkValue.addClass('lightgray');
				blkValue.css('border-radius', 5);
				blkValue.css('border', '1px solid black');
				blkValue.css('padding', '0.3em');
				blkValue.css('cursor', 'pointer');
				blkValue.append($(Popover.displayOntologyValue(annot).elHTML));
			}
			else
			{
				let ital = $('<i></i>').appendTo(para);
				ital.text(annot.valueLabel);
			}
		}
	}
}

/* EOF */ }
