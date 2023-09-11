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
	Provides a selection of supporting functionality for popover tooltips - whether it be hover, or click-to-activate.
*/

export interface PopoverOptions
{
	schemaURI?:string;
	propURI?:string;
	groupNest?:string[];
	valueURI?:string; // used in combination with substitution label/description
}

// persistent cache, to avoid gratuitously unnecessary trips to the server
let cacheLabel:Record<string, string> = {};
let cacheDescr:Record<string, string> = {};
let cacheBranch:Record<string, ValueBranchInfo> = {};

// hashing function for lookup of custom labels/descriptions
function keySchemaPropGroupValue(schemaURI:string, propURI:string, groupNest:string[], valueURI:string):string
{
	return valueURI + '::' + schemaURI + '::' + propURI + '::' + (groupNest == null ? '' : groupNest.join('::'));
}

// parameters to the special JSP pages that serve back labels/descriptions
/*function queryParamsFromSchemaPropGroupValue(schemaURI:string, propURI:string, groupNest:string[], valueURI:string):string
{
	let params:string[] = [];
	if (valueURI) params.push('valueURI=' + encodeURIComponent(collapsePrefix(valueURI)));
	if (schemaURI) params.push('schemaURI=' + encodeURIComponent(collapsePrefix(schemaURI)));
	if (propURI) params.push('propURI=' + encodeURIComponent(collapsePrefix(propURI)));
	if (Vec.arrayLength(groupNest) > 0)
	{
		let bits = collapsePrefixes(groupNest).join(',');
		params.push('groupNest=' + encodeURIComponent(bits));
	}
	return params.join('&');
}*/

export class Popover
{
	public static uriPatternMaps:URIPatternMaps = null;

	public static CACHE_LABEL_CODE = '$LABEL';
	public static CACHE_DESCR_CODE = '$DESCR';
	public static CACHE_BRANCH_CODE = '$BRANCH';

	private static currentPopover:DOM = null;

	// install event handlers that hide any current popover
	public static installHiders():void
	{
		let body = dom(document.body);
		body.onKeyDown((event) =>
		{
			if (this.currentPopover && !this.currentPopover.isVisible()) this.currentPopover = null;
			if (!this.currentPopover) return true;

			let keyCode = event.keyCode || event.which;
			if (keyCode == KeyCode.Escape)
			{
				this.removeAllPopovers();
				event.stopPropagation();
				return false;
			}
			return true;
		});
		body.onClick((event) =>
		{
			if ((event.target as Element).tagName.toLowerCase() == 'a') return true;

			if (this.currentPopover && !this.currentPopover.isVisible()) this.currentPopover = null;
			if (!this.currentPopover) return true;

			this.removeAllPopovers();
			event.stopPropagation();
			return false;
		});
	}

	// access to cached label/description content; will either provide whatever content is available if any (get~) or make it happen
	// and call back when it's done (fetch~)
	public static getCachedVocabLabel(opt:PopoverOptions):string
	{
		let key = keySchemaPropGroupValue(opt.schemaURI, opt.propURI, opt.groupNest, opt.valueURI);
		return cacheLabel[key];
	}
	public static async fetchCachedVocabLabel(opt:PopoverOptions):Promise<string>
	{
		/*return new Promise<string>((resolve, reject) =>
		{
			let key = keySchemaPropGroupValue(opt.schemaURI, opt.propURI, opt.groupNest, opt.valueURI);
			let label = cacheLabel[key];
			if (label != null) {resolve(label); return;}

			let queryParams = queryParamsFromSchemaPropGroupValue(opt.schemaURI, opt.propURI, opt.groupNest, opt.valueURI);
			(async () =>
			{
				let data = await wmk.readTextURL(restBaseURL + '/vocablabel.jsp?' + queryParams);
				label = data.trim();
				cacheLabel[key] = label;
				resolve(label);
			})();
		});*/

		let key = keySchemaPropGroupValue(opt.schemaURI, opt.propURI, opt.groupNest, opt.valueURI);
		let label = cacheLabel[key];
		if (label != null) return label;

		let params = {'schemaURI': opt.schemaURI, 'propURI': opt.propURI, 'groupNest': opt.groupNest, 'valueURIList': [opt.valueURI]};
		let result = await asyncREST('REST/BranchInfo', params);
		if (!result.branches || result.branches.length == 0)
		{
			cacheLabel[key] = '';
			return '';
		}

		let {valueLabel, valueDescr} = result.branches[0];
		cacheLabel[key] = valueLabel;
		cacheDescr[key] = valueDescr;
		return valueLabel;
	}

	public static getCachedVocabDescr(opt:PopoverOptions):string
	{
		let key = keySchemaPropGroupValue(opt.schemaURI, opt.propURI, opt.groupNest, opt.valueURI);
		return cacheDescr[key];
	}
	public static async fetchCachedVocabDescr(opt:PopoverOptions):Promise<string>
	{
		/*return new Promise<string>((resolve, reject) =>
		{
			let key = keySchemaPropGroupValue(opt.schemaURI, opt.propURI, opt.groupNest, opt.valueURI);
			let descr = cacheDescr[key];
			if (descr != null) {resolve(descr); return;}

			let queryParams = queryParamsFromSchemaPropGroupValue(opt.schemaURI, opt.propURI, opt.groupNest, opt.valueURI);
			(async () =>
			{
				let data = await wmk.readTextURL(restBaseURL + '/vocabdescr.jsp?' + queryParams);
				descr = data.trim();
				cacheDescr[key] = descr;
				resolve(descr);
			})();
		});*/

		let key = keySchemaPropGroupValue(opt.schemaURI, opt.propURI, opt.groupNest, opt.valueURI);
		let label = cacheDescr[key];
		if (label != null) return label;

		let params = {'schemaURI': opt.schemaURI, 'propURI': opt.propURI, 'groupNest': opt.groupNest, 'valueURIList': [opt.valueURI]};
		let result = await asyncREST('REST/BranchInfo', params);
		if (!result.branches || result.branches.length == 0)
		{
			cacheDescr[key] = '';
			return '';
		}

		let {valueLabel, valueDescr} = result.branches[0];
		cacheLabel[key] = valueLabel;
		cacheDescr[key] = valueDescr;
		return valueDescr;
	}
	public static async fetchCachedBranch(opt:PopoverOptions):Promise<ValueBranchInfo>
	{
		return new Promise<ValueBranchInfo>((resolve, reject) =>
		{
			let key = keySchemaPropGroupValue(opt.schemaURI, opt.propURI, opt.groupNest, opt.valueURI);
			let branch = cacheBranch[key];
			if (branch != null)
			{
				if (!branch.valueURI) branch = null; // this code for accessed before, not not found
				resolve(branch);
				return;
			}

			let params =
			{
				'schemaURI': opt.schemaURI,
				'propURI': opt.propURI,
				'groupNest': opt.groupNest,
				'valueURIList': [opt.valueURI]
			};
			(async () =>
			{
				let result = await asyncREST('REST/BranchInfo', params);
				let branchList:ValueBranchInfo[] = result.branches;
				if (Vec.arrayLength(branchList) == 0)
				{
					cacheBranch[key] = {} as ValueBranchInfo;
					resolve(null);
				}
				else
				{
					cacheBranch[key] = branchList[0];
					resolve(branchList[0]);
				}
			})();
		});
	}

	// creates a span element that renders an ontology term, which is ideally shown as just the name, with the rest of the information
	// shown in a popover; abbreviation or URI are used as fallbacks for primary display, depending on availability
	public static popoverOntologyTerm(uri:string, label:string, descr:string, altLabels?:string[], externalURLs?:string[]):[string, string]
	{
		let abbrev = collapsePrefix(uri);
		if (abbrev)
		{
			let externalLinks = this.uriPatternMaps && this.uriPatternMaps.joinLinks($('<span/>'), abbrev);
			abbrev = '<i>' + escapeHTML(abbrev) + '</i>';
			if (externalLinks) abbrev += ' ' + externalLinks.html();
		}

		let title:string;
		if (label && !uri) title = escapeHTML(label);
		else if (label) title = '<b>' + escapeHTML(label) + '</b>';
		else if (abbrev) title = abbrev;
		else title = '<i>' + escapeHTML(uri) + '</i>';

		let content = '';
		if (label) content += abbrev;
		if (uri) content += this.CACHE_BRANCH_CODE;

		if (Vec.arrayLength(altLabels) > 0)
		{
			content += '<div>Other Label' + (altLabels.length == 1 ? '' : 's') + ': ';
			for (let j = 0; j < altLabels.length; j++)
			{
				if (j > 0) content += ', ';
				content += '<i>' + escapeHTML(altLabels[j]) + '</i>';
			}
			content += '</div>';
		}

		if (Vec.arrayLength(externalURLs) > 0)
		{
			content += '<div>External URL' + (externalURLs.length == 1 ? '' : 's') + ': ';
			for (let j = 0; j < externalURLs.length; j++)
			{
				if (j > 0) content += ', ';
				let link = externalURLs[j];
				content += `<a href="${link}" target="_blank">` + escapeHTML(link) + '</a>';
			}
			content += '</div>';
		}

		if (descr) content += '<div align="left">' + escapeHTML(descr.trim()) + '</div>';
		else if (uri) content += this.CACHE_DESCR_CODE;
		else content += '<br/>';

		return [title, content];
	}
	public static displayOntologyTerm(opt:PopoverOptions, label:string, descr:string, altLabels?:string[], externalURLs?:string[]):DOM
	{
		let span = dom('<span/>').css({'width': '100%'});

		// valueURI has priority over propURI
		let uri:string = opt.valueURI != null ? opt.valueURI : opt.propURI;
		let [title, content] = this.popoverOntologyTerm(uri, label, descr, altLabels, externalURLs);
		span.setHTML(title);
		this.hover(span, title, content, opt);
		return span;
	}

	// common convenience versions of above
	public static displayOntologyAssn(assn:SchemaAssignment):DOM
	{
		let opt = {'propURI': assn.propURI};
		return this.displayOntologyTerm(opt, assn.name, assn.descr, null, null);
	}
	public static displayOntologyProp(annot:AssayAnnotation):DOM
	{
		let opt = {'propURI': annot.propURI};
		return this.displayOntologyTerm(opt, annot.propLabel, annot.propDescr, null, null);
	}
	public static displayOntologyValue(annot:AssayAnnotation):DOM
	{
		let opt = {'valueURI': annot.valueURI};
		return this.displayOntologyTerm(opt, annot.valueLabel, annot.valueDescr, annot.altLabels, annot.externalURLs);
	}

	// adds a "click this icon" popover to a widget, which provides the requisite help information; if there are any CACHE_*_CODE strings
	// embedded in the contentHTML, it will resolve them at the time that the popover is triggered (which may result in a slight delay the
	// first time if it's not already in the cache)
	public static click(widget:DOM, titleHTML:string, contentHTML:string, opt:PopoverOptions = {}):void
	{
		let src = restBaseURL + '/images/question.svg';
		let img = dom('<img/>').appendTo(widget).attr({'src': src}).css({'cursor': 'help'});

		let substHTML:string = null;

		img.onClick((event) =>
		{
			(async () =>
			{
				if (!substHTML) substHTML = await this.substitutePopoverIncludes(opt, contentHTML);
				this.launchPopup(img, titleHTML, contentHTML, opt);
			})();
			event.stopPropagation();
			return false;
		});
	}

	// adds transient popover content that will be triggered by hovering over the widget; like for the clicky version above, will resolve
	// special CACHE_*_CODE embeds, at the time that the popover is first triggered
	public static hover(widget:DOM, titleHTML:string, contentHTML:string, opt:PopoverOptions = {}):void
	{
		let substHTML:string = null;
		let hoveredPop:DOM = null;

		widget.onMouseEnter((event) =>
		{
			(async () =>
			{
				if (!substHTML) substHTML = await this.substitutePopoverIncludes(opt, contentHTML);
				this.launchPopup(widget, titleHTML, contentHTML, opt);
				hoveredPop = this.currentPopover;
			})();
		});
		widget.onMouseLeave((event) =>
		{
			if (hoveredPop === this.currentPopover) this.removeAllPopovers();
		});
	}

	// ensures that there are no popovers visible on anything (actually there can only be one now, but that's OK)
	public static removeAllPopovers():void
	{
		if (this.currentPopover) this.currentPopover.remove();
		this.currentPopover = null;
	}

	// ------------ private methods ------------

	// substitution: checks to see if the embedded codes are in the content, and if so, swap them out with actual values; if not using them,
	// or they're in the cache already, will callback immediately; otherwise will defer to a web request to obtain the missing content;
	// returns true if the callback function was or is going to be called
	private static async substitutePopoverIncludes(opt:PopoverOptions, contentHTML:string):Promise<string>
	{
		// remove inserted placeholders if there's insufficient information
		while (true)
		{
			let purged = false;
			if (!opt.propURI && !opt.valueURI && contentHTML.indexOf(this.CACHE_LABEL_CODE) >= 0)
			{
				contentHTML = contentHTML.replace(this.CACHE_LABEL_CODE, '');
				purged = true;
			}
			if (!opt.propURI && !opt.valueURI && contentHTML.indexOf(this.CACHE_DESCR_CODE) >= 0)
			{
				contentHTML = contentHTML.replace(this.CACHE_DESCR_CODE, '');
				purged = true;
			}
			if ((!opt.propURI || !opt.valueURI || !opt.schemaURI) && contentHTML.indexOf(this.CACHE_BRANCH_CODE) >= 0)
			{
				contentHTML = contentHTML.replace(this.CACHE_BRANCH_CODE, '');
				purged = true;
			}
			if (!purged) break;
		}

		//let key = keySchemaPropGroupValue(opt.schemaURI, opt.propURI, opt.groupNest, opt.valueURI);

		// special deal: if the content has substitution(s) to be made and a URI is given, loads that description into a cache and makes that
		// substitution; this is a way to avoid having to pre-transmit all the descriptions, which kills bandwidth
		if ((opt.propURI || opt.valueURI) && contentHTML.indexOf(this.CACHE_LABEL_CODE) >= 0)
		{
			let labelValue = await this.fetchCachedVocabLabel(opt);
			if (!labelValue) labelValue = 'No label.';
			contentHTML = contentHTML.replace(this.CACHE_LABEL_CODE, labelValue);
		}
		if ((opt.propURI || opt.valueURI) && contentHTML.indexOf(this.CACHE_DESCR_CODE) >= 0)
		{
			let descrValue = await this.fetchCachedVocabDescr(opt);
			if (!descrValue) descrValue = 'No description.';
			contentHTML = contentHTML.replace(this.CACHE_DESCR_CODE, `<div>${descrValue}</div>`);
		}
		if (opt.propURI && opt.valueURI && opt.schemaURI && contentHTML.indexOf(this.CACHE_BRANCH_CODE) >= 0)
		{
			let branch = await this.fetchCachedBranch(opt);
			let embedHTML = '';
			if (branch.labelHier)
			{
				let indent = 0;
				for (let n = branch.labelHier.length - 1; n >= -1; n--, indent++)
				{
					embedHTML += `<div style="padding-left: ${indent}em;">`;
					if (indent > 0) embedHTML += '\u{21B3} ';
					embedHTML += '<b>' + escapeHTML(n < 0 ? branch.valueLabel : branch.labelHier[n]) + '</b>';
					embedHTML += '</div>\n';
				}
			}
			contentHTML = contentHTML.replace(this.CACHE_BRANCH_CODE, embedHTML);
		}

		return contentHTML;
	}

	// perform the actual launching of the popup, by creating a new widget
	private static launchPopup(reference:DOM, titleHTML:string, contentHTML:string, opt:PopoverOptions):void
	{
		this.removeAllPopovers();

		let pop = this.currentPopover = dom('<div/>').css({'position': 'fixed', 'z-index': 22000, 'visibility': 'hidden'});
		pop.css({'left': '0', 'top': '0', 'max-width': '20em'});
		pop.css({'background-color': '#FFFFFF', 'background-image': 'linear-gradient(to right bottom, #FFFFFF, #D0D0D0)'});
		pop.css({'color': 'black', 'border': '1px solid black', 'border-radius': '4px', 'box-shadow': '5px 5px 5px #808080'});
		pop.appendTo(document.body);

		let divFlex = dom('<div/>').appendTo(pop).css({'display': 'flex', 'flex-direction': 'column'});

		if (titleHTML)
		{
			let div = dom('<div/>').appendTo(divFlex).css({'padding': '0.3em', 'font-weight': 'bold', 'background-color': 'white', 'border-radius': '4px 4px 0 0'});
			div.setHTML(titleHTML);
			if (contentHTML) div.css({'border-bottom': '1px solid #808080'});
		}
		if (contentHTML)
		{
			let div = dom('<div/>').appendTo(divFlex).css({'padding': '0.3em'});
			div.setHTML(contentHTML);
		}

		let winW = window.innerWidth, winH = window.innerHeight;
		const GAP = 6;
		let boundDiv = reference.el.getBoundingClientRect();
		let wx1 = boundDiv.left, wy1 = boundDiv.top;
		let wx2 = wx1 + boundDiv.width, wy2 = wy1 + boundDiv.height;

		let setPosition = (visible:boolean) =>
		{
			let popW = pop.width(), popH = pop.height();
			let posX = 0, posY = 0;
			if (wx1 + popW < winW) posX = wx1;
			else if (popW < wx2) posX = wx2 - popW;
			if (wy2 + GAP + popH < winH) posY = wy2 + GAP;
			else if (wy1 - GAP - popH > 0) posY = wy1 - GAP - popH;
			else posY = wy2 + GAP;

			pop.css({'left': `${posX}px`, 'top': `${posY}px`});
			if (visible) pop.setCSS('visibility', 'visible');
		};

		setPosition(false);
		setTimeout(() => setPosition(true), 1);

		// poll to check for disappearing parent
		let checkParent = () =>
		{
			if (pop !== this.currentPopover) return; // moved on
			if (!pop.parent() || !pop.parent().isVisible())
			{
				this.removeAllPopovers();
				return;
			}
			setTimeout(checkParent, 100);
		};
		setTimeout(checkParent, 100);
	}

}

/* EOF */ }
