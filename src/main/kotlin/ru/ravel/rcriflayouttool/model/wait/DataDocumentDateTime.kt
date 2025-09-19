package ru.ravel.rcriflayouttool.model.wait

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class DataDocumentDateTime(
	@JacksonXmlProperty(localName = "DocumentID")
	val documentId: String? = null,

	@JacksonXmlProperty(localName = "XPath")
	val xPath: String? = null,

	@JacksonXmlProperty(localName = "Alias")
	val alias: String? = null
)