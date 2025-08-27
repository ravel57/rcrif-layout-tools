module ru.ravel.rcriflayouttool {
	requires javafx.controls;
	requires javafx.fxml;
	requires kotlin.stdlib;
	requires com.fasterxml.jackson.dataformat.xml;
	requires com.fasterxml.jackson.annotation;
	requires org.fxmisc.flowless;
	requires org.fxmisc.richtext;
	requires reactfx;
	requires io.github.javadiffutils;
	requires com.fasterxml.jackson.databind;
	requires org.eclipse.jgit;
	requires com.fasterxml.jackson.kotlin;
	requires java.xml;
	requires org.apache.poi.ooxml;


	opens ru.ravel.rcriflayouttool to javafx.graphics, com.fasterxml.jackson.databind;
	opens ru.ravel.rcriflayouttool.model.layout to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.procedure to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.datasource to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.datamapping to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.segmentationtree to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml, kotlin.reflect;
	opens ru.ravel.rcriflayouttool.model.form to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml, kotlin.reflect;
	opens ru.ravel.rcriflayouttool.model.dispatch to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml, kotlin.reflect;
	opens ru.ravel.rcriflayouttool.model.setvalue to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.bizrule to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.sendemail to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.setphase to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.wait to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	opens ru.ravel.rcriflayouttool.model.procedurereturn to com.fasterxml.jackson.databind, com.fasterxml.jackson.dataformat.xml;
	exports ru.ravel.rcriflayouttool.util;
	exports ru.ravel.rcriflayouttool;
}