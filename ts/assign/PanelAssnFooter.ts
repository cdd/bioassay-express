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
	Assignment footer panel: everything that comes after the main annotation working area on the assignment page, including
	a second set of command buttons, other metadata & compounds.
*/

export class PanelAssnFooter
{
	public btnWidget:JQuery = null;
	public btnSubmit:JQuery;
	public btnDownload:JQuery;
	public annotationHistory:DisplayAnnotationHistory = null;
	// (obsolete) public divLink:JQuery;

	constructor(private delegate:AssignmentDelegate,
		public divButtons:JQuery, public divXRef:JQuery, public divHistory:JQuery, public divWidget:JQuery, public divMeasure:JQuery)
	{
	}

	public render():void
	{
		let uniqueID = this.delegate.assay.uniqueID || '';
		if (uniqueID.startsWith('pubchemAID:'))
		{
			this.btnWidget = $('<button class="btn btn-normal"></button>').appendTo($('<div></div>').appendTo(this.divButtons));
			this.btnWidget.append('<img src="images/benzene.svg" style="padding-bottom: 3px;"></img> PubChem Widget');
			this.btnWidget.click(() => this.showWidget());
		}

		this.btnSubmit = $('<button class="btn btn-action"></button>').appendTo($('<div></div>').appendTo(this.divButtons));
		this.btnSubmit.append('<span class="glyphicon glyphicon-upload"></span> Submit Changes');
		this.btnSubmit.click(this.delegate.actionSubmitAssayChanges);

		this.btnDownload = $('<button class="btn btn-action"></button>').appendTo($('<div></div>').appendTo(this.divButtons));
		this.btnDownload.append('<span class="glyphicon glyphicon-save"></span> Download');
		this.btnDownload.click(this.delegate.actionDownloadAssay);

		//this.divLink = $('<div></div>').appendTo(this.divButtons);

		this.setupXRefWidget();
		this.rebuildHistory();
	}

	// presents the assay history
	public rebuildHistory():void
	{
		let para = this.divHistory;
		para.empty();

		// determine list of holding bay IDs still not used in assay shown here
		let hbay:number[] = [], usedCount:number = 0;
		if (this.delegate.assay.holdingIDList != null)
		{
			for (let holdingID of this.delegate.assay.holdingIDList)
			{
				let found:boolean = false;
				for (let hbayDelta of this.delegate.selectedHbayChanges)
				{
					if (holdingID == hbayDelta.holdingID) {found = true; break;}
				}
				if (!found) hbay.push(holdingID);
				else usedCount++;
			}
		}

		// if there's anything in the holding bay, now is the time to mention it
		if (Vec.arrayLength(hbay) > 0 || usedCount > 0)
		{
			let hpara = $('<p></p>').appendTo(para);
			hpara.append('This assay has ' + ((hbay.length <= 0) ? 'no' : hbay.length) + ' pending change' + (hbay.length == 1 ? '' : 's') + ' in the ');
			let ahref = $('<a target="_blank">holding bay</a>').appendTo(hpara);
			ahref.attr('href', restBaseURL + '/holding.jsp?assayID=' + this.delegate.assay.assayID);
			if (usedCount > 0)
				hpara.append(', but does already use ' + usedCount + ' change' + (usedCount == 1 ? '' : 's') + ' from said holding bay');
			hpara.append('.');
		}

		// now the actual history
		this.annotationHistory = new DisplayAnnotationHistory(this.delegate);
		this.annotationHistory.render(para);
	}

	// sets up the table compounds & measurements; this is not called by default
	public rebuildMeasurementTable():void
	{
		let assayID = this.delegate.assay.assayID;
		this.divMeasure.empty();
		if (!assayID) return;

		this.divMeasure.text('Loading measurements...');
		let mdata = new MeasureData([assayID]);
		mdata.obtainCompounds(() =>
		{
			this.delegate.dataColumns = mdata.columns;
			if (this.delegate.dataColumns.length > 0) this.delegate.actionRedisplayAnnotations();

			if (mdata.compounds.length > 0)
			{
				this.delegate.measureTable = new MeasureTable(mdata);
				this.delegate.measureTable.render(this.divMeasure);
			}
			else
			{
				this.divMeasure.empty();
				// (consider putting a note to say that there's nothing available?)
			}
		});
	}

	// ------------ private methods ------------

	// enable the PubChem widget, which is not on by default
	public showWidget():void
	{
		this.divWidget.empty();

		let [src, id] = UniqueIdentifier.parseKey(this.delegate.assay.uniqueID);
		if (!src || src.prefix != 'pubchemAID:' || !id) return;

		this.btnWidget.prop('disabled', true);

		let url = 'https://pubchem.ncbi.nlm.nih.gov/bioassay/' + id + '#section=Data-Table&embed=true';

		let iframe = $('<iframe></iframe>');
		iframe.attr('src', url);
		iframe.attr('frameBorder', 0);
		iframe.css('width', '100%');
		iframe.css('height', '700px');
		this.divWidget.append(iframe);

		let para = $('<div align="right"></div>').appendTo(this.divWidget);
		let ahref = $('<a target="_blank">Show</a>').appendTo(para);
		ahref.attr('href', url);
	}

	// formulates information about the other miscellaneous cross references within the source PubChem record, if any
	public setupXRefWidget():void
	{
		// note: will be deprecating this fairly soon
		let para = this.divXRef;
		para.empty();
		new VariousAssayXRef(this.delegate.assay).render(para);
	}

	public updateEditStatus():void
	{
		this.btnSubmit.prop('disabled', !this.delegate.editMode);
		if (this.annotationHistory != null) this.annotationHistory.updateEditStatus();
	}
}

/* EOF */ }
