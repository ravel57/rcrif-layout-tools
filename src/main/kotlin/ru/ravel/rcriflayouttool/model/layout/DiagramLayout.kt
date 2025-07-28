package ru.ravel.rcriflayouttool.model.layout

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement

@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "DiagramLayout")
data class DiagramLayout(
	@JacksonXmlProperty(isAttribute = true, localName = "UID")
	val uid: String? = null,

	@JacksonXmlProperty(localName = "Elements")
	val elements: Elements? = null,

	@JacksonXmlProperty(localName = "Connections")
	val connections: Connections? = null,

	@JacksonXmlProperty(localName = "StartElement")
	val startElement: String? = null,
)