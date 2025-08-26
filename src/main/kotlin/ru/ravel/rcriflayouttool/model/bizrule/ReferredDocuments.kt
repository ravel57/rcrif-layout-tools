package ru.ravel.rcriflayouttool.model.bizrule

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class ReferredDocuments(
	@JacksonXmlProperty(localName = "ReferredDocument")
	@JacksonXmlElementWrapper(useWrapping = false)
	val referredDocuments: List<ReferredDocument>? = emptyList()
)