package ru.ravel.rcriflayouttool.model.sendemail

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty
import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement


@JacksonXmlRootElement(localName = "SendEMailActivityDefinition")
@JsonIgnoreProperties(ignoreUnknown = true)
data class SendEmail(
	@JacksonXmlProperty(isAttribute = true, localName = "ReferenceName")
	val referenceName: String,

	@JacksonXmlProperty(localName = "Header")
	val header: Header? = null,

	@JacksonXmlProperty(localName = "ReferredDocuments")
	val referredDocuments: ReferredDocuments? = null,

	@JacksonXmlProperty(localName = "MailMessage")
	val mailMessage: MailMessage? = null,

	@JacksonXmlProperty(localName = "DocumentCulture")
	val documentCulture: DocumentCulture? = null
)