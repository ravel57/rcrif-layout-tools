package ru.ravel.rcriflayouttool.model.bizrule

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


@JsonIgnoreProperties(ignoreUnknown = true)
data class Header(
	@JacksonXmlProperty(localName = "DisplayName")
	val displayName: DisplayName,

	@JacksonXmlProperty(localName = "Description")
	val description: Description,

	@JacksonXmlProperty(localName = "Documentation")
	val documentation: String? = null,

	@JacksonXmlProperty(localName = "SkipTracing")
	val skipTracing: Boolean,

	@JacksonXmlProperty(localName = "AuditBusinessData")
	val auditBusinessData: Boolean
)