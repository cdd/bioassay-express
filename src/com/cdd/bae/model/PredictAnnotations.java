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

package com.cdd.bae.model;

import com.cdd.bae.data.*;
import com.cdd.bae.data.DataObject.*;
import com.cdd.bae.data.ModelPredict.*;
import com.cdd.bao.template.*;

import java.io.*;
import java.util.*;
import java.util.Map.*;
import java.util.stream.*;

/*
	Apply all annotation prediction methods and return results.
*/

public class PredictAnnotations
{
	public enum Method
	{
		CORRELATION("Correlation"), NLP("NLP"), COMBINED("Combined"), DICTIONARY("Dictionary"), ASSOCIATION("Association");

		private final String representation;

		private Method(String representation)
		{
			this.representation = representation;
		}

		@Override
		public String toString()
		{
			return this.representation;
		}

		public static List<Method> methods()
		{
			return Arrays.asList(Method.DICTIONARY, Method.ASSOCIATION, Method.NLP, Method.CORRELATION);
		}
	}

	private static Map<Method, PredictionModel> predictionModel = new EnumMap<>(Method.class);

	private PredictAnnotations()
	{
		/* static class */
	}

	public static class Predictions
	{
		public List<Schema.Assignment> assignments;
		// TODO: need to incorporate groupNest to disambiguate
		public Map<String, Set<String>> annotations; // {propURI : collection of value URIs}
		private Map<Method, Map<String, List<String>>> methodPredictions = new EnumMap<>(Method.class);

		public void put(Method method, Map<String, List<String>> prediction)
		{
			methodPredictions.put(method, prediction);
		}

		public Map<String, List<String>> get(Method method)
		{
			return methodPredictions.get(method);
		}
	}

	public static Predictions getAnnotations(Assay assay)
	{
		Predictions predictions = new Predictions();

		Schema schema = Common.getSchema(assay.schemaURI);
		predictions.assignments = Arrays.stream(schema.getRoot().flattenedAssignments())
				.sorted((a1, a2) -> a1.name.compareTo(a2.name)).collect(Collectors.toList());

		predictions.annotations = new HashMap<>();
		for (Schema.Assignment assignment : predictions.assignments)
			predictions.annotations.put(assignment.propURI, new TreeSet<>());
		for (DataObject.Annotation annotation : assay.annotations)
		{
			Set<String> values = predictions.annotations.get(annotation.propURI);
			if (values != null) values.add(annotation.valueURI);
		}

		return predictions;
	}

	public static Predictions getPredictions(Assay assay) throws IOException
	{
		return getPredictions(assay, Method.methods());
	}

	public static Predictions getPredictions(Assay assay, List<Method> methods) throws IOException
	{
		initialize(methods);
		Schema schema = Common.getSchema(assay.schemaURI);

		Predictions predictions = getAnnotations(assay);
		for (Entry<Method, PredictionModel> pm : predictionModel.entrySet())
			if (methods.contains(pm.getKey()))
				predictions.put(pm.getKey(), pm.getValue().getPrediction(assay));

		if (methods.contains(Method.NLP) || methods.contains(Method.CORRELATION))
		{
			predictions.put(Method.NLP, new HashMap<>());
			predictions.put(Method.CORRELATION, new HashMap<>());
			predictions.put(Method.COMBINED, new HashMap<>());
			for (Schema.Assignment assignment : predictions.assignments)
				addNLPpredictions(assignment, schema, assay, predictions);
		}
		return predictions;
	}

	public static Predictions getPredictions(Assay assay, Schema.Assignment assignment) throws IOException
	{
		initialize(Method.methods());
		Schema schema = Common.getSchema(assay.schemaURI);

		Predictions predictions = getPredictions(assay, Arrays.asList(Method.DICTIONARY, Method.ASSOCIATION));
		predictions.put(Method.NLP, new HashMap<>());
		predictions.put(Method.CORRELATION, new HashMap<>());
		predictions.put(Method.COMBINED, new HashMap<>());
		addNLPpredictions(assignment, schema, assay, predictions);

		return predictions;
	}

	private static void initialize(List<Method> methods) throws IOException
	{
		for (Method method : methods)
		{
			if (predictionModel.containsKey(method)) continue;
			if (method == Method.DICTIONARY) predictionModel.put(Method.DICTIONARY, new DictionaryPredict());
			if (method == Method.ASSOCIATION) predictionModel.put(Method.ASSOCIATION, new AssociationPredict());
		}
	}

	public static Predictions getDictionaryPredictions(Assay assay) throws IOException
	{
		return getPredictions(assay, Arrays.asList(Method.DICTIONARY));
	}

	public static Predictions getAssociationPredictions(Assay assay) throws IOException
	{
		return getPredictions(assay, Arrays.asList(Method.ASSOCIATION));
	}

	private static void addNLPpredictions(Schema.Assignment assignment, Schema schema, Assay assay, Predictions predictions)
	{
		String propURI = assignment.propURI;
		ModelPredict model = new ModelPredict(schema, assay.text, new String[]{propURI}, assay.annotations, null);
		model.calculate();
		ModelPredict.Prediction[] modelPredictions = model.getPredictions();

		predictions.get(Method.NLP).put(propURI, sortedNLPpredictions(modelPredictions, (p1, p2) -> Double.compare(p2.nlp, p1.nlp)));
		predictions.get(Method.CORRELATION).put(propURI, sortedNLPpredictions(modelPredictions, (p1, p2) -> Double.compare(p2.corr, p1.corr)));
		predictions.get(Method.COMBINED).put(propURI, sortedNLPpredictions(modelPredictions, (p1, p2) -> Double.compare(p2.combined, p1.combined)));
	}

	private static List<String> sortedNLPpredictions(ModelPredict.Prediction[] modelPredictions, Comparator<? super Prediction> comparator)
	{
		return Arrays.stream(modelPredictions).sorted(comparator).map(p -> p.valueURI).collect(Collectors.toList());
	}
}
