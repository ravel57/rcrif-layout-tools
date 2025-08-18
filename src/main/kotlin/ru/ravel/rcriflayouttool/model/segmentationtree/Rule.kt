package ru.ravel.rcriflayouttool.model.segmentationtree

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class Rule(
	@JacksonXmlProperty(localName = "RuleID")
	val ruleID: String,

	@JacksonXmlProperty(localName = "ConnectionID")
	val connectionID: String,

	@JacksonXmlProperty(localName = "ExecutionOrder")
	val executionOrder: Int
)