package ru.ravel.rcriflayouttool.model.dispatch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement


@JacksonXmlRootElement(localName = "DispatchActivityDefinition")
@JsonIgnoreProperties(ignoreUnknown = true)
data class Dispatch(
	@JacksonXmlProperty(isAttribute = true, localName = "ReferenceName")
	val referenceName: String,

	@JacksonXmlProperty(localName = "Header")
	val header: Header? = null,

	@JacksonXmlProperty(localName = "DispatchRuleIDs")
	val dispatchRuleIDs: DispatchRuleIDs? = null
)