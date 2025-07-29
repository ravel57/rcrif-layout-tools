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
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Priority
import javafx.scene.layout.VBox
import javafx.stage.DirectoryChooser
import javafx.stage.Stage
import javafx.util.Callback
import org.fxmisc.flowless.VirtualizedScrollPane
import org.fxmisc.richtext.CodeArea
import ru.ravel.rcriflayouttool.dto.ActivitiesForMenu
import ru.ravel.rcriflayouttool.dto.Activity
import ru.ravel.rcriflayouttool.dto.LayoutActivity
import ru.ravel.rcriflayouttool.dto.ParamRow
import ru.ravel.rcriflayouttool.model.connectorproperties.DataSourceActivityDefinition
import ru.ravel.rcriflayouttool.model.layout.DiagramLayout
import ru.ravel.rcriflayouttool.model.procedureproperties.ProcedureCallActivityDefinition
import java.io.File
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean


class RCrifLayoutTool : Application() {

	private var selectedDirectory: File? = null
	private var layoutActivitiesCache: List<ActivitiesForMenu> = emptyList()


	override fun start(stage: Stage) {
		val allProcedures = FXCollections.observableArrayList<String>()
		val filteredProcedures = FilteredList(allProcedures) { true }

		val allConnectors = FXCollections.observableArrayList<String>()
		val filteredConnectors = FilteredList(allConnectors) { true }

		val allMarge = FXCollections.observableArrayList<String>()
		val filteredMarge = FilteredList(allMarge) { true }

		val folderField = TextField().apply {
			isEditable = false
			promptText = "Рабочая папка кредитного процесса"
		}

		/* Поиск процедур */
		val searchProcedureTableColumn = TableColumn<ParamRow, String>("Название в процессе").apply {
			cellValueFactory = Callback { it.value.field }
			isEditable = true
		}
		val procedureTableView = TableView<ParamRow>().apply {
			columns.addAll(searchProcedureTableColumn)
			isEditable = true
			setRowFactory { _ ->
				val row = TableRow<ParamRow>()
				row.setOnContextMenuRequested { event ->
					if (layoutActivitiesCache.isEmpty()) return@setOnContextMenuRequested
					val menu = ContextMenu()
					val items = layoutActivitiesCache
						.filter { la -> la.reference == row.item.field.value }
						.flatMap { a ->
							List(a.toActivity.size) { index -> "${a.exitName[index]} <-> ${a.toActivity[index]}" }
						}
					menu.items.addAll(items.distinct().map { MenuItem(it) })
					menu.show(row, event.screenX, event.screenY)
				}
				row
			}
		}
		searchProcedureTableColumn.prefWidthProperty().bind(procedureTableView.widthProperty().subtract(2))

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
						val activities = getProceduresActivities(newVal)
						layoutActivitiesCache = activities
						procedureTableView.items.clear()
						procedureTableView.items.addAll(activities.map { ParamRow(SimpleStringProperty(it.reference)) })
					}
				}
			}
		}

		val lastPath = loadSelectedPath()
		if (lastPath != null) {
			selectedDirectory = File(lastPath)
			if (selectedDirectory?.exists() == true) {
				folderField.text = lastPath
				allProcedures.setAll(File(selectedDirectory, "Procedures").list()?.sorted() ?: emptyList())
				allConnectors.setAll(getAllConnectors(lastPath).map { it.connectorName }.distinct())
				allMarge.setAll(File(selectedDirectory, "Procedures").list()
					?.sorted()
					?.toMutableList()
					?.apply { add(0, "MainFlow") }
					?: emptyList())
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
					allMarge.setAll(File(selectedDirectory, "Procedures").list()
						?.sorted()
						?.toMutableList()
						?.apply { add(0, "MainFlow") }
						?: emptyList())
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

		val proceduresTabContent = VBox(
			10.0,
			HBox(Label("Название процедуры:"), procedureComboBox).apply { padding = Insets(5.0) },
			procedureTableView
		).apply {
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
			HBox(Label("Название коннектора:"), connectorsComboBox).apply { padding = Insets(5.0) },
			connectorTableView
		).apply {
			padding = Insets(20.0)
		}

		/* Marge */
		val mergeComboBox = ComboBox(filteredMarge).apply {
			isEditable = true
			promptText = "Layout процедуры"
			var guard = false
			editor.textProperty().addListener { _, _, newValue ->
				Platform.runLater {
					if (guard) return@runLater
					guard = true
					val predicate: (String) -> Boolean = { s ->
						newValue.isNullOrBlank() || s.contains(newValue, ignoreCase = true)
					}
					filteredMarge.setPredicate(predicate)
					if (newValue.isNullOrBlank()) {
						selectionModel.clearSelection()
						value = null
						hide()
					} else {
						if (filteredMarge.isNotEmpty()) show() else hide()
					}
					guard = false
				}
			}
			valueProperty().addListener { _, _, newVal ->
				Platform.runLater {
					if (newVal != null) {
						filteredMarge.setPredicate { true }
						editor.text = newVal
						hide()
					}
				}
			}
		}

		val margeArea1 = CodeArea()
		val margeArea2 = CodeArea()
		val margePane1 = VirtualizedScrollPane(margeArea1).apply {
			maxHeight = Double.MAX_VALUE
		}
		val margePane2 = VirtualizedScrollPane(margeArea2).apply {
			maxHeight = Double.MAX_VALUE
		}
		bindScrollSync(margePane1, margePane2)

		val panesHBox = HBox(4.0, Label("Layout: "), mergeComboBox).apply {
			padding = Insets(5.0)
		}

		// 2) Непосредственно редакторы
		val contentBox = HBox(4.0, margePane1, margePane2).apply {
			spacing = 4.0
			isFillHeight = true
			HBox.setHgrow(margePane1, Priority.ALWAYS)
			HBox.setHgrow(margePane2, Priority.ALWAYS)
			margePane1.maxHeight = Double.MAX_VALUE
			margePane2.maxHeight = Double.MAX_VALUE
		}

		val margeBp = BorderPane().apply {
			top = panesHBox
			center = contentBox
			padding = Insets(20.0)
		}
		VBox.setVgrow(margeBp, Priority.ALWAYS)

		/* Добавление связей */
		val emptyTabContent = VBox(20.0, Label("Пусто"))

		/* Сборка вкладок */
		val tabPane = TabPane(
			Tab("Поиск использования процедур", proceduresTabContent).apply { isClosable = false },
			Tab("Поиск использования коннекторов", connectorsTabContent).apply { isClosable = false },
			Tab("Layout marge", margeBp).apply { isClosable = false },
			Tab("Добавление связей", emptyTabContent).apply { isClosable = false },
		)

		VBox.setVgrow(tabPane, Priority.ALWAYS)

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
		Platform.runLater {
			procedureComboBox.requestFocus()
			procedureComboBox.editor.requestFocus()
		}
	}


	private fun getProceduresActivities(selectedProcedure: String): List<ActivitiesForMenu> {
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

		val diagramLayout = File(selectedDirectory, "Procedures")
			.listFiles { file -> File(file, "Layout.xml").exists() }
			?.map { file -> File(file, "Layout.xml") }
			?.mapNotNull { file ->
				file.inputStream().use { input -> mapper.readValue(input, DiagramLayout::class.java) }
			}
			?.toMutableList()
		val diagramLayoutMainFlow = File(selectedDirectory, "MainFlow/Layout.xml").let { file ->
			file.takeIf { it.exists() }?.let { mapper.readValue(it, DiagramLayout::class.java) }
		}
		diagramLayoutMainFlow?.let { diagramLayout?.add(0, it) }
		val layoutActivities = diagramLayout
			?.flatMap { layout ->
				layout.elements?.diagramElements
					?.filter { de ->
						(pcNamesFromProcedures + pcNamesFromMainFlow).map { it.referenceName }.contains(de.reference)
					}
					?.map { de -> Activity(uid = de.uid!!, reference = de.reference!!) }
					?: emptyList()
			}
			?.flatMap {
				diagramLayout.flatMap { dl ->
					dl.connections?.diagramConnections
						?.map { dc ->
							val exitName = dc.endPoints?.points?.firstOrNull { p -> p.elementRef == it.uid }?.exitPointRef ?: ""
							val anotherUid = dc.endPoints?.points?.firstOrNull { p -> p.elementRef != exitName }?.elementRef ?: ""
							val toActivity = dl.elements?.diagramElements
								?.firstOrNull { de -> de.uid == anotherUid }?.reference ?: ""
							LayoutActivity(
								exitName = exitName,
								name = selectedProcedure,
								reference = it.reference,
								toActivity = toActivity
							)
						}
						?.filter { la -> la.exitName.isNotEmpty() }
						?: emptyList()
				}
			}
			?.groupBy { it.reference }
			?.map { (reference, list) ->
				ActivitiesForMenu(
					list[0].name,
					reference,
					list.map { it.exitName },
					list.map { it.toActivity })
			}
			?: emptyList()

		return layoutActivities
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


	private fun bindScrollSync(p1: VirtualizedScrollPane<*>, p2: VirtualizedScrollPane<*>) {
		val guard = AtomicBoolean(false)

		fun clampRatio(r: Double) = r.coerceIn(0.0, 1.0)

		listOf(p1 to p2, p2 to p1).forEach { (src, dst) ->
			src.addEventFilter(ScrollEvent.SCROLL) { ev ->
				if (ev.deltaY == 0.0 || !guard.compareAndSet(false, true)) return@addEventFilter
				dst.scrollYBy(-ev.deltaY)
				guard.set(false)
			}
		}

		listOf(p1 to p2, p2 to p1).forEach { (src, dst) ->
			src.estimatedScrollYProperty().addListener { _, _, newY ->
				if (!guard.compareAndSet(false, true)) return@addListener
				Platform.runLater {
					val totalSrc = src.totalHeightEstimateProperty().value
					val totalDst = dst.totalHeightEstimateProperty().value
					if (totalSrc > 0 && totalDst > 0) {
						val ratio = clampRatio(newY.toDouble() / totalSrc)
						dst.scrollYToPixel(ratio * totalDst)
					}
					guard.set(false)
				}
			}
		}
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