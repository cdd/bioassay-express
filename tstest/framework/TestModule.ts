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
	Base class for all test modules: all groups of tests should be inherited directly from this.

	Notes:
		- each test action method *must* begin with 'test' (e.g. testThing()), otherwise it will be ignored
		- test methods should be of type 'public async'; they may optionally return a Promise
		- any test failure should be reported using one of the 'assert' functions
		- other metadata can be reported using log/warn/error
		- any pre/post configuration should be done by overriding the before/after methods
*/

export interface TestModuleAction
{
	funcName:string;
	funcAction:() => Promise<void>;
	description?:string; // not currently used
}

export class TestModule
{
	public actions:TestModuleAction[] = [];

	// these get filled in before the tests are run; log = miscellaneous information; warn = suspicious but not necessarily
	// broken; error = information about what was going on before it hit the fan, expected to precede an assertion trigger
	public log:(txt:string) => void = null;
	public warn:(txt:string) => void = null;
	public error:(txt:string) => void = null;

	constructor(public moduleName:string)
	{
		for (let proto = Object.getPrototypeOf(this); proto; proto = Object.getPrototypeOf(proto))
		{
			for (let name of Object.getOwnPropertyNames(proto)) if (name.startsWith('test'))
			{
				const desc = Object.getOwnPropertyDescriptor(proto, name);
				if (desc && typeof desc.value == 'function')
				{
					// (description?)
					this.actions.push({'funcName': name, 'funcAction': (this as any)[name].bind(this)});
				}
			}
		}
	}

	// override these methods to do initialisation/teardown
	public async beforeAll():Promise<void> {}
	public async afterAll():Promise<void> {}
	public async beforeEach():Promise<void> {}
	public async afterEach():Promise<void> {}
}

// convenience assertions
export function assert(msg?:string):void
{
	throw new Error(msg ? 'Assertion: ' + msg : 'Assertion triggered');
}
export function assertTrue(condition:boolean, msg?:string):void
{
	if (!condition) assert(defaultInvalidMessage('expected true', condition, msg));
}
export function assertFalse(condition:boolean, msg?:string):void
{
	if (condition) assert(defaultInvalidMessage('expected false', condition, msg));
}
export function assertNull(value:any, msg?:string):void
{
	if (value != null) assert(defaultInvalidMessage('expected null', value, msg));
}
export function assertNotNull(value:any, msg?:string):void
{
	if (value == null) assert(defaultInvalidMessage('expected not null', value, msg));
}
export function assertEquals(expected:any, obtained:any, msg?:string):void
{
	if (expected != obtained) assert(defaultMismatchMessage(expected, obtained, msg));
}
export function assertNotEquals(notExpected:any, obtained:any, msg?:string):void
{
	if (notExpected == obtained) assert(defaultMismatchMessage(notExpected, obtained, msg));
}
export function assertArrayEquals(expected:any, obtained:any, msg?:string):void
{
	if ((expected == null) != (obtained == null)) assert(defaultMismatchMessage(expected, obtained, msg)); // null != blank
	if (!Vec.equals(expected, obtained)) assert(defaultMismatchMessage(expected, obtained, msg));
}
export function assertArrayNotEquals(notExpected:any, obtained:any, msg?:string):void
{
	if (Vec.equals(notExpected, obtained)) assert(defaultMisNotmatchMessage(notExpected, obtained, msg));
}

// convenience functions for preferring the user's custom message, else falling back to something informative
function defaultMessage(defmsg:string, msg?:string):string
{
	return msg || defmsg;
}
function defaultInvalidMessage(note:string, value:any, msg?:string):string
{
	if (msg) return msg;
	return note + ', obtained ' + JSON.stringify(value);
}
function defaultMismatchMessage(expected:any, obtained:any, msg?:string):string
{
	if (msg) return msg;
	return 'expected ' + JSON.stringify(expected) + ', obtained ' + JSON.stringify(obtained);
}
function defaultMisNotmatchMessage(notExpected:any, obtained:any, msg?:string):string
{
	if (msg) return msg;
	return 'expected NOT ' + JSON.stringify(notExpected) + ', obtained ' + JSON.stringify(obtained);
}

// custom exception raised when a test is skipped
export class SkippedTestException extends Error
{
}

// generic decorator to define a condition required to execute a method
export function condition(condFunction:() => boolean):MethodDecorator
{
	return (target:any, propertyKey:string, descriptor:PropertyDescriptor) =>
	{
		const originalMethod = descriptor.value;
		descriptor.value = function()
		{
			if (!condFunction()) throw new SkippedTestException();
			originalMethod.apply(this, arguments);
		};
		return descriptor;
	};
}

/*
	Decorators: use @name before the test method (e.g. @skipTest) to apply the given effect, similar
	to Java/JUnit conventions.
*/

// skip test if window variable not available
export const requireWindow:MethodDecorator = condition(() => typeof(window) != 'undefined');
// skip test always
export const skipTest:MethodDecorator = condition(() => false);

/* EOF */ }
