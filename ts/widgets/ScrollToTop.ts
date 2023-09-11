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
	ScrollToTop.enable() adds a button to the page that becomes visible
	when the page is scrolled more than minScroll. Clicking the button
	scrolls the page back to the top.
*/

export class ScrollToTop
{
	private static minScroll = 50;
	private static cssPosition =
	{
		'position': 'fixed',
		'bottom': 0, 'right': 0,
		'margin': '0 1em 1em 0',
		'z-index': '10000'
	};
	private static cssBox =
	{
		'border': '1px solid #96CAFF',
		'background-color': '#F8FCFF',
		'color': '#666666',
		'padding': '0.1em 0.3em',
		'display': 'inline-flex',
		'align-items': 'center',
		'justify-content': 'center'
	};
	private static cssShow =
	{
		'visibility': 'visible',
		'opacity': 0.8
	};
	private static cssHide =
	{
		'visibility': 'hidden',
		'opacity': 0
	};

	private static button:JQuery = null;
	public static enable():void
	{
		if (this.button != null) return;

		this.button = $('<div></div>').appendTo(document.body);
		this.button.append('<span class="glyphicon glyphicon-chevron-up"></span> top');
		this.button.css({...this.cssPosition, ...this.cssBox, ...this.cssHide});
		this.button.css({'transition': 'all .25s ease-in-out', 'cursor': 'pointer'});
		this.button.click(ScrollToTop.scrollToTop);
		this.setButtonVisibility();
		window.addEventListener('scroll', this.setButtonVisibility.bind(this));
	}

	private static setButtonVisibility():void
	{
		if (window.scrollY > this.minScroll)
			this.button.css(this.cssShow);
		else
			this.button.css(this.cssHide);
	}

	private static scrollToTop():void
	{
		// number of pixels we are from the top of the document
		const c = document.documentElement.scrollTop || document.body.scrollTop;
		// animate scroll to top
		if (c > 0)
		{
			window.requestAnimationFrame(ScrollToTop.scrollToTop);
			window.scrollTo(0, c - c / 10);
		}
	}
}

/* EOF */ }
