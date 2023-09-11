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
	Base class for dialogs, which use the WebMolKit basic class.
*/

export abstract class BaseDialog extends wmk.Dialog
{
	protected withCloseButton = true;

	constructor(title?:string)
	{
		super();
		if (title) this.title = title;
	}

	// intermediate setup: subclasses should call this at the beginning
	protected populate():void
	{
		this.domTitle.css({'background-color': '#F8F8F8', 'background-image': 'none'});
		this.domTitleButtons.empty();

		if (this.withCloseButton)
		{
			let btnClose = dom('<button class="btn btn-normal">Close</button>').appendTo(this.domTitleButtons).css({'margin-left': '0.5em'});
			btnClose.onClick(() => this.close());
		}
	}

	// sets up the dialog so that the escape key closes it without further affect; optionally sets the focus to the first input control
	protected installEscapeKey(andFocus = true):void
	{
		this.domBody.onKeyDown((event) =>
		{
			if (event.key == 'Escape') this.close();
		});
		if (andFocus)
		{
			let focusable = this.domBody.findAll('input,textarea');
			if (focusable.length > 0) focusable[0].grabFocus();
		}
	}
}

/* EOF */ }