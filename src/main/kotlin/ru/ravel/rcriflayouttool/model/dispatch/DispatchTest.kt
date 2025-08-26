package ru.ravel.rcriflayouttool.model.dispatch

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


data class DispatchTest(
	@JacksonXmlProperty(localName = "DispatchRuleID")
	val dispatchRuleId: String? = null,

	@JacksonXmlProperty(localName = "BusinessRuleID")
	val businessRuleId: String? = null
)