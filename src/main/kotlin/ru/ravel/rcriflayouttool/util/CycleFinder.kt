package ru.ravel.rcriflayouttool.util

import com.fasterxml.jackson.dataformat.xml.XmlMapper
import ru.ravel.rcriflayouttool.model.layout.DiagramLayout
import java.io.File


object CycleFinder {
	data class Result(
		val cycles: List<List<String>>,  // списки Reference из одной SCC/цикла
		val totalNodes: Int,
		val totalEdges: Int,
	)

	fun findCycles(file: File): Result {
		val xmlMapper = XmlMapper()
		val layout: DiagramLayout = xmlMapper.readValue(file, DiagramLayout::class.java)
		// 1. UID -> Reference
		val uidToRef: Map<String, String> = layout.elements?.diagramElements
			?.mapNotNull { el ->
				val uid = el.uid
				val ref = el.reference
				if (!uid.isNullOrBlank() && !ref.isNullOrBlank()) uid to ref else null
			}
			?.toMap()
			?: emptyMap()

		// 2. Построение графа
		val graph = LinkedHashMap<String, MutableList<String>>()
		uidToRef.values.forEach { graph[it] = mutableListOf() }
		var edgeCount = 0
		layout.connections?.diagramConnections?.forEach { conn ->
			val endPoints = conn.endPoints?.points ?: emptyList()
			if (endPoints.size >= 2) {
				val srcUid = endPoints[0].elementRef
				val dstUid = endPoints[1].elementRef
				val srcRef = srcUid?.let(uidToRef::get)
				val dstRef = dstUid?.let(uidToRef::get)
				if (srcRef != null && dstRef != null) {
					graph[srcRef]?.add(dstRef)
					edgeCount++
				}
			}
		}
		// 3. Алгоритм Тарьяна для поиска SCC
		val index = HashMap<String, Int>()
		val low = HashMap<String, Int>()
		val stack = ArrayDeque<String>()
		val onStack = HashSet<String>()
		val sccs = mutableListOf<List<String>>()
		var idx = 0

		fun strongConnect(v: String) {
			index[v] = idx
			low[v] = idx
			idx++
			stack.addLast(v)
			onStack.add(v)
			for (w in graph[v].orEmpty()) {
				if (!index.containsKey(w)) {
					strongConnect(w)
					low[v] = minOf(low[v]!!, low[w]!!)
				} else if (w in onStack) {
					low[v] = minOf(low[v]!!, index[w]!!)
				}
			}
			if (low[v] == index[v]) {
				val comp = mutableListOf<String>()
				while (true) {
					val w = stack.removeLast()
					onStack.remove(w)
					comp.add(w)
					if (w == v) break
				}
				sccs += comp
			}
		}

		graph.keys.forEach { if (!index.containsKey(it)) strongConnect(it) }
		// 4. Фильтруем только циклы
		val edgeSet = graph.entries
			.flatMap { (u, vs) -> vs.map { v -> u to v } }
			.toSet()
		val cycles = sccs.filter {
			it.size > 1 || (it.size == 1 && edgeSet.contains(it[0] to it[0]))
		}
		return Result(
			cycles = cycles,
			totalNodes = graph.size,
			totalEdges = edgeCount
		)
	}
}
