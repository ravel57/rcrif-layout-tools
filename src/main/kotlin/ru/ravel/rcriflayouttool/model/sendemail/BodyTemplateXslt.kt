package ru.ravel.rcriflayouttool.model.sendemail

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class BodyTemplateXslt(
	@JacksonXmlProperty(localName = "Mode")
	val mode: String? = null,

	@JacksonXmlProperty(localName = "Source")
	val source: String? = null
)