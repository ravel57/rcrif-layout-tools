package ru.ravel.rcriflayouttool.model.wait

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


@JsonIgnoreProperties(ignoreUnknown = true)
data class WaitDetails(
	@JacksonXmlElementWrapper(useWrapping = false)
	@JacksonXmlProperty(localName = "ExitTimeout")
	val exitTimeout: List<ExitTimeout>? = null,

	@JacksonXmlProperty(localName = "UpdaterRoleID")
	val updaterRoleId: String? = null,

	@JacksonXmlProperty(localName = "OnlyOneExitTimeOut")
	val onlyOneExitTimeOut: Int? = null,

	@JacksonXmlProperty(localName = "Notifications")
	val notifications: Notifications? = null
)