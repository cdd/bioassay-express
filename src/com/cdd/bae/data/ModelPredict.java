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

package com.cdd.bae.data;

import com.cdd.bao.template.*;
import com.cdd.bao.util.*;

import java.util.*;

/*
	Takes a text document, and possibly some preexisting assignments, and comes up with predictions for the requested properties.
*/

public class ModelPredict
{
	private Schema schema;
	private String text;
	private int[] prespecFPList;
	private String[] properties; // if not null, only these propURI matches will be calculated
	private DataObject.Annotation[] accepted, rejected; // assignments that have been previously included or left out
	private Set<String> applicable = null; // optional whitelist for target annotations (specified by valueURI)
	private boolean applicableFiltered = false; // set to true if the 'applicable' list filtered anything
	private boolean repredictKnown = false; // normally "accepted" annotations don't get a prediction

	private DataStore store;
	
	public static final class Prediction extends DataObject.Annotation
	{
		// predictions: all transformed to 0..1 range
		public double nlp; // from using natural language fingerprints (NaN if n/a)
		public double corr; // from using correlation fingerprints (NaN if n/a)
		public double combined; // from either or both of the above, as available
	}

	private List<Prediction> predictions = new ArrayList<>();

	// ------------ public methods ------------

	// normal constructor: NL will be run on the text; all possible annotations for the given list of properties will be predicted; the accepted/rejected list
	// will be taken into account when making the predictions
	public ModelPredict(Schema schema, String text, String[] properties, DataObject.Annotation[] accepted, DataObject.Annotation[] rejected)
	{
		this.schema = schema;
		this.text = text;
		this.properties = properties;
		this.accepted = accepted;
		this.rejected = rejected;

		store = Common.getDataStore();
	}
	
	// alternate constructor for when the NLP fingerprints are already determined
	public ModelPredict(Schema schema, int[] fplist, String[] properties, DataObject.Annotation[] accepted, DataObject.Annotation[] rejected)
	{
		this.schema = schema;
		prespecFPList = fplist;
		this.properties = properties;
		this.accepted = accepted;
		this.rejected = rejected;

		store = Common.getDataStore();
	}

	// changing parameters after the fact
	public void setProperties(String[] properties) {this.properties = properties;}
	public void setAccepted(DataObject.Annotation[] accepted) {this.accepted = accepted;}
	public void setRejected(DataObject.Annotation[] rejected) {this.rejected = rejected;}
	
	// define a set of value URIs which are fair game: if this parameter is specified, predictions will be skipped for anything
	// that isn't in the list
	public void setApplicable(Set<String> applicable) {this.applicable = applicable;}
	
	// decide whether to predict for values that are already in the known to be asserted list ("accepted") which is usually
	// redundant, but sometimes interesting for validation purposes
	public void setRepredictKnown(boolean repredictKnown) {this.repredictKnown = repredictKnown;}

	// recalculates the requested properties (matched by propURI), less anything explicitly rejected
	public void calculate()
	{
		predictions.clear();
	
		int[] fplist = prespecFPList;
		if (fplist == null)
		{
			String[] blocks = new NLPCalculator(text).calculate();
			fplist = convertFromNLP(blocks);
		}
		
		final String SEP = "::";

		DataObject.AnnotationFP[] allTargets = store.annot().fetchAnnotationFP();
		Map<String, Integer> targetIndex = new HashMap<>();
		for (DataObject.AnnotationFP t : allTargets) targetIndex.put(t.propURI + SEP + t.valueURI, t.fp);

		// map the already-accepted annotations to indices (where available), to use for correlation predictions
		int[] already = new int[accepted == null ? 0 : accepted.length];
		int numAlready = 0;
		for (int n = 0; n < already.length; n++)
		{
			int idx = targetIndex.getOrDefault(accepted[n].propURI + SEP + accepted[n].valueURI, -1);
			if (idx < 0) continue;
			already[numAlready++] = idx;
		}
		if (fplist.length == 0 && numAlready == 0) return; // nothing can be done
		if (numAlready != already.length) already = Arrays.copyOf(already, numAlready);
		Arrays.sort(already);
		
		// designate things to do/not to do
		Set<String> inclProps = new HashSet<>();
		Set<String> exclAssn = new HashSet<>(); // note: accepted & rejected both imply do not bother predicting
		if (properties != null) for (String propURI : properties) inclProps.add(propURI);
		if (!repredictKnown)
		{
			if (accepted != null) for (DataObject.Annotation assn : accepted) exclAssn.add(assn.propURI + SEP + assn.valueURI);
			if (rejected != null) for (DataObject.Annotation assn : rejected) exclAssn.add(assn.propURI + SEP + assn.valueURI);
		}

		// make a list of allowed terms for each requested property; sometimes predictions can linger for terms subsequently expelled
		Map<String, Set<String>> allowedBySchema = new HashMap<>();
		for (String propURI : properties)
		{
			Set<String> values = new HashSet<>();
			for (Schema.Assignment assn : schema.findAssignmentByProperty(propURI))
			{
				SchemaTree tree = Common.obtainTree(schema, assn);
				if (tree != null) for (SchemaTree.Node node : tree.getList()) values.add(node.uri);
			}
			allowedBySchema.put(propURI, values);
		}

		for (DataObject.AnnotationFP target : allTargets)
		{
			if (properties != null && !inclProps.contains(target.propURI)) continue;
			if (exclAssn.contains(target.propURI + SEP + target.valueURI)) continue;
			if (target.valueURI.startsWith(ModelSchema.PFX_BAT)) continue; // "bat:" terms are not suitable for primary models (e.g. Absence)
			if (!allowedBySchema.get(target.propURI).contains(target.valueURI)) continue;
			if (applicable != null && !applicable.contains(target.valueURI)) 
			{
				applicableFiltered = true;
				continue;
			}
			
			Prediction pred = new Prediction();
			pred.propURI = target.propURI;
			pred.valueURI = target.valueURI;
			
			pred.nlp = predictNLP(fplist, target.fp);
			pred.corr = predictCorrelation(already, target.fp);
			
			if (!Double.isNaN(pred.nlp) && !Double.isNaN(pred.corr)) pred.combined = pred.nlp * pred.corr;
			else if (!Double.isNaN(pred.nlp)) pred.combined = pred.nlp;
			else if (!Double.isNaN(pred.corr)) pred.combined = pred.corr;
			else continue;
			
			predictions.add(pred);
		}
	}
	
	public Prediction[] getPredictions() {return predictions.toArray(new Prediction[predictions.size()]);}
	
	public boolean wasAnythingFiltered() {return applicableFiltered;}

	// ------------ private methods ------------

	// turn the NLP blocks into NLP fingerprint indices
	private int[] convertFromNLP(String[] blocks)
	{
		Map<String, Integer> nlpToFP = store.nlp().fetchFingerprints();
		Set<Integer> fpset = new TreeSet<>();
		for (String nlp : blocks)
		{
			Integer fp = nlpToFP.get(nlp);
			if (fp != null) fpset.add(fp);
		}
		int[] fplist = new int[fpset.size()];
		int idx = 0;
		for (int fp : fpset) fplist[idx++] = fp;
		return fplist;
	}

	// turns the NLP fingerprints into a calibrated result, or NaN if unavailable for some reason
	private double predictNLP(int[] fplist, int targetFP)
	{
		if (fplist == null || fplist.length == 0) return Double.NaN;
	
		DataObject.Model model = store.model().getModelNLP(targetFP);
		if (model == null || model.fplist == null) return Double.NaN;
		if (!model.isExplicit) return Double.NaN; // don't want parent-only terms showing up independently
		
		double raw = 0;
		int i = 0;
		for (int fp : fplist)
		{
			// note: both 'fplist's are sorted
			for (; i < model.fplist.length; i++)
			{
				if (model.fplist[i] == fp) raw += model.contribs[i];
				if (model.fplist[i] >= fp) break;
			}			
		}
		
		// convert the raw Bayesian output based on the calibration range (originally from the ROC): if there's no range, just give it a
		// more/less binary choice; if there is a range, use a sinusoidal pattern, using tan: so 0 and 1 are approached asymptotically
		
		if (model.calibLow == model.calibHigh) return raw > model.calibLow ? 0.7 : 0.3;

		double cal = (raw - model.calibLow) / (model.calibHigh - model.calibLow);
		return Math.atan(cal) / Math.PI + 0.5;
	}
	
	// turn the preexisting target assignments into a calibrated correlation prediction, if possible; the fingerprints are target indices,
	// as is the targetFP parameter
	private double predictCorrelation(int[] fplist, int targetFP)
	{
		if (fplist.length < 1) return Double.NaN; 
		
		DataObject.Model model = store.model().getModelCorr(targetFP);
		if (model == null || model.fplist == null) return Double.NaN;
		if (!model.isExplicit) return Double.NaN; // don't want parent-only terms showing up independently
		
		double raw = 0;
		int i = 0;
		for (int fp : fplist)
		{
			// note: both 'fplist's are sorted
			for (; i < model.fplist.length; i++)
			{
				if (model.fplist[i] == fp && fp != targetFP) raw += model.contribs[i];
				if (model.fplist[i] >= fp) break;
			}			
		}
		
		// convert the raw Bayesian output based on the calibration range (originally from the ROC): if there's no range, just give it a
		// more/less binary choice; if there is a range, use a sinusoidal pattern, using tan: so 0 and 1 are approached asymptotically
		
		if (model.calibLow == model.calibHigh) return raw > model.calibLow ? 0.7 : 0.3;

		double cal = (raw - model.calibLow) / (model.calibHigh - model.calibLow);
		return Math.atan(cal) / Math.PI + 0.5;
	}
}


