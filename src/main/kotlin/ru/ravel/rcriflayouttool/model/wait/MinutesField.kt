package ru.ravel.rcriflayouttool.model.wait

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class MinutesField(
	@JacksonXmlProperty(localName = "DocumentID")
	val documentId: String? = null,

	@JacksonXmlProperty(localName = "XPath")
	val xPath: String? = null,

	@JacksonXmlProperty(localName = "Alias")
	val alias: String? = null
)