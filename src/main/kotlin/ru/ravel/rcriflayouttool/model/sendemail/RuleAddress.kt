package ru.ravel.rcriflayouttool.model.sendemail

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class RuleAddress(
	@JacksonXmlProperty(localName = "RuleID")
	val ruleId: String? = null,

	@JacksonXmlProperty(localName = "XPath")
	val xPath: String? = null
)