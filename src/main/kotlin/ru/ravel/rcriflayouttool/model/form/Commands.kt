package ru.ravel.rcriflayouttool.model.form

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class Commands(
	@JacksonXmlElementWrapper(useWrapping = false)
	@JacksonXmlProperty(localName = "ActivityCommand")
	val activityCommands: List<ActivityCommand> = emptyList()
)