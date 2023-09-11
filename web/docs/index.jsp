<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="com.cdd.bae.util.*"%>
<%@ page import="com.cdd.bae.data.Common"%>
<%@ page session="false" %>
<% MiscInserts.CSPPolicy cspPolicy = MiscInserts.getCSPPolicy(request, response, ".."); %>
<!DOCTYPE html>
<html>
<head>
<title>BioAssay Express</title>
<%=MiscInserts.includeCommonHead(1)%>

<style>

h1
{
	margin-top: 1em;
	font-size: 1.7em;
	color: #1362B3;
}

h2
{
	font-size: 1.4em;
}

table.points
{
	border: 1px solid black;
	border-collapse: 5px;
	box-shadow: 0 0 5px rgba(66,88,77,0.5);
	max-width: 50em;
}

b
{
	color: #134293;
}

tr.even
{
	background-color: #F8F8F8;
}

tr.odd
{
	background-color: #E6EDF2;
}

td.point
{
	padding: 0.5em;
	text-align: left;
}

</style>

</head>

<body>
<jsp:include page="/inc/header.jsp">
	<jsp:param name="depth" value="1" />
</jsp:include>

<img src="overview01.png" align="right" width="400">
<p id="headerContainer">
	The <b>BioAssay Express</b> is a web-based tool for annotating bioassay protocols using semantic web terms. Curation involves a human 
	operator, assisted by machine learning and an ergonomic UI/UX. Each assay is annotated once, after which it becomes fully machine readable: 
	assays can be searched, sorted, clustered and analyzed without ever having to read through the original text. This approach is suitable 
	for institutions with assay protocols stored in legacy formats, for creating new content as an electronic lab notebook, and for exploring 
	structure-activity relationships within large volumes of public data.
</p>
<br clear="all">

<h1>Domain</h1>

<p>
	One of the key activities in early stage drug discovery is the <i>screening</i> of potential chemical entities (either small molecules
	or larger biological fragments) to test for activity against various targets (which include specific proteins for diseases or biological
	processes, whole cell tests, ADME/toxicology, etc.). Each screening configuration is a distinct scientific experiment, and even when
	two scientists are measuring the same property, no two experiments are ever identical: and all too often, the nuances are important.
</p>

<p>
	The problem that the <b>BioAssay Express</b> was created to solve is that current methods for recording bioassay protocols are
	very far behind other aspects of drug discovery. The standard practice for assay protocols is to document them using
	scientific English, which is effective for communication between screening biology experts, but it becomes a rapidly escalating
	challenge to <i>manage</i> large collections of protocol descriptions. Many companies and institutions have developed in-house
	solutions for collating their growing assay collections, typically using bespoke user interfaces to encourage scientists to select
	labels from lists of controlled vocabulary.
</p>

<p>
	Common issues with the status quo of assay management include:
</p>

<p><table class="points" align="center">
	<tr><td>
		<b>text assays</b> are only searchable by keywords, which have a high failure rate (false positives &amp; false negatives abound)
	</td></tr>

	<tr><td>
		marking up assays with <b>controlled vocabulary</b> helps, but the terms are not interchangeable between institutions, providing
		no value for collaboration or externalization
	</td></tr>

	<tr><td>
		controlled vocabulary terms are often <b>poorly defined and optional</b>, resulting in sporadic coverage or imperfect understanding
	</td></tr>

	<tr><td>
		<b>compliance</b> with documentation policies is difficult to measure or enforce
	</td></tr>

	<tr><td>
		text descriptions are <b>time consuming to review</b>, and it is easy to make mistakes
	</td></tr>

	<tr><td>
		<b>reproducibility</b> suffers from the absence of rigorously defined annotation standards
	</td></tr>

	<tr><td>
		<b>institutional knowledge</b> is frequently lost: assays are often repeated unnecessarily
	</td></tr>

	<tr><td>
		many scientists dislike or have difficulty using <b>scientific English</b> to document everything they do
	</td></tr>

	<tr><td>
		there are no easy ways to <b>peruse similar assays</b>, or analyze previous work, or compare experiments with other
		institutions or public data
	</td></tr>

	<tr><td>
		<b>mergers and acquisitions</b> are an information systems nightmare
	</td></tr>
</table></p>

<p>
	The <b>BioAssay Express</b> addresses all of these issues by using semantic standards, allowing scientists to do a one-time
	curation of each of their assays, with minimum fuss.
</p>

<p>
	The overall objective of the project is to make <i>assay informatics</i> into a standard practice within the drug discovery field:
	this approach has been overwhelmingly successful for <i>cheminformatics</i> and <i>bioinformatics</i>, and there are numerous ways
	in which this can aid projects:
</p>

<p><table class="points" align="center">
	<tr><td>
		<b>query with precision</b>: find assays immediately and reliably, without missing anything or digging through superfluous matches
	</td></tr>

	<tr><td>
		<b>tag</b> assays with labels - it becomes easy to see what belongs to each category
	</td></tr>

	<tr><td>
		standardized ontologies maximize <b>data compatibility</b>, both for public datasets and private collaborations
	</td></tr>

	<tr><td>
		combining datasets from two <b>different institutions</b> becomes simple, as long as they are both using semantic annotations: one
		less reason to be terrified of mergers and acquisitions
	</td></tr>

	<tr><td>
		<b>universal vocabulary</b> makes it much easier to train scientists to use them correctly
	</td></tr>

	<tr><td>
		the underlying public ontologies have been very carefully <b>vetted by domain experts</b>, and the implicit hierarchy, labels and
		descriptions provide valuable extra metadata
	</td></tr>

	<tr><td>
		science evolves, and so do the ontologies: new terms to describe <b>new biology concepts</b> (e.g. newly druggable targets) can be 
		easily incorporated at regular intervals
	</td></tr>

	<tr><td>
		semantic web terms can be assigned multiple labels, meaning that <b>translation to other languages</b> is a cosmetic change
	</td></tr>

	<tr><td>
		annotation during registration makes <b>completeness</b> easy to monitor: missing content can be filled in later, or detected
		with simple reports
	</td></tr>

	<tr><td>
		document the key concepts using <b>semantic annotations</b>, and use full text to describe the rest
	</td></tr>

	<tr><td>
		publicly annotated data can be <b>imported and mixed</b> with private content, to increase domain knowledge and improve structure-activity
		relationship predictions
	</td></tr>

	<tr><td>
		<b>related assays</b> can be discovered with ease, which can promote internal collaboration, as well as reduce wasteful repetition of
		experiments
	</td></tr>

	<tr><td>
		highly structured descriptions of assays allow <b>analysis techniques</b> that were previously impractical, e.g. clustering, aggregating,
		filtering and combining with compound measurements
	</td></tr>

	<tr><td>
		<b>cloning</b> an assay is easy, and much less error prone: duplicate the terms, then change only those which are different
	</td></tr>

	<tr><td>
		<b>template</b> customization allows the creation of business rules for biologists, which are much easier to understand - as well 
		as enforce - than text
	</td></tr>

	<tr><td>
		the choice between <b>public vs. private</b> applies to assays, templates and ontologies: mixing public and private content behind a
		firewall allows choice of tradeoff between secrecy and seemless collaboration
	</td></tr>

	<tr><td>
		annotate each assay <b>once</b>, gain value from it <b>forever</b>
	</td></tr>
</table></p>

<h1>Annotation</h1>

<h2>Semantic Web</h2>

<p>
	The semantic web provides a great starting point for universally understood descriptive labels that can be applied to any
	concept, including biology. Because of the efforts of projects such as
		<a href="http://bioassayontology.org/" target="_blank">BioAssay Ontology</a>
		<a href="http://www.clo-ontology.org/" target="_blank">Cell Line Ontology</a>,
		<a href="http://www.geneontology.org/" target="_blank">Gene Ontology</a>,
		<a href="http://drugtargetontology.org/" target="_blank">Drug Target Ontology</a>
	and many others, there are enough well defined <i>terms</i> in the public domain to provide the vocabulary necessary to
	describe most all of the details involved in a screening experiment. These semantic web terms are highly appropriate for
	documenting laboratory procedures because they are at their core a <i>machine readable</i> technology, whereby every
	annotation is precisely and unambiguously defined. Additionally, the annotations can easily be presented to humans in a meaningful
	way, which means that it is possible to achieve the best of both worlds: perfect indexing and high readability.
</p>

<p>
	In spite of its favorable characteristics and expressive power, the semantic web is only as good as its data. Using the  
	raw tools that are available for annotation is rather like being handed a dictionary and being expected to write a novel.
	For practical purposes, content creation requires further guidelines for how to use the terms (which can be thought of as
	the <i>grammar</i>), as well as an efficient and easy to learn user interface, and ideally a collection of prior examples.
</p>

<h2>Assay Templates</h2>

<p>
	In order to reduce the massive degrees of freedom that the raw semantic web allows, the <b>BioAssay Express</b> project uses
	assay <i>templates</i> to define assignment categories, and the applicable terms within each one (this underlying technology
	is open source, and can be found on <a href="https://github.com/cdd/bioassay-template" target="_blank">GitHub</a>, and has also
	been described in the <a href="https://peerj.com/articles/cs-61/" target="_blank">literature</a>).
</p>

<img src="overview02.png" align="right" width="300">
<p>
	The default template for public data is called the <b>Common Assay Template</b>, which consists of a couple dozen assignment
	categories, which are designed to capture <i>most</i> of the summary characteristics of a bioassay protocol, most of the time.
	While it lacks the depth needed to replace the detailed text description, it provides enough information about the assays to
	perform sophisticated searching and analysis, to an extent that is not possible with alternative assay management systems.
</p>

<p>
	In practice, any number of assay templates can created, and some of them can be very detailed, and delve into the finer
	details of a protocol. The available <i>terms</i> that the templates use are drawn from a list of ontologies, most of which
	are public and maintained by independent stakeholders. The public ontologies can be supplemented by any number of private
	extensions, allowing an unlimited amount of template customization.
</p>
<br clear="all">

<h2>Machine Learning</h2>

<p>
	Since most of the world's assay data is stored in the form of text descriptions, this means that most public data, or legacy
	content from within an institution, requires some amount of curation. While it is possible to devise a mapping system to
	import assays that were marked up with a custom designed <i>controlled vocabulary</i>, these typically cover only a handful of
	the fields, if they are available at all. Recognizing that the legacy curation process is one of the largest barriers to
	entry, the <b>BioAssay Express</b> devotes a significant amount of energy to making the curation as painless as possible.
</p>

<p>
	The underlying principle is that both extremes of automation or lack thereof are impractical:
</p>

<ul>
	<p><li>
		<b>purely automated</b> algorithms for marking up text protocols exist, but the failure rate is extremely high, making them
		useful only for tasks that can withstand a very low signal to noise ratio (e.g. imprecise forms of searching)
	</li></p>

	<p><li>
		<b>purely manual</b> user interfaces tend to require an investment of time that is prohibitive: this is often done using
		Excel spreadsheets with long drop-down menus, which are so onerous that they are typically rejected by scientists
	</li></p>
</ul>

<p>
	The <b>BioAssay Express</b> splits the difference and makes heavy use of machine learning to <i>minimize</i> the amount of time
	and expertise required to curate each assay, rather than to eliminate the human curator altogether. This hybrid approach has been
	used to reduce the curation time by an order of magnitude or more (which we have described in the
	<a href="https://peerj.com/articles/524/" target="_blank">literature</a>, as well as being
	<a href="https://www.collaborativedrug.com/buzz/2017/04/11/uspto-issues-cdd-patent/" target="_blank">awarded a patent</a> for).
</p>

<p>
	The machine learning support is divided into two steps: the first part involves analyzing all of the available assay text using
	natural language processing, to partition the sentence structure into 
	<a href="http://opennlp.apache.org/" target="_blank">parts-of-speech blocks</a>, which are fed into a Bayesian model for each and
	every annotation term that has been encountered thus far. This means that a fresh database has essentially no idea how to
	make recommendations for annotating an assay, but as the documents start to accumulate, correlations between text and annotations
	begin to resolve themselves. The association between the text and the ability to predict which annotations are correct is quite fuzzy,
	but this is appropriate for the use case: the objective is to guess the right terms <i>most of the time</i>, so that the user can
	scan through the suggestions and quickly confirm those which are correct. The penalty for failure manifests in the form of wasting a
	scientist's valuable time, which is certainly to be avoided, but this is far less severe than creating false data.
</p>

<p>
	The second part of the machine learning is the building of <i>correlation models</i>, which also use the Bayesian method, for
	observing when combinations of terms are more or less likely to be used together. This is effective because assay protocol
	annotations are frequently non-orthogonal (e.g. <i>disease</i> and <i>protein target</i> are strongly interrelated, as are
	<i>detection instrument</i> and <i>physical detection method</i>). These correlation models can be used to refine the quality
	of suggestions as the annotation process continues.
</p>

<p align="center"><img src="overview05.png" style="border: 1px solid black; max-width: 50em;"></p>

<p>
	The diagram above shows the recall rate for a test set of previously curated assays: each row shows the highest rated annotation
	suggestions from left to right. Each correct suggestion is marked as a dark blue dot. The graphic simulates the human/machine
	hybrid approach by assuming that each correct suggestion is approved by a human operator, which allows it to be added to the
	secondary <i>correlation model</i> to improve the remaining suggestions. In all cases the majority of the annotations are correctly
	guessed early on in the sequence, although it can be seen that there are a handful of annotations for which their models perform 
	very poorly, which is why a human presence is always necessary.
</p>

<p>
	In short, the machine learning technology is useful for accelerating bulk annotation of a series of assays with a fair bit of
	commonality, which is often the case for legacy data. The technique's strength is its ability to learn from literally anything,
	but it lacks any kind of predictive ability for unfamiliar content, and can provide relatively little assistance for assays
	that are significantly different from what has been curated already.
</p>

<h2>PubChem Curation</h2>

<p>
	The components that make up the <b>BioAssay Express</b> were developed by testing the curation interface on a collection of public
	data, using the <b>Common Assay Template</b>. While there are millions of bioassay protocols that can be found in public databases,
	and untold more that are still locked away within the primary scientific literature, there are relatively few public sources that
	provide detailed text descriptions for assay protocols. One of the most valuable resources is a subset of the information stored
	within the <a href="https://pubchem.ncbi.nlm.nih.gov/" target="_blank">PubChem</a> database deposited by the
	<a href="https://www.ncbi.nlm.nih.gov/books/NBK47352/" target="_blank">Molecular Libraries Program</a>.
</p>

<p>
	During the summer of 2016, a small team at <a href="http://collaborativedrug.com">Collaborative Drug Discovery</a> curated just over
	3,500 of these assays, using the <b>BioAssay Express</b> project, while simultaneously refining the <b>Common Assay Template</b>,
	iteratively improving what was initially a relatively crude user interface, requesting new ontology terms and tweaking the machine 
	learning support. In spite of the work-in-progress nature of the project, the typical curation time per assay was measured in minutes.
</p>

<p>
	The resulting annotation content has been made publicly available, and can be accessed using the main website at
	<a href="http://www.bioassayexpress.com">www.bioassayexpress.com</a>. While the number of assays is a small fraction of the entire
	PubChem collection, it does represent a valuable resource, both for real world use, and for designing and testing new technologies
	for making use of well annotated assay data. This resource can be used by itself, or it can be combined with private data.
</p>

<p>
	It is also possible to join in on the annotation process: the <i>beta</i> website 
	(<a href="http://beta.bioassayexpress.com">beta.bioassayexpress.com</a>) allows anyone to authenticate themselves and submit their own
	annotations for assays that have been selected for inclusion within the dataset. Scientists are invited to join in and <i>crowd source</i>
	the curation of this increasing valuable resource.
</p>

<h2>Lab Notebook Context</h2>

<p>
	Curation of legacy data is the single largest bottleneck, but the quantity is finite. The <b>BioAssay Express</b> is designed to
	be equally applicable to the curation of <i>new</i> data, as it is generated. Typically when writing up an experiment that is
	imminent or complete, there is no convenient text description to help the machine learning algorithms, but it is often very
	convenient to simply hunt through the existing data to find the most similar experiment, and clone it. The cloned record can be
	quickly adjusted as necessary (e.g. just changing the target and protein for an otherwise identical screening run).
</p>

<p>
	The web-based user interface is designed so that selection of values for each of the assignment categories is fast - in most cases,
	more convenient than actually writing the text. The data entry page contains a lot of keyboard shortcuts and convenient ways to navigate
	the available options, in addition to the fact that the machine learning algorithms can provide some level of support, even without
	any text description, once the first few annotations have been asserted.
</p>

<h1>Access</h1>

<p>
	Once an assay protocol has been annotated with semantic web terms, the range and diversity of algorithms that can be used to
	organize, filter, sort, group or study them is almost limitless, and all of them can be carried out without requiring a human
	expert to go back to the original data and read through it.
</p>

<h2>Explore</h2>

<img src="overview03.png" align="right" width="200">
<p>
	One of the most effective ways to explore the assay collection is via the <i>Explore</i> interface, which is essentially a filter:
	a series of selection steps are applied, each of which reduces the qualifying assays - starting from everything, and eventually
	winnowing down to a selected subset that contains everything of interest. Each filtering step involves picking an assignment
	category, then selecting terms (sometimes a whole branch at once). This can be used to quickly look at one category to examine the
	diversity of annotations, or it can be used to very specifically refine a set of assays, which can be examined or used for a subsequent
	purpose.
</p>

<p>
	The <i>Explore</i> interface is dynamic and responds immediately whenever a term is clicked upon, which makes it effective for
	exploring assays when one is not sure what to expect. It also provides the ability to filter based on keywords (or numeric ranges),
	which is less precise than using semantic web terms, but can be bluntly effective.
</p>
<br clear="all">

<h2>Search</h2>

<img src="overview04.png" align="right" width="200">
<p>
	The annotations that adorn the assays can be treated like <i>fingerprints</i>, each of which is either present or absent.
	We can look the the adjacent discipline of cheminformatics for ideas on how to treat these: for example, it is straightforward to 
	compute a Tanimoto similarity coefficient for two assays. The <b>Search</b> feature performs a comparison method that is conceptually
	similar, with some modifications to make use of the assignment categories and the hierarchical nature of the annotation terms.
	Searching for assays involves specifying a set of annotations and starting the search, which retrieves a list of assays ranked
	in decreasing order of similarity. For convenience, it is possible to pick an existing assay and initiate a search using its own
	annotations as the starting point, i.e. the "self" will return a similarity of 100%.
</p>
<br clear="all">

<h2>Reports</h2>

<p>
	A variety of summary pages are available, e.g. the assay counts for each kind of annotation, contributions from various sources,
	issues and inconsistencies, etc. This functionality is readily adapted in order to keep track of the health of the database.
</p>

<!--
<h1>Analysis</h1>
.... compounds ... SAR ... grids
-->

<!--
<h1>Advanced</h1>
.... new terms ... 
-->

<h1>Product</h1>

<p>
	The <b>BioAssay Express</b> is a commercial product, designed and built by 
	<a href="http://collaborativedrug.com">Collaborative Drug Discovery, Inc.</a>. The public-facing instance
	(at <a href="http://www.bioassayexpress.com">www.bioassayexpress.com</a>), and a large amount
	of assay curation based on the original PubChem data, was supported in part by a small business grant and 
	is made available as a free service.
</p>

<p>
	For inquiries about running the product privately, 
	<a href="http://info.collaborativedrug.com/contact-us-collabortive-drug-discovery" target="_blank">contact us</a>.
</p>

<h2>Platform</h2>

<p>
	The user interface for <b>BioAssay Express</b> is purely web-based and runs on any modern browser. The <i>JavaScript</i> 
	codebase is written using cross-compiled <i>TypeScript</i>. The middleware is written in <i>Java</i>, using the <i>J2EE</i> 
	framework, and is tested and deployed from <i>Apache Tomcat</i>. Natural language processing is done with <i>Apache OpenNLP</i>,
	semantic web processing with <i>Apache Jena</i>, and cheminformatics with the <i>Chemical Development Kit</i>. The back-end
	database is the NoSQL <i>MongoDB</i>.
</p>

<jsp:include page="/inc/footer.jsp" />

<script src="../js/jquery-2.2.4.min.js" type="text/javascript"></script>

<script nonce="<%=cspPolicy.nonce%>">

$(document).ready(function()
{
	$('.points').each(function()
	{
		var trList = $(this).find('tr'), sz = trList.length;
		for (var n = 0; n < sz; n++)
		{
			$(trList[n]).addClass(n % 2 == 0 ? 'even' : 'odd');
			$(trList[n]).find('td').addClass('point');
		}
	});
});

</script>

</body>
</html>