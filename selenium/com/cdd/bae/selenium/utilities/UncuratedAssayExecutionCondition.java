/*
	BioAssay Express (BAE)

	(c) 2016-2018 Collaborative Drug Discovery Inc.
	All rights reserved
*/

package com.cdd.bae.selenium.utilities;

import com.cdd.bae.selenium.*;

import java.io.*;

import org.junit.jupiter.api.extension.*;

public class UncuratedAssayExecutionCondition implements ExecutionCondition
{
	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context)
	{
		try
		{
			if (Setup.hasUncuratedAssays())
				return ConditionEvaluationResult.enabled("Test enabled - uncurated assays found");
		}
		catch (IOException e)
		{
			return ConditionEvaluationResult.disabled("Test disabled - exception occurred");
		}
		return ConditionEvaluationResult.disabled("Test disabled - uncurated assays required");
	}
}
