package ru.ravel.rcriflayouttool.model.dispatch

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


@JsonIgnoreProperties(ignoreUnknown = true)
data class DispatchRuleIDs(
	@JacksonXmlElementWrapper(useWrapping = false)
	@JacksonXmlProperty(localName = "DispatchRuleID")
	val dispatchRuleIds: List<String>? = emptyList(),

	@JacksonXmlProperty(localName = "UseUnassignedExit")
	val useUnassignedExit: Boolean? = null,

	@JacksonXmlProperty(localName = "UseFailExit")
	val useFailExit: Boolean? = null,

	@JacksonXmlElementWrapper(useWrapping = false)
	@JacksonXmlProperty(localName = "DispatchTest")
	val dispatchTests: List<DispatchTest>? = emptyList()
)