package ru.ravel.rcriflayouttool.model.sendemail

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty


data class MailMessage(
	@JacksonXmlElementWrapper(useWrapping = false)
	@JacksonXmlProperty(localName = "MailTo")
	val mailTo: List<MailTo>? = emptyList(),

	@JacksonXmlProperty(localName = "Subject")
	val subject: String? = null,

	@JacksonXmlProperty(localName = "BodyTemplateXslt")
	val bodyTemplateXslt: BodyTemplateXslt? = null
)