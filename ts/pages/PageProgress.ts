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
	Supporting functionality for the progress report.
*/

export class PageProgress
{
	constructor(private templates:TemplateSummary[])
	{
	}

	// assembles the page from scratch
	public buildContent():void
	{
		let main = $('#main');
		main.empty();

		let width = main.width() - 30; // ghetto "reactive" sizing...

		$('<h1>FAIRness</h1>').appendTo(main);

		let divGrid = $('<div/>').appendTo(main).css('display', 'grid');
		divGrid.css({'grid-column-gap': '0.5em', 'grid-row-gap': '0.2em'});
		divGrid.css('grid-template-columns', '[start button] auto [date] auto [title] 1fr [end]');

		$('<div/>').appendTo(divGrid).css({'grid-row': '1', 'grid-column': 'button', 'text-decoration': 'underline'}).text('Action');
		$('<div/>').appendTo(divGrid).css({'grid-row': '1', 'grid-column': 'date', 'text-decoration': 'underline'}).text('Start Date');
		$('<div/>').appendTo(divGrid).css({'grid-row': '1', 'grid-column': 'title', 'text-decoration': 'underline'}).text('Template');
		let row = 2;

		for (let template of this.templates)
		{
			let divButton = $('<div/>').appendTo(divGrid).css({'grid-column': 'button'});
			let divDate = $('<div/>').appendTo(divGrid).css({'grid-column': 'date'});
			let divTitle = $('<div/>').appendTo(divGrid).css({'grid-column': 'title'});
			for (let div of [divButton, divDate, divTitle]) div.css({'grid-row': row.toString(), 'align-self': 'center'});
			row++;

			let btnShow = $('<button class="btn btn-action">Show</button>').prependTo(divButton);
			let inputDate = $('<input type="text" size="11"/>').appendTo(divDate);
			inputDate.attr({'placeholder': 'YYYY-MM-DD', 'pattern': '\\d\\d\\d\\d-\\d\\d-\\d\\d'});
			$('<span/>').appendTo(divTitle).text(template.title);

			let curDate:number = null, curQuery:string = null;
			inputDate.on('input', () =>
			{
				let txt = inputDate.val().toString();
				let g:string[];
				curDate = null;
				curQuery = null;
				let enabled = true;
				if (txt == '') {}
				else if (g = txt.match(/^(\d\d\d\d)-(\d\d?)-(\d\d?)$/))
					curDate = new Date(parseInt(g[1]), parseInt(g[2]) - 1, parseInt(g[3])).getTime();
				else if (g = txt.match(/^(\d\d\d\d)-(\d\d?)$/))
					curDate = new Date(parseInt(g[1]), parseInt(g[2]) - 1, 1).getTime();
				else if (g = txt.match(/^(\d\d\d\d)$/))
					curDate = new Date(parseInt(g[1]), 0, 1).getTime();
				else if (txt.match(/^\(.*\)$/) || txt.match(/^\[.*\]$/))
					curQuery = txt;
				else enabled = false;

				btnShow.prop('disabled', !enabled);
			});

			let divContent = $('<div/>').appendTo(divGrid);
			divContent.css({'grid-row': (row++).toString(), 'grid-column': 'start / end', 'display': 'none'});
			btnShow.click(() =>
			{
				btnShow.prop('disabled', true);
				divContent.css('display', 'block');
				divContent.empty();
				new FAIRnessChart(template, curDate, curQuery, width, 400, 200).render(divContent);
			});
		}

		$('<h1>Model Building</h1>').appendTo(main);

		for (let template of this.templates)
		{
			let para = $('<p/>').appendTo(main).css('margin-top', '0.5em');
			let btnShow = $('<button class="btn btn-action">Show</button>').prependTo(para);
			let spanTitle = $('<span/>').appendTo(para).css('margin-left', '1em');
			spanTitle.text(template.title);

			let divContent = $('<div/>').appendTo(main);
			btnShow.click(() =>
			{
				btnShow.prop('disabled', true);
				new ModelProgress(template).render(divContent);
			});
		}
	}

	// ------------ private methods ------------
}

/* EOF */ }
