package ru.ravel.rcriflayouttool.model.sendemail

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class DocumentAddress(
	@JacksonXmlProperty(localName = "DocumentID")
	val documentId: String? = null,

	@JacksonXmlProperty(localName = "XPath")
	val xPath: String? = null
)