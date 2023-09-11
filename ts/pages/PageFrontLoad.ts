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
	Front-loading capabilities: getting started with assay annotation from the front page.
*/

export class PageFrontLoad
{
	private domInfo:JQuery;
	private domIDTitle:JQuery;
	private domIDList:JQuery;
	private domIDValue:JQuery;
	private domIDCurate:JQuery;
	private domForm:JQuery;
	private hiddenData:JQuery;
	private hiddenFileInput:JQuery;
	private annotationEntry:JQuery;
	private annotationResponse:JQuery;
	private annotationResponseContent:JQuery;
	private annotationResponseButton:JQuery;
	private multiAssayTextArea:JQuery;
	private annotationResponseAssayId:JQuery;
	private annotationResponseAssayText:JQuery;
	private annotationResponseAnnotationCount:JQuery;
	private annotationResponseMessage:JQuery;
	private annotationResetPage:JQuery;
	private singleAssaySectionHeader:JQuery;
	private examples:JQuery;

	private curSrc:UniqueIDSource;
	private curID = '';
	private curAssay:AssayDefinition = null;

	private acquireWatermark = 0;

	constructor(private mainBlock:JQuery, private curationRecent:any[])
	{
		this.createContent();

		let sources = UniqueIdentifier.sources();
		if (sources.length == 0) return; // this is a configuration fail
		this.curSrc = sources[0];

		this.setupIdentifiers();

		mainBlock.css('background-color', '#F8FCFF');

		// turn off form-submit on Enter
		this.domIDValue.off('keypress');
		this.domIDValue.keypress((event:JQueryKeyEventObject):boolean =>
		{
			if (event.keyCode == 13) {event.preventDefault(); return false;}
			return true;
		});

		this.domIDValue.off('keyup');
		this.domIDValue.keyup(() => this.updateUniqueIDValue());

		this.domIDValue.off('keydown');
		this.domIDValue.keydown((event:JQueryKeyEventObject) =>
		{
			let keyCode = event.keyCode || event.which;
			if (keyCode == KeyCode.Enter) this.createAssay();
		});

		this.domIDValue.focus();
		this.domIDCurate.click(() => this.createAssay());
		this.domIDCurate.prop('disabled', true);

		// NOTE: currently the paste/drop handlers just accept text formats, but they should be levelled up at some
		// point so they can take all kinds of formats, e.g. MS Word, PDF, etc., and give the background service a chance
		// to handle them

		// pasting: captures the menu/hotkey form
		let pasteHandler = (event:ClipboardEvent):boolean =>
		{
			let focused = false;
			for (let dom of [mainBlock, this.domIDValue, this.domIDList, this.domIDCurate]) focused = focused || dom.is(':focus');
			if (!focused) return false;

			let wnd = window as any;
			let handled = false, txt:string = null;
			if (wnd.clipboardData && wnd.clipboardData.getData) txt = wnd.clipboardData.getData('Text');
			else if (event.clipboardData && event.clipboardData.getData) txt = event.clipboardData.getData('text/plain');

			if (this.acquireText(txt)) handled = true;
			if (handled) event.preventDefault();
			return handled;
		};
		document.addEventListener('paste', pasteHandler);
		this.domIDValue[0].addEventListener('paste', pasteHandler);

		// dragging
		let dragTarget = document.body; // formerly mainBlock[0]
		dragTarget.addEventListener('dragover', (event:DragEvent) =>
		{
			event.stopPropagation();
			event.preventDefault();
			event.dataTransfer.dropEffect = 'copy';
		});
		dragTarget.addEventListener('drop', (event:DragEvent) =>
		{
			event.stopPropagation();
			event.preventDefault();
			let items = event.dataTransfer.items;
			for (let n = 0; n < items.length; n++)
			{
				if (items[n].kind == 'string')
				{
					items[n].getAsString((str:string) => this.acquireText(str));
					return;
				}
				if (items[n].kind == 'file')
				{
					let file = items[n].getAsFile();

					let reader = new FileReader();
					reader.onload = (event:ProgressEvent) => this.acquireBinary(file.name, reader.result as ArrayBuffer);
					reader.readAsArrayBuffer(file);
				}
			}
			// (complain?)
		});

		// file input using dialog
		this.hiddenFileInput = $('<input>').appendTo(document.body).attr('type', 'file').css({'display': 'none'});
		this.hiddenFileInput.change((e) =>
		{
			let reader = new FileReader();
			reader.onload = () => this.acquireBinary(this.hiddenFileInput.val().toString(), reader.result as ArrayBuffer);
			let file = (e.target as unknown as {files:Blob[]}).files[0];
			reader.readAsArrayBuffer(file);
		});
	}

	// ------------ private methods ------------

	// builds the first-look UI components for the main block, which invite the user to provide information to kickstart a new assay
	private createContent():void
	{
		this.domInfo = $('<div></div>').appendTo(this.mainBlock);
		let domInfoHeader = $('<h1></h1>').appendTo(this.domInfo);
		domInfoHeader.text('Quick Start');
		domInfoHeader.css('text-align', 'center');
		domInfoHeader.css('margin-bottom', '10px');
		domInfoHeader.css('font-weight', 'bold');

		this.createAnnotationSection(this.mainBlock);
		this.createComparisonSection(this.mainBlock);
	}

	private createAnnotationSection(parent:JQuery):void
	{
		let annotationSection = $('<div></div>').appendTo(parent);
		annotationSection.css('margin-top', '0px');
		annotationSection.css('padding', '0.5em');

		let annotationHeader = $('<p></p>').appendTo(annotationSection);
		annotationHeader.text('Create New / Annotate an Assay');
		annotationHeader.css('margin-left', '-5px');
		annotationHeader.css('font-weight', 'bold');

		this.annotationEntry = $('<div></div>').appendTo(annotationSection);

		// build hidden assay response section
		this.annotationResponse = $('<div></div>').appendTo(annotationSection);
		this.annotationResponse.css('display', 'none');
		this.annotationResponse.css('margin', '15px');
		this.annotationResponseContent = $('<div></div>').appendTo(this.annotationResponse);

		$('<b>Message:</b> &nbsp; ').appendTo(this.annotationResponseContent);
		this.annotationResponseMessage = $('<span></span>').appendTo(this.annotationResponseContent);
		$('<br/>').appendTo(this.annotationResponseContent);
		$('<b>Assay Text:</b> &nbsp; ').appendTo(this.annotationResponseContent);
		this.annotationResponseAssayText = $('<span></span>').appendTo(this.annotationResponseContent);
		$('<br/>').appendTo(this.annotationResponseContent);
		$('<b>Annotation Count:</b> &nbsp; ').appendTo(this.annotationResponseContent);
		this.annotationResponseAnnotationCount = $('<span></span><br/>').appendTo(this.annotationResponseContent);

		let dragInSection = $('<div></div>').appendTo(this.annotationEntry);
		dragInSection.css('margin', '10px');

		let dragInSectionHeader = $('<div></div>');
		dragInSectionHeader.text('Option 1: Create a new assay by pasting or dragging in a document (e.g. Word or plain text)').appendTo(dragInSection);

		let dragBoxTarget = $('<div></div>').appendTo(dragInSection);
		dragBoxTarget.css({'text-align': 'center', 'margin': '1em auto', 'padding': '1em', 'width': '80%'});
		dragBoxTarget.css({'color': '#808080', 'border': '1px solid #1362B3', 'opacity': '0.5'});
		dragBoxTarget.append('Drag your file here or ');
		let linkSelect = $('<a></a>').appendTo(dragBoxTarget).css('cursor', 'pointer');
		linkSelect.text('select file');
		linkSelect.click(() => this.hiddenFileInput.click());
		dragBoxTarget.append('.');

		let singleAssayContainer = $('<div></div>').appendTo(annotationSection);
		singleAssayContainer.css('margin', '10px');

		this.singleAssaySectionHeader = $('<div></div>').appendTo(singleAssayContainer);
		this.singleAssaySectionHeader.text('Option 2: Enter a document ID to create (or edit, if it already exists)');

		this.domForm = $('<form></form>').appendTo(singleAssayContainer);
		this.domForm.attr('method', 'POST');
		this.domForm.attr('action', restBaseURL + '/assign.jsp');
		this.domForm.attr('target', '_blank');
		this.hiddenData = $('<input></input>').appendTo(this.domForm);
		this.hiddenData.attr('type', 'hidden');
		this.hiddenData.attr('name', 'data');

		let inputCriteria = $('<div></div>').appendTo(this.domForm);
		inputCriteria.css('display', 'flex');
		inputCriteria.css('flex-wrap', 'nowrap');
		inputCriteria.css('width', '80%');
		inputCriteria.css('margin', '0px auto');

		let div = $('<div class="btn-group"></div>').appendTo(inputCriteria);
		div.css('flex-grow', '1');
		div.css('margin', '5px');
		let btn = $('<button type="button" class="form-control btn btn-action dropdown-toggle" data-toggle="dropdown"></button>').appendTo(div);
		this.domIDTitle = $('<span>Unique ID</span>').appendTo(btn);
		btn.append(' ');
		btn.append('<span class="caret"></span>');
		this.domIDList = $('<ul id="uniqueIDList" class="dropdown-menu" role="menu"></ul>').appendTo(div);

		let div2 = $('<div class="identity-box"></div>').appendTo(inputCriteria);
		div2.css('flex-grow', '10');
		div2.css('margin', '5px');
		this.domIDValue = $('<input value="" placeholder=""></input>').appendTo(div2);
		this.domIDValue.css('width', '100%');

		// create the action button
		let div3 = $('<div></div>').appendTo(inputCriteria);
		div3.css({'flex-grow': '1', 'margin': '5px', 'text-align': 'center'});
		this.domIDCurate = $('<button type="button" class="btn btn-action">Go</button>').appendTo(div3);
		this.domIDCurate.css('margin', '0px auto');

		this.annotationResetPage = $('<button type="button" class="btn btn-action">Reset Page</button>').appendTo(div3);
		this.annotationResetPage.css('margin-left', '10px');
		this.annotationResetPage.css('display', 'none');
		this.annotationResetPage.click(() => window.location.reload());

		this.examples = $('<div></div>').appendTo(this.domForm);
		this.examples.css({'width': '80%', 'margin': '0px auto', 'text-align': 'left'});

		let recentExamples = this.getRecentExamples();
		if (recentExamples.length > 0)
		{
			this.examples.append('e.g. use ');
			for (let [src, id] of recentExamples)
			{
				let example = $('<span>' + id + ' for ' + src.name + ' ' + id + '</span>').appendTo(this.examples);
				example.click(() => { this.updateNewAssayForm(src, id); });
				this.examples.append('; ');
			}
			this.examples.append('or enter a new identifier.');
		}

		let clearDiv = $('<div></div>').appendTo(singleAssayContainer);
		clearDiv.css('clear', 'both');
	}

	private updateNewAssayForm(assaySrc:UniqueIDSource, assayUniqueId:string):void
	{
		this.domIDValue.val(assayUniqueId);
		this.domIDList.val(assayUniqueId);
		this.curSrc = assaySrc;
		this.setupIdentifiers();
		this.updateUniqueIDValue();
	}

	private createComparisonSection(parent:JQuery):void
	{
		let comparisonSection = $('<div></div>').appendTo(parent);
		comparisonSection.css('padding', '0.5em');

		let comparisonHeader = $('<p></p>').appendTo(comparisonSection);
		comparisonHeader.text('Compare Assays');
		comparisonHeader.css('font-weight', 'bold');
		comparisonHeader.css('margin-left', '-5px');

		comparisonSection = $('<div></div>').appendTo(comparisonSection);
		comparisonSection.css('margin', '10px');

		let multiAssaySectionHeader = $('<p></p>').appendTo(comparisonSection);
		multiAssaySectionHeader.text('Paste in a list of IDs');

		let multiAssayContainer = $('<div></div>').appendTo(comparisonSection);
		multiAssayContainer.css('width', '80%');
		multiAssayContainer.css('margin', '0px auto');

		this.multiAssayTextArea = $('<textarea></textarea>').appendTo(multiAssayContainer);
		this.multiAssayTextArea.css('flex-grow', '-1');
		this.multiAssayTextArea.css({'width': '100%', 'border': '1px solid #CCD9E8', 'border-radius': '3px'});

		let examples = $('<div></div>').appendTo(multiAssayContainer);

		let recentExamples = this.getRecentExamples();
		if (recentExamples.length > 0)
		{
			let sampleIds:string[] = [];
			let sampleNames:string[] = [];
			for (let [src, id] of this.getRecentExamples())
			{
				sampleIds.push(id);
				sampleNames.push(src.name + ' ' + id);
			}

			examples.append('e.g. use ');
			let example = $('<span>' + sampleIds.join(', ') + '</span>').appendTo(examples);
			examples.append(' to compare ' + sampleNames.join(', ') + '</span>');
			example.addClass('hoverUnderline');
			example.css('cursor', 'pointer');
			example.click(() => this.multiAssayTextArea.val(sampleIds));
		}

		// create the action button
		let compareButton = $('<button type="button" class="btn btn-action">Compare Assays</button>').appendTo(multiAssayContainer);
		compareButton.css({'text-align': 'right', 'float': 'left', 'margin': '10px 0px 0px 5px'});
		compareButton.click(() => this.compareAssay());

		// close out the float
		let clearFloat = $('<div></div>').appendTo(comparisonSection);
		clearFloat.css('clear', 'both');
	}

	private getRecentExamples():any[]
	{
		if (!this.curationRecent || this.curationRecent.length == 0) return [];
		return this.curationRecent.slice(0, 3)
				.map((a) => a.uniqueID)
				.filter((uniqueID) => uniqueID && uniqueID.indexOf(':') > -1)
				.map((uniqueID) => UniqueIdentifier.parseKey(uniqueID));
	}

	// open explore page for list of assays
	private compareAssay():void
	{
		if (!this.multiAssayTextArea.val()) return;

		// find matching assays
		let params = {'id': this.multiAssayTextArea.val(), 'permissive': false};
		callREST('REST/FindIdentifier', params,
			(data:any) =>
			{
				// extract uniqueIDs from multiMatches
				let idList:Set<string> = new Set();
				for (let multiMatchContainer of data.multiMatches)
					for (const match of multiMatchContainer)
						idList.add(match.uniqueID);

				// nothing to do if no uniqueIDs found
				if (idList.size == 0) return;

				let identifier = encodeURIComponent([...idList].join(' OR '));
				let url = `${restBaseURL}/explore.jsp?IDENTIFIER=${identifier}`;
				if (window.open(url, '_blank') == null)
					alert('Pop-up Blocker is enabled! Please add this site to your exception list.');
			});
	}

	// recreate the dropdown list with the identifier type options
	private setupIdentifiers():void
	{
		this.domIDTitle.text(this.curSrc.name);
		let sources = UniqueIdentifier.sources();

		this.domIDList.empty();
		for (let n = 0; n < sources.length; n++)
		{
			let src = sources[n];
			let li = $('<li></li>').appendTo(this.domIDList);
			let href = $('<a href="#"></a>').appendTo(li);
			href.text(src.name);
			if (src.name != this.curSrc.name)
			{
				href.click(() =>
				{
					this.curSrc = src;
					this.setupIdentifiers();
				});
			}
		}
	}

	// value changed, react accordingly
	private updateUniqueIDValue():void
	{
		this.curID = purifyTextPlainInput(this.domIDValue.val());
		this.domIDCurate.prop('disabled', this.curID == '' && this.curAssay == null);
	}

	// requested new/edit assay
	private createAssay():void
	{
		if (this.curID)
		{
			// see if the ID is already there; behave differently either way
			let uniqueID = this.curSrc.prefix + this.curID;
			callREST('REST/FindIdentifier', {'id': this.curID},
				(data:any) =>
				{
					// single matches take priority over multi-matches
					for (let match of data.matches) if (match.uniqueID == uniqueID)
					{
						if (confirm('Assay already exists. Would you like to edit it?'))
						{
							let url = restBaseURL + '/assign.jsp?assayID=' + match.assayID + '&edit=true';
							window.open(url, '_blank');
						}
						return;
					}

					// no match or multi-matches, launch new assay
					this.launchNewAssay();
				});
		}
		else this.launchNewAssay();
	}

	// take the current information and fire up a new tab
	private launchNewAssay():void
	{
		let assay:AssayDefinition = {} as any;
		if (this.curAssay) assay = deepClone(this.curAssay);
		if (this.curID)
		{
			assay.uniqueID = this.curSrc.prefix + this.curID;
			assay.schemaURI = this.curSrc.defaultSchema;
		}

		// this usually works, unless the popup blocker intercedes, which breaks everything; so we're having our own
		// local form instead
		//launchTabPOST(restBaseURL + '/assign.jsp', JSON.stringify(assay));

		assay.isFresh = true;
		this.hiddenData.attr('value', JSON.stringify(assay));
		this.domForm.submit();
	}

	// text came in through paste or drag
	private acquireText(text:string):boolean
	{
		if (text == null || text.length < 10 /*|| text.indexOf('\n') < 0*/) return false; // methinks this is an assay not

		let watermark = ++this.acquireWatermark;
		this.annotationEntry.hide();
		this.annotationResponse.show();
		this.annotationResponseMessage.text('Processing...');

		let uniqueID = this.curID ? this.curSrc.prefix + this.curID : null;
		let params = {'format': 'text/plain', 'text': text};
		callREST('REST/InterpretAssay', params,
			(result:any) =>
			{
				if (watermark != this.acquireWatermark) return; // superceded by another request
				if (result.error)
				{
					this.annotationResponseContent.text(result.error);
					return;
				}
				if (uniqueID && !result.assay.uniqueID) result.assay.uniqueID = uniqueID; // if not supplied, use current one
				this.processResult(result.assay);
			},
			() =>
			{
				if (watermark == this.acquireWatermark) this.annotationResponseContent.html('Request failed.');
			});

		return true;
	}

	// some kind of binary format came through from a drag operation; filename is optional, may be null
	private acquireBinary(filename:string, data:ArrayBuffer):boolean
	{
		if (data == null || data.byteLength < 10) return false;

		let watermark = ++this.acquireWatermark;
		this.annotationEntry.hide();
		this.annotationResponse.show();
		this.annotationResponseMessage.text('Processing...');

		let uniqueID = this.curID ? this.curSrc.prefix + this.curID : null;
		let base64:string;
		try
		{
			// have to do the ArrayBuffer-to-string in stripes, otherwise it bugs out with a memory error
			let buffer = new Uint8Array(data), length = buffer.length;
			let stripes:string[] = [];
			for (let n = 0; n < length; n += 65535)
			{
				let blk = Math.min(65535, length - n);
				stripes.push(String.fromCharCode.apply(null, buffer.subarray(n, n + blk)));
			}
			base64 = btoa(stripes.join(''));
		}
		catch (e)
		{
			console.log('Binary file size: ' + data.byteLength);
			console.log('Failure reason: ' + e);
			this.annotationResponseContent.text('File too large (' + data.byteLength + ' bytes)');
			return;
		}
		let params = {'filename': filename, 'format': 'application/octet-stream', 'base64': base64};
		callREST('REST/InterpretAssay', params,
			(result:any) =>
			{
				if (watermark != this.acquireWatermark) return; // superceded by another request
				if (result.error)
				{
					this.annotationResponseContent.text(result.error);
					return;
				}
				if (uniqueID && !result.assay.uniqueID) result.assay.uniqueID = uniqueID; // if not supplied, use current one
				this.processResult(result.assay);
			},
			() =>
			{
				if (watermark == this.acquireWatermark) this.annotationResponseContent.text('Request failed.');
			});

		return true;
	}

	// an assay was unpacked, so make it show
	private processResult(assay:AssayDefinition):void
	{
		this.annotationResponseMessage.html('<b>Assay Found</b>');
		this.domIDValue.val(assay.uniqueID);
		this.examples.hide();

		const LIMIT = 200;
		let assayText = escapeHTML(assay.text.substring(0, LIMIT));
		if (assay.text.length > LIMIT) assayText += ' ...';

		this.annotationResponseAssayText.text(assayText);

		let sz = Vec.arrayLength(assay.annotations);
		if (sz > 0)
		{
			let accountCountText = '<i>' + sz + ' annotation' + (sz == 1 ? '' : 's') + '</i>';
			this.annotationResponseAnnotationCount.html(accountCountText);
		}

		this.annotationResetPage.show();
		this.singleAssaySectionHeader.text('Please provide an assay source / assay id');

		if (assay.uniqueID)
		{
			let [src, id] = UniqueIdentifier.parseKey(assay.uniqueID);
			if (src)
			{
				this.curSrc = src;
				this.domIDValue.val(id);
				this.setupIdentifiers();
			}
		}

		this.curAssay = assay;
		this.updateUniqueIDValue();
	}
}

/* EOF */ }
