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

package com.cdd.bae.util;

import com.cdd.bae.data.*;
import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

import org.slf4j.*;

/*
	Threshold estimation: given a collection of pairs (value:numeric, active:boolean), tries to pick a good threshold to divide
	actives & inactives. Good is defined as separating the two sides (as well as possible), and also rounding to few significant
	figures. This functionality is only likely to be relevant to a single use case, namely importing of PubChem activity data,
	which provides value & outcome but no threshold or operator. This use case is unlikely occur elsewhere.
*/

public class ThresholdEstimator
{
	private static final Logger logger = LoggerFactory.getLogger(ThresholdEstimator.class);

	public static final String OPERATOR_GREATER = ModelSchema.expandPrefix("obo:GENEPIO_0001006");
	public static final String OPERATOR_LESSTHAN = ModelSchema.expandPrefix("obo:GENEPIO_0001002");
	public static final String OPERATOR_GREQUAL = ModelSchema.expandPrefix("obo:GENEPIO_0001005");
	public static final String OPERATOR_LTEQUAL = ModelSchema.expandPrefix("obo:GENEPIO_0001003");
	public static final String OPERATOR_EQUAL = ModelSchema.expandPrefix("obo:GENEPIO_0001004");

	private static final class Datum
	{
		double value;
		boolean active;
	}
	private List<Datum> data = new ArrayList<>();
	
	private double threshold = Double.NaN;
	private String operator = null;
	private String fieldName = null; // this may be determined, if adding from underlying data

	// ------------ public methods ------------

	public ThresholdEstimator()
	{
	}

	// for adding values one at a time
	public void addValue(Double value, Boolean active)
	{
		if (value == null || active == null) return;
		Datum d = new Datum();
		d.value = value;
		d.active = active;
		data.add(d);
	}
	public void addValues(double[] values, boolean[] actives)
	{
		for (int n = 0; n < values.length; n++) addValue(values[n], actives[n]);
	}
	
	// bring in the relevant content from an assay; returns true only if the necessary content can be found
	public boolean addAssay(long assayID, String field)
	{
		fieldName = field;
	
		String[] types = new String[]{DataMeasure.TYPE_PRIMARY, DataMeasure.TYPE_ACTIVITY};
		DataObject.Measurement[] measures = Common.getDataStore().measure().getMeasurements(assayID, types);
		
		Set<Long> compounds = new HashSet<>();
		Map<Long, Double> mapValues = new HashMap<>();
		Map<Long, Boolean> mapActives = new HashMap<>();
		for (DataObject.Measurement measure : measures)
		{
			if (measure.type.equals(DataMeasure.TYPE_PRIMARY))
			{
				if (mapValues.size() > 0) return false; // only one is permitted
				if (fieldName != null && !measure.name.equals(fieldName)) continue;
				fieldName = measure.name;
				for (int n = 0; n < measure.compoundID.length; n++)
				{
					compounds.add(measure.compoundID[n]);
					mapValues.put(measure.compoundID[n], measure.value[n]);
				}
			}
			else if (measure.type.equals(DataMeasure.TYPE_ACTIVITY))
			{
				if (mapActives.size() > 0) return false; // only one is permitted
				for (int n = 0; n < measure.compoundID.length; n++)
				{
					compounds.add(measure.compoundID[n]);
					mapActives.put(measure.compoundID[n], measure.value[n] >= 0.5);
				}
			}
		}
		
		for (long compoundID : compounds) addValue(mapValues.get(compoundID), mapActives.get(compoundID));
		return true;
	}
	
	// performs the threshold calculation; returns false if unable to determine for any reason
	public boolean calculate()
	{
		final int sz = data.size();
		if (sz == 0) return false;
		data.sort((d1, d2) -> d1.value < d2.value ? -1 : d1.value > d2.value ? 1 : 0);
		
		double[] value = new double[sz];
		boolean[] active = new boolean[sz];
		for (int n = 0; n < sz; n++) {Datum d = data.get(n); value[n] = d.value; active[n] = d.active;}

		// if all the same: stop
		int numActives = 0;
		double avgActive = 0, avgInactive = 0;
		for (int n = 0; n < sz; n++) 
		{
			if (active[n]) 
			{
				numActives++;
				avgActive += value[n];
			}
			else avgInactive += value[n];
		}
		if (numActives == 0 || numActives == sz) return false;
		avgActive /= numActives;
		avgInactive /= sz - numActives;
		if (avgActive == avgInactive) return false;
		
		boolean higher = avgActive > avgInactive; // true of higher = active (e.g. pIC50), false otherwise (e.g. IC50)
		
		// first option: they really do separate perfectly
		int div = -1;
		for (int n = 0; n < sz - 1; n++) if (active[n] != active[n + 1])
		{
			if (div >= 0)
			{
				div = -1;
				break;
			}
			else div = n;
		}
		if (div >= 0 && value[div] != value[div + 1])
		{
			if (!active[div]) 
				determinePartition(value[div], value[div + 1]);
			else
				determinePartition(value[div + 1], value[div]);
			return true;
		}
		
		// there's some overlap, so make the best of it
		int lo = 0, hi = sz - 1;
		for (; lo < hi; lo++) if (active[lo] != active[lo + 1]) break;
		for (; hi > lo; hi--) if (active[hi] != active[hi - 1]) break;
		int bestScore = 0;
		for (int n = lo; n < hi; n++)
		{
			double t = 0.5 * (value[n] + value[n + 1]);
			int score = 0;
			for (int i = 0; i < sz; i++)
			{
				boolean a = higher ? value[i] > t : value[i] < t;
				if (a == active[i]) score++;
			}
			if (score > bestScore) {threshold = t; bestScore = score; div = n;}
		}
		if (bestScore == 0) return false;
		
		if (!active[div]) 
			determinePartition(value[div], value[div + 1]);
		else
			determinePartition(value[div + 1], value[div]);
			
		return true;
	}
	
	// access to results
	public double getThreshold() {return threshold;}
	public String getOperator() {return operator;}
	public String getFieldName() {return fieldName;}
	
	// human readable version
	public static String formatComparator(String op)
	{
		if (op.equals(OPERATOR_GREATER)) return ">";
		if (op.equals(OPERATOR_LESSTHAN)) return "<";
		if (op.equals(OPERATOR_GREQUAL)) return ">=";
		if (op.equals(OPERATOR_LTEQUAL)) return "<=";
		if (op.equals(OPERATOR_EQUAL)) return "=";
		return "?";
	}
	
	// ------------ private methods ------------
	@Deprecated
	public static boolean selfValidateSet(double[] values, boolean[] actives, boolean shouldWork, double resultThreshold, String resultOperator)
	{
		logger.debug("Validate: {} values, should work: {}", values.length, shouldWork);
		if (logger.isDebugEnabled())
			for (int n = 0; n < values.length; n++) logger.debug("    {} ==> {}", values[n], actives[n]);
		if (shouldWork) logger.debug("    Expecting: {} {}", formatComparator(resultOperator), (float)resultThreshold);
	
		ThresholdEstimator est = new ThresholdEstimator();
		est.addValues(values, actives);
		if (est.calculate() != shouldWork) 
		{
			logger.debug("    Wrong calculate outcome");
			return false;
		}
		if (!shouldWork) return true; // no need to further analyze
		
		logger.debug("    Achieved: {} {}", formatComparator(est.getOperator()), (float)est.getThreshold());
		
		if (!Util.dblEqual(est.getThreshold(), resultThreshold))
		{
			logger.debug("    Wrong threshold");
			return false;
		}
		if (!est.getOperator().equals(resultOperator))
		{
			logger.debug("    Wrong operator");
			return false;
		}
		return true;
	}
	
	// given the two closest values of opposite polarity, fill out the threshold and operator
	private void determinePartition(double ival, double aval)
	{
		double lo = Math.min(ival, aval), hi = Math.max(ival, aval);
		threshold = 0.5 * (ival + aval);
		
		// round it, if possible
		double mul = 1;
		for (int digits = 0; digits < 10; digits++)
		{
			double mod = Math.round(threshold * mul) / mul;
			if (mod >= lo && mod <= hi) {threshold = mod; break;}
			
			mod = Math.round(threshold * mul * 0.5) / (mul * 0.5);
			if (mod >= lo && mod <= hi) {threshold = mod; break;}
			
			mul *= 0.1;
		}
		
		// decide on the operator		
		operator = null;
		if (ival < aval)
		{
			if (threshold < aval) operator = OPERATOR_GREATER;
			else if (threshold <= aval) operator = OPERATOR_GREQUAL;
		}
		else
		{
			if (threshold > aval) operator = OPERATOR_LESSTHAN;
			else if (threshold >= aval) operator = OPERATOR_LTEQUAL;
		}
		if (operator == null) throw new IllegalArgumentException(String.format("Bad midpoint (%g) from %g, %g", threshold, ival, aval));
	}
}



