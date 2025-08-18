package ru.ravel.rcriflayouttool.model.segmentationtree

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class Rules(
	@JacksonXmlElementWrapper(useWrapping = false)
	@JacksonXmlProperty(localName = "Rule")
	val ruleList: List<Rule> = emptyList()
)