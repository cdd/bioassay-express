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
	Base class for dialogs, which use the Bootstrap CSS.
*/

export abstract class BootstrapDialog
{
	protected titleUsesHTML = false; // set to true if the title should be HTML rather than plain text
	protected withCloseButton = true; // whether to create an [x] button for closing
	protected modalWidth = ''; // set this for specific desired width (e.g. '90%', '300px')
	protected minWidth = '50%'; // minumum width (usually worth setting)
	protected maxWidth = '95%'; // maximum width (will use up to this much - the default behaviour)

	public dlg:JQuery; // the whole dialog
	public header:JQuery; // the title area of the dialog
	public content:JQuery; // the main body-area of the dialog
	public areaTitle:JQuery; // the container that usually holds just the title
	public areaTopRight:JQuery; // the top-right section where the close button usually goes
	public btnClose:JQuery = null; // the close-button itself

	// non-JQuery fill-ins
	public get dlgDOM():DOM {return dom(this.dlg[0]);}
	public get headerDOM():DOM {return dom(this.header[0]);}
	public get contentDOM():DOM {return dom(this.content[0]);}
	public get areaTitleDOM():DOM {return dom(this.areaTitle[0]);}
	public get areaTopRightDOM():DOM {return dom(this.areaTopRight[0]);}
	public get btnCloseDOM():DOM {return dom(this.btnClose[0]);}

	// note that the dlgID parameter is for reusability purposes; it should be assumed that only one dialog
	// with that value can be simultaneously active
	constructor(public title:string)
	{
	}

	// creates and displays the dialog; if the named dialog object is present, it will be taken repurposed
	public show():void
	{
		Popover.removeAllPopovers();

		this.dlg = $('<div class="modal fade" tabindex="-1"/>').appendTo(document.body);

		let div1 = $('<div class="modal-dialog"/>').appendTo(this.dlg);
		if (this.modalWidth)
		{
			div1.css('width', this.modalWidth);
		}
		else
		{
			div1.css({'width': 'fit-content', 'max-width': this.maxWidth});
			if (this.minWidth) div1.css('min-width', this.minWidth);
		}

		let div2 = $('<div class="modal-content"/>').appendTo(div1);

		let hdr = $('<div class="modal-header"/>').appendTo(div2);
		let hrow = $('<tr></tr>').appendTo($('<table width="100%"/>').appendTo(hdr));
		this.areaTitle = $('<td align="left"/>').appendTo(hrow);
		let stitle = $('<span style="display: inline-block;"/>').appendTo(this.areaTitle);
		let htitle = $('<h4 class="modal-title"/>').appendTo(stitle);
		htitle.css('font-weight', 'bold');
		if (this.titleUsesHTML) htitle.html(this.title); else htitle.text(this.title);

		this.content = $('<div class="modal-body"/>').appendTo(div2);

		this.areaTopRight = $('<td align="right"/>').appendTo(hrow);
		if (this.withCloseButton)
		{
			this.btnClose = $('<button class="close" data-dismiss="modal" aria-hidden="true">&times;</button>');
			this.areaTopRight.append(this.btnClose);
		}

		this.populateContent();
		this.dlg.modal({'backdrop': 'static', 'keyboard': true});
		this.dlg.on('shown.bs.modal', () => this.onShown());
		this.dlg.on('hidden.bs.modal', () =>
		{
			this.onHidden(); // for the benefit of inheriting classes, to do their thing
			this.dlg.remove();
		});
	}

	// puts away the dialog gracefully (but note that the DOM objects are not destroyed)
	public hide():void
	{
		this.dlg.modal('hide');
	}

	// subclass should override this and add all the objects to this.content, immediately prior to showing
	protected abstract populateContent():void;

	// optional overrides
	protected onShown():void {}
	protected onHidden():void {}

	// ------------ private methods ------------

}

/* EOF */ }