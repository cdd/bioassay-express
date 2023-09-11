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
	Assignment panel that shows a dynamically updating list of assays that are similar to the current one.
*/

export class PanelAssnSimilar
{
	private divSearch:JQuery;
	private watermark = 0;
	private assayIDList:number[] = null; // all assays, only fetched the first time
	private currentTerms:AssayAnnotation[] = []; // terms for current search state
	private divResults:JQuery = null;

	constructor(private delegate:AssignmentDelegate, private divColumn:JQuery)
	{
	}

	public render():void
	{
		this.divSearch = $('<div></div>').appendTo(this.divColumn);
		this.divSearch.css({'margin': '0.3em'});
		this.updateSearch();
	}

	// returns true if the annotations have anything that can be searched
	public worthSearching():boolean
	{
		for (let annot of this.delegate.assay.annotations) if (annot.valueURI) return true;
		return false;
	}

	// makes the search results current; this may involve
	public updateSearch():void
	{
		if (!this.worthSearching())
		{
			this.divSearch.text('No similar assays.');
			this.divResults = null;
			return;
		}

		if (this.divResults == null)
		{
			this.divSearch.empty();
			this.divSearch.append('<b>Similar</b>');

			this.divResults = $('<div></div>').appendTo(this.divSearch);
			this.divResults.css({'padding': '0.5em', 'white-space': 'nowrap'});
			this.divResults.css({'border': '1px solid #96CAFF', 'box-shadow': '0 0 5px rgba(66,77,88,0.1)', 'background-color': '#F8FCFF'});
			this.divResults.text('Searching...');

			let para = $('<p style="padding-top: 0.5em;"></p>').appendTo(this.divSearch);
			let btn = $('<button id="btnSearch" class="btn btn-normal"></button>').appendTo(para);
			btn.append('<span class="glyphicon glyphicon-search"></span> Search');
			btn.click(() => this.launchSearch());
		}

		if (this.assayIDList == null)
		{
			callREST('REST/Search', {'assayIDList': null},
				(data:any) =>
				{
					this.assayIDList = data.assayIDList;
					this.beginSearch();
				},
				() => console.log('Fetching search identifiers failed'));
		}
		else this.beginSearch();
	}

	// ------------ private methods ------------

	// assuming the assay IDs are available, proceeds to do a search with the current assay content
	private beginSearch():void
	{
		let terms:AssayAnnotation[] = [];
		for (let annot of this.delegate.assay.annotations) if (annot.valueURI) terms.push(annot);

		if (this.sameTerms(terms, this.currentTerms)) return;
		this.currentTerms = terms;

		let sessionWM = ++this.watermark;

		let params =
		{
			'assayIDList': this.assayIDList,
			'search': terms,
			'maxResults': 10 + (this.delegate.assay.assayID > 0 ? 1 : 0),
			'curatedOnly': true,
			'countCompounds': false,
			'translitPreviews': true
		};
		callREST('REST/Search', params,
			(data:any) =>
			{
				if (sessionWM != this.watermark) return;
				this.replaceResults(data.results);
			},
			() => console.log('Fetching next batch of search results failed'));
	}

	private replaceResults(results:any[]):void
	{
		if (!this.divResults) return;
		this.divResults.empty();

		let numUsed = 0;
		for (let n = 0; n < results.length; n++)
		{
			let assay = results[n]; // note: this is AssayDefinition + some extra stuff
			if (assay.assayID == this.delegate.assay.assayID) continue; // skip self
			let para = $('<p></p>').appendTo(this.divResults);
			if (n == results.length - 1) para.css('margin', '0');
			let href = $('<a></a>').appendTo(para);
			let [src, id] = UniqueIdentifier.parseKey(assay.uniqueID);
			if (src) href.html(src.shortName + '&nbsp;' + id); else href.text(assay.uniqueID);
			href.attr('href', restBaseURL + '/assign.jsp?uniqueID=' + encodeURIComponent(assay.uniqueID));

			let preview:string = null;
			if (assay.translitPreviews) for (let translit of assay.translitPreviews) // items are AssayTranslitPreview
				if (!translit.title) {preview = translit.html; break;}
			if (preview) Popover.hover(domLegacy(href), null, preview);

			numUsed++;
		}

		if (numUsed == 0)
		{
			this.divSearch.text('No similar assays.');
			this.divResults.remove();
			this.divResults = null;
			return;
		}
	}

	// returns true if the search terms haven't changed since last time
	private sameTerms(terms1:AssayAnnotation[], terms2:AssayAnnotation[]):boolean
	{
		if (terms1.length != terms2.length) return false;

		let keys1 = new Set<string>();
		for (let term of terms1) keys1.add(keyPropGroup(term.propURI, term.groupNest));
		for (let term of terms2) if (!keys1.has(keyPropGroup(term.propURI, term.groupNest))) return false;

		return true;
	}

	// brings up a search tab, using the currently selected terms
	public launchSearch():void
	{
		let assay = this.delegate.assay;
		if (assay.annotations.length == 0)
		{
			alert('Need to enter at least one annotation before searching for similar assays.');
			return;
		}
		let param:Record<string, string> = {};
		param.schema = assay.schemaURI;
		for (let n = 0; n < assay.annotations.length; n++)
		{
			let annot = assay.annotations[n];
			let propAbbrev = collapsePrefix(annot.propURI), valueAbbrev = collapsePrefix(annot.valueURI);
			if (!propAbbrev || !valueAbbrev) continue;
			let groupAbbrev:string[] = [];
			if (annot.groupNest) for (let gn of annot.groupNest) groupAbbrev.push(collapsePrefix(gn));

			param['p' + n] = propAbbrev;
			param['v' + n] = valueAbbrev;
			if (groupAbbrev.length > 0) param['g' + n] = groupAbbrev.join('::');
		}
		openWindowWithPOST('search.jsp', param);
	}
}

/* EOF */ }
