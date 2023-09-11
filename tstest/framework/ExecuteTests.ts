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

namespace BAETest /* BOF */ {

/*
	Enumerates and executes all tests. Both console & web versions are provided.
*/

export abstract class ExecuteTests
{
	protected modules:TestModule[] = [];
	public numPassed = 0;
	public numSkipped = 0;
	public numFailed = 0;

	constructor()
	{
		for (let [className, classObj] of Object.entries(BAETest))
		{
			if (!(classObj && (classObj as any).prototype instanceof TestModule && className.endsWith('Test'))) continue;

			let module:TestModule = null;
			try
			{
				module = new (classObj as any)();
			}
			catch (e) {alert('Class named [' + className + '] is not a TestModule instance.\n' + e);}

			this.modules.push(module);
		}
	}
}

export class ExecuteTestsWeb extends ExecuteTests
{
	private divModules:JQuery[] = [];

	// ------------ public methods ------------

	constructor(public root:JQuery)
	{
		super();
	}

	public async run():Promise<void>
	{
		this.root.append('<h1>BioAssay Express Test Suite</h1>');

		// TODO: show a running total along the top

		for (let module of this.modules)
		{
			let div = $('<div></div>').appendTo(this.root).css({'margin-bottom': '0.5em'});
			this.divModules.push(div);

			let divBanner = $('<div></div>').appendTo(div);
			divBanner.css({'font-weight': 'bold', 'background-color': '#C0C0C0', 'border': '1px solid black', 'padding': '0.2em'});
			divBanner.text(module.moduleName);
		}

		for (let n = 0; n < this.modules.length; n++) await this.runModule(n);

		this.root.append(`<p>Results: ${this.numPassed} passed, ${this.numSkipped} skipped, ${this.numFailed} failed.</p>`);
	}

	// ------------ private methods ------------

	private async runModule(idx:number):Promise<void>
	{
		let module = this.modules[idx];

		let divPreTests = $('<div/>').appendTo(this.divModules[idx]);
		let spanResult:JQuery[] = [];
		let divFeedback:JQuery[] = [];

		// create a section for each test action prior to executing
		for (let n = 0; n < module.actions.length; n++)
		{
			let action = module.actions[n];

			let div = $('<div/>').appendTo(this.divModules[idx]).css({'margin-left': '2em'});

			let divBanner = $('<div></div>').appendTo(div);
			spanResult.push($('<span>\u{2610}</span>').appendTo(divBanner));
			divBanner.append(' [');
			let spanFunc = $('<span/>').appendTo(divBanner).css({'font-weight': 'bold'});
			spanFunc.text(action.funcName);
			divBanner.append('] ');

			divFeedback.push($('<div/>').appendTo(div));
		}

		let divPostTests = $('<div/>').appendTo(this.divModules[idx]);

		// perform the execution

		this.installFeedback(divPreTests, module);
		await module.beforeAll();

		for (let n = 0; n < module.actions.length; n++)
		{
			let action = module.actions[n];
			this.installFeedback(divFeedback[n], module);

			let span = spanResult[n];

			try
			{
				await module.beforeEach();
				span.text('\u{027F3}');
				await action.funcAction();
				this.numPassed++;
			}
			catch (e)
			{
				if (e instanceof SkippedTestException)
				{
					span.text('\u{2717}').css({'color': '#E0A500'});
					this.numSkipped++;
					continue;
				}
				span.text('\u{2717}').css({'color': '#800000'});
				module.error(e.stack);
				console.error(e); // note: the Chrome/Electron console translates to sourcemap; the stack
								  // info that is displayed graphically does not (and doing so would be tricky)
				this.numFailed++;
				continue;
			}

			await module.afterEach();

			span.text('\u{2714}');
			span.css({'color': '#00C000'});
		}

		this.installFeedback(divPostTests, module);
		await module.afterAll();
	}

	// points the feedback functions in the given module to writing content to the parent div
	private installFeedback(div:JQuery, module:TestModule):void
	{
		let appendLines = (blk:JQuery, txt:string):void =>
		{
			txt.split('\n').forEach((line, idx) =>
			{
				if (idx > 0) blk.append('<br/>');
				blk.append(escapeHTML(line));
			});
		};
		module.log = (txt) =>
		{
			let blk = $('<div/>').appendTo(div).css({'color': '#808080', 'margin-left': '2em'});
			appendLines(blk, txt);
		};
		module.warn = (txt) =>
		{
			let blk = $('<div/>').appendTo(div).css({'color': '#8C6B26', 'margin-left': '2em'});
			appendLines(blk, txt);
		};
		module.error = (txt) =>
		{
			let blk = $('<div/>').appendTo(div).css({'color': '#800000', 'margin-left': '2em'});
			appendLines(blk, txt);
		};
	}
}

export class ExecuteTestsConsole extends ExecuteTests
{
	// ------------ public methods ------------

	constructor()
	{
		super();
	}

	public async run():Promise<void>
	{
		console.log('BioAssay Express: executing tests');

		for (let n = 0; n < this.modules.length; n++) await this.runModule(n);

		console.log(`\nOverall Results: ${this.numPassed} passed, ${this.numSkipped} skipped, ${this.numFailed} failed.`);
	}

	// ------------ private methods ------------

	private async runModule(idx:number):Promise<void>
	{
		let module = this.modules[idx];

		console.log('\n--<' + module.moduleName + '>--');

		module.log = (txt) => console.log('    ' + txt);
		module.warn = (txt) => console.log('    ! ' + txt);
		module.error = (txt) => console.log('    ** ' + txt);

		// perform the execution

		await module.beforeAll();

		let modPassed = 0, modSkipped = 0, modFailed = 0;

		for (let n = 0; n < module.actions.length; n++)
		{
			let action = module.actions[n];

			// console.log(`  [${action.funcName}]`);
			process.stdout.write(`  [${action.funcName}]`);

			try
			{
				await module.beforeEach();
				await action.funcAction();
				modPassed++;
				this.numPassed++;
				console.log();
			}
			catch (e)
			{
				if (e instanceof SkippedTestException)
				{
					console.log('\x1b[33m%s\x1b[0m', ' ... skipped');
					modSkipped++;
					this.numSkipped++;
					continue;
				}
				console.log('\x1b[31m%s\x1b[0m', ' ... failed');
				console.error(e);
				modFailed++;
				this.numFailed++;
				continue;
			}

			await module.afterEach();
		}

		await module.afterAll();

		console.log(`  (${modPassed} passed, ${modSkipped} skipped, ${modFailed} failed)`);
	}
}

/* EOF */ }
