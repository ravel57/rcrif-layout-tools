package ru.ravel.rcriflayouttool.model.dispatch

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


data class DispatchTest(
	@JacksonXmlProperty(localName = "DispatchRuleID")
	val dispatchRuleID: String? = null,

	@JacksonXmlProperty(localName = "BusinessRuleID")
	val businessRuleID: String? = null
)