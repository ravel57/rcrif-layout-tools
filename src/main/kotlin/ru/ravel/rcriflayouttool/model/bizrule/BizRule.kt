package ru.ravel.rcriflayouttool.model.bizrule

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement


@JsonIgnoreProperties(ignoreUnknown = true)
@JacksonXmlRootElement(localName = "BizRuleActivityDefinition")
data class BizRule(
	@JacksonXmlProperty(localName = "ReferenceName", isAttribute = true)
	val referenceName: String,

	@JacksonXmlProperty(localName = "Header")
	val header: Header? = null,

	@JacksonXmlProperty(localName = "ReferredDocuments")
	val referredDocuments: ReferredDocuments? = null,

	@JacksonXmlProperty(localName = "XmlRule")
	val xmlRule: String? = null
)