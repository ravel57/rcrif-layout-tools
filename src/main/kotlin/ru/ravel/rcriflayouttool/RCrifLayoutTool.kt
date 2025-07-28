package ru.ravel.rcriflayouttool

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import javafx.application.Application
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.collections.transformation.FilteredList
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.util.Callback
import ru.ravel.rcriflayouttool.model.Activity
import ru.ravel.rcriflayouttool.model.LayoutActivity
import ru.ravel.rcriflayouttool.model.ParamRow
import ru.ravel.rcriflayouttool.model.connectorproperties.DataSourceActivityDefinition
import ru.ravel.rcriflayouttool.model.layout.DiagramLayout
import ru.ravel.rcriflayouttool.model.procedureproperties.ProcedureCallActivityDefinition
import java.io.File
import java.util.*


class RCrifLayoutTool : Application() {

	private var selectedDirectory: File? = null


	override fun start(stage: Stage) {
		val allProcedures = FXCollections.observableArrayList<String>()
		val filteredProcedures = FilteredList(allProcedures) { true }

		val allConnectors = FXCollections.observableArrayList<String>()
		val filteredConnectors = FilteredList(allConnectors) { true }

		val folderField = TextField().apply {
			isEditable = false
			promptText = "Рабочая папка кредитного процесса"
		}

		/* Поиск процедур */
		val searchProcedureTableColumn = TableColumn<ParamRow, String>("Название в процессе").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val searchProcedureTableView = TableView<ParamRow>().apply {
			columns.addAll(searchProcedureTableColumn)
			isEditable = true
		}
		searchProcedureTableColumn.prefWidthProperty().bind(searchProcedureTableView.widthProperty().subtract(2))

		val procedureComboBox = ComboBox(filteredProcedures).apply {
			isEditable = true
			promptText = "Название процедуры"
			var guard = false
			editor.textProperty().addListener { _, _, newValue ->
				Platform.runLater {
					if (guard) return@runLater
					guard = true
					val predicate: (String) -> Boolean = { s ->
						newValue.isNullOrBlank() || s.contains(newValue, ignoreCase = true)
					}
					filteredProcedures.setPredicate(predicate)
					if (newValue.isNullOrBlank()) {
						selectionModel.clearSelection()
						value = null
						hide()
					} else {
						if (filteredProcedures.isNotEmpty()) show() else hide()
					}
					guard = false
				}
			}
			valueProperty().addListener { _, _, newVal ->
				Platform.runLater {
					if (newVal != null) {
						filteredProcedures.setPredicate { true }
						editor.text = newVal
						hide()
						searchProcedureTableView.items.clear()
						val layoutActivities = getProceduresReferences(newVal)
						searchProcedureTableView.items.addAll(
							layoutActivities.map { it.reference }.map { ParamRow(SimpleStringProperty(it)) }
						)
					}
				}
			}
		}

		val lastPath = loadSelectedPath()
		if (lastPath != null) {
			selectedDirectory = File(lastPath)
			if (selectedDirectory?.exists() == true) {
				folderField.text = lastPath
				val list = File(selectedDirectory, "Procedures").list()?.sorted() ?: emptyList()
				allProcedures.setAll(list)
				allConnectors.setAll(getAllConnectors(lastPath).map { it.connectorName }.distinct())
			}
		}
		val chooseFolderButton = Button("Выбрать папку").apply {
			setOnAction {
				val initialDir = lastPath?.let { File(it).parentFile }
				val chooser = DirectoryChooser().apply {
					title = "Выберите рабочую папку"
					if (initialDir?.exists() == true) {
						initialDirectory = initialDir
					}
				}
				selectedDirectory = chooser.showDialog(stage)
				if (selectedDirectory != null) {
					folderField.text = selectedDirectory!!.absolutePath
					saveSelectedPath(selectedDirectory!!.absolutePath)
					allProcedures.setAll(File(selectedDirectory, "Procedures").list()?.sorted() ?: emptyList())
					allConnectors.setAll(getAllConnectors(selectedDirectory!!.absolutePath).map { it.connectorName }.distinct())
					procedureComboBox.selectionModel.clearSelection()
					procedureComboBox.value = null
					procedureComboBox.editor.clear()
					filteredProcedures.setPredicate { true }
				}
			}
		}

		val folderChooserPanel = HBox(5.0, folderField, chooseFolderButton).apply {
			HBox.setHgrow(folderField, Priority.ALWAYS)
		}

		val proceduresTabContent = VBox(10.0, Label("Название процедуры:"), procedureComboBox, searchProcedureTableView).apply {
			padding = Insets(20.0)
		}

		/* Поиск коннекторов */
		val connectorTableColumn = TableColumn<ParamRow, String>("Название в активности").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val connectorTableView = TableView<ParamRow>().apply {
			columns.addAll(connectorTableColumn)
			isEditable = true
		}
		connectorTableColumn.prefWidthProperty().bind(connectorTableView.widthProperty().subtract(2.0))

		val connectorsComboBox = ComboBox(filteredConnectors).apply {
			isEditable = true
			promptText = "Название коннектора"
			var guard = false
			editor.textProperty().addListener { _, _, newValue ->
				Platform.runLater {
					if (guard) return@runLater
					guard = true
					val predicate: (String) -> Boolean = { s ->
						newValue.isNullOrBlank() || s.contains(newValue, ignoreCase = true)
					}
					filteredConnectors.setPredicate(predicate)
					if (newValue.isNullOrBlank()) {
						selectionModel.clearSelection()
						value = null
						hide()
					} else {
						if (filteredConnectors.isNotEmpty()) show() else hide()
					}
					guard = false
				}
			}
			valueProperty().addListener { _, _, newVal ->
				Platform.runLater {
					if (newVal != null) {
						filteredConnectors.setPredicate { true }
						editor.text = newVal
						hide()
						connectorTableView.items.clear()
						connectorTableView.items.addAll(getConnectorReferences(folderField.text, newVal).map {
							ParamRow(SimpleStringProperty(it))
						})
					}
				}
			}
		}
		val connectorsTabContent = VBox(
			10.0,
			Label("Название коннектора:"),
			connectorsComboBox,
			connectorTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Добавление связей */
		val emptyTabContent = VBox(20.0, Label("Пусто"))

		val tabPane = TabPane(
			Tab("Поиск использования процедур", proceduresTabContent).apply { isClosable = false },
			Tab("Поиск использования коннекторов", connectorsTabContent).apply { isClosable = false },
			Tab("Добавление связей", emptyTabContent).apply { isClosable = false },
		)

		val root = VBox(
			10.0,
			folderChooserPanel,
			tabPane
		).apply {
			padding = Insets(20.0)
		}

		stage.scene = Scene(root, 600.0, 500.0)
		stage.title = "rCrif Layout Tool"
		stage.show()
	}


	private fun getProceduresReferences(selectedProcedure: String): List<LayoutActivity> {
		val mapper = XmlMapper()

		val pcNamesFromMainFlow = File(selectedDirectory, "MainFlow")
			.listFiles { file -> file.isDirectory }
			?.mapNotNull { dir ->
				val props = File(dir, "Properties.xml")
				if (props.isFile) {
					props.inputStream().use { input ->
						mapper.readValue(input, ProcedureCallActivityDefinition::class.java)
					}
				} else null
			}
			?.filter { it.procedureToCall == selectedProcedure }
			?: emptyList()

		val pcNamesFromProcedures = File(selectedDirectory, "Procedures")
			.walkTopDown()
			.filter { it.isFile && it.name == "Properties.xml" }
			.toList()
			.map { file ->
				file.inputStream().use { input -> mapper.readValue(input, ProcedureCallActivityDefinition::class.java) }
			}
			.filter { it.procedureToCall == selectedProcedure }

		val pcList = pcNamesFromMainFlow + pcNamesFromProcedures

		val diagramLayout = File(selectedDirectory, "Procedures")
			.listFiles { file -> File(file, "Layout.xml").exists() }
			?.map { file -> File(file, "Layout.xml") }
			?.mapNotNull { file ->
				file.inputStream().use { input -> mapper.readValue(input, DiagramLayout::class.java) }
			}
		return diagramLayout
			?.flatMap { layout ->
				layout.elements?.diagramElements
					?.filter { de -> pcNamesFromProcedures.map { it.referenceName }.contains(de.reference) }
					?.map { de -> Activity(uid = de.uid!!, reference = de.reference!!) }
					?: emptyList()
			}
			?.flatMap {
				diagramLayout.flatMap { dl ->
					dl.connections?.diagramConnections
						?.map { dc ->
							val exitName = dc.endPoints?.points?.firstOrNull { p -> p.elementRef == it.uid }?.exitPointRef ?: ""
							val procedureToCall =
								pcList.firstOrNull { pc -> pc.referenceName == it.reference }?.procedureToCall ?: ""
							LayoutActivity(exitName = exitName, name = procedureToCall, reference = it.reference)
						}
						?.filter { la -> la.exitName.isNotEmpty() && la.name.isNotEmpty() }
						?: emptyList()
				}
			}
			?.distinct()
			?: emptyList()
	}


	private fun getAllConnectors(selectedProcess: String): List<DataSourceActivityDefinition> {
		val mapper = XmlMapper()

		val dataSourceActivityDefinitions = File(selectedProcess, "Procedures")
			.walkTopDown()
			.filter { it.isFile && it.name == "Properties.xml" }
			.toList()
			.map { file ->
				file.inputStream().use { input -> mapper.readValue(input, DataSourceActivityDefinition::class.java) }
			}
			.filter { activity -> activity.connectorName != null }

		val propertiesFile = File(selectedProcess, "MainFlow")
			.walkTopDown()
			.filter { it.isFile && it.name == "Properties.xml" }
			.toList()
			.map { file ->
				file.inputStream().use { input -> mapper.readValue(input, DataSourceActivityDefinition::class.java) }
			}
			.filter { activity -> activity.connectorName != null }

		return dataSourceActivityDefinitions + propertiesFile
	}


	private fun getConnectorReferences(selectedProcess: String, selectedConnectorName: Any): List<String> {
		return getAllConnectors(selectedProcess)
			.filter { activity -> activity.connectorName == selectedConnectorName }
			.map { activity -> activity.referenceName ?: "" }
	}


	private fun getConfigFile(): File {
		val os = System.getProperty("os.name").lowercase()
		val configDir = when {
			os.contains("win") -> File(System.getenv("APPDATA"), "rcrif-layout-tool")
			os.contains("mac") -> File(System.getProperty("user.home"), "Library/Application Support/rcrif-layout-tool")
			else -> File(System.getProperty("user.home"), ".config/rcrif-layout-tool")
		}
		if (!configDir.exists()) {
			configDir.mkdirs()
		}
		return File(configDir, SAVE_FILE_NAME)
	}


	private fun saveSelectedPath(path: String) {
		val props = Properties()
		props[SAVE_FOLDER_NAME] = path
		getConfigFile().outputStream().use { props.store(it, null) }
	}


	private fun loadSelectedPath(): String? {
		val file = getConfigFile()
		if (!file.exists()) return null
		val props = Properties()
		file.inputStream().use { props.load(it) }
		return props.getProperty(SAVE_FOLDER_NAME)
	}


	companion object {
		private const val SAVE_FILE_NAME = "config.properties"
		private const val SAVE_FOLDER_NAME = "selectedDirectory"
	}

}


fun main() {
	Application.launch(RCrifLayoutTool::class.java)
}