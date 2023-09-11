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
	Embeddable widget that receives a list of one-or-more compounds (typically from paste or drag) and displays a concise
	summary of the contents.
*/

export class UploadCompounds
{
	public changedContent:() => void = null;
	private ds = new wmk.DataSheet();
	private canvas:JQuery = null;

	public static DEMO_UPLOADCOMPOUND = '\n\n\n' +
		' 19 20  0  0001  0  0  0  0  0999 V2000\n' +
		'    1.5393    0.0000    0.0000 O   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    1.2657   -1.3113    0.0000 C   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    0.0000   -1.7217    0.0000 O   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    2.2577   -2.2006    0.0000 C   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    3.5233   -1.7902    0.0000 C   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    4.5039   -2.6795    0.0000 C   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    5.7696   -2.2691    0.0000 C   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    6.7616   -3.1584    0.0000 C   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    7.0352   -1.8586    0.0000 H   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    5.9748   -4.2303    0.0000 S   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    6.7616   -5.3021    0.0000 C   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    8.0272   -4.8916    0.0000 C   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    8.0272   -3.5689    0.0000 C   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    8.0272   -2.2349    0.0000 H   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    9.2929   -3.1584    0.0000 N   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    8.0272   -6.2257    0.0000 H   0  0  0  0  0  0  0  0  0  0  0\n' +
		'    9.2929   -5.3021    0.0000 N   0  0  0  0  0  0  0  0  0  0  0\n' +
		'   10.0682   -4.2303    0.0000 C   0  0  0  0  0  0  0  0  0  0  0\n' +
		'   11.4023   -4.2303    0.0000 O   0  0  0  0  0  0  0  0  0  0  0\n' +
		'  1  2  2  0\n' +
		'  2  3  1  0\n' +
		'  2  4  1  0\n' +
		'  4  5  1  0\n' +
		'  5  6  1  0\n' +
		'  6  7  1  0\n' +
		'  7  8  1  0\n' +
		'  8  9  1  6\n' +
		'  8 10  1  0\n' +
		'  8 13  1  0\n' +
		' 10 11  1  0\n' +
		' 11 12  1  0\n' +
		' 12 13  1  0\n' +
		' 12 16  1  6\n' +
		' 12 17  1  0\n' +
		' 13 14  1  6\n' +
		' 13 15  1  0\n' +
		' 15 18  1  0\n' +
		' 17 18  1  0\n' +
		' 18 19  2  0\n' +
		'M  END';

	constructor()
	{
	}

	// affecting content
	public getContent():wmk.DataSheet {return this.ds;}
	public setContent(ds:wmk.DataSheet):void
	{
		this.ds = ds;
		this.redrawContent();
		if (this.changedContent != null) this.changedContent();
	}
	public hasContent():boolean {return this.ds.numRows > 0;}

	// presents a string that may contain a molecular data collection; if it can be parsed, the molecules are used, and the
	// method returns true; if the method returns false, it means the data was not in an appropriate format
	public claimContent(txt:string):boolean
	{
		let ds:wmk.DataSheet = null;
		try {ds = wmk.DataSheetStream.readXML(txt);}
		catch (ex) {}
		if (ds == null)
		{
			let mol = wmk.MoleculeStream.readNative(txt);
			if (mol != null)
			{
				ds = new wmk.DataSheet();
				ds.appendColumn('Molecule', wmk.DataSheetColumn.Molecule, '');
				ds.appendRow();
				ds.setMolecule(0, 0, mol);
			}
		}
		if (ds == null)
		{
			try
			{
				ds = new wmk.MDLSDFReader(txt).parse();
			}
			catch (ex) {}
		}
		if (ds == null)
		{
			try
			{
				let mol = wmk.MoleculeStream.readMDLMOL(txt);
				if (mol != null)
				{
					ds = new wmk.DataSheet();
					ds.appendColumn('Molecule', wmk.DataSheetColumn.Molecule, '');
					ds.appendRow();
					ds.setMolecule(0, 0, mol);
				}
			}
			catch (ex) {}
		}

		if (ds != null && ds.numRows > 0 && ds.firstColOfType(wmk.DataSheetColumn.Molecule) >= 0)
		{
			this.setContent(ds);
			return true;
		}
		else return false;
	}

	// creates the enclosing object
	public render(domParent:JQuery):void
	{
		this.canvas = $('<canvas></canvas>').appendTo(domParent);
		this.canvas.click(() =>
		{
			if (this.ds.numRows == 0) this.claimContent(UploadCompounds.DEMO_UPLOADCOMPOUND);
		});
		this.canvas[0].addEventListener('dragover', (event) =>
		{
			event.stopPropagation();
			event.preventDefault();
			event.dataTransfer.dropEffect = 'copy';
		});
		this.canvas[0].addEventListener('drop', (event) =>
		{
			event.stopPropagation();
			event.preventDefault();
			this.dropInto(event.dataTransfer);
		});

		this.redrawContent();
	}

	// ------------ private methods ------------

	// conditional redraw
	private redrawContent():void
	{
		if (this.canvas == null) return;
		if (this.ds.numRows == 0) this.redrawEmpty(); else this.redrawMolecules();
	}

	// resize & redraw the canvas with an invitation to provide content
	private redrawEmpty():void
	{
		let width = 300, height = 150;

		this.canvas.css('width', width + 'px');
		this.canvas.css('height', height + 'px');
		let density = pixelDensity();
		this.canvas.attr('width', width * density);
		this.canvas.attr('height', height * density);

		let ctx = (this.canvas[0] as HTMLCanvasElement).getContext('2d');
		ctx.save();
		ctx.scale(density, density);
		ctx.clearRect(0, 0, width, height);

		ctx.fillStyle = '#E6EDF2';
		ctx.fill(pathRoundedRect(0, 0, width, height, 5));

		ctx.font = '14px sans-serif';
		let txt = 'Paste or Drag Molecules', tw = ctx.measureText(txt).width;
		ctx.fillStyle = '#313A44';
		ctx.textBaseline = 'middle';
		ctx.fillText(txt, 0.5 * (width - tw), 0.5 * height);

		ctx.restore();
	}

	// resize & redraw the canvas, given that molecules are available
	private redrawMolecules():void
	{
		let policy = wmk.RenderPolicy.defaultColourOnWhite();
		let molecules:wmk.Molecule[] = [];
		for (let i = 0; i < this.ds.numRows; i++) for (let j = 0; j < this.ds.numCols; j++) if (this.ds.colType(j) == wmk.DataSheetColumn.Molecule)
		{
			let mol = this.ds.getMolecule(i, j);
			if (wmk.MolUtil.notBlank(mol)) molecules.push(mol);
		}
		let ncols = 1, nrows = 1;
		if (molecules.length <= 1) {}
		else if (molecules.length == 2) [ncols, nrows] = [2, 1];
		else if (molecules.length == 3) [ncols, nrows] = [3, 1];
		else if (molecules.length == 4) [ncols, nrows] = [2, 2];
		else if (molecules.length <= 6) [ncols, nrows] = [3, 2];
		else [ncols, nrows] = [3, 3];
		let molW = Math.ceil(300 / ncols), molH = Math.min(150, Math.ceil(300 / nrows)), gap = 6, fsz = 10;
		let width = molW * ncols + gap * (ncols + 1), height = molH * nrows + gap * (nrows + 1);
		if (molecules.length > ncols * nrows) height += gap + fsz;

		this.canvas.css('width', width + 'px');
		this.canvas.css('height', height + 'px');
		let density = pixelDensity();
		this.canvas.attr('width', width * density);
		this.canvas.attr('height', height * density);

		let ctx = (this.canvas[0] as HTMLCanvasElement).getContext('2d');
		ctx.save();
		ctx.scale(density, density);
		ctx.clearRect(0, 0, width, height);

		ctx.fillStyle = '#E6EDF2';
		ctx.fill(pathRoundedRect(0, 0, width, height, 5));

		for (let i = 0; i < nrows; i++) for (let j = 0; j < ncols; j++)
		{
			let n = i * ncols + j;
			if (n >= molecules.length) continue;

			let x = gap + j * (molW + gap), y = gap + i * (molH + gap);
			ctx.fillStyle = 'white';
			ctx.fill(pathRoundedRect(x, y, x + molW, y + molH, 5));

			let effects = new wmk.RenderEffects();
			let measure = new wmk.OutlineMeasurement(0, 0, policy.data.pointScale);
			let layout = new wmk.ArrangeMolecule(molecules[n], measure, policy, effects);
			layout.arrange();
			layout.squeezeInto(x, y, molW, molH, 2);

			let metavec = new wmk.MetaVector();
			new wmk.DrawMolecule(layout, metavec).draw();
			metavec.renderContext(ctx);
		}

		if (molecules.length > ncols * nrows)
		{
			ctx.font = `italic ${fsz}px sans-serif`;
			let txt = molecules.length + ' molecules', tw = ctx.measureText(txt).width;
			ctx.fillStyle = '#313A44';
			ctx.textBaseline = 'bottom';
			ctx.fillText(txt, gap, height - gap);
		}

		ctx.restore();
	}

	// something was dragged into the box
	private dropInto(transfer:DataTransfer):void
	{
		let items = transfer.items, files = transfer.files;

		for (let n = 0; n < items.length; n++)
		{
			if (items[n].type.startsWith('text/plain'))
			{
				items[n].getAsString((str:string) =>
				{
					if (!this.claimContent(str)) console.log('Dragged data is not a recognized molecule: ' + str);
				});
				return;
			}
		}
		for (let n = 0; n < files.length; n++)
		{
			if (files[n].name.endsWith('.el') || files[n].name.endsWith('.mol') ||
				files[n].name.endsWith('.ds') || files[n].name.endsWith('.sdf'))
			{
				let reader = new FileReader();
				reader.onload = (event) =>
				{
					let str = reader.result.toString();
					if (!this.claimContent(str)) console.log('Dragged file is not a recognized molecule: ' + str);
				};
				reader.readAsText(files[n]);
				return;
			}
		}
	}
}

/* EOF */ }
