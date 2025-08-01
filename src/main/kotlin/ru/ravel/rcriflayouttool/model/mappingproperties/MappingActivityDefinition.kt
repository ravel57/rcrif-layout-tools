package ru.ravel.rcriflayouttool.model.mappingproperties

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement


@JacksonXmlRootElement(localName = "MappingActivityDefinition")
@JsonIgnoreProperties(ignoreUnknown = true)
data class MappingActivityDefinition @JsonCreator constructor(
	@JacksonXmlProperty(isAttribute = true, localName = "ReferenceName")
	@JsonProperty("ReferenceName")
	val referenceName: String,

	@JacksonXmlProperty(localName = "Header")
	@JsonProperty("Header")
	val header: Header,

	@JacksonXmlProperty(localName = "ReferredDocuments")
	@JsonProperty("ReferredDocuments")
	val referredDocuments: ReferredDocuments,

	@JacksonXmlProperty(localName = "XsltDataMapping")
	@JsonProperty("XsltDataMapping")
	val xsltDataMapping: XsltDataMapping? = null
)