package ru.ravel.rcriflayouttool.model.procedureproperties

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement


@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "ProcedureCallActivityDefinition")
data class ProcedureCallActivityDefinition(
	@JacksonXmlProperty(isAttribute = true, localName = "ReferenceName")
	val referenceName: String? = null,

	@JacksonXmlProperty(localName = "Header")
	val header: Header? = null,

	@JacksonXmlProperty(localName = "ProcedureToCall")
	val procedureToCall: String? = null
)