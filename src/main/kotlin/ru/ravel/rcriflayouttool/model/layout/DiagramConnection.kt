package ru.ravel.rcriflayouttool.model.layout

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


data class DiagramConnection(
	@JacksonXmlProperty(isAttribute = true, localName = "UID")
	val uid: String? = null,

	@JacksonXmlProperty(localName = "Splits")
	val splits: Splits? = null,

	@JacksonXmlProperty(localName = "EndPoints")
	val endPoints: EndPoints? = null
)