package ru.ravel.rcriflayouttool.model.bizrule

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class ReferredDocument(
	@JacksonXmlProperty(localName = "ReferenceName", isAttribute = true)
	val referenceName: String,

	@JacksonXmlProperty(localName = "Access", isAttribute = true)
	val access: String
)