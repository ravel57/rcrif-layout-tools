package ru.ravel.rcriflayouttool.model.mappingproperties

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty

data class Header(
	@JacksonXmlProperty(localName = "DisplayName")
	val displayName: MnemonicWrapper,

	@JacksonXmlProperty(localName = "Description")
	val description: MnemonicWrapper,

	@JacksonXmlProperty(localName = "Documentation")
	val documentation: String?,

	@JacksonXmlProperty(localName = "SkipTracing")
	val skipTracing: Boolean,

	@JacksonXmlProperty(localName = "AuditBusinessData")
	val auditBusinessData: Boolean
)