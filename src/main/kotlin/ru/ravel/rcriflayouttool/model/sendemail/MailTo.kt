package ru.ravel.rcriflayouttool.model.sendemail

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class MailTo(
	@JacksonXmlProperty(localName = "DocumentAddress")
	val documentAddress: DocumentAddress? = null,

	@JacksonXmlProperty(localName = "RuleAddress")
	val ruleAddress: String? = null
)