<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ page import="org.json.*" %>
<%@ page import="com.cdd.bae.data.*" %>
<%@ page import="com.cdd.bao.template.*" %>
<%@ page import="com.cdd.bao.util.*" %>
<%@ page session="false" %>
<%
	String schemaURI = ModelSchema.expandPrefix(request.getParameter("schemaURI"));
	String propURI = ModelSchema.expandPrefix(request.getParameter("propURI"));
	String groupNestParam = request.getParameter("groupNest");
	String[] groupNest = groupNestParam == null ? null : ModelSchema.expandPrefixes(groupNestParam.split(","));
	String valueURI = ModelSchema.expandPrefix(request.getParameter("valueURI"));

	// return string
	String result = "";
	Schema schema = Common.getSchema(schemaURI);

	if (schema != null && Util.notBlank(propURI) && Util.notBlank(groupNestParam) && Util.notBlank(valueURI))
	{
		result = Common.getCustomName(schema, propURI, groupNest, valueURI);
	}
	if (Util.isBlank(result) && Util.notBlank(valueURI))
	{
		result = schvoc.getLabel(valueURI);
	}
	if (Util.isBlank(result) && Util.notBlank(valueURI))
	{
		result = Common.getOntoValues().getLabel(valueURI);
		if (Util.isBlank(result))
		{
			DataObject.Provisional prov = Common.getProvCache().getTerm(valueURI);
			if (prov != null) result = prov.label;
		}
	}
	if (result == null) result = "";
%>

<%=result%>
