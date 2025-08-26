package ru.ravel.rcriflayouttool.model.sendemail

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class DocumentCulture(
	@JacksonXmlProperty(localName = "DocumentID")
	val documentId: String? = null,

	@JacksonXmlProperty(localName = "XPath")
	val xPath: String? = null,

	@JacksonXmlProperty(localName = "Alias")
	val alias: String? = null
)